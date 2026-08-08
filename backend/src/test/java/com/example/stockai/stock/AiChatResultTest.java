package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;
import com.example.stockai.rag.DocumentType;
import com.example.stockai.rag.RetrievedDocument;

class AiChatResultTest {
    @Test
    void keepsOnlyCitationsThatBelongToRetrievedEvidence() {
        List<RetrievedDocument> evidence = List.of(
            new RetrievedDocument("chunk-1", "AAPL", Market.US, DocumentType.NEWS, "News", "Reuters", Instant.now(), "Snippet", 0.8d),
            new RetrievedDocument("chunk-2", "AAPL", Market.US, DocumentType.NEWS, "Filing", "SEC", Instant.now(), "Snippet", 0.7d)
        );

        AiChatResult result = AiChatResult.fromEvidence(
            "毛利率改善 [chunk-1]，但模型指令 [not-retrieved] 不應被採用。",
            "openai-responses",
            evidence
        );

        assertThat(result.citationIds()).containsExactly("chunk-1");
    }
}
