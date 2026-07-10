package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class RagDocumentControllerTest {
    private final InMemoryVectorStore vectorStore = new InMemoryVectorStore();
    private final RagDocumentController controller = new RagDocumentController(
        new DocumentIngestionService(),
        new DocumentEmbeddingService(new HashEmbeddingModel()),
        new DocumentRetriever(new HashEmbeddingModel(), vectorStore),
        vectorStore
    );

    @Test
    void importIndexesChunksAndRetrieveFindsThem() {
        RagDocumentController.ImportResponse imported = controller.importDocument(new DocumentImportRequest(
            "AAPL",
            Market.US,
            DocumentType.NEWS,
            "Apple guidance raised",
            "Reuters",
            Instant.parse("2026-07-06T00:00:00Z"),
            "Apple raised guidance after strong iPhone demand and better services margin."
        )).data();

        List<RetrievedDocument> hits = controller.retrieve(new DocumentRetrieveRequest(
            "iphone guidance",
            3,
            "AAPL",
            Market.US,
            DocumentType.NEWS,
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-07-06T23:59:59Z")
        )).data();

        assertThat(imported.symbol()).isEqualTo("AAPL");
        assertThat(imported.chunksImported()).isEqualTo(1);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).title()).isEqualTo("Apple guidance raised");
    }
}
