package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class InMemoryVectorStoreTest {
    @Test
    void ranksCloserVectorsFirst() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert(List.of(
            document("chunk-a", "AAPL", Market.US, DocumentType.NEWS, Instant.parse("2026-07-06T00:00:00Z"), List.of(1.0, 0.0)),
            document("chunk-b", "AAPL", Market.US, DocumentType.NEWS, Instant.parse("2026-07-06T00:00:00Z"), List.of(0.0, 1.0))
        ));

        List<VectorSearchHit> hits = store.search(new VectorSearchQuery(List.of(0.9, 0.1), 2, "AAPL", Market.US, DocumentType.NEWS, null, null));

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).document().chunk().chunkId()).isEqualTo("chunk-a");
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
    }

    @Test
    void filtersByTypeAndPublishedAt() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert(List.of(
            document("chunk-news", "2330.TW", Market.TW, DocumentType.NEWS, Instant.parse("2026-07-05T00:00:00Z"), List.of(1.0, 1.0)),
            document("chunk-report", "2330.TW", Market.TW, DocumentType.FINANCIAL_REPORT, Instant.parse("2026-06-01T00:00:00Z"), List.of(1.0, 1.0))
        ));

        List<VectorSearchHit> hits = store.search(new VectorSearchQuery(
            List.of(1.0, 1.0),
            5,
            "2330.TW",
            Market.TW,
            DocumentType.NEWS,
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-07-06T00:00:00Z")
        ));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).document().chunk().chunkId()).isEqualTo("chunk-news");
    }

    @Test
    void filtersOutVectorsFromAnotherEmbeddingFamily() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert(List.of(
            new VectorDocument(
                new DocumentChunk("chunk-hash", "AAPL", Market.US, DocumentType.NEWS, "Hash", "Test", Instant.parse("2026-07-06T00:00:00Z"), "Hash content"),
                "hash-embedding-v1",
                List.of(1.0, 0.0)
            ),
            new VectorDocument(
                new DocumentChunk("chunk-openai", "AAPL", Market.US, DocumentType.NEWS, "OpenAI", "Test", Instant.parse("2026-07-06T00:00:00Z"), "OpenAI content"),
                "openai-text-embedding-3-small-dim16",
                List.of(1.0, 0.0)
            )
        ));

        List<VectorSearchHit> hits = store.search(new VectorSearchQuery(
            List.of(1.0, 0.0),
            5,
            "AAPL",
            Market.US,
            DocumentType.NEWS,
            null,
            null,
            "",
            "openai-text-embedding-3-small-dim16"
        ));

        assertThat(hits).extracting(hit -> hit.document().chunk().chunkId()).containsExactly("chunk-openai");
    }

    private static VectorDocument document(
        String chunkId,
        String symbol,
        Market market,
        DocumentType docType,
        Instant publishedAt,
        List<Double> embedding
    ) {
        return new VectorDocument(
            new DocumentChunk(chunkId, symbol, market, docType, "Title " + chunkId, "Test", publishedAt, "Content " + chunkId),
            "test-embedding",
            embedding
        );
    }
}
