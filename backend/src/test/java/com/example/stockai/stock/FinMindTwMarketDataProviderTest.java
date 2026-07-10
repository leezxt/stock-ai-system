package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class FinMindTwMarketDataProviderTest {
    @Test
    void parsesTaiwanStockPriceRowsIntoStockRecord() {
        List<Map<String, Object>> rows = List.of(
            Map.of("date", "2026-07-07", "close", "1030", "spread", "10"),
            Map.of("date", "2026-07-08", "close", "1045", "spread", "15"),
            Map.of("date", "2026-07-09", "close", "1050", "spread", "5")
        );

        StockRecord record = FinMindTwMarketDataProvider.parsePriceRows("2330", "台積電", rows, null);

        assertThat(record.symbol()).isEqualTo("2330.TW");
        assertThat(record.name()).isEqualTo("台積電");
        assertThat(record.source()).isEqualTo("finmind");
        assertThat(record.lastPrice()).isEqualByComparingTo("1050");
        assertThat(record.changePercent()).isEqualByComparingTo("0.48");
        assertThat(record.prices()).containsExactly(
            new BigDecimal("1030"),
            new BigDecimal("1045"),
            new BigDecimal("1050")
        );
    }

    @Test
    void usesPreviousCloseWhenSpreadIsMissing() {
        List<Map<String, Object>> rows = List.of(
            Map.of("date", "2026-07-08", "close", "100"),
            Map.of("date", "2026-07-09", "close", "105")
        );

        StockRecord record = FinMindTwMarketDataProvider.parsePriceRows(
            "1101",
            "台泥",
            rows,
            new StockRecord("1101.TW", "台泥", Market.TW, "TWD", null, null, List.of(), "mock")
        );

        assertThat(record.changePercent()).isEqualByComparingTo("5.00");
    }
}
