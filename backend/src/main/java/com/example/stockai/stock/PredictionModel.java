package com.example.stockai.stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * A local prediction seam. Implementations must train only on bars before the
 * bar being scored; callers keep the model version visible in the API.
 */
interface PredictionModel {
    Optional<PredictionEstimate> predict(List<PriceBar> bars, int horizonDays);

    record PredictionEstimate(
        BigDecimal upProbability,
        BigDecimal expectedReturn,
        BigDecimal volatility,
        String riskLevel,
        String modelVersion
    ) {}
}
