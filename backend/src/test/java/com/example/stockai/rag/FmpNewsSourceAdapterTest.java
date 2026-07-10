package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class FmpNewsSourceAdapterTest {
    @Test
    void parsesStockNewsListIntoImportRequests() {
        List<DocumentImportRequest> requests = FmpNewsSourceAdapter.parseNewsResponse(
            List.of(
                Map.of(
                    "symbol", "AAPL",
                    "publishedDate", "2026-07-10 12:00:00",
                    "title", "Apple AI supplier outlook improves",
                    "site", "Reuters",
                    "text", "Apple suppliers rose after demand expectations improved.",
                    "url", "https://example.com/aapl-news"
                )
            ),
            "AAPL",
            Market.US,
            5
        );

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).docType()).isEqualTo(DocumentType.NEWS);
        assertThat(requests.get(0).title()).isEqualTo("Apple AI supplier outlook improves");
        assertThat(requests.get(0).source()).isEqualTo("Reuters");
        assertThat(requests.get(0).publishedAt()).isEqualTo(Instant.parse("2026-07-10T12:00:00Z"));
        assertThat(requests.get(0).content()).contains("https://example.com/aapl-news");
    }
}
