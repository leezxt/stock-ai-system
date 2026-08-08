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
    Instant generatedAt,
    MarketDataQuality dataQuality
) {
    public PredictionResponse(
        String symbol,
        Market market,
        int horizonDays,
        BigDecimal upProbability,
        BigDecimal expectedReturn,
        BigDecimal volatility,
        String riskLevel,
        String modelVersion,
        Instant generatedAt
    ) {
        this(symbol, market, horizonDays, upProbability, expectedReturn, volatility, riskLevel, modelVersion, generatedAt, MarketDataQuality.unknown());
    }

    public PredictionResponse {
        dataQuality = dataQuality == null ? MarketDataQuality.unknown() : dataQuality;
    }
}
