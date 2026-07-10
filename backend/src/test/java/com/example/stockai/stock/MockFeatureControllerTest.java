package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.stockai.auth.AuthService;
import com.example.stockai.auth.UserStore;
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
        return new AuthService(new UserStore(tempDir.resolve("users.txt")), "test-secret", 3600);
    }

    private MockFeatureController controller() {
        StockService stockService = new StockService(new MockMarketDataProvider());
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        HashEmbeddingModel embeddingModel = new HashEmbeddingModel();
        vectorStore.upsert(new DocumentEmbeddingService(embeddingModel).embed(java.util.List.of(
            new DocumentChunk(
                "chunk-1",
                "AAPL",
                Market.US,
                DocumentType.NEWS,
                "Apple demand holds",
                "Reuters",
                java.time.Instant.parse("2026-07-06T00:00:00Z"),
                "Apple demand held up and services margin improved in recent channel checks."
            )
        )));
        return new MockFeatureController(
            new MockAiProviderAdapter(),
            new RagContextService(stockService, new DocumentRetriever(embeddingModel, vectorStore)),
            stockService,
            new WatchlistService(tempDir.resolve("watchlist.txt")),
            authService()
        );
    }

    @Test
    void analysisUsesDefaultProvider() {
        MockFeatureController.AiAnalysisResponse response = controller().analysis(new MockFeatureController.AiRequest(Market.US, "AAPL", null, 5)).data();

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
        MockFeatureController.AiChatResponse response = controller().chat(new MockFeatureController.AiChatRequest(Market.US, "AAPL", "OPENAI", "最近風險在哪")).data();

        assertThat(response.source()).isEqualTo("mock-ai");
        assertThat(response.evidence()).hasSize(1);
        assertThat(response.evidence().get(0).source()).isEqualTo("Reuters");
    }

    @Test
    void watchlistNormalizesTaiwanSymbol() {
        MockFeatureController controller = controller();
        StockRecord stock = controller.addWatchlist(null, new WatchlistItem(Market.TW, "2330")).data();

        assertThat(stock.symbol()).isEqualTo("2330.TW");
        assertThat(controller.watchlist(null).data()).extracting(StockRecord::symbol).contains("2330.TW");
    }

    @Test
    void backtestReturnsMetrics() {
        MockFeatureController.BacktestResponse response = controller().backtest(new MockFeatureController.BacktestRequest(Market.TW, "2454", null)).data();

        assertThat(response.symbol()).isEqualTo("2454.TW");
        assertThat(response.tradeCount()).isEqualTo(42);
        assertThat(response.maxDrawdown()).isNegative();
    }
}
