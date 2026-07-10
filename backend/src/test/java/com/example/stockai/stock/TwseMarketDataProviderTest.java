package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class TwseMarketDataProviderTest {
    @Test
    void parsesStockDayAllRow() {
        StockRecord record = TwseMarketDataProvider.parseRow(
            Map.of(
                "Code", "2330",
                "Name", "台積電",
                "ClosingPrice", "1,035.00",
                "Change", "+15.00"
            ),
            new StockRecord("2330.TW", "台積電", Market.TW, "TWD", new BigDecimal("1020"), BigDecimal.ZERO, List.of(new BigDecimal("1000"), new BigDecimal("1020")), "mock")
        );

        assertThat(record.symbol()).isEqualTo("2330.TW");
        assertThat(record.name()).isEqualTo("台積電");
        assertThat(record.currency()).isEqualTo("TWD");
        assertThat(record.source()).isEqualTo("twse-openapi");
        assertThat(record.lastPrice()).isEqualByComparingTo("1035.00");
        assertThat(record.changePercent()).isEqualByComparingTo("1.47");
        assertThat(record.prices()).containsExactly(new BigDecimal("1020"), new BigDecimal("1035.00"));
    }
}
