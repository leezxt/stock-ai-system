package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class AlphaVantageMarketDataProviderTest {
    @Test
    void parsesDailyResponseIntoStockRecord() {
        Map<String, Object> json = Map.of(
            "Meta Data", Map.of("2. Symbol", "IBM"),
            "Time Series (Daily)", Map.of(
                "2026-07-02", Map.of("4. close", "289.5200"),
                "2026-07-01", Map.of("4. close", "286.2500"),
                "2026-06-30", Map.of("4. close", "281.2100")
            )
        );
        StockRecord record = AlphaVantageMarketDataProvider.parseDailyResponse(
            "IBM",
            json,
            new StockRecord("IBM", "IBM Corp.", Market.US, "USD", null, null, java.util.List.of(), "mock")
        );

        assertThat(record.symbol()).isEqualTo("IBM");
        assertThat(record.name()).isEqualTo("IBM Corp.");
        assertThat(record.source()).isEqualTo("alpha-vantage");
        assertThat(record.lastPrice()).isEqualByComparingTo("289.5200");
        assertThat(record.changePercent()).isEqualByComparingTo("1.14");
        assertThat(record.prices()).containsExactly(
            new java.math.BigDecimal("281.2100"),
            new java.math.BigDecimal("286.2500"),
            new java.math.BigDecimal("289.5200")
        );
    }
}
