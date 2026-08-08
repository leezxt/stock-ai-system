package com.example.stockai.stock;

/**
 * Deterministic, provider-independent conditions that can be evaluated for a
 * user's own watchlist. Thresholds are expressed in quote currency, percent
 * points, risk rank (LOW=1, MEDIUM=2, HIGH=3), or ignored for data quality.
 */
enum WatchlistAlertCondition {
    PRICE_ABOVE,
    PRICE_BELOW,
    CHANGE_PCT_ABOVE,
    CHANGE_PCT_BELOW,
    RISK_AT_LEAST,
    DATA_QUALITY_NOT_OK
}
