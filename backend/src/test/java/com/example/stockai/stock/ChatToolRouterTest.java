package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;
import com.example.stockai.rag.DocumentType;
import com.example.stockai.rag.RetrievedDocument;

class ChatToolRouterTest {
    @Test
    void routesTechnicalPredictionAndNewsToAuditableTools() {
        RagContext context = new RagContext(
            new StockRecord("AAPL", "Apple", Market.US, "USD", java.math.BigDecimal.valueOf(200), java.math.BigDecimal.valueOf(1.2), List.of(java.math.BigDecimal.valueOf(198), java.math.BigDecimal.valueOf(200)), "yahoo-finance"),
            new TechnicalSummaryResponse("AAPL", Market.US, java.math.BigDecimal.valueOf(199), java.math.BigDecimal.valueOf(195), java.math.BigDecimal.valueOf(190), java.math.BigDecimal.valueOf(61), "BULLISH", java.math.BigDecimal.valueOf(4), java.time.Instant.parse("2026-08-08T00:00:00Z")),
            new PredictionResponse("AAPL", Market.US, 5, java.math.BigDecimal.valueOf(0.73), java.math.BigDecimal.valueOf(0.04), java.math.BigDecimal.valueOf(0.02), "MEDIUM", "local-logistic-v1", java.time.Instant.parse("2026-08-08T00:00:00Z")),
            List.of(new RetrievedDocument("news-1", "AAPL", Market.US, DocumentType.NEWS, "Apple demand", "Reuters", java.time.Instant.parse("2026-08-07T00:00:00Z"), "Demand remains solid.", 0.8d))
        );

        ChatToolPlan plan = ChatToolRouter.plan(context, "請分析技術面、未來上漲機率與最新新聞風險");

        assertThat(plan.intent()).contains("TECHNICAL", "PREDICTION", "NEWS");
        assertThat(plan.answerStatus()).isEqualTo("PARTIAL");
        assertThat(plan.tools()).extracting(AiToolCall::name)
            .containsExactly("market_quote", "technical_indicators", "prediction_model", "news_documents");
        assertThat(plan.tools().get(3).citationIds()).containsExactly("news-1");
        assertThat(plan.promptBlock()).contains("citationIds=news-1");
    }

    @Test
    void marksFinancialQuestionInsufficientWhenNoFinancialEvidenceExists() {
        RagContext context = new RagContext(
            new StockRecord("2330.TW", "台積電", Market.TW, "TWD", java.math.BigDecimal.valueOf(1000), java.math.BigDecimal.ZERO, List.of(java.math.BigDecimal.valueOf(990), java.math.BigDecimal.valueOf(1000)), "finmind"),
            new TechnicalSummaryResponse("2330.TW", Market.TW, java.math.BigDecimal.valueOf(995), java.math.BigDecimal.valueOf(980), java.math.BigDecimal.valueOf(950), java.math.BigDecimal.valueOf(60), "BULLISH", java.math.BigDecimal.valueOf(12), java.time.Instant.now()),
            new PredictionResponse("2330.TW", Market.TW, 5, java.math.BigDecimal.valueOf(0.6), java.math.BigDecimal.valueOf(0.01), java.math.BigDecimal.valueOf(0.02), "MEDIUM", "local-logistic-v1", java.time.Instant.now()),
            List.of()
        );

        ChatToolPlan plan = ChatToolRouter.plan(context, "最新財報營收與毛利率如何？");

        assertThat(plan.intent()).isEqualTo("FUNDAMENTALS");
        assertThat(plan.answerStatus()).isEqualTo("PARTIAL");
        assertThat(plan.tools()).extracting(AiToolCall::status).containsExactly("READY", "INSUFFICIENT_DATA");
    }

    @Test
    void rejectsClearlyNonStockQuestion() {
        RagContext context = new RagContext(
            new StockRecord("AAPL", "Apple", Market.US, "USD", java.math.BigDecimal.valueOf(200), java.math.BigDecimal.ZERO, List.of(java.math.BigDecimal.valueOf(200)), "mock"),
            new TechnicalSummaryResponse("AAPL", Market.US, java.math.BigDecimal.valueOf(200), java.math.BigDecimal.valueOf(200), java.math.BigDecimal.valueOf(200), java.math.BigDecimal.valueOf(50), "BULLISH", java.math.BigDecimal.ONE, java.time.Instant.now()),
            new PredictionResponse("AAPL", Market.US, 5, java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, "LOW", "local-logistic-v1", java.time.Instant.now()),
            List.of()
        );

        ChatToolPlan plan = ChatToolRouter.plan(context, "幫我寫程式 debug 這段錯誤");

        assertThat(plan.isOutOfScope()).isTrue();
        assertThat(plan.answerStatus()).isEqualTo("OUT_OF_SCOPE");
    }
}
