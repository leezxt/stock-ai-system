package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class FinMindTwNewsSourceAdapterTest {
    @Test
    void parsesNewsRowsIntoImportRequests() {
        List<DocumentImportRequest> requests = FinMindTwNewsSourceAdapter.parseNewsResponse(
            Map.of(
                "status", 200,
                "data", List.of(
                    Map.of(
                        "title", "台積電法說前市場觀望",
                        "source", "工商時報",
                        "description", "投資人聚焦 AI 訂單與展望。",
                        "link", "https://example.com/tsmc-news",
                        "date", "2026-07-09"
                    )
                )
            ),
            "2330",
            Market.TW,
            5
        );

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).symbol()).isEqualTo("2330.TW");
        assertThat(requests.get(0).docType()).isEqualTo(DocumentType.NEWS);
        assertThat(requests.get(0).title()).isEqualTo("台積電法說前市場觀望");
        assertThat(requests.get(0).source()).isEqualTo("工商時報");
        assertThat(requests.get(0).publishedAt()).isEqualTo(Instant.parse("2026-07-09T00:00:00Z"));
        assertThat(requests.get(0).content()).contains("https://example.com/tsmc-news");
    }
}
