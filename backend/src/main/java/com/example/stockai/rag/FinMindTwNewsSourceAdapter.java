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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
class FinMindTwNewsSourceAdapter {
    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final ExternalApiKeyHeaderResolver apiKeyHeaderResolver;
    private final String baseUrl;
    private final String token;

    @Autowired
    FinMindTwNewsSourceAdapter(
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

    FinMindTwNewsSourceAdapter(HttpClient httpClient, JsonParser jsonParser, String baseUrl, String token) {
        this(httpClient, jsonParser, null, baseUrl, token);
    }

    FinMindTwNewsSourceAdapter(HttpClient httpClient, JsonParser jsonParser, ExternalApiKeyHeaderResolver apiKeyHeaderResolver, String baseUrl, String token) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.apiKeyHeaderResolver = apiKeyHeaderResolver;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.token = token == null ? "" : token.trim();
    }

    List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
        if (market != Market.TW || isBlank(baseUrl)) {
            return List.of();
        }
        try {
            Map<String, Object> root = getJson(symbol, limit);
            return parseNewsResponse(root, symbol, market, limit);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    static List<DocumentImportRequest> parseNewsResponse(Map<String, Object> root, String symbol, Market market, int limit) {
        if (root == null) {
            return List.of();
        }
        Object rawStatus = root.get("status");
        if (rawStatus instanceof Number number && number.intValue() != 200) {
            return List.of();
        }
        Object rawData = root.get("data");
        if (!(rawData instanceof List<?> data)) {
            return List.of();
        }
        return data.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .limit(Math.max(1, limit))
            .map(item -> toImportRequest(symbol, market, item))
            .toList();
    }

    private static DocumentImportRequest toImportRequest(String symbol, Market market, Map<?, ?> item) {
        String title = text(item.get("title"), symbol + " market news");
        String source = text(item.get("source"), "finmind-news");
        String description = text(item.get("description"), title);
        String link = text(item.get("link"), "");
        String content = link.isBlank() ? title + "\n\n" + description : title + "\n\n" + description + "\n\n" + link;
        return new DocumentImportRequest(
            symbol,
            market,
            DocumentType.NEWS,
            title,
            source,
            parsePublishedAt(item.get("date")),
            content
        );
    }

    private Map<String, Object> getJson(String symbol, int limit) throws IOException, InterruptedException {
        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = end.minusDays(Math.max(3, limit * 7L));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("dataset", "TaiwanStockNews");
        params.put("data_id", symbol.replace(".TW", ""));
        params.put("start_date", start.toString());
        String url = baseUrl + "/data?" + encode(params);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .GET();
        String resolvedToken = resolvedToken();
        if (!resolvedToken.isBlank()) {
            builder.header("Authorization", "Bearer " + resolvedToken);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("finmind news http " + response.statusCode());
        }
        return jsonParser.parseMap(response.body());
    }

    private static Instant parsePublishedAt(Object raw) {
        String text = text(raw, "");
        if (text.isBlank()) {
            return Instant.now();
        }
        return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static String encode(Map<String, String> params) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (isBlank(entry.getValue())) {
                continue;
            }
            parts.add(
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)
            );
        }
        return String.join("&", parts);
    }

    private static String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolvedToken() {
        return apiKeyHeaderResolver == null ? token : apiKeyHeaderResolver.resolveFinMindToken(token);
    }
}
