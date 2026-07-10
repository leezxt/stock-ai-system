package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class FinMindTwFinancialReportSourceAdapterTest {
    @Test
    void parsesMonthRevenueRowsIntoFinancialReportDocuments() {
        List<DocumentImportRequest> requests = FinMindTwFinancialReportSourceAdapter.parseMonthRevenueResponse(
            Map.of(
                "status", 200,
                "data", List.of(
                    Map.of(
                        "date", "2026-07-01",
                        "stock_id", "2330",
                        "revenue_year", "2026",
                        "revenue_month", "06",
                        "revenue", "100000000",
                        "last_month_revenue_change", "2.1",
                        "last_year_revenue_change", "28.4"
                    )
                )
            ),
            "2330",
            Market.TW,
            3
        );

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).symbol()).isEqualTo("2330.TW");
        assertThat(requests.get(0).docType()).isEqualTo(DocumentType.FINANCIAL_REPORT);
        assertThat(requests.get(0).title()).isEqualTo("2330 月營收 2026-06");
        assertThat(requests.get(0).source()).isEqualTo("finmind-month-revenue");
        assertThat(requests.get(0).publishedAt()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(requests.get(0).content()).contains("revenue: 100000000");
    }

    @Test
    void groupsStatementRowsIntoSingleDocumentPerDate() {
        List<DocumentImportRequest> requests = FinMindTwFinancialReportSourceAdapter.parseStatementResponse(
            Map.of(
                "status", 200,
                "data", List.of(
                    Map.of("date", "2026-03-31", "type", "營業收入", "value", "123456"),
                    Map.of("date", "2026-03-31", "type", "營業毛利", "value", "65432"),
                    Map.of("date", "2025-12-31", "type", "營業收入", "value", "111111")
                )
            ),
            "2330",
            Market.TW,
            1,
            "綜合損益表",
            "finmind-financial-statements"
        );

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).title()).isEqualTo("2330 綜合損益表 2026-03-31");
        assertThat(requests.get(0).source()).isEqualTo("finmind-financial-statements");
        assertThat(requests.get(0).content()).contains("營業收入: 123456");
        assertThat(requests.get(0).content()).contains("營業毛利: 65432");
    }
}
