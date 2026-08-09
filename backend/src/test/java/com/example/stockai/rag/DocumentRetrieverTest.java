package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class DocumentRetrieverTest {
    @Test
    void retrievesRankedDocumentsWithSnippetAndFilters() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        DocumentEmbeddingService embeddingService = new DocumentEmbeddingService(new HashEmbeddingModel());
        DocumentRetriever retriever = new DocumentRetriever(new HashEmbeddingModel(), store);

        List<DocumentChunk> chunks = List.of(
            new DocumentChunk("chunk-1", "AAPL", Market.US, DocumentType.NEWS, "Apple iPhone demand", "Reuters", Instant.parse("2026-07-06T00:00:00Z"), "Apple iPhone demand improved and revenue outlook strengthened."),
            new DocumentChunk("chunk-2", "AAPL", Market.US, DocumentType.COMPANY_ANNOUNCEMENT, "Apple filing", "SEC", Instant.parse("2026-06-01T00:00:00Z"), "Board approved a new share repurchase plan."),
            new DocumentChunk("chunk-3", "2330.TW", Market.TW, DocumentType.NEWS, "TSMC capex", "CNA", Instant.parse("2026-07-06T00:00:00Z"), "TSMC raised capex guidance for advanced nodes.")
        );
        store.upsert(embeddingService.embed(chunks));

        List<RetrievedDocument> hits = retriever.retrieve(new DocumentRetrieveRequest(
            "iphone revenue outlook",
            3,
            "AAPL",
            Market.US,
            DocumentType.NEWS,
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-07-06T23:59:59Z")
        ));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo("chunk-1");
        assertThat(hits.get(0).title()).isEqualTo("Apple iPhone demand");
        assertThat(hits.get(0).snippet()).contains("Apple iPhone demand");
        assertThat(hits.get(0).score()).isGreaterThan(0);
    }

    @Test
    void isolatesDocumentsByOwner() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        HashEmbeddingModel model = new HashEmbeddingModel();
        store.upsert(new DocumentEmbeddingService(model).embed(List.of(
            new DocumentChunk("owner-a", "a@example.com", "AAPL", Market.US, DocumentType.NEWS, "Private A", "A", Instant.parse("2026-07-06T00:00:00Z"), "owner a evidence"),
            new DocumentChunk("owner-b", "b@example.com", "AAPL", Market.US, DocumentType.NEWS, "Private B", "B", Instant.parse("2026-07-06T00:00:00Z"), "owner b evidence")
        )));
        DocumentRetriever retriever = new DocumentRetriever(model, store);
        DocumentRetrieveRequest request = new DocumentRetrieveRequest("evidence", 10, "AAPL", Market.US, null, null, null);

        assertThat(retriever.retrieve(request, "a@example.com")).extracting(RetrievedDocument::title).containsExactly("Private A");
        assertThat(retriever.retrieve(request, "b@example.com")).extracting(RetrievedDocument::title).containsExactly("Private B");
    }

    @Test
    void filtersHitsBelowConfiguredRelevanceThreshold() {
        DocumentChunk chunk = new DocumentChunk(
            "chunk-threshold",
            "AAPL",
            Market.US,
            DocumentType.NEWS,
            "Threshold test",
            "Test source",
            Instant.parse("2026-07-06T00:00:00Z"),
            "Evidence content"
        );
        VectorDocument document = new VectorDocument(chunk, "test", List.of(1d, 0d));
        VectorDocument lowRelevanceDocument = new VectorDocument(new DocumentChunk(
            "chunk-low",
            "MSFT",
            Market.US,
            DocumentType.NEWS,
            "Unrelated test",
            "Other source",
            Instant.parse("2026-07-06T00:00:00Z"),
            "Unrelated content"
        ), "test", List.of(1d, 0d));
        VectorStore store = new VectorStore() {
            @Override
            public void upsert(List<VectorDocument> documents) {}

            @Override
            public List<VectorSearchHit> search(VectorSearchQuery query) {
                return List.of(
                    new VectorSearchHit(lowRelevanceDocument, 0.14d),
                    new VectorSearchHit(document, 0.30d)
                );
            }
        };

        DocumentRetriever retriever = new DocumentRetriever(
            new HashEmbeddingModel(),
            store,
            0.15d
        );

        List<RetrievedDocument> hits = retriever.retrieve(
            new DocumentRetrieveRequest("AAPL", 3, "AAPL", Market.US, null, null, null)
        );

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).score()).isCloseTo(0.475d, org.assertj.core.data.Offset.offset(1e-9d));
    }

    @Test
    void lexicalSignalsCanRerankCandidatesWithSimilarVectorScores() {
        DocumentChunk relevant = new DocumentChunk(
            "chunk-relevant",
            "AAPL",
            Market.US,
            DocumentType.NEWS,
            "Revenue outlook",
            "Reuters",
            Instant.parse("2026-07-06T00:00:00Z"),
            "Revenue outlook strengthened after services growth."
        );
        DocumentChunk unrelated = new DocumentChunk(
            "chunk-unrelated",
            "AAPL",
            Market.US,
            DocumentType.NEWS,
            "Board update",
            "SEC",
            Instant.parse("2026-07-06T00:00:00Z"),
            "The board approved a routine administrative update."
        );
        VectorDocument relevantVector = new VectorDocument(relevant, "test", List.of(1d, 0d));
        VectorDocument unrelatedVector = new VectorDocument(unrelated, "test", List.of(1d, 0d));
        VectorStore store = new VectorStore() {
            @Override
            public void upsert(List<VectorDocument> documents) {}

            @Override
            public List<VectorSearchHit> search(VectorSearchQuery query) {
                return List.of(
                    new VectorSearchHit(unrelatedVector, 0.80d),
                    new VectorSearchHit(relevantVector, 0.79d)
                );
            }
        };

        DocumentRetriever retriever = new DocumentRetriever(new HashEmbeddingModel(), store, 0.15d);
        List<RetrievedDocument> hits = retriever.retrieve(
            new DocumentRetrieveRequest("revenue outlook", 1, "AAPL", Market.US, null, null, null)
        );

        assertThat(hits).extracting(RetrievedDocument::chunkId).containsExactly("chunk-relevant");
    }
}
