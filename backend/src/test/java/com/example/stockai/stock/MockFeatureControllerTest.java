package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.stockai.auth.AuthService;
import com.example.stockai.auth.UserStore;
import com.example.stockai.common.RequestGuard;
import com.example.stockai.market.Market;
import com.example.stockai.rag.DocumentChunk;
import com.example.stockai.rag.DocumentEmbeddingService;
import com.example.stockai.rag.DocumentRetriever;
import com.example.stockai.rag.DocumentType;
import com.example.stockai.rag.HashEmbeddingModel;
import com.example.stockai.rag.InMemoryVectorStore;

class MockFeatureControllerTest {
    @TempDir
    Path tempDir;

    private AuthService authService() {
        return new AuthService(new UserStore(tempDir.resolve("users.txt")), "test-secret-that-is-at-least-32-bytes", 3600);
    }

    private Fixture fixture() {
        AuthService authService = authService();
        String token = authService.register("demo@example.com", "secret-password-123").token();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        StockService stockService = new StockService(new MockMarketDataProvider());
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        HashEmbeddingModel embeddingModel = new HashEmbeddingModel();
        vectorStore.upsert(new DocumentEmbeddingService(embeddingModel).embed(java.util.List.of(
            new DocumentChunk(
                "chunk-1",
                "demo@example.com",
                "AAPL",
                Market.US,
                DocumentType.NEWS,
                "Apple demand holds",
                "Reuters",
                java.time.Instant.parse("2026-07-06T00:00:00Z"),
                "Apple demand held up and services margin improved in recent channel checks."
            )
        )));
        MockFeatureController controller = new MockFeatureController(
            new MockAiProviderAdapter(),
            new RagContextService(stockService, new DocumentRetriever(embeddingModel, vectorStore)),
            stockService,
            new WatchlistService(tempDir.resolve("watchlist.txt")),
            new RequestGuard(authService)
        );
        return new Fixture(controller, request);
    }

    @Test
    void analysisUsesDefaultProvider() {
        Fixture fixture = fixture();
        MockFeatureController.AiAnalysisResponse response = fixture.controller().analysis(fixture.request(), new MockFeatureController.AiRequest(Market.US, "AAPL", null, 5)).data();

        assertThat(response.provider()).isEqualTo("OPENAI");
        assertThat(response.source()).isEqualTo("mock-ai");
        assertThat(response.aiScore()).isGreaterThan(0);
        assertThat(response.bullishReasons()).isNotEmpty();
        assertThat(response.evidence()).hasSize(1);
        assertThat(response.evidence().get(0).title()).isEqualTo("Apple demand holds");
        assertThat(response.conclusion()).contains("不代表投資建議");
    }

    @Test
    void chatReturnsEvidence() {
        Fixture fixture = fixture();
        MockFeatureController.AiChatResponse response = fixture.controller().chat(fixture.request(), new MockFeatureController.AiChatRequest(Market.US, "AAPL", "OPENAI", "最近風險在哪")).data();

        assertThat(response.source()).isEqualTo("mock-ai");
        assertThat(response.evidence()).hasSize(1);
        assertThat(response.evidence().get(0).source()).isEqualTo("Reuters");
        assertThat(response.chart()).isNull();
    }

    @Test
    void chatReturnsChartForNumericTrendQuestion() {
        Fixture fixture = fixture();
        MockFeatureController.AiChatResponse response = fixture.controller().chat(
            fixture.request(),
            new MockFeatureController.AiChatRequest(Market.US, "AAPL", "OPENAI", "請顯示最近價格走勢圖表")
        ).data();

        assertThat(response.chart()).isNotNull();
        assertThat(response.chart().type()).isEqualTo("line");
        assertThat(response.chart().labels()).hasSameSizeAs(response.chart().values());
        assertThat(response.chart().values()).hasSizeGreaterThan(1);
    }

    @Test
    void watchlistNormalizesTaiwanSymbol() {
        Fixture fixture = fixture();
        MockFeatureController controller = fixture.controller();
        StockRecord stock = controller.addWatchlist(fixture.request(), new WatchlistItem(Market.TW, "2330")).data();

        assertThat(stock.symbol()).isEqualTo("2330.TW");
        assertThat(controller.watchlist(fixture.request()).data()).extracting(StockRecord::symbol).contains("2330.TW");
    }

    @Test
    void backtestReturnsMetrics() {
        Fixture fixture = fixture();
        MockFeatureController.BacktestResponse response = fixture.controller().backtest(fixture.request(), new MockFeatureController.BacktestRequest(Market.TW, "2454", null)).data();

        assertThat(response.symbol()).isEqualTo("2454.TW");
        assertThat(response.tradeCount()).isEqualTo(42);
        assertThat(response.maxDrawdown()).isNegative();
    }

    private record Fixture(MockFeatureController controller, MockHttpServletRequest request) {}
}
