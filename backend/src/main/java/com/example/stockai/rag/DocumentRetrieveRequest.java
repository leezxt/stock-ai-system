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
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }
        queryText = queryText.trim();
        symbol = symbol == null || symbol.isBlank() ? null : symbol.trim();
    }
}
