package com.example.stockai.stock;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.stockai.market.Market;

public record PredictionResponse(
    String symbol,
    Market market,
    int horizonDays,
    BigDecimal upProbability,
    BigDecimal expectedReturn,
    BigDecimal volatility,
    String riskLevel,
    String modelVersion,
    Instant generatedAt
) {}
