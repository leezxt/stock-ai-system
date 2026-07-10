package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class FmpTranscriptSourceAdapterTest {
    @Test
    void parsesTranscriptListIntoImportRequest() {
        DocumentImportRequest request = FmpTranscriptSourceAdapter.parseTranscriptResponse(
            List.of(
                Map.of(
                    "symbol", "AAPL",
                    "date", "2026-07-10",
                    "content", "Operator: Welcome.\nCEO: Demand remained strong."
                )
            ),
            "AAPL",
            Market.US,
            "2026Q2"
        ).orElseThrow();

        assertThat(request.docType()).isEqualTo(DocumentType.EARNINGS_TRANSCRIPT);
        assertThat(request.title()).isEqualTo("AAPL earnings call 2026Q2");
        assertThat(request.source()).isEqualTo("FMP earnings transcript");
        assertThat(request.publishedAt()).isEqualTo(Instant.parse("2026-07-10T00:00:00Z"));
        assertThat(request.content()).contains("Demand remained strong");
    }
}
