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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;

import com.example.stockai.market.Market;
import com.example.stockai.market.SymbolNormalizer;

@Service
class TwseRealtimeMarketDataProvider {
    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final String quoteUrl;
    private final String userAgent;

    @Autowired
    TwseRealtimeMarketDataProvider(
        @Value("${stockai.twse.realtime-url:https://mis.twse.com.tw/stock/api/getStockInfo.jsp}") String quoteUrl,
        @Value("${stockai.twse.realtime-user-agent:Mozilla/5.0}") String userAgent
    ) {
        this(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
            JsonParserFactory.getJsonParser(),
            quoteUrl,
            userAgent
        );
    }

    TwseRealtimeMarketDataProvider(HttpClient httpClient, JsonParser jsonParser, String quoteUrl, String userAgent) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.quoteUrl = quoteUrl == null ? "" : quoteUrl.trim();
        this.userAgent = userAgent == null || userAgent.isBlank() ? "Mozilla/5.0" : userAgent.trim();
    }

    Optional<StockRecord> tryGet(Market market, String symbol, StockRecord fallback) {
        if (market != Market.TW || isBlank(symbol) || isBlank(quoteUrl)) {
            return Optional.empty();
        }
        try {
            String normalized = SymbolNormalizer.normalize(market, symbol);
            String code = normalized.replace(".TW", "");
            Map<String, Object> root = getJson(code);
            return parseResponse(root, code, fallback);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    Optional<List<StockRecord>> trySearch(Market market, String query) {
        if (market != Market.TW || isBlank(query)) {
            return Optional.empty();
        }
        String code = query.trim().toUpperCase().replace(".TW", "");
        if (!code.matches("[0-9]{4}")) {
            return Optional.empty();
        }
        return Optional.of(tryGet(market, code, null).map(List::of).orElse(List.of()));
    }

    static Optional<StockRecord> parseResponse(Map<String, Object> root, String requestedCode, StockRecord fallback) {
        if (root == null) {
            return Optional.empty();
        }
        Object rawMessages = root.get("msgArray");
        if (!(rawMessages instanceof List<?> messages)) {
            return Optional.empty();
        }
        String normalizedCode = requestedCode == null ? "" : requestedCode.trim().replace(".TW", "");
        for (Object rawMessage : messages) {
            if (!(rawMessage instanceof Map<?, ?> rawRow)) {
                continue;
            }
            Map<?, ?> row = rawRow;
            String code = text(row.get("c"), normalizedCode);
            if (!normalizedCode.isBlank() && !normalizedCode.equals(code)) {
                continue;
            }
            BigDecimal latest = firstDecimal(row.get("z"), row.get("pz"), row.get("o"));
            BigDecimal previousClose = decimal(row.get("y"));
            if (latest == null || previousClose == null || previousClose.signum() == 0) {
                continue;
            }
            BigDecimal changePercent = latest.subtract(previousClose)
                .divide(previousClose, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
            List<BigDecimal> prices = fallback == null || fallback.prices().isEmpty()
                ? List.of(latest)
                : appendLast(fallback.prices(), latest);
            return Optional.of(new StockRecord(
                code + ".TW",
                text(row.get("n"), fallback == null ? code : fallback.name()),
                Market.TW,
                "TWD",
                latest,
                changePercent,
                prices,
                "twse-realtime"
            ));
        }
        return Optional.empty();
    }

    private Map<String, Object> getJson(String code) throws IOException, InterruptedException {
        String channels = "tse_" + code + ".tw|otc_" + code + ".tw";
        String url = quoteUrl
            + "?ex_ch=" + URLEncoder.encode(channels, StandardCharsets.UTF_8)
            + "&json=1&delay=0";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(6))
            .header("Accept", "application/json")
            .header("Referer", "https://mis.twse.com.tw/stock/index.jsp")
            .header("User-Agent", userAgent)
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("twse realtime http " + response.statusCode());
        }
        return jsonParser.parseMap(response.body());
    }

    private static List<BigDecimal> appendLast(List<BigDecimal> prices, BigDecimal latest) {
        if (prices.get(prices.size() - 1).compareTo(latest) == 0) {
            return prices;
        }
        return java.util.stream.Stream.concat(prices.stream().skip(1), java.util.stream.Stream.of(latest)).toList();
    }

    private static BigDecimal firstDecimal(Object... values) {
        for (Object value : values) {
            BigDecimal decimal = decimal(value);
            if (decimal != null) {
                return decimal;
            }
        }
        return null;
    }

    private static BigDecimal decimal(Object value) {
        String text = String.valueOf(value == null ? "" : value).trim()
            .replace(",", "")
            .replace("+", "");
        if (text.isBlank() || "-".equals(text) || "--".equals(text)) {
            return null;
        }
        return new BigDecimal(text);
    }

    private static String text(Object raw, String fallback) {
        String value = raw == null ? "" : String.valueOf(raw).trim();
        return value.isBlank() ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
