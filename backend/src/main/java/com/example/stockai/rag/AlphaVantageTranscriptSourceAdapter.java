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
class AlphaVantageTranscriptSourceAdapter {
    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final ExternalApiKeyHeaderResolver apiKeyHeaderResolver;
    private final String apiKey;
    private final String baseUrl;

    @Autowired
    AlphaVantageTranscriptSourceAdapter(
        ExternalApiKeyHeaderResolver apiKeyHeaderResolver,
        @Value("${stockai.alpha-vantage.api-key:${ALPHAVANTAGE_API_KEY:}}") String apiKey,
        @Value("${stockai.alpha-vantage.base-url:https://www.alphavantage.co/query}") String baseUrl
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), JsonParserFactory.getJsonParser(), apiKeyHeaderResolver, apiKey, baseUrl);
    }

    AlphaVantageTranscriptSourceAdapter(HttpClient httpClient, JsonParser jsonParser, String apiKey, String baseUrl) {
        this(httpClient, jsonParser, null, apiKey, baseUrl);
    }

    AlphaVantageTranscriptSourceAdapter(HttpClient httpClient, JsonParser jsonParser, ExternalApiKeyHeaderResolver apiKeyHeaderResolver, String apiKey, String baseUrl) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.apiKeyHeaderResolver = apiKeyHeaderResolver;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
    }

    Optional<DocumentImportRequest> fetch(Market market, String symbol, String quarter) {
        String resolvedApiKey = resolvedApiKey();
        if (market != Market.US || resolvedApiKey.isBlank() || quarter == null || quarter.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> root = getJson(symbol, quarter.trim().toUpperCase(), resolvedApiKey);
            return parseTranscriptResponse(root, symbol, market, quarter.trim().toUpperCase());
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    static Optional<DocumentImportRequest> parseTranscriptResponse(Map<String, Object> root, String symbol, Market market, String quarter) {
        if (root == null || root.containsKey("Information") || root.containsKey("Error Message")) {
            return Optional.empty();
        }
        String transcript = text(root.get("transcript"), "");
        if (transcript.isBlank()) {
            transcript = text(root.get("content"), "");
        }
        if (transcript.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new DocumentImportRequest(
            symbol,
            market,
            DocumentType.EARNINGS_TRANSCRIPT,
            symbol + " earnings call " + quarter,
            text(root.get("source"), "alpha-vantage-transcript"),
            parseQuarter(quarter),
            transcript
        ));
    }

    private Map<String, Object> getJson(String symbol, String quarter, String resolvedApiKey) throws IOException, InterruptedException {
        String url = baseUrl
            + "?function=EARNINGS_CALL_TRANSCRIPT"
            + "&symbol=" + encode(symbol)
            + "&quarter=" + encode(quarter)
            + "&apikey=" + encode(resolvedApiKey);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("alpha vantage transcript http " + response.statusCode());
        }
        return jsonParser.parseMap(response.body());
    }

    private static Instant parseQuarter(String quarter) {
        int year = Integer.parseInt(quarter.substring(0, 4));
        int q = Integer.parseInt(quarter.substring(5, 6));
        int month = switch (q) {
            case 1 -> 3;
            case 2 -> 6;
            case 3 -> 9;
            default -> 12;
        };
        return LocalDate.of(year, month, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
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
