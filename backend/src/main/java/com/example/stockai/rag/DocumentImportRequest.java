package com.example.stockai.rag;

import java.time.Instant;

import com.example.stockai.market.Market;
import com.example.stockai.market.SymbolNormalizer;

public record DocumentImportRequest(
    String symbol,
    Market market,
    DocumentType docType,
    String title,
    String source,
    Instant publishedAt,
    String content
) {
    public DocumentImportRequest {
        market = requireNonNull(market, "market");
        docType = requireNonNull(docType, "docType");
        symbol = SymbolNormalizer.normalize(market, requireText(symbol, "symbol"));
        title = requireText(title, "title");
        source = requireText(source, "source");
        publishedAt = requireNonNull(publishedAt, "publishedAt");
        content = requireText(content, "content");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
