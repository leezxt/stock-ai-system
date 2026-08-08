package com.example.stockai.stock;

import java.time.Instant;
import java.util.List;

record WatchlistAlertCenterResponse(
    List<WatchlistAlertRule> rules,
    List<WatchlistAlertEvaluation> evaluations,
    int triggeredCount,
    int newTriggerCount,
    List<WatchlistAlertEvent> recentEvents,
    Instant evaluatedAt,
    String source
) {
    WatchlistAlertCenterResponse {
        rules = rules == null ? List.of() : List.copyOf(rules);
        evaluations = evaluations == null ? List.of() : List.copyOf(evaluations);
        recentEvents = recentEvents == null ? List.of() : List.copyOf(recentEvents);
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
        source = source == null || source.isBlank() ? "watchlist-alerts-v1" : source.trim();
        if (triggeredCount < 0) {
            throw new IllegalArgumentException("triggeredCount must not be negative");
        }
        if (newTriggerCount < 0) {
            throw new IllegalArgumentException("newTriggerCount must not be negative");
        }
    }
}
