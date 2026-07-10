package com.example.stockai.rag;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;

import com.example.stockai.common.ExternalApiKeyHeaderResolver;
import com.example.stockai.market.Market;

@Service
class AlphaVantageNewsSourceAdapter {
    private static final DateTimeFormatter PUBLISHED_AT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final ExternalApiKeyHeaderResolver apiKeyHeaderResolver;
    private final String apiKey;
    private final String baseUrl;

    @Autowired
    AlphaVantageNewsSourceAdapter(
        ExternalApiKeyHeaderResolver apiKeyHeaderResolver,
        @Value("${stockai.alpha-vantage.api-key:${ALPHAVANTAGE_API_KEY:}}") String apiKey,
        @Value("${stockai.alpha-vantage.base-url:https://www.alphavantage.co/query}") String baseUrl
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), JsonParserFactory.getJsonParser(), apiKeyHeaderResolver, apiKey, baseUrl);
    }

    AlphaVantageNewsSourceAdapter(HttpClient httpClient, JsonParser jsonParser, String apiKey, String baseUrl) {
        this(httpClient, jsonParser, null, apiKey, baseUrl);
    }

    AlphaVantageNewsSourceAdapter(HttpClient httpClient, JsonParser jsonParser, ExternalApiKeyHeaderResolver apiKeyHeaderResolver, String apiKey, String baseUrl) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.apiKeyHeaderResolver = apiKeyHeaderResolver;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
    }

    List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
        String resolvedApiKey = resolvedApiKey();
        if (market != Market.US || resolvedApiKey.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> root = getJson(symbol, limit, resolvedApiKey);
            return parseNewsResponse(root, symbol, market, limit);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    static List<DocumentImportRequest> parseNewsResponse(Map<String, Object> root, String symbol, Market market, int limit) {
        if (root == null || root.containsKey("Information") || root.containsKey("Error Message")) {
            return List.of();
        }
        Object feedObject = root.get("feed");
        if (!(feedObject instanceof List<?> feed)) {
            return List.of();
        }
        return feed.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .limit(Math.max(1, limit))
            .map(item -> toImportRequest(symbol, market, item))
            .toList();
    }

    private static DocumentImportRequest toImportRequest(String symbol, Market market, Map<?, ?> item) {
        String title = text(item.get("title"), symbol + " market news");
        String source = text(item.get("source"), "alpha-vantage-news");
        String summary = text(item.get("summary"), title);
        String url = text(item.get("url"), "");
        String content = url.isBlank() ? title + "\n\n" + summary : title + "\n\n" + summary + "\n\n" + url;
        return new DocumentImportRequest(
            symbol,
            market,
            DocumentType.NEWS,
            title,
            source,
            parsePublishedAt(item.get("time_published")),
            content
        );
    }

    private Map<String, Object> getJson(String symbol, int limit, String resolvedApiKey) throws IOException, InterruptedException {
        String url = baseUrl
            + "?function=NEWS_SENTIMENT"
            + "&tickers=" + encode(symbol)
            + "&sort=LATEST"
            + "&limit=" + Math.min(50, Math.max(1, limit))
            + "&apikey=" + encode(resolvedApiKey);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("alpha vantage news http " + response.statusCode());
        }
        return jsonParser.parseMap(response.body());
    }

    private static Instant parsePublishedAt(Object value) {
        String text = text(value, "");
        if (text.isBlank()) {
            return Instant.now();
        }
        return LocalDateTime.parse(text, PUBLISHED_AT).toInstant(ZoneOffset.UTC);
    }

    private static String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String resolvedApiKey() {
        return apiKeyHeaderResolver == null ? apiKey : apiKeyHeaderResolver.resolveAlphaVantage(apiKey);
    }
}
