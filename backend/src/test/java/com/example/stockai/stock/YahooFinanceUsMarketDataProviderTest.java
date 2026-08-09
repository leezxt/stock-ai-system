package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class YahooFinanceUsMarketDataProviderTest {
    @Test
    void parsesChartResponseIntoStockRecord() {
        Map<String, Object> json = Map.of(
            "chart", Map.of(
                "result", List.of(Map.of(
                    "meta", Map.of(
                        "symbol", "AVGO",
                        "longName", "Broadcom Inc.",
                        "currency", "USD",
                        "regularMarketPrice", 401.145,
                        "previousClose", 392.16,
                        "regularMarketTime", 1783686600L
                    ),
                    "indicators", Map.of(
                        "adjclose", List.of(Map.of(
                            "adjclose", List.of(391.54, 388.69, 401.145)
                        ))
                    )
                ))
            )
        );

        StockRecord record = YahooFinanceUsMarketDataProvider.parseChartResponse(
            "AVGO",
            json,
            new StockRecord("AVGO", "Fallback Broadcom", Market.US, "USD", null, null, List.of(), "mock")
        );

        assertThat(record.symbol()).isEqualTo("AVGO");
        assertThat(record.name()).isEqualTo("Broadcom Inc.");
        assertThat(record.source()).isEqualTo("yahoo-finance");
        assertThat(record.adjustmentStatus()).isEqualTo("ADJUSTED_CLOSE");
        assertThat(record.lastPrice()).isEqualByComparingTo("401.145");
        assertThat(record.changePercent()).isEqualByComparingTo("2.29");
        assertThat(record.prices()).containsExactly(
            new BigDecimal("391.54"),
            new BigDecimal("388.69"),
            new BigDecimal("401.145")
        );
        assertThat(record.observedAt()).isEqualTo(Instant.ofEpochSecond(1783686600L));
    }

    @Test
    void parsesYahooCorporateActionsWithoutInferringFromPriceGaps() {
        Map<String, Object> json = Map.of(
            "chart", Map.of(
                "result", List.of(Map.of(
                    "meta", Map.of("symbol", "AAPL", "regularMarketPrice", 200.0, "previousClose", 199.0),
                    "events", Map.of(
                        "dividends", Map.of("1780000000", Map.of("amount", 0.25, "date", 1780000000L)),
                        "splits", Map.of("1770000000", Map.of("splitRatio", "2:1", "date", 1770000000L))
                    ),
                    "indicators", Map.of(
                        "adjclose", List.of(Map.of("adjclose", List.of(198.0, 199.0)))
                    )
                ))
            )
        );

        StockRecord record = YahooFinanceUsMarketDataProvider.parseChartResponse(
            "AAPL", json, new StockRecord("AAPL", "Fallback Apple", Market.US, "USD", null, null, List.of(), "mock")
        );

        assertThat(record.corporateActions()).extracting(CorporateAction::type)
            .containsExactly("SPLIT", "DIVIDEND");
        assertThat(record.corporateActions()).extracting(CorporateAction::description)
            .containsExactly("分割 2:1", "股利 0.25");
        assertThat(record.prices()).containsExactly(new BigDecimal("198.0"), new BigDecimal("199.0"));
    }

    @Test
    void extractsUsEquitySymbolsFromSearchPayload() {
        Map<String, Object> json = Map.of(
            "quotes", List.of(
                Map.of("symbol", "AVGO", "quoteType", "EQUITY"),
                Map.of("symbol", "AVGO34.SA", "quoteType", "EQUITY"),
                Map.of("symbol", "AVGW", "quoteType", "ETF"),
                Map.of("symbol", "AVGO", "quoteType", "EQUITY")
            )
        );

        List<String> symbols = YahooFinanceUsMarketDataProvider.parseSearchSymbols(json, "AVGO");

        assertThat(symbols).containsExactly("AVGO");
    }
}
