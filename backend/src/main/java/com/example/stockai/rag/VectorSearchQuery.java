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
    Instant publishedTo
) {
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
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }
        symbol = symbol == null || symbol.isBlank() ? null : symbol.trim();
    }
}
