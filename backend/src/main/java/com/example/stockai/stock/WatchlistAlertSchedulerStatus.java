package com.example.stockai.stock;

import java.time.Instant;

/**
 * Operational status for the optional local alert evaluator. It deliberately
 * reports counts and timestamps only; account identifiers and credentials are
 * never returned.
 */
record WatchlistAlertSchedulerStatus(
    boolean enabled,
    long fixedDelayMs,
    String notificationMode,
    Instant lastStartedAt,
    Instant lastCompletedAt,
    int lastEvaluatedUsers,
    int lastEvaluatedRules,
    int lastNewTriggerCount,
    int lastErrorCount,
    String lastError
) {
    WatchlistAlertSchedulerStatus {
        if (fixedDelayMs < 0) {
            throw new IllegalArgumentException("fixedDelayMs must not be negative");
        }
        if (lastEvaluatedUsers < 0 || lastEvaluatedRules < 0 || lastNewTriggerCount < 0 || lastErrorCount < 0) {
            throw new IllegalArgumentException("scheduler counters must not be negative");
        }
        notificationMode = notificationMode == null || notificationMode.isBlank()
            ? "LOCAL_ONLY" : notificationMode.trim().toUpperCase();
        lastError = lastError == null ? "" : lastError.trim();
    }
}
