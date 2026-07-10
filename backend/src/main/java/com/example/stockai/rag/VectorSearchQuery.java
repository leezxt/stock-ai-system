package com.example.stockai.rag;

import java.time.Instant;
import java.util.List;

import com.example.stockai.market.Market;

public record VectorSearchQuery(
    List<Double> embedding,
    int topK,
    String symbol,
    Market market,
    DocumentType docType,
    Instant publishedFrom,
    Instant publishedTo,
    String ownerEmail
) {
    public VectorSearchQuery(
        List<Double> embedding,
        int topK,
        String symbol,
        Market market,
        DocumentType docType,
        Instant publishedFrom,
        Instant publishedTo
    ) {
        this(embedding, topK, symbol, market, docType, publishedFrom, publishedTo, "");
    }

    public VectorSearchQuery {
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        embedding = List.copyOf(embedding);
        for (Double value : embedding) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("embedding must contain only finite numbers");
            }
        }
        if (topK <= 0 || topK > 20) {
            throw new IllegalArgumentException("topK must be between 1 and 20");
        }
        symbol = symbol == null || symbol.isBlank() ? null : symbol.trim();
        ownerEmail = ownerEmail == null ? "" : ownerEmail.trim().toLowerCase();
    }
}
