package com.example.stockai.stock;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.stockai.market.Market;

record WatchlistAlertRule(
    String id,
    Market market,
    String symbol,
    WatchlistAlertCondition condition,
    BigDecimal threshold,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
    WatchlistAlertRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("alert id is required");
        }
        if (market == null) {
            throw new IllegalArgumentException("alert market is required");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("alert symbol is required");
        }
        if (condition == null) {
            throw new IllegalArgumentException("alert condition is required");
        }
        if (condition != WatchlistAlertCondition.DATA_QUALITY_NOT_OK && threshold == null) {
            throw new IllegalArgumentException("alert threshold is required");
        }
        if (condition == WatchlistAlertCondition.DATA_QUALITY_NOT_OK) {
            threshold = null;
        }
        if (threshold != null && threshold.signum() < 0 && condition == WatchlistAlertCondition.RISK_AT_LEAST) {
            throw new IllegalArgumentException("risk threshold must not be negative");
        }
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    WatchlistAlertRule withEnabled(boolean value) {
        return new WatchlistAlertRule(id, market, symbol, condition, threshold, value, createdAt, Instant.now());
    }
}
