package com.example.stockai.stock;

import java.util.List;

record WatchlistAlertNotificationResponse(
    List<WatchlistAlertNotification> notifications,
    int unreadCount,
    String source
) {
    WatchlistAlertNotificationResponse {
        notifications = notifications == null ? List.of() : List.copyOf(notifications);
        if (unreadCount < 0) {
            throw new IllegalArgumentException("unreadCount must not be negative");
        }
        source = source == null || source.isBlank() ? "watchlist-alert-notifications-v1" : source.trim();
    }
}
