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
    Instant updatedAt
) {}
