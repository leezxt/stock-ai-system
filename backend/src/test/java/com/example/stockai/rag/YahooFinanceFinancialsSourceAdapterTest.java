package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class YahooFinanceFinancialsSourceAdapterTest {
    @Test
    void parsesQuoteSummaryFinancialModulesIntoImportRequests() {
        Map<String, Object> root = Map.of(
            "quoteSummary", Map.of(
                "result", List.of(
                    Map.of(
                        "incomeStatementHistory", Map.of(
                            "incomeStatementHistory", List.of(
                                Map.of(
                                    "endDate", Map.of("raw", 1782777600),
                                    "totalRevenue", Map.of("raw", 1000000),
                                    "netIncome", Map.of("raw", 250000)
                                )
                            )
                        ),
                        "balanceSheetHistory", Map.of(
                            "balanceSheetStatements", List.of(
                                Map.of("totalAssets", Map.of("raw", 5000000))
                            )
                        ),
                        "cashflowStatementHistory", Map.of(
                            "cashflowStatements", List.of(
                                Map.of("totalCashFromOperatingActivities", Map.of("raw", 300000))
                            )
                        )
                    )
                )
            )
        );

        List<DocumentImportRequest> requests = YahooFinanceFinancialsSourceAdapter.parseQuoteSummary(root, "AVGO", Market.US, 5);

        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).docType()).isEqualTo(DocumentType.FINANCIAL_REPORT);
        assertThat(requests.get(0).source()).isEqualTo("yahoo-finance-financials");
        assertThat(requests.get(0).content()).contains("totalRevenue: 1000000");
        assertThat(requests.get(1).content()).contains("totalAssets: 5000000");
        assertThat(requests.get(2).content()).contains("totalCashFromOperatingActivities: 300000");
    }
}
