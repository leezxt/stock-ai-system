package com.example.stockai.stock;

import java.time.Instant;

/**
 * Per-account notification preferences. External channels are persisted as
 * intent only; the current delivery capability remains LOCAL_ONLY.
 */
record WatchlistAlertNotificationPreferences(
    boolean localEnabled,
    boolean emailEnabled,
    boolean pushEnabled,
    Instant updatedAt
) {
    WatchlistAlertNotificationPreferences {
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    static WatchlistAlertNotificationPreferences defaults() {
        return new WatchlistAlertNotificationPreferences(true, false, false, Instant.now());
    }
}
