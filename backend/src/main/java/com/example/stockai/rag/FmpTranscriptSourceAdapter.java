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
class FmpTranscriptSourceAdapter {
    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final ExternalApiKeyHeaderResolver apiKeyHeaderResolver;
    private final String apiKey;
    private final String baseUrl;

    @Autowired
    FmpTranscriptSourceAdapter(
        ExternalApiKeyHeaderResolver apiKeyHeaderResolver,
        @Value("${stockai.fmp.api-key:${FMP_API_KEY:}}") String apiKey,
        @Value("${stockai.fmp.base-url:https://financialmodelingprep.com/stable}") String baseUrl
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), JsonParserFactory.getJsonParser(), apiKeyHeaderResolver, apiKey, baseUrl);
    }

    FmpTranscriptSourceAdapter(HttpClient httpClient, JsonParser jsonParser, String apiKey, String baseUrl) {
        this(httpClient, jsonParser, null, apiKey, baseUrl);
    }

    FmpTranscriptSourceAdapter(HttpClient httpClient, JsonParser jsonParser, ExternalApiKeyHeaderResolver apiKeyHeaderResolver, String apiKey, String baseUrl) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.apiKeyHeaderResolver = apiKeyHeaderResolver;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    Optional<DocumentImportRequest> fetch(Market market, String symbol, String quarter) {
        String resolvedApiKey = resolvedApiKey();
        if (market != Market.US || resolvedApiKey.isBlank() || baseUrl.isBlank() || quarter == null || quarter.isBlank()) {
            return Optional.empty();
        }
        try {
            return parseTranscriptResponse(getJson(symbol, quarter, resolvedApiKey), symbol, market, quarter);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    static Optional<DocumentImportRequest> parseTranscriptResponse(List<Object> root, String symbol, Market market, String quarter) {
        if (root == null || root.isEmpty() || !(root.get(0) instanceof Map<?, ?> item)) {
            return Optional.empty();
        }
        String content = text(firstNonNull(item.get("content"), item.get("transcript")), "");
        if (content.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new DocumentImportRequest(
            symbol,
            market,
            DocumentType.EARNINGS_TRANSCRIPT,
            symbol + " earnings call " + quarter.trim().toUpperCase(),
            "FMP earnings transcript",
            parsePublishedAt(firstNonNull(item.get("date"), quarter)),
            content
        ));
    }

    private List<Object> getJson(String symbol, String quarter, String resolvedApiKey) throws IOException, InterruptedException {
        QuarterParts parts = QuarterParts.parse(quarter);
        String url = trimSlash(baseUrl)
            + "/earning-call-transcript?symbol=" + encode(symbol)
            + "&year=" + parts.year()
            + "&quarter=" + parts.quarter()
            + "&apikey=" + encode(resolvedApiKey);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("fmp transcript http " + response.statusCode());
        }
        return jsonParser.parseList(response.body());
    }

    private static Instant parsePublishedAt(Object raw) {
        String value = text(raw, "");
        if (value.matches("\\d{4}Q[1-4]")) {
            QuarterParts parts = QuarterParts.parse(value);
            return LocalDate.of(parts.year(), parts.quarter() * 3, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        if (value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
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

    private record QuarterParts(int year, int quarter) {
        static QuarterParts parse(String raw) {
            String value = raw == null ? "" : raw.trim().toUpperCase();
            if (!value.matches("\\d{4}Q[1-4]")) {
                throw new IllegalArgumentException("quarter must be like 2026Q2");
            }
            return new QuarterParts(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(5, 6)));
        }
    }
}
