package com.example.stockai.rag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.example.stockai.market.Market;

@Service
class YahooFinanceNewsSourceAdapter {
    private final HttpClient httpClient;
    private final String rssUrl;

    @Autowired
    YahooFinanceNewsSourceAdapter(
        @Value("${stockai.yahoo.news-rss-url:https://feeds.finance.yahoo.com/rss/2.0/headline}") String rssUrl
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), rssUrl);
    }

    YahooFinanceNewsSourceAdapter(HttpClient httpClient, String rssUrl) {
        this.httpClient = httpClient;
        this.rssUrl = rssUrl == null ? "" : rssUrl.trim();
    }

    List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
        if (rssUrl.isBlank()) {
            return List.of();
        }
        try {
            return parseRss(getRss(symbol), symbol, market, limit);
        } catch (IOException | InterruptedException | ParserConfigurationException | SAXException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    static List<DocumentImportRequest> parseRss(String xml, String symbol, Market market, int limit)
        throws ParserConfigurationException, IOException, SAXException {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        org.w3c.dom.Document document = factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList items = document.getElementsByTagName("item");
        List<DocumentImportRequest> requests = new ArrayList<>();
        int max = Math.max(1, limit);
        for (int i = 0; i < items.getLength() && requests.size() < max; i++) {
            if (items.item(i) instanceof Element item) {
                String title = text(item, "title", symbol + " Yahoo Finance news");
                String link = text(item, "link", "");
                String description = text(item, "description", title);
                String content = link.isBlank() ? title + "\n\n" + description : title + "\n\n" + description + "\n\n" + link;
                requests.add(new DocumentImportRequest(
                    symbol,
                    market,
                    DocumentType.NEWS,
                    title,
                    "Yahoo Finance",
                    parsePublishedAt(text(item, "pubDate", "")),
                    content
                ));
            }
        }
        return requests;
    }

    private String getRss(String symbol) throws IOException, InterruptedException {
        String url = rssUrl
            + "?s=" + encode(symbol)
            + "&region=US&lang=en-US";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/rss+xml, application/xml, text/xml")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("yahoo finance news http " + response.statusCode());
        }
        return response.body();
    }

    private static Instant parsePublishedAt(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException ex) {
            return Instant.now();
        }
    }

    private static String text(Element parent, String tag, String fallback) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null || nodes.item(0).getTextContent().isBlank()) {
            return fallback;
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
