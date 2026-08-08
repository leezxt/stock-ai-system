package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class HistoricalBacktestEngineTest {
    private final HistoricalBacktestEngine engine = new HistoricalBacktestEngine();

    @Test
    void replaysCompletedTradesWithoutFixedTradeCount() {
        StockRecord stock = stockWithBars(24, 100, 1.2);
        MockFeatureController.Strategy strategy = new MockFeatureController.Strategy(
            new BigDecimal("70"),
            new BigDecimal("0.60"),
            "LOW",
            3,
            new BigDecimal("0.001"),
            new BigDecimal("0.002"),
            new BigDecimal("0.001"),
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );

        BacktestEvaluation result = engine.evaluate(stock, strategy);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.source()).isEqualTo("historical-price-replay-v1");
        assertThat(result.signalModel()).isEqualTo("technical-momentum-replay-v1");
        assertThat(result.dataPointCount()).isEqualTo(24);
        assertThat(result.completedTradeCount()).isGreaterThan(0);
        assertThat(result.trades()).allSatisfy(trade -> {
            assertThat(trade.entryDate()).isBefore(trade.exitDate());
            assertThat(trade.costRate()).isPositive();
            assertThat(trade.exitReason()).isEqualTo("HORIZON");
        });
    }

    @Test
    void reportsInsufficientDataInsteadOfInventingTrades() {
        StockRecord stock = stockWithBars(8, 100, 1.0);
        MockFeatureController.Strategy strategy = new MockFeatureController.Strategy(
            new BigDecimal("70"), new BigDecimal("0.60"), "MEDIUM", 5, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        BacktestEvaluation result = engine.evaluate(stock, strategy);

        assertThat(result.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.completedTradeCount()).isZero();
        assertThat(result.trades()).isEmpty();
        assertThat(result.note()).contains("至少");
    }

    private static StockRecord stockWithBars(int count, double base, double step) {
        var bars = IntStream.range(0, count)
            .mapToObj(i -> new PriceBar(LocalDate.of(2026, 1, 1).plusDays(i), BigDecimal.valueOf(base + i * step), "test"))
            .toList();
        return new StockRecord("AAPL", "Apple", com.example.stockai.market.Market.US, "USD", bars.get(count - 1).close(), BigDecimal.ZERO, bars.stream().map(PriceBar::close).toList(), "test", bars);
    }
}
