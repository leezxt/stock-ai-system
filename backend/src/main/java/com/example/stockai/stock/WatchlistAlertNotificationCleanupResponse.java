package com.example.stockai.stock;

record WatchlistAlertNotificationCleanupResponse(
    int deleted,
    boolean localOnly
) {
    WatchlistAlertNotificationCleanupResponse {
        if (deleted < 0) {
            throw new IllegalArgumentException("deleted must not be negative");
        }
    }
}
