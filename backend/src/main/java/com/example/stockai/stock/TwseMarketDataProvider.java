package com.example.stockai.stock;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
class TwseMarketDataProvider {
    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final String stockDayAllUrl;

    @Autowired
    TwseMarketDataProvider(
        @Value("${stockai.twse.stock-day-all-url:https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL}") String stockDayAllUrl
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), JsonParserFactory.getJsonParser(), stockDayAllUrl);
    }

    TwseMarketDataProvider(HttpClient httpClient, JsonParser jsonParser, String stockDayAllUrl) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.stockDayAllUrl = stockDayAllUrl;
    }

    Optional<StockRecord> tryGet(Market market, String symbol, StockRecord fallback) {
        if (market != Market.TW) {
            return Optional.empty();
        }
        try {
            String normalized = SymbolNormalizer.normalize(market, symbol);
            String code = normalized.replace(".TW", "");
            return getRows().stream()
                .filter(row -> code.equals(String.valueOf(row.get("Code"))))
                .findFirst()
                .map(row -> parseRow(row, fallback));
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    Optional<List<StockRecord>> trySearch(Market market, String query) {
        if (market != Market.TW || query == null || query.isBlank()) {
            return Optional.empty();
        }
        String q = query.trim().toUpperCase().replace(".TW", "");
        try {
            List<StockRecord> results = getRows().stream()
                .filter(row -> String.valueOf(row.get("Code")).contains(q) || String.valueOf(row.get("Name")).contains(query.trim()))
                .limit(10)
                .map(row -> parseRow(row, null))
                .toList();
            return Optional.of(results);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    static StockRecord parseRow(Map<String, Object> row, StockRecord fallback) {
        String code = String.valueOf(row.get("Code"));
        String name = String.valueOf(row.getOrDefault("Name", code));
        BigDecimal close = decimal(row.get("ClosingPrice"));
        BigDecimal change = decimal(row.getOrDefault("Change", "0"));
        BigDecimal previousClose = close.subtract(change);
        BigDecimal changePercent = previousClose.signum() == 0
            ? BigDecimal.ZERO
            : change.divide(previousClose, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
        List<BigDecimal> prices = fallback == null || fallback.prices().isEmpty()
            ? List.of(close)
            : appendLast(fallback.prices(), close);
        LocalDate quoteDate = LocalDate.now(ZoneId.of("Asia/Taipei"));
        List<PriceBar> bars = fallback != null && !fallback.priceHistory().isEmpty()
            ? mergePriceHistory(fallback.priceHistory(), quoteDate, close)
            : fallback == null ? List.of(new PriceBar(quoteDate, close, "twse-openapi")) : List.of();
        return new StockRecord(
            code + ".TW",
            name,
            Market.TW,
            "TWD",
            close,
            changePercent,
            prices,
            "twse-openapi",
            bars,
            quoteDate.atStartOfDay(ZoneId.of("Asia/Taipei")).toInstant()
        );
    }

    private List<Map<String, Object>> getRows() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(stockDayAllUrl))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("twse http " + response.statusCode());
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) (List<?>) jsonParser.parseList(response.body());
        return rows;
    }

    private static List<BigDecimal> appendLast(List<BigDecimal> prices, BigDecimal close) {
        if (prices.get(prices.size() - 1).compareTo(close) == 0) {
            return prices;
        }
        return java.util.stream.Stream.concat(prices.stream().skip(1), java.util.stream.Stream.of(close)).toList();
    }

    private static List<PriceBar> mergePriceHistory(List<PriceBar> history, LocalDate quoteDate, BigDecimal close) {
        List<PriceBar> bars = new ArrayList<>(history);
        PriceBar latest = new PriceBar(quoteDate, close, "twse-openapi");
        if (bars.get(bars.size() - 1).date().equals(quoteDate)) {
            bars.set(bars.size() - 1, latest);
        } else {
            bars.add(latest);
        }
        return List.copyOf(bars);
    }

    private static BigDecimal decimal(Object value) {
        String text = String.valueOf(value == null ? "0" : value).trim()
            .replace(",", "")
            .replace("+", "")
            .replace("X", "")
            .replace("--", "0");
        if (text.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(text);
    }
}
