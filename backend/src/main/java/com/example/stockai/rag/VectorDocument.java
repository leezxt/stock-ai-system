package com.example.stockai.rag;

import java.util.List;

public record VectorDocument(
    DocumentChunk chunk,
    String embeddingModel,
    List<Double> embedding
) {
    public VectorDocument {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk must not be null");
        }
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("embeddingModel must not be blank");
        }
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        embeddingModel = embeddingModel.trim();
        embedding = List.copyOf(embedding);
        for (Double value : embedding) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("embedding must contain only finite numbers");
            }
        }
    }
}
