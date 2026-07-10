package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;
import com.example.stockai.rag.DocumentChunk;
import com.example.stockai.rag.DocumentEmbeddingService;
import com.example.stockai.rag.DocumentRetriever;
import com.example.stockai.rag.DocumentType;
import com.example.stockai.rag.HashEmbeddingModel;
import com.example.stockai.rag.InMemoryVectorStore;

class RagContextServiceTest {
    @Test
    void analysisContextCombinesTechnicalPredictionAndEvidence() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        HashEmbeddingModel embeddingModel = new HashEmbeddingModel();
        vectorStore.upsert(new DocumentEmbeddingService(embeddingModel).embed(List.of(
            new DocumentChunk(
                "chunk-1",
                "AAPL",
                Market.US,
                DocumentType.NEWS,
                "Apple guidance raised",
                "Reuters",
                Instant.parse("2026-07-05T00:00:00Z"),
                "Apple raised guidance after strong iPhone demand and services growth."
            )
        )));
        StockService stockService = new StockService(new MockMarketDataProvider());
        RagContextService service = new RagContextService(stockService, new DocumentRetriever(embeddingModel, vectorStore));

        StockRecord stock = stockService.get(Market.US, "AAPL");
        RagContext context = service.buildAnalysisContext(stock, 7);

        assertThat(context.technicalSummary().symbol()).isEqualTo("AAPL");
        assertThat(context.prediction().horizonDays()).isEqualTo(7);
        assertThat(context.evidence()).hasSize(1);
        assertThat(context.analysisPrompt()).contains("技術面");
        assertThat(context.analysisPrompt()).contains("Apple guidance raised");
    }

    @Test
    void chatContextFallsBackWhenNoEvidenceExists() {
        StockService stockService = new StockService(new MockMarketDataProvider());
        RagContextService service = new RagContextService(
            stockService,
            new DocumentRetriever(new HashEmbeddingModel(), new InMemoryVectorStore())
        );

        RagContext context = service.buildChatContext(stockService.get(Market.TW, "2330"), 5, "現在適合追嗎");

        assertThat(context.evidence()).isEmpty();
        assertThat(context.evidenceSummary()).contains("無文件證據");
        assertThat(context.chatPrompt("現在適合追嗎")).contains("使用者問題");
    }
}
