package com.example.stockai.rag;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;

import com.example.stockai.market.Market;

@Service
class TwseDisclosureSourceAdapter {
    static final String DEFAULT_DISCLOSURE_URL = "https://mopsov.twse.com.tw/mops/web/ajax_t05st01?firstin=1&TYPEK=all&co_id={symbol}&year={rocYear}&month=&b_date=&e_date=";
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final Pattern TABLE_ROW = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    private static final Pattern TABLE_CELL = Pattern.compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>");

    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final String disclosureUrl;

    @Autowired
    TwseDisclosureSourceAdapter(
        @Value("${stockai.twse.disclosure-url:" + DEFAULT_DISCLOSURE_URL + "}") String disclosureUrl
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), JsonParserFactory.getJsonParser(), disclosureUrl);
    }

    TwseDisclosureSourceAdapter(HttpClient httpClient, JsonParser jsonParser, String disclosureUrl) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.disclosureUrl = disclosureUrl == null ? "" : disclosureUrl.trim();
    }

    List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
        if (market != Market.TW || isBlank(disclosureUrl)) {
            return List.of();
        }
        try {
            String code = symbol.replace(".TW", "");
            String url = disclosureUrl
                .replace("{symbol}", encode(code))
                .replace("{code}", encode(code))
                .replace("{rocYear}", String.valueOf(LocalDate.now(TAIPEI).getYear() - 1911))
                .replace("{year}", String.valueOf(LocalDate.now(TAIPEI).getYear() - 1911))
                .replace("{limit}", String.valueOf(Math.max(1, limit)));
            String body = getBody(url);
            if (looksLikeJson(body)) {
                Object parsed = body.trim().startsWith("[") ? jsonParser.parseList(body) : jsonParser.parseMap(body);
                return parseDisclosureResponse(parsed, symbol, market, limit);
            }
            return parseMopsHtml(body, symbol, market, limit);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    static List<DocumentImportRequest> parseDisclosureResponse(Object parsed, String symbol, Market market, int limit) {
        List<Map<String, Object>> items;
        if (parsed instanceof List<?> list) {
            items = (List<Map<String, Object>>) (List<?>) list;
        } else if (parsed instanceof Map<?, ?> root) {
            Object data = root.get("data");
            if (data == null) {
                data = root.get("items");
            }
            if (!(data instanceof List<?> list)) {
                return List.of();
            }
            items = (List<Map<String, Object>>) (List<?>) list;
        } else {
            return List.of();
        }
        return items.stream()
            .limit(Math.max(1, limit))
            .filter(item -> !text(item.get("content"), text(item.get("summary"), "")).isBlank())
            .map(item -> new DocumentImportRequest(
                symbol,
                market,
                DocumentType.COMPANY_ANNOUNCEMENT,
                text(item.get("title"), symbol + " company announcement"),
                text(item.get("source"), "twse-disclosure"),
                parsePublishedAt(item.get("publishedAt")),
                text(item.get("content"), text(item.get("summary"), ""))
            ))
            .toList();
    }

    static List<DocumentImportRequest> parseMopsHtml(String html, String symbol, Market market, int limit) {
        if (isBlank(html)) {
            return List.of();
        }
        String code = symbol.replace(".TW", "");
        Matcher rowMatcher = TABLE_ROW.matcher(html);
        return rowMatcher.results()
            .map(match -> cells(match.group(1)))
            .filter(cells -> cells.size() >= 4)
            .filter(cells -> cells.stream().anyMatch(cell -> cell.equals(code)))
            .map(cells -> toMopsImportRequest(cells, symbol, market))
            .filter(request -> !isBlank(request.content()))
            .limit(Math.max(1, limit))
            .toList();
    }

    private static DocumentImportRequest toMopsImportRequest(List<String> cells, String symbol, Market market) {
        String code = symbol.replace(".TW", "");
        int codeIndex = cells.indexOf(code);
        String company = valueAt(cells, codeIndex + 1, symbol);
        String title = firstLongTextAfter(cells, Math.max(0, codeIndex + 2), symbol + " 重大訊息");
        String dateText = firstMatching(cells, "\\d{2,3}/\\d{1,2}/\\d{1,2}");
        String timeText = firstMatching(cells, "\\d{1,2}:\\d{2}(:\\d{2})?");
        String content = String.join("\n", cells);
        return new DocumentImportRequest(
            symbol,
            market,
            DocumentType.COMPANY_ANNOUNCEMENT,
            company + " " + title,
            "MOPS 重大訊息",
            parseMopsPublishedAt(dateText, timeText),
            content
        );
    }

    private String getBody(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json, text/html, */*")
            .header("User-Agent", "Mozilla/5.0")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("twse disclosure http " + response.statusCode());
        }
        return response.body();
    }

    private static Instant parsePublishedAt(Object value) {
        String text = text(value, "");
        if (text.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(text);
    }

    private static Instant parseMopsPublishedAt(String dateText, String timeText) {
        if (isBlank(dateText)) {
            return Instant.now();
        }
        try {
            String[] dateParts = dateText.split("/");
            int year = Integer.parseInt(dateParts[0]) + 1911;
            int month = Integer.parseInt(dateParts[1]);
            int day = Integer.parseInt(dateParts[2]);
            LocalTime time = isBlank(timeText) ? LocalTime.MIDNIGHT : LocalTime.parse(timeText.length() == 5 ? timeText + ":00" : timeText);
            return LocalDate.of(year, month, day).atTime(time).atZone(TAIPEI).toInstant();
        } catch (RuntimeException ex) {
            return Instant.now();
        }
    }

    private static List<String> cells(String rowHtml) {
        Matcher cellMatcher = TABLE_CELL.matcher(rowHtml);
        return cellMatcher.results()
            .map(match -> cleanHtml(match.group(1)))
            .filter(value -> !value.isBlank())
            .toList();
    }

    private static String cleanHtml(String html) {
        return html
            .replaceAll("(?is)<br\\s*/?>", "\n")
            .replaceAll("(?is)<[^>]+>", "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replaceAll("[\\t\\x0B\\f ]+", " ")
            .replaceAll(" *\n *", "\n")
            .trim();
    }

    private static String valueAt(List<String> cells, int index, String fallback) {
        return index >= 0 && index < cells.size() && !isBlank(cells.get(index)) ? cells.get(index) : fallback;
    }

    private static String firstLongTextAfter(List<String> cells, int start, String fallback) {
        for (int i = start; i < cells.size(); i++) {
            String value = cells.get(i);
            if (!isBlank(value) && value.length() >= 6 && !value.matches("\\d{1,3}(/\\d{1,2}){0,2}") && !value.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) {
                return value;
            }
        }
        return fallback;
    }

    private static String firstMatching(List<String> cells, String pattern) {
        for (String value : cells) {
            if (value.matches(pattern)) {
                return value;
            }
        }
        return "";
    }

    private static String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private static boolean looksLikeJson(String body) {
        if (body == null) {
            return false;
        }
        String trimmed = body.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
