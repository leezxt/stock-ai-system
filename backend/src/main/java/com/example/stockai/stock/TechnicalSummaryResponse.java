package com.example.stockai.stock;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.stockai.market.Market;

public record TechnicalSummaryResponse(
    String symbol,
    Market market,
    BigDecimal ma5,
    BigDecimal ma20,
    BigDecimal ma60,
    BigDecimal rsi14,
    String macdSignal,
    BigDecimal atr14,
    Instant updatedAt,
    MarketDataQuality dataQuality
) {
    public TechnicalSummaryResponse(
        String symbol,
        Market market,
        BigDecimal ma5,
        BigDecimal ma20,
        BigDecimal ma60,
        BigDecimal rsi14,
        String macdSignal,
        BigDecimal atr14,
        Instant updatedAt
    ) {
        this(symbol, market, ma5, ma20, ma60, rsi14, macdSignal, atr14, updatedAt, MarketDataQuality.unknown());
    }

    public TechnicalSummaryResponse {
        dataQuality = dataQuality == null ? MarketDataQuality.unknown() : dataQuality;
    }
}
