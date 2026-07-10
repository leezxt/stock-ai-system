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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
class FmpNewsSourceAdapter {
    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final ExternalApiKeyHeaderResolver apiKeyHeaderResolver;
    private final String apiKey;
    private final String baseUrl;

    @Autowired
    FmpNewsSourceAdapter(
        ExternalApiKeyHeaderResolver apiKeyHeaderResolver,
        @Value("${stockai.fmp.api-key:${FMP_API_KEY:}}") String apiKey,
        @Value("${stockai.fmp.base-url:https://financialmodelingprep.com/stable}") String baseUrl
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), JsonParserFactory.getJsonParser(), apiKeyHeaderResolver, apiKey, baseUrl);
    }

    FmpNewsSourceAdapter(HttpClient httpClient, JsonParser jsonParser, String apiKey, String baseUrl) {
        this(httpClient, jsonParser, null, apiKey, baseUrl);
    }

    FmpNewsSourceAdapter(HttpClient httpClient, JsonParser jsonParser, ExternalApiKeyHeaderResolver apiKeyHeaderResolver, String apiKey, String baseUrl) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.apiKeyHeaderResolver = apiKeyHeaderResolver;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
        String resolvedApiKey = resolvedApiKey();
        if (market != Market.US || resolvedApiKey.isBlank() || baseUrl.isBlank()) {
            return List.of();
        }
        try {
            return parseNewsResponse(getJson(symbol, limit, resolvedApiKey), symbol, market, limit);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    static List<DocumentImportRequest> parseNewsResponse(List<Object> root, String symbol, Market market, int limit) {
        if (root == null || root.isEmpty()) {
            return List.of();
        }
        return root.stream()
            .filter(Map.class::isInstance)
            .map(item -> toImportRequest(symbol, market, (Map<?, ?>) item))
            .limit(Math.max(1, limit))
            .toList();
    }

    private List<Object> getJson(String symbol, int limit, String resolvedApiKey) throws IOException, InterruptedException {
        String url = trimSlash(baseUrl)
            + "/news/stock?symbols=" + encode(symbol)
            + "&limit=" + Math.min(50, Math.max(1, limit))
            + "&apikey=" + encode(resolvedApiKey);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("fmp news http " + response.statusCode());
        }
        return jsonParser.parseList(response.body());
    }

    private static DocumentImportRequest toImportRequest(String symbol, Market market, Map<?, ?> item) {
        String title = text(firstNonNull(item.get("title"), item.get("headline")), symbol + " FMP news");
        String publisher = text(firstNonNull(item.get("site"), item.get("publisher")), "FMP News");
        String summary = text(firstNonNull(item.get("text"), item.get("summary")), title);
        String url = text(item.get("url"), "");
        String content = url.isBlank() ? title + "\n\n" + summary : title + "\n\n" + summary + "\n\n" + url;
        return new DocumentImportRequest(
            symbol,
            market,
            DocumentType.NEWS,
            title,
            publisher,
            parsePublishedAt(firstNonNull(item.get("publishedDate"), item.get("date"))),
            content
        );
    }

    private static Instant parsePublishedAt(Object raw) {
        String value = text(raw, "");
        if (value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            // FMP often returns "yyyy-MM-dd HH:mm:ss".
        }
        try {
            return LocalDateTime.parse(value.replace(" ", "T")).toInstant(ZoneOffset.UTC);
        } catch (RuntimeException ignored) {
            return LocalDate.parse(value.substring(0, Math.min(10, value.length()))).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private static String trimSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String resolvedApiKey() {
        return apiKeyHeaderResolver == null ? apiKey : apiKeyHeaderResolver.resolveFmp(apiKey);
    }
}
