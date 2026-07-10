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
import java.util.Comparator;
import java.util.HashMap;
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
class FinMindTwFinancialReportSourceAdapter {
    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final ExternalApiKeyHeaderResolver apiKeyHeaderResolver;
    private final String baseUrl;
    private final String token;

    @Autowired
    FinMindTwFinancialReportSourceAdapter(
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

    FinMindTwFinancialReportSourceAdapter(HttpClient httpClient, JsonParser jsonParser, String baseUrl, String token) {
        this(httpClient, jsonParser, null, baseUrl, token);
    }

    FinMindTwFinancialReportSourceAdapter(HttpClient httpClient, JsonParser jsonParser, ExternalApiKeyHeaderResolver apiKeyHeaderResolver, String baseUrl, String token) {
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
            List<DocumentImportRequest> documents = new ArrayList<>();
            documents.addAll(parseMonthRevenueResponse(getJson("TaiwanStockMonthRevenue", symbol, monthRevenueStartDate(limit)), symbol, market, limit));
            documents.addAll(parseStatementResponse(
                getJson("TaiwanStockFinancialStatements", symbol, statementStartDate(limit)),
                symbol,
                market,
                limit,
                "綜合損益表",
                "finmind-financial-statements"
            ));
            documents.addAll(parseStatementResponse(
                getJson("TaiwanStockBalanceSheet", symbol, statementStartDate(limit)),
                symbol,
                market,
                limit,
                "資產負債表",
                "finmind-balance-sheet"
            ));
            documents.addAll(parseStatementResponse(
                getJson("TaiwanStockCashFlowsStatement", symbol, statementStartDate(limit)),
                symbol,
                market,
                limit,
                "現金流量表",
                "finmind-cashflow"
            ));
            return documents;
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    static List<DocumentImportRequest> parseMonthRevenueResponse(Map<String, Object> root, String symbol, Market market, int limit) {
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
            .map(item -> (Map<?, ?>) item)
            .sorted(Comparator.comparing((Map<?, ?> row) -> text(row.get("date"), "")).reversed())
            .limit(Math.max(1, limit))
            .map(item -> toImportRequest(symbol, market, item))
            .toList();
    }

    static List<DocumentImportRequest> parseStatementResponse(
        Map<String, Object> root,
        String symbol,
        Market market,
        int limit,
        String reportLabel,
        String source
    ) {
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
        Map<String, List<Map<?, ?>>> grouped = new HashMap<>();
        for (Object item : data) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            String date = text(row.get("date"), "");
            if (date.isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(date, ignored -> new ArrayList<>()).add(row);
        }
        return grouped.entrySet().stream()
            .sorted(Map.Entry.<String, List<Map<?, ?>>>comparingByKey().reversed())
            .limit(Math.max(1, limit))
            .map(entry -> toStatementImportRequest(symbol, market, entry.getKey(), reportLabel, source, entry.getValue()))
            .toList();
    }

    private static DocumentImportRequest toImportRequest(String symbol, Market market, Map<?, ?> item) {
        String revenueYear = text(item.get("revenue_year"), "");
        String revenueMonth = text(item.get("revenue_month"), "");
        String period = !revenueYear.isBlank() && !revenueMonth.isBlank()
            ? revenueYear + "-" + revenueMonth
            : text(item.get("date"), "latest");
        String title = symbol.replace(".TW", "") + " 月營收 " + period;
        String source = "finmind-month-revenue";
        String content = buildContent(item);
        return new DocumentImportRequest(
            symbol,
            market,
            DocumentType.FINANCIAL_REPORT,
            title,
            source,
            parsePublishedAt(item.get("date")),
            content
        );
    }

    private static DocumentImportRequest toStatementImportRequest(
        String symbol,
        Market market,
        String date,
        String reportLabel,
        String source,
        List<Map<?, ?>> rows
    ) {
        String title = symbol.replace(".TW", "") + " " + reportLabel + " " + date;
        String content = buildStatementContent(rows);
        return new DocumentImportRequest(
            symbol,
            market,
            DocumentType.FINANCIAL_REPORT,
            title,
            source,
            parsePublishedAt(date),
            content
        );
    }

    private Map<String, Object> getJson(String dataset, String symbol, LocalDate startDate) throws IOException, InterruptedException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("dataset", dataset);
        params.put("data_id", symbol.replace(".TW", ""));
        params.put("start_date", startDate.toString());
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/data?" + encode(params)))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .GET();
        String resolvedToken = resolvedToken();
        if (!resolvedToken.isBlank()) {
            builder.header("Authorization", "Bearer " + resolvedToken);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("finmind financials http " + response.statusCode());
        }
        return jsonParser.parseMap(response.body());
    }

    private static String buildContent(Map<?, ?> item) {
        List<String> lines = new ArrayList<>();
        appendLine(lines, "stock_id", item.get("stock_id"));
        appendLine(lines, "date", item.get("date"));
        appendLine(lines, "revenue_year", item.get("revenue_year"));
        appendLine(lines, "revenue_month", item.get("revenue_month"));
        appendLine(lines, "revenue", firstNonNull(item.get("revenue"), item.get("monthly_revenue")));
        appendLine(lines, "month_over_month", firstNonNull(item.get("month_over_month"), item.get("YoY_12M")));
        appendLine(lines, "revenue_growth_rate", item.get("revenue_growth_rate"));
        appendLine(lines, "last_month_revenue_change", item.get("last_month_revenue_change"));
        appendLine(lines, "last_year_revenue_change", item.get("last_year_revenue_change"));
        if (lines.isEmpty()) {
            for (Map.Entry<?, ?> entry : item.entrySet()) {
                appendLine(lines, String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return String.join("\n", lines);
    }

    private static String buildStatementContent(List<Map<?, ?>> rows) {
        List<String> lines = new ArrayList<>();
        for (Map<?, ?> row : rows) {
            String type = firstNonBlank(
                text(row.get("type"), ""),
                text(row.get("origin_name"), ""),
                text(row.get("accounting_title"), "")
            );
            String value = firstNonBlank(
                text(row.get("value"), ""),
                text(row.get("amount"), ""),
                text(row.get("money"), "")
            );
            if (!type.isBlank() || !value.isBlank()) {
                lines.add((type.isBlank() ? "item" : type) + ": " + value);
                continue;
            }
            for (Map.Entry<?, ?> entry : row.entrySet()) {
                appendLine(lines, String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return String.join("\n", lines);
    }

    private static void appendLine(List<String> lines, String key, Object rawValue) {
        String value = text(rawValue, "");
        if (!value.isBlank()) {
            lines.add(key + ": " + value);
        }
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static LocalDate monthRevenueStartDate(int limit) {
        return LocalDate.now(ZoneOffset.UTC).minusMonths(Math.max(6, limit + 2L)).withDayOfMonth(1);
    }

    private static LocalDate statementStartDate(int limit) {
        return LocalDate.now(ZoneOffset.UTC).minusYears(Math.max(2, limit)).withDayOfYear(1);
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
