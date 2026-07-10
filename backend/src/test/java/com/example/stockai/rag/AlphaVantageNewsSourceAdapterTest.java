package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class AlphaVantageNewsSourceAdapterTest {
    @Test
    void parsesNewsFeedIntoImportRequests() {
        List<DocumentImportRequest> requests = AlphaVantageNewsSourceAdapter.parseNewsResponse(
            Map.of(
                "feed", List.of(
                    Map.of(
                        "title", "Apple raises guidance",
                        "source", "Reuters",
                        "summary", "Apple cited strong iPhone demand.",
                        "url", "https://example.com/apple",
                        "time_published", "20260706T120000"
                    )
                )
            ),
            "AAPL",
            Market.US,
            5
        );

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).docType()).isEqualTo(DocumentType.NEWS);
        assertThat(requests.get(0).title()).isEqualTo("Apple raises guidance");
        assertThat(requests.get(0).source()).isEqualTo("Reuters");
        assertThat(requests.get(0).publishedAt()).isEqualTo(Instant.parse("2026-07-06T12:00:00Z"));
        assertThat(requests.get(0).content()).contains("https://example.com/apple");
    }
}
