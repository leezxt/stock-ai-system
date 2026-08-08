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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;

import com.example.stockai.market.Market;

@Service
class YahooFinanceUsMarketDataProvider {
    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final String chartBaseUrl;
    private final String searchBaseUrl;
    private final String userAgent;

    @Autowired
    YahooFinanceUsMarketDataProvider(
        @Value("${stockai.yahoo.chart-url:https://query1.finance.yahoo.com/v8/finance/chart}") String chartBaseUrl,
        @Value("${stockai.yahoo.search-url:https://query2.finance.yahoo.com/v1/finance/search}") String searchBaseUrl,
        @Value("${stockai.yahoo.user-agent:Mozilla/5.0}") String userAgent
    ) {
        this(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
            JsonParserFactory.getJsonParser(),
            chartBaseUrl,
            searchBaseUrl,
            userAgent
        );
    }

    YahooFinanceUsMarketDataProvider(HttpClient httpClient, JsonParser jsonParser, String chartBaseUrl, String searchBaseUrl, String userAgent) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.chartBaseUrl = chartBaseUrl;
        this.searchBaseUrl = searchBaseUrl;
        this.userAgent = userAgent == null || userAgent.isBlank() ? "Mozilla/5.0" : userAgent.trim();
    }

    Optional<StockRecord> tryGet(Market market, String symbol, StockRecord fallback) {
        if (market != Market.US || isBlank(symbol) || isBlank(chartBaseUrl)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> root = getChartJson(symbol.trim().toUpperCase());
            return Optional.of(parseChartResponse(symbol.trim().toUpperCase(), root, fallback));
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    Optional<List<StockRecord>> trySearch(Market market, String query) {
        if (market != Market.US || isBlank(query) || isBlank(searchBaseUrl)) {
            return Optional.empty();
        }
        String normalized = query.trim().toUpperCase();
        try {
            Map<String, Object> root = getSearchJson(normalized);
            List<String> symbols = parseSearchSymbols(root, normalized);
            if (symbols.isEmpty()) {
                return Optional.of(List.of());
            }
            List<StockRecord> results = new ArrayList<>();
            for (String symbol : symbols) {
                tryGet(market, symbol, null).ifPresent(results::add);
                if (results.size() >= 5) {
                    break;
                }
            }
            return Optional.of(results);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    static StockRecord parseChartResponse(String symbol, Map<String, Object> root, StockRecord fallback) {
        Map<String, Object> result = firstResult(root);
        Map<String, Object> meta = asMap(result.get("meta"), "meta");
        String resolvedSymbol = text(meta.get("symbol"), symbol).trim().toUpperCase();
        String name = text(meta.get("longName"), text(meta.get("shortName"), fallback == null ? resolvedSymbol : fallback.name()));
        String currency = text(meta.get("currency"), fallback == null ? "USD" : fallback.currency());
        BigDecimal lastPrice = decimal(meta.get("regularMarketPrice"));
        BigDecimal previousClose = decimal(firstNonNull(meta.get("previousClose"), meta.get("chartPreviousClose")));
        if (lastPrice == null) {
            lastPrice = latestClose(result);
        }
        if (lastPrice == null) {
            throw new IllegalArgumentException("missing yahoo last price for " + symbol);
        }
        BigDecimal changePercent = changePercent(lastPrice, previousClose);
        boolean adjustedClose = hasAdjustedClose(result);
        List<BigDecimal> prices = parsePrices(result, lastPrice, fallback);
        List<PriceBar> bars = parsePriceBars(result);
        // Keep adjusted historical bars internally consistent. Appending an
        // unadjusted regularMarketPrice here creates a false split/dividend
        // jump, so the current quote remains in the summary only.
        Instant observedAt = observedAt(meta, bars);
        return new StockRecord(
            resolvedSymbol,
            isBlank(name) ? resolvedSymbol : name,
            Market.US,
            isBlank(currency) ? "USD" : currency,
            lastPrice,
            changePercent,
            prices,
            "yahoo-finance",
            bars,
            observedAt,
            parseCorporateActions(result),
            adjustedClose ? "ADJUSTED_CLOSE" : "UNADJUSTED_CLOSE"
        );
    }

    static List<String> parseSearchSymbols(Map<String, Object> root, String query) {
        List<Map<String, Object>> quotes = asListOfMaps(root.get("quotes"));
        String normalized = query == null ? "" : query.trim().toUpperCase();
        return quotes.stream()
            .filter(item -> "EQUITY".equalsIgnoreCase(text(item.get("quoteType"), "")))
            .map(item -> text(item.get("symbol"), ""))
            .filter(symbol -> !symbol.isBlank())
            .map(String::trim)
            .map(String::toUpperCase)
            .filter(symbol -> symbol.matches("[A-Z.]{1,10}"))
            .filter(symbol -> !symbol.contains("."))
            .sorted((left, right) -> compareSearchRank(left, right, normalized))
            .distinct()
            .limit(5)
            .toList();
    }

    private Map<String, Object> getChartJson(String symbol) throws IOException, InterruptedException {
        String url = chartBaseUrl
            + "/" + encode(symbol)
            + "?range=1mo&interval=1d&includePrePost=false&events=div%2Csplits";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(6))
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("yahoo chart http " + response.statusCode());
        }
        return jsonParser.parseMap(response.body());
    }

    private Map<String, Object> getSearchJson(String query) throws IOException, InterruptedException {
        String url = searchBaseUrl + "?q=" + encode(query) + "&quotesCount=8&newsCount=0";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(6))
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("yahoo search http " + response.statusCode());
        }
        return jsonParser.parseMap(response.body());
    }

    private static Map<String, Object> firstResult(Map<String, Object> root) {
        Map<String, Object> chart = asMap(root.get("chart"), "chart");
        List<Map<String, Object>> results = asListOfMaps(chart.get("result"));
        if (results.isEmpty()) {
            throw new IllegalArgumentException("missing yahoo chart result");
        }
        return results.get(0);
    }

    private static List<BigDecimal> parsePrices(Map<String, Object> result, BigDecimal lastPrice, StockRecord fallback) {
        List<BigDecimal> prices = parseAdjClose(result);
        if (prices.isEmpty()) {
            prices = parseQuoteClose(result);
        }
        if (prices.isEmpty()) {
            return fallback != null && fallback.prices() != null && !fallback.prices().isEmpty()
                ? fallback.prices()
                : List.of(lastPrice);
        }
        if (!hasAdjustedClose(result) && (prices.isEmpty() || prices.get(prices.size() - 1).compareTo(lastPrice) != 0)) {
            List<BigDecimal> appended = new ArrayList<>(prices);
            appended.add(lastPrice);
            prices = appended;
        }
        return List.copyOf(prices);
    }

    private static List<PriceBar> parsePriceBars(Map<String, Object> result) {
        Object rawTimestamps = result.get("timestamp");
        if (!(rawTimestamps instanceof List<?> timestamps)) {
            return List.of();
        }
        List<?> closes = rawCloseValues(result);
        int size = Math.min(timestamps.size(), closes.size());
        List<PriceBar> bars = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Object rawTimestamp = timestamps.get(i);
            if (!(rawTimestamp instanceof Number timestamp)) {
                continue;
            }
            BigDecimal close = decimal(closes.get(i));
            if (close == null) {
                continue;
            }
            LocalDate date = Instant.ofEpochSecond(timestamp.longValue())
                .atZone(ZoneId.of("America/New_York"))
                .toLocalDate();
            bars.add(new PriceBar(date, close, "yahoo-finance"));
        }
        return List.copyOf(bars);
    }

    private static Instant observedAt(Map<String, Object> meta, List<PriceBar> bars) {
        Object rawRegularMarketTime = meta.get("regularMarketTime");
        if (rawRegularMarketTime instanceof Number value) {
            return Instant.ofEpochSecond(value.longValue());
        }
        if (rawRegularMarketTime != null) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(String.valueOf(rawRegularMarketTime).trim()));
            } catch (RuntimeException ignored) {
                // Fall through to the latest chart date when metadata is malformed.
            }
        }
        if (!bars.isEmpty()) {
            return bars.get(bars.size() - 1).date()
                .atStartOfDay(ZoneId.of("America/New_York"))
                .toInstant();
        }
        return null;
    }

    private static List<?> rawCloseValues(Map<String, Object> result) {
        Map<String, Object> indicators = asMap(result.get("indicators"), "indicators");
        List<Map<String, Object>> adjCloseList = asListOfMaps(indicators.get("adjclose"));
        if (!adjCloseList.isEmpty() && adjCloseList.get(0).get("adjclose") instanceof List<?> values) {
            return values;
        }
        List<Map<String, Object>> quoteList = asListOfMaps(indicators.get("quote"));
        if (!quoteList.isEmpty() && quoteList.get(0).get("close") instanceof List<?> values) {
            return values;
        }
        return List.of();
    }

    private static List<BigDecimal> parseAdjClose(Map<String, Object> result) {
        Map<String, Object> indicators = asMap(result.get("indicators"), "indicators");
        List<Map<String, Object>> adjCloseList = asListOfMaps(indicators.get("adjclose"));
        if (adjCloseList.isEmpty()) {
            return List.of();
        }
        Object raw = adjCloseList.get(0).get("adjclose");
        return decimals(raw);
    }

    private static boolean hasAdjustedClose(Map<String, Object> result) {
        Map<String, Object> indicators = asMap(result.get("indicators"), "indicators");
        return !asListOfMaps(indicators.get("adjclose")).isEmpty();
    }

    private static List<CorporateAction> parseCorporateActions(Map<String, Object> result) {
        Object rawEvents = result.get("events");
        if (!(rawEvents instanceof Map<?, ?> events)) {
            return List.of();
        }
        List<CorporateAction> actions = new ArrayList<>();
        parseEventGroup(events.get("dividends"), "DIVIDEND", actions);
        parseEventGroup(events.get("splits"), "SPLIT", actions);
        return actions.stream()
            .sorted(java.util.Comparator.comparing(CorporateAction::date))
            .toList();
    }

    private static void parseEventGroup(Object rawGroup, String type, List<CorporateAction> target) {
        if (!(rawGroup instanceof Map<?, ?> group)) {
            return;
        }
        for (Map.Entry<?, ?> entry : group.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> event)) {
                continue;
            }
            LocalDate date = eventDate(entry.getKey(), event);
            if (date == null) {
                continue;
            }
            String description = "DIVIDEND".equals(type)
                ? "股利 " + text(event.get("amount"), "未知金額")
                : "分割 " + text(event.get("splitRatio"), text(event.get("numerator"), "?") + ":" + text(event.get("denominator"), "?"));
            target.add(new CorporateAction(date, type, description, "yahoo-finance-events"));
        }
    }

    private static LocalDate eventDate(Object key, Map<?, ?> event) {
        Object rawDate = event.get("date");
        if (rawDate == null) {
            rawDate = key;
        }
        try {
            if (rawDate instanceof Number number) {
                return Instant.ofEpochSecond(number.longValue()).atZone(ZoneId.of("America/New_York")).toLocalDate();
            }
            String value = String.valueOf(rawDate).trim();
            if (value.matches("\\d+")) {
                return Instant.ofEpochSecond(Long.parseLong(value)).atZone(ZoneId.of("America/New_York")).toLocalDate();
            }
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<BigDecimal> parseQuoteClose(Map<String, Object> result) {
        Map<String, Object> indicators = asMap(result.get("indicators"), "indicators");
        List<Map<String, Object>> quoteList = asListOfMaps(indicators.get("quote"));
        if (quoteList.isEmpty()) {
            return List.of();
        }
        Object raw = quoteList.get(0).get("close");
        return decimals(raw);
    }

    private static BigDecimal latestClose(Map<String, Object> result) {
        List<BigDecimal> prices = parseAdjClose(result);
        if (prices.isEmpty()) {
            prices = parseQuoteClose(result);
        }
        return prices.isEmpty() ? null : prices.get(prices.size() - 1);
    }

    private static List<BigDecimal> decimals(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(Objects::nonNull)
            .map(YahooFinanceUsMarketDataProvider::decimal)
            .filter(Objects::nonNull)
            .toList();
    }

    private static BigDecimal changePercent(BigDecimal lastPrice, BigDecimal previousClose) {
        if (lastPrice == null || previousClose == null || previousClose.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return lastPrice.subtract(previousClose)
            .divide(previousClose, 6, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static List<Map<String, Object>> asListOfMaps(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                results.add(typed);
            }
        }
        return results;
    }

    private static Map<String, Object> asMap(Object raw, String label) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("missing yahoo " + label);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return typed;
    }

    private static BigDecimal decimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(raw).trim();
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return new BigDecimal(text);
    }

    private static String text(Object raw, String fallback) {
        String value = raw == null ? "" : String.valueOf(raw).trim();
        return value.isBlank() ? fallback : value;
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static int compareSearchRank(String left, String right, String query) {
        return Integer.compare(rankSymbol(left, query), rankSymbol(right, query)) != 0
            ? Integer.compare(rankSymbol(left, query), rankSymbol(right, query))
            : left.compareTo(right);
    }

    private static int rankSymbol(String symbol, String query) {
        if (symbol.equals(query)) {
            return 0;
        }
        if (symbol.startsWith(query)) {
            return 1;
        }
        return 2;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
