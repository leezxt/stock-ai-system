package com.example.stockai.stock;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;

import com.example.stockai.common.ExternalApiKeyHeaderResolver;
import com.example.stockai.market.Market;
import com.example.stockai.market.SymbolNormalizer;

@Service
class FinMindTwMarketDataProvider {
    private static final Duration INFO_CACHE_TTL = Duration.ofHours(12);

    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final ExternalApiKeyHeaderResolver apiKeyHeaderResolver;
    private final String baseUrl;
    private final String token;

    private volatile CachedRows cachedInfoRows;

    @Autowired
    FinMindTwMarketDataProvider(
        ExternalApiKeyHeaderResolver apiKeyHeaderResolver,
        @Value("${stockai.finmind.base-url:${FINMIND_BASE_URL:https://api.finmindtrade.com/api/v4}}") String baseUrl,
        @Value("${stockai.finmind.token:${FINMIND_API_TOKEN:}}") String token
    ) {
        this(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
            JsonParserFactory.getJsonParser(),
            apiKeyHeaderResolver,
            baseUrl,
            token
        );
    }

    FinMindTwMarketDataProvider(HttpClient httpClient, JsonParser jsonParser, String baseUrl, String token) {
        this(httpClient, jsonParser, null, baseUrl, token);
    }

    FinMindTwMarketDataProvider(HttpClient httpClient, JsonParser jsonParser, ExternalApiKeyHeaderResolver apiKeyHeaderResolver, String baseUrl, String token) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.apiKeyHeaderResolver = apiKeyHeaderResolver;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.token = token == null ? "" : token.trim();
    }

    Optional<StockRecord> tryGet(Market market, String symbol, StockRecord fallback) {
        if (market != Market.TW || isBlank(baseUrl)) {
            return Optional.empty();
        }
        try {
            String normalized = SymbolNormalizer.normalize(market, symbol);
            String code = normalized.replace(".TW", "");
            String name = findStockName(code).orElse(fallback == null ? normalized : fallback.name());
            List<Map<String, Object>> rows = getData("TaiwanStockPrice", Map.of(
                "data_id", code,
                "start_date", LocalDate.now(ZoneOffset.UTC).minusDays(40).toString(),
                "end_date", LocalDate.now(ZoneOffset.UTC).toString()
            ));
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(parsePriceRows(code, name, rows, fallback));
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    Optional<List<StockRecord>> trySearch(Market market, String query) {
        if (market != Market.TW || isBlank(query) || isBlank(baseUrl)) {
            return Optional.empty();
        }
        try {
            String trimmed = query.trim();
            String upper = trimmed.toUpperCase().replace(".TW", "");
            List<Map<String, Object>> matches = getInfoRows().stream()
                .filter(row -> {
                    String stockId = text(row.get("stock_id"), "");
                    String stockName = text(row.get("stock_name"), "");
                    return stockId.contains(upper) || stockName.contains(trimmed);
                })
                .sorted(Comparator.comparingInt(row -> searchRank(text(row.get("stock_id"), ""), text(row.get("stock_name"), ""), upper, trimmed)))
                .collect(java.util.stream.Collectors.toMap(
                    row -> text(row.get("stock_id"), ""),
                    row -> row,
                    (left, right) -> left,
                    LinkedHashMap::new
                ))
                .values().stream()
                .limit(5)
                .toList();
            if (matches.isEmpty()) {
                return Optional.of(List.of());
            }
            List<StockRecord> results = new ArrayList<>();
            for (Map<String, Object> row : matches) {
                String code = text(row.get("stock_id"), "");
                if (code.isBlank()) {
                    continue;
                }
                String normalized = SymbolNormalizer.normalize(Market.TW, code);
                StockRecord fallback = new StockRecord(
                    normalized,
                    text(row.get("stock_name"), normalized),
                    Market.TW,
                    "TWD",
                    null,
                    null,
                    List.of(),
                    "finmind-info"
                );
                tryGet(Market.TW, normalized, fallback).ifPresent(results::add);
            }
            return Optional.of(results);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    static StockRecord parsePriceRows(String stockId, String stockName, List<Map<String, Object>> rows, StockRecord fallback) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("finmind price rows are empty");
        }
        List<Map<String, Object>> sorted = rows.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(row -> text(row.get("date"), "")))
            .toList();
        List<BigDecimal> prices = sorted.stream()
            .map(row -> decimal(row.get("close")))
            .filter(Objects::nonNull)
            .toList();
        if (prices.isEmpty()) {
            throw new IllegalArgumentException("finmind close prices are empty");
        }
        Map<String, Object> lastRow = sorted.get(sorted.size() - 1);
        BigDecimal close = decimal(lastRow.get("close"));
        BigDecimal spread = decimal(lastRow.get("spread"));
        BigDecimal changePercent = changePercent(close, spread, sorted);
        return new StockRecord(
            SymbolNormalizer.normalize(Market.TW, stockId),
            isBlank(stockName) ? SymbolNormalizer.normalize(Market.TW, stockId) : stockName,
            Market.TW,
            "TWD",
            close,
            changePercent,
            List.copyOf(prices),
            "finmind"
        );
    }

    private List<Map<String, Object>> getInfoRows() throws IOException, InterruptedException {
        CachedRows current = cachedInfoRows;
        if (current != null && !current.isExpired()) {
            return current.rows();
        }
        synchronized (this) {
            current = cachedInfoRows;
            if (current != null && !current.isExpired()) {
                return current.rows();
            }
            List<Map<String, Object>> rows = getData("TaiwanStockInfo", Map.of());
            cachedInfoRows = new CachedRows(List.copyOf(rows), Instant.now());
            return rows;
        }
    }

    private Optional<String> findStockName(String code) throws IOException, InterruptedException {
        return getInfoRows().stream()
            .filter(row -> code.equals(text(row.get("stock_id"), "")))
            .map(row -> text(row.get("stock_name"), ""))
            .filter(name -> !name.isBlank())
            .findFirst();
    }

    private List<Map<String, Object>> getData(String dataset, Map<String, String> params) throws IOException, InterruptedException {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("dataset", dataset);
        query.putAll(params);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/data?" + encode(query)))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .GET();
        String resolvedToken = resolvedToken();
        if (!resolvedToken.isBlank()) {
            builder.header("Authorization", "Bearer " + resolvedToken);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("finmind http " + response.statusCode());
        }
        Map<String, Object> root = jsonParser.parseMap(response.body());
        Object rawStatus = root.get("status");
        if (rawStatus instanceof Number number && number.intValue() != 200) {
            throw new IOException("finmind api status " + number.intValue());
        }
        Object rawData = root.get("data");
        if (!(rawData instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                rows.add(typed);
            }
        }
        return rows;
    }

    private static BigDecimal changePercent(BigDecimal close, BigDecimal spread, List<Map<String, Object>> sortedRows) {
        if (close == null) {
            return BigDecimal.ZERO;
        }
        if (spread != null) {
            BigDecimal previousClose = close.subtract(spread);
            if (previousClose.signum() != 0) {
                return spread.divide(previousClose, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            }
        }
        if (sortedRows.size() >= 2) {
            BigDecimal previous = decimal(sortedRows.get(sortedRows.size() - 2).get("close"));
            if (previous != null && previous.signum() != 0) {
                return close.subtract(previous)
                    .divide(previous, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal decimal(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim().replace(",", "");
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return new BigDecimal(text);
    }

    private static String text(Object raw, String fallback) {
        String value = raw == null ? "" : String.valueOf(raw).trim();
        return value.isBlank() ? fallback : value;
    }

    private static String encode(Map<String, String> params) {
        return params.entrySet().stream()
            .filter(entry -> !isBlank(entry.getValue()))
            .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
    }

    private static int searchRank(String stockId, String stockName, String upperQuery, String rawQuery) {
        if (stockId.equals(upperQuery) || stockName.equals(rawQuery)) {
            return 0;
        }
        if (stockId.startsWith(upperQuery) || stockName.startsWith(rawQuery)) {
            return 1;
        }
        return 2;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolvedToken() {
        return apiKeyHeaderResolver == null ? token : apiKeyHeaderResolver.resolveFinMindToken(token);
    }

    private record CachedRows(List<Map<String, Object>> rows, Instant loadedAt) {
        boolean isExpired() {
            return loadedAt.plus(INFO_CACHE_TTL).isBefore(Instant.now());
        }
    }
}
