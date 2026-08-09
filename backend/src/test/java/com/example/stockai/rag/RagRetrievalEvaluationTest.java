package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

/**
 * Small deterministic retrieval set. These cases protect the ranking seam while
 * the production embedding implementation is still replaceable.
 */
class RagRetrievalEvaluationTest {
    @Test
    void hybridRetrieverRanksExpectedEvidenceForRepresentativeQuestions() {
        HashEmbeddingModel embeddingModel = new HashEmbeddingModel();
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert(new DocumentEmbeddingService(embeddingModel).embed(List.of(
            chunk("news-services", "AAPL", "Services margin", "Services margin improved after stronger subscription growth."),
            chunk("filing-buyback", "AAPL", "Share repurchase", "The board approved a new share repurchase plan."),
            chunk("tw-capex", "2330.TW", "Advanced node capex", "Capital expenditure guidance increased for advanced nodes.")
        )));
        DocumentRetriever retriever = new DocumentRetriever(embeddingModel, store, 0.15d);

        List<EvaluationCase> cases = List.of(
            new EvaluationCase("services margin growth", Market.US, "AAPL", "news-services"),
            new EvaluationCase("share repurchase plan", Market.US, "AAPL", "filing-buyback"),
            new EvaluationCase("advanced node capital expenditure", Market.TW, "2330.TW", "tw-capex")
        );

        for (EvaluationCase evaluationCase : cases) {
            List<RetrievedDocument> hits = retriever.retrieve(new DocumentRetrieveRequest(
                evaluationCase.query(),
                1,
                evaluationCase.symbol(),
                evaluationCase.market(),
                null,
                null,
                null
            ));

            assertThat(hits)
                .as("query=%s", evaluationCase.query())
                .isNotEmpty()
                .first()
                .extracting(RetrievedDocument::chunkId)
                .isEqualTo(evaluationCase.expectedChunkId());
        }
    }

    @Test
    void lowSignalQuestionProducesNoEvidenceInsteadOfArbitraryTopK() {
        HashEmbeddingModel embeddingModel = new HashEmbeddingModel();
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert(new DocumentEmbeddingService(embeddingModel).embed(List.of(
            chunk("news-services", "AAPL", "Services margin", "Services margin improved after stronger subscription growth.")
        )));

        List<RetrievedDocument> hits = new DocumentRetriever(embeddingModel, store, 0.15d).retrieve(
            new DocumentRetrieveRequest("unrelated weather event", 3, "AAPL", Market.US, null, null, null)
        );

        assertThat(hits).isEmpty();
    }

    private static DocumentChunk chunk(String id, String symbol, String title, String content) {
        return new DocumentChunk(
            id,
            symbol,
            symbol.endsWith(".TW") ? Market.TW : Market.US,
            DocumentType.NEWS,
            title,
            "Evaluation source",
            Instant.parse("2026-07-06T00:00:00Z"),
            content
        );
    }

    private record EvaluationCase(String query, Market market, String symbol, String expectedChunkId) {}
}
