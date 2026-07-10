package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.stockai.market.Market;
import com.example.stockai.auth.AuthService;
import com.example.stockai.auth.UserStore;
import com.example.stockai.common.RequestGuard;

import java.nio.file.Path;

class RagDocumentControllerTest {
    @TempDir
    Path tempDir;

    private final InMemoryVectorStore vectorStore = new InMemoryVectorStore();

    @Test
    void importIndexesChunksAndRetrieveFindsThem() {
        AuthService authService = new AuthService(new UserStore(tempDir.resolve("users.txt")), "test-secret-that-is-at-least-32-bytes", 3600);
        String token = authService.register("demo@example.com", "secret-password-123").token();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer " + token);
        RagDocumentController controller = new RagDocumentController(
            new DocumentIngestionService(),
            new DocumentEmbeddingService(new HashEmbeddingModel()),
            new DocumentRetriever(new HashEmbeddingModel(), vectorStore),
            vectorStore,
            new RequestGuard(authService)
        );
        RagDocumentController.ImportResponse imported = controller.importDocument(servletRequest, new DocumentImportRequest(
            "AAPL",
            Market.US,
            DocumentType.NEWS,
            "Apple guidance raised",
            "Reuters",
            Instant.parse("2026-07-06T00:00:00Z"),
            "Apple raised guidance after strong iPhone demand and better services margin."
        )).data();

        List<RetrievedDocument> hits = controller.retrieve(servletRequest, new DocumentRetrieveRequest(
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
