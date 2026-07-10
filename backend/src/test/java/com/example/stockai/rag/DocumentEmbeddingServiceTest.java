package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class DocumentEmbeddingServiceTest {
    @Test
    void embedsChunksIntoVectorDocuments() {
        DocumentEmbeddingService service = new DocumentEmbeddingService(new HashEmbeddingModel());
        DocumentChunk chunk = new DocumentChunk(
            "chunk-1",
            "AAPL",
            Market.US,
            DocumentType.NEWS,
            "Apple news",
            "Reuters",
            Instant.parse("2026-07-06T00:00:00Z"),
            "Apple revenue rose sharply."
        );

        List<VectorDocument> embedded = service.embed(List.of(chunk));

        assertThat(embedded).hasSize(1);
        assertThat(embedded.get(0).chunk()).isEqualTo(chunk);
        assertThat(embedded.get(0).embeddingModel()).isEqualTo("hash-embedding-v1");
        assertThat(embedded.get(0).embedding()).hasSize(16);
    }

    @Test
    void producesDeterministicEmbeddings() {
        HashEmbeddingModel model = new HashEmbeddingModel();

        List<Double> first = model.embed("same text");
        List<Double> second = model.embed("same text");

        assertThat(first).isEqualTo(second);
    }
}
