package com.example.stockai.rag;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class DocumentEmbeddingService {
    private final EmbeddingModel embeddingModel;

    public DocumentEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<VectorDocument> embed(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
            .map(chunk -> new VectorDocument(chunk, embeddingModel.modelName(), embeddingModel.embed(chunk.content())))
            .toList();
    }
}
