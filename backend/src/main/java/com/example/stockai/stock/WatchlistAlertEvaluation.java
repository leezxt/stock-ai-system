package com.example.stockai.stock;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.stockai.market.Market;

record WatchlistAlertEvaluation(
    String ruleId,
    Market market,
    String symbol,
    String name,
    WatchlistAlertCondition condition,
    BigDecimal threshold,
    BigDecimal currentValue,
    String currentLabel,
    String status,
    boolean triggered,
    boolean newlyTriggered,
    Instant lastTriggeredAt,
    String message,
    String source,
    String dataStatus,
    Instant observedAt
) {
    WatchlistAlertEvaluation {
        status = status == null || status.isBlank() ? "UNKNOWN" : status.trim().toUpperCase();
        source = source == null || source.isBlank() ? "unknown" : source.trim();
        dataStatus = dataStatus == null || dataStatus.isBlank() ? "UNKNOWN" : dataStatus.trim().toUpperCase();
        message = message == null ? "" : message.trim();
        newlyTriggered = newlyTriggered && triggered;
    }

    WatchlistAlertEvaluation withHistory(boolean nextNewlyTriggered, Instant nextLastTriggeredAt) {
        return new WatchlistAlertEvaluation(
            ruleId, market, symbol, name, condition, threshold, currentValue, currentLabel, status,
            triggered, nextNewlyTriggered, nextLastTriggeredAt, message, source, dataStatus, observedAt
        );
    }
}
