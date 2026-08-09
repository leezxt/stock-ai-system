package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.stockai.auth.AuthService;
import com.example.stockai.auth.UserStore;
import com.example.stockai.common.RequestGuard;
import com.example.stockai.market.Market;
import com.example.stockai.rag.DocumentRetrieveRequest;
import com.example.stockai.rag.DocumentRetriever;
import com.example.stockai.rag.HashEmbeddingModel;
import com.example.stockai.rag.InMemoryVectorStore;
import com.example.stockai.rag.RetrievedDocument;

class NewsEvaluationControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void evaluatesNewsForAuthenticatedOwner() {
        AuthService authService = new AuthService(
            new UserStore(tempDir.resolve("users.txt")),
            "test-secret-that-is-at-least-32-bytes",
            3600
        );
        String token = authService.register("demo@example.com", "secret-password-123").token();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        NewsScoreService service = new NewsScoreService(new DocumentRetriever(new HashEmbeddingModel(), new InMemoryVectorStore()) {
            @Override
            public List<RetrievedDocument> retrieve(DocumentRetrieveRequest ignored, String ownerEmail) {
                return List.of(new RetrievedDocument(
                    "news-1",
                    "AAPL",
                    Market.US,
                    com.example.stockai.rag.DocumentType.NEWS,
                    "Apple demand improves",
                    "Reuters",
                    Instant.parse("2026-08-07T00:00:00Z"),
                    "Services growth improved and demand remained strong.",
                    0.88d
                ));
            }
        }, Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC));
        NewsEvaluationController controller = new NewsEvaluationController(service, new RequestGuard(authService));

        NewsEvaluationResponse response = controller.score(request, Market.US, "AAPL", 5).data();

        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.articleCount()).isEqualTo(1);
        assertThat(response.overallLabel()).isEqualTo("BULLISH");
        assertThat(response.articles().get(0).source()).isEqualTo("Reuters");
    }
}
