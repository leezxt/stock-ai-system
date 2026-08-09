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
        assertThat(response.intent()).contains("RISK");
        assertThat(response.answerStatus()).isEqualTo("READY");
        assertThat(response.tools()).extracting(AiToolCall::name)
            .contains("market_quote", "technical_indicators", "prediction_model");
    }

    @Test
    void chatAcceptsBoundedConversationHistory() {
        Fixture fixture = fixture();
        MockFeatureController.AiChatRequest request = new MockFeatureController.AiChatRequest(
            Market.US,
            "AAPL",
            "OPENAI",
            "那如果跌破 MA20 呢？",
            java.util.List.of(
                new ChatTurn("user", "先看一下目前趨勢"),
                new ChatTurn("assistant", "目前偏中性偏多。")
            )
        );

        MockFeatureController.AiChatResponse response = fixture.controller().chat(fixture.request(), request).data();

        assertThat(response.message()).contains("跌破 MA20");
        assertThat(request.history()).hasSize(2);
    }

    @Test
    void chatAcceptsOpenEndedResearchQuestionInsteadOfAKeywordWhitelist() {
        Fixture fixture = fixture();
        MockFeatureController.AiChatResponse response = fixture.controller().chat(
            fixture.request(),
            new MockFeatureController.AiChatRequest(
                Market.US,
                "AAPL",
                "OPENAI",
                "供應鏈集中度如何影響這檔股票的風險？"
            )
        ).data();

        assertThat(response.message()).contains("供應鏈集中度如何影響這檔股票的風險？");
        assertThat(response.message()).contains("不限制快捷問題");
    }

    @Test
    void outOfScopeQuestionFailsClosedBeforeCallingProvider() {
        Fixture fixture = fixture();

        MockFeatureController.AiChatResponse response = fixture.controller().chat(
            fixture.request(),
            new MockFeatureController.AiChatRequest(Market.US, "AAPL", "OPENAI", "台北明天天氣如何？")
        ).data();

        assertThat(response.source()).isEqualTo("unavailable-ai:out-of-scope");
        assertThat(response.answerStatus()).isEqualTo("OUT_OF_SCOPE");
        assertThat(response.intent()).isEqualTo("OUT_OF_SCOPE");
        assertThat(response.message()).contains("只支援股票價格");
        assertThat(response.tools()).isEmpty();
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
        assertThat(response.tradeCount()).isZero();
        assertThat(response.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(response.dataPointCount()).isZero();
        assertThat(response.source()).isEqualTo("historical-price-replay-v1");
        assertThat(response.priceSources()).containsExactly("mock");
        assertThat(response.conditions().minAiScore()).isEqualByComparingTo("70");
        assertThat(response.conditions().minUpProbability()).isEqualByComparingTo("0.60");
        assertThat(response.conditions().maxRiskLevel()).isEqualTo("MEDIUM");
        assertThat(response.conditions().entryRule()).contains("signal score (proxy) >= 70", "risk <= MEDIUM");
        assertThat(response.note()).contains("至少");
    }

    @Test
    void backtestReturnsDetailedCustomConditions() {
        Fixture fixture = fixture();
        MockFeatureController.BacktestResponse response = fixture.controller().backtest(
            fixture.request(),
            new MockFeatureController.BacktestRequest(
                Market.US,
                "AAPL",
                new MockFeatureController.Strategy(new java.math.BigDecimal("82"), new java.math.BigDecimal("0.68"), "LOW")
            )
        ).data();

        assertThat(response.conditions().minAiScore()).isEqualByComparingTo("82");
        assertThat(response.conditions().minUpProbability()).isEqualByComparingTo("0.68");
        assertThat(response.conditions().maxRiskLevel()).isEqualTo("LOW");
        assertThat(response.conditions().entryRule()).contains("signal score (proxy) >= 82", "upProbability >= 0.68", "risk <= LOW");
        assertThat(response.conditions().dataScope()).contains("priceSources=mock");
    }

    @Test
    void backtestRejectsInvalidStrategyThresholds() {
        Fixture fixture = fixture();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.controller().backtest(
            fixture.request(),
            new MockFeatureController.BacktestRequest(
                Market.US,
                "AAPL",
                new MockFeatureController.Strategy(new java.math.BigDecimal("101"), new java.math.BigDecimal("0.60"), "MEDIUM")
            )
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minAiScore");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.controller().backtest(
            fixture.request(),
            new MockFeatureController.BacktestRequest(
                Market.US,
                "AAPL",
                new MockFeatureController.Strategy(new java.math.BigDecimal("70"), new java.math.BigDecimal("0.60"), "UNKNOWN")
            )
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxRiskLevel");
    }

    private record Fixture(MockFeatureController controller, MockHttpServletRequest request) {}
}
