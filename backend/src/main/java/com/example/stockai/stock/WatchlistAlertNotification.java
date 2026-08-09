package com.example.stockai.stock;

import java.time.Instant;

import com.example.stockai.market.Market;

/**
 * Account-scoped local notification generated from a newly triggered alert.
 * The channel is intentionally LOCAL_ONLY until an explicitly authorized
 * delivery provider is added.
 */
record WatchlistAlertNotification(
    String id,
    String eventId,
    String ruleId,
    Market market,
    String symbol,
    WatchlistAlertCondition condition,
    String title,
    String message,
    String channel,
    String state,
    String source,
    String dataStatus,
    Instant createdAt,
    Instant readAt
) {
    WatchlistAlertNotification {
        id = id == null || id.isBlank() ? "unknown" : id.trim();
        eventId = eventId == null || eventId.isBlank() ? "unknown" : eventId.trim();
        ruleId = ruleId == null || ruleId.isBlank() ? "unknown" : ruleId.trim();
        title = title == null ? "警示通知" : title.trim();
        message = message == null ? "" : message.trim();
        channel = channel == null || channel.isBlank() ? "LOCAL_ONLY" : channel.trim().toUpperCase();
        state = state == null || state.isBlank() ? "UNREAD" : state.trim().toUpperCase();
        source = source == null || source.isBlank() ? "unknown" : source.trim();
        dataStatus = dataStatus == null || dataStatus.isBlank() ? "UNKNOWN" : dataStatus.trim().toUpperCase();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        if ("READ".equals(state) && readAt == null) {
            readAt = createdAt;
        }
        if (!"READ".equals(state)) {
            readAt = null;
        }
    }

    WatchlistAlertNotification withRead(boolean read) {
        return new WatchlistAlertNotification(
            id, eventId, ruleId, market, symbol, condition, title, message, channel,
            read ? "READ" : "UNREAD", source, dataStatus, createdAt, read ? Instant.now() : null
        );
    }
}
