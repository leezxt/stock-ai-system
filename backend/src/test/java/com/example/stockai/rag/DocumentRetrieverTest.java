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
}
