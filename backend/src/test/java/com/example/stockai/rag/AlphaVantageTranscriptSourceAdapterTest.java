package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class AlphaVantageTranscriptSourceAdapterTest {
    @Test
    void parsesTranscriptIntoImportRequest() {
        DocumentImportRequest request = AlphaVantageTranscriptSourceAdapter.parseTranscriptResponse(
            Map.of(
                "transcript", "Operator: Welcome.\nCEO: Demand remained strong."
            ),
            "AAPL",
            Market.US,
            "2026Q2"
        ).orElseThrow();

        assertThat(request.docType()).isEqualTo(DocumentType.EARNINGS_TRANSCRIPT);
        assertThat(request.title()).isEqualTo("AAPL earnings call 2026Q2");
        assertThat(request.content()).contains("Demand remained strong");
    }
}
