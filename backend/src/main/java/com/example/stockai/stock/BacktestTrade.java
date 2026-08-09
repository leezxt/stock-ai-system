package com.example.stockai.stock;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One completed close-to-close trade produced by the historical replay.
 * Prices are close-only because the current provider contract does not expose
 * historical intraday/open prices.
 */
record BacktestTrade(
    LocalDate entryDate,
    LocalDate exitDate,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal grossReturn,
    BigDecimal costRate,
    BigDecimal netReturn,
    boolean win,
    String exitReason
) {}
