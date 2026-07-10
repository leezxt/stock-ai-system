package com.example.stockai.rag;

import java.time.Instant;

import com.example.stockai.market.Market;

public record DocumentRetrieveRequest(
    String queryText,
    int topK,
    String symbol,
    Market market,
    DocumentType docType,
    Instant publishedFrom,
    Instant publishedTo
) {
    public DocumentRetrieveRequest {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText must not be blank");
        }
        if (topK <= 0 || topK > 20) {
            throw new IllegalArgumentException("topK must be between 1 and 20");
        }
        queryText = queryText.trim();
        if (queryText.length() > 2_000) {
            throw new IllegalArgumentException("queryText must not exceed 2000 characters");
        }
        if (publishedFrom != null && publishedTo != null && publishedFrom.isAfter(publishedTo)) {
            throw new IllegalArgumentException("publishedFrom must not be after publishedTo");
        }
        symbol = symbol == null || symbol.isBlank() ? null : symbol.trim();
    }
}
