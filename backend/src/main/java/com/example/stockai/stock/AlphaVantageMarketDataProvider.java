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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;

import com.example.stockai.common.ExternalApiKeyHeaderResolver;
import com.example.stockai.market.Market;

@Service
class AlphaVantageMarketDataProvider {
    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final ExternalApiKeyHeaderResolver apiKeyHeaderResolver;
    private final String apiKey;
    private final String baseUrl;

    @Autowired
    AlphaVantageMarketDataProvider(
        ExternalApiKeyHeaderResolver apiKeyHeaderResolver,
        @Value("${stockai.alpha-vantage.api-key:${ALPHAVANTAGE_API_KEY:}}") String apiKey,
        @Value("${stockai.alpha-vantage.base-url:https://www.alphavantage.co/query}") String baseUrl
    ) {
        this(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
            JsonParserFactory.getJsonParser(),
            apiKeyHeaderResolver,
            apiKey,
            baseUrl
        );
    }

    AlphaVantageMarketDataProvider(HttpClient httpClient, JsonParser jsonParser, String apiKey, String baseUrl) {
        this(httpClient, jsonParser, null, apiKey, baseUrl);
    }

    AlphaVantageMarketDataProvider(HttpClient httpClient, JsonParser jsonParser, ExternalApiKeyHeaderResolver apiKeyHeaderResolver, String apiKey, String baseUrl) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.apiKeyHeaderResolver = apiKeyHeaderResolver;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
    }

    Optional<StockRecord> tryGet(Market market, String symbol, StockRecord fallback) {
        String resolvedApiKey = resolvedApiKey();
        if (market != Market.US || resolvedApiKey.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> root = getJson(symbol, resolvedApiKey);
            return Optional.of(parseDailyResponse(symbol, root, fallback));
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    static StockRecord parseDailyResponse(String symbol, Map<String, Object> root, StockRecord fallback) {
        if (root == null || root.containsKey("Error Message") || root.containsKey("Information")) {
            throw new IllegalArgumentException("alpha vantage returned no usable data for " + symbol);
        }

        Object rawTimeSeries = root.get("Time Series (Daily)");
        if (!(rawTimeSeries instanceof Map<?, ?> timeSeriesRaw)) {
            throw new IllegalArgumentException("missing daily time series for " + symbol);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> timeSeries = (Map<String, Object>) timeSeriesRaw;

        List<String> dates = new ArrayList<>();
        dates.addAll(timeSeries.keySet());
        dates.sort(Comparator.reverseOrder());
        if (dates.size() < 2) {
            throw new IllegalArgumentException("not enough daily points for " + symbol);
        }

        List<BigDecimal> prices = dates.stream()
            .limit(23)
            .sorted()
            .map(date -> close(timeSeries, date))
            .toList();

        BigDecimal lastPrice = close(timeSeries, dates.get(0));
        BigDecimal previousClose = close(timeSeries, dates.get(1));
        BigDecimal changePercent = previousClose.signum() == 0
            ? BigDecimal.ZERO
            : lastPrice.subtract(previousClose)
                .divide(previousClose, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        return new StockRecord(
            symbol,
            fallback == null ? symbol : fallback.name(),
            Market.US,
            fallback == null ? "USD" : fallback.currency(),
            lastPrice,
            changePercent,
            prices,
            "alpha-vantage"
        );
    }

    private Map<String, Object> getJson(String symbol, String resolvedApiKey) throws IOException, InterruptedException {
        String url = baseUrl
            + "?function=TIME_SERIES_DAILY"
            + "&symbol=" + encode(symbol)
            + "&outputsize=compact"
            + "&apikey=" + encode(resolvedApiKey);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(5))
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("alpha vantage http " + response.statusCode());
        }
        return jsonParser.parseMap(response.body());
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal close(Map<String, Object> timeSeries, String date) {
        Object rawPoint = timeSeries.get(date);
        if (!(rawPoint instanceof Map<?, ?> pointRaw)) {
            throw new IllegalArgumentException("missing close point for " + date);
        }
        Object rawClose = pointRaw.get("4. close");
        if (!(rawClose instanceof String close)) {
            throw new IllegalArgumentException("missing close value for " + date);
        }
        return decimal(close);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String resolvedApiKey() {
        return apiKeyHeaderResolver == null ? apiKey : apiKeyHeaderResolver.resolveAlphaVantage(apiKey);
    }
}
