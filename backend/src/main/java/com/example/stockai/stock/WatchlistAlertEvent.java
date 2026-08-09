package com.example.stockai.stock;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.stockai.market.Market;

/**
 * A local, auditable alert state transition. It records evaluation history
 * only; no email, push notification, or broker action is implied.
 */
record WatchlistAlertEvent(
    String id,
    String ruleId,
    Market market,
    String symbol,
    WatchlistAlertCondition condition,
    String status,
    BigDecimal currentValue,
    String message,
    String source,
    String dataStatus,
    Instant observedAt,
    Instant createdAt
) {
    WatchlistAlertEvent {
        id = id == null || id.isBlank() ? "unknown" : id.trim();
        ruleId = ruleId == null || ruleId.isBlank() ? "unknown" : ruleId.trim();
        status = status == null || status.isBlank() ? "UNKNOWN" : status.trim().toUpperCase();
        message = message == null ? "" : message.trim();
        source = source == null || source.isBlank() ? "unknown" : source.trim();
        dataStatus = dataStatus == null || dataStatus.isBlank() ? "UNKNOWN" : dataStatus.trim().toUpperCase();
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
