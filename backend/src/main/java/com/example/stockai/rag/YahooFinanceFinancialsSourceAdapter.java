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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;

import com.example.stockai.market.Market;

@Service
class YahooFinanceFinancialsSourceAdapter {
    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final String quoteSummaryUrl;

    @Autowired
    YahooFinanceFinancialsSourceAdapter(
        @Value("${stockai.yahoo.quote-summary-url:https://query2.finance.yahoo.com/v10/finance/quoteSummary}") String quoteSummaryUrl
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), JsonParserFactory.getJsonParser(), quoteSummaryUrl);
    }

    YahooFinanceFinancialsSourceAdapter(HttpClient httpClient, JsonParser jsonParser, String quoteSummaryUrl) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.quoteSummaryUrl = quoteSummaryUrl == null ? "" : quoteSummaryUrl.trim();
    }

    List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
        if (quoteSummaryUrl.isBlank()) {
            return List.of();
        }
        try {
            return parseQuoteSummary(getJson(symbol), symbol, market, limit);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    static List<DocumentImportRequest> parseQuoteSummary(Map<String, Object> root, String symbol, Market market, int limit) {
        Map<?, ?> result = firstQuoteSummaryResult(root);
        if (result.isEmpty()) {
            return List.of();
        }
        List<DocumentImportRequest> documents = new ArrayList<>();
        addModuleDocument(documents, symbol, market, result, "incomeStatementHistory", "incomeStatementHistory", "損益表");
        addModuleDocument(documents, symbol, market, result, "balanceSheetHistory", "balanceSheetStatements", "資產負債表");
        addModuleDocument(documents, symbol, market, result, "cashflowStatementHistory", "cashflowStatements", "現金流量表");
        return documents.stream().limit(Math.max(1, limit)).toList();
    }

    private Map<String, Object> getJson(String symbol) throws IOException, InterruptedException {
        String url = quoteSummaryUrl.replaceAll("/+$", "")
            + "/" + encode(symbol)
            + "?modules=incomeStatementHistory,balanceSheetHistory,cashflowStatementHistory";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("yahoo finance financials http " + response.statusCode());
        }
        return jsonParser.parseMap(response.body());
    }

    private static void addModuleDocument(
        List<DocumentImportRequest> documents,
        String symbol,
        Market market,
        Map<?, ?> result,
        String moduleName,
        String rowsName,
        String label
    ) {
        Object module = result.get(moduleName);
        if (!(module instanceof Map<?, ?> moduleMap)) {
            return;
        }
        Object rows = moduleMap.get(rowsName);
        if (!(rows instanceof List<?> rowItems) || rowItems.isEmpty()) {
            return;
        }
        String content = flattenRows(rowItems);
        if (content.isBlank()) {
            return;
        }
        documents.add(new DocumentImportRequest(
            symbol,
            market,
            DocumentType.FINANCIAL_REPORT,
            symbol + " Yahoo Finance " + label,
            "yahoo-finance-financials",
            Instant.now(),
            content
        ));
    }

    private static String flattenRows(List<?> rowItems) {
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (Object item : rowItems) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            lines.add("period " + index++);
            appendMap(lines, "", row);
        }
        return String.join("\n", lines);
    }

    private static void appendMap(List<String> lines, String prefix, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = prefix.isBlank() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                Object raw = nested.get("raw");
                Object fmt = nested.get("fmt");
                if (raw != null || fmt != null) {
                    lines.add(key + ": " + (raw == null ? fmt : raw));
                    continue;
                }
                appendMap(lines, key, nested);
            } else if (value != null && !String.valueOf(value).isBlank()) {
                lines.add(key + ": " + value);
            }
        }
    }

    private static Map<?, ?> firstQuoteSummaryResult(Map<String, Object> root) {
        if (root == null || !(root.get("quoteSummary") instanceof Map<?, ?> quoteSummary)) {
            return Map.of();
        }
        Object rawResult = quoteSummary.get("result");
        if (!(rawResult instanceof List<?> results) || results.isEmpty() || !(results.get(0) instanceof Map<?, ?> result)) {
            return Map.of();
        }
        return result;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
