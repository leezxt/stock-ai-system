package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class LocalLogisticPredictionModelTest {
    private final LocalLogisticPredictionModel model = new LocalLogisticPredictionModel();

    @Test
    void trainsOnlyFromAvailableHistoryAndReturnsBoundedPrediction() {
        List<PriceBar> bars = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            bars.add(new PriceBar(LocalDate.of(2026, 7, 1).plusDays(index), BigDecimal.valueOf(100 + index * 1.5 + (index % 3)), "test"));
        }

        var result = model.predict(bars, 5).orElseThrow();

        assertThat(result.modelVersion()).isEqualTo("local-logistic-v1");
        assertThat(result.upProbability()).isBetween(new BigDecimal("0.05"), new BigDecimal("0.95"));
        assertThat(result.volatility()).isNotNegative();
    }

    @Test
    void refusesToPretendThereIsAStableModelWithTooLittleHistory() {
        List<PriceBar> bars = List.of(
            new PriceBar(LocalDate.of(2026, 8, 1), new BigDecimal("100"), "test"),
            new PriceBar(LocalDate.of(2026, 8, 2), new BigDecimal("101"), "test"),
            new PriceBar(LocalDate.of(2026, 8, 3), new BigDecimal("102"), "test")
        );

        assertThat(model.predict(bars, 5)).isEmpty();
    }
}
