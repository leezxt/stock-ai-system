package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class TwseRealtimeMarketDataProviderTest {
    @Test
    void parsesLatestTradeResponse() {
        Map<String, Object> root = Map.of(
            "msgArray",
            List.of(Map.of(
                "c", "2610",
                "n", "華航",
                "z", "21.40",
                "y", "22.05",
                "d", "20260710",
                "t", "13:30:00"
            ))
        );

        StockRecord stock = TwseRealtimeMarketDataProvider.parseResponse(root, "2610", null).orElseThrow();

        assertThat(stock.symbol()).isEqualTo("2610.TW");
        assertThat(stock.name()).isEqualTo("華航");
        assertThat(stock.market()).isEqualTo(Market.TW);
        assertThat(stock.lastPrice()).isEqualByComparingTo("21.40");
        assertThat(stock.changePercent()).isEqualByComparingTo("-2.95");
        assertThat(stock.source()).isEqualTo("twse-realtime");
        assertThat(stock.priceHistory()).containsExactly(
            new PriceBar(LocalDate.of(2026, 7, 10), new BigDecimal("21.40"), "twse-realtime")
        );
        assertThat(stock.observedAt()).isEqualTo(Instant.parse("2026-07-10T05:30:00Z"));
    }

    @Test
    void fallsBackToPreviousTradeWhenLatestTradeIsBlank() {
        Map<String, Object> root = Map.of(
            "msgArray",
            List.of(Map.of(
                "c", "2610",
                "n", "華航",
                "z", "-",
                "pz", "21.35",
                "y", "22.05"
            ))
        );

        StockRecord stock = TwseRealtimeMarketDataProvider.parseResponse(
            root,
            "2610",
            new StockRecord("2610.TW", "Fallback", Market.TW, "TWD", new BigDecimal("22.05"), BigDecimal.ZERO, List.of(new BigDecimal("22.05")), "mock")
        ).orElseThrow();

        assertThat(stock.lastPrice()).isEqualByComparingTo("21.35");
        assertThat(stock.prices()).containsExactly(new BigDecimal("21.35"));
    }

    @Test
    void keepsRealtimeQuoteSeparateFromHistoricalBars() {
        StockRecord fallback = new StockRecord(
            "2610.TW",
            "華航",
            Market.TW,
            "TWD",
            new BigDecimal("22.05"),
            BigDecimal.ZERO,
            List.of(new BigDecimal("22.05")),
            "finmind",
            List.of(new PriceBar(LocalDate.of(2026, 7, 9), new BigDecimal("22.05"), "finmind"))
        );

        StockRecord stock = TwseRealtimeMarketDataProvider.parseResponse(
            Map.of("msgArray", List.of(Map.of(
                "c", "2610",
                "n", "華航",
                "z", "21.40",
                "y", "22.05",
                "d", "20260710",
                "t", "13:30:00"
            ))),
            "2610",
            fallback
        ).orElseThrow();

        assertThat(stock.source()).isEqualTo("finmind+twse-realtime");
        assertThat(stock.priceHistory()).extracting(PriceBar::source)
            .containsExactly("finmind");
        assertThat(stock.priceHistory()).extracting(PriceBar::date)
            .containsExactly(LocalDate.of(2026, 7, 9));
        assertThat(stock.lastPrice()).isEqualByComparingTo("21.40");
    }
}
