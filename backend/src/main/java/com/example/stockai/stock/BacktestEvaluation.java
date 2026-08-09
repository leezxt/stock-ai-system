package com.example.stockai.stock;

import java.math.BigDecimal;
import java.util.List;

record BacktestEvaluation(
    String status,
    String source,
    String signalModel,
    int dataPointCount,
    int completedTradeCount,
    BigDecimal totalReturn,
    BigDecimal winRate,
    BigDecimal maxDrawdown,
    BigDecimal sharpeRatio,
    String note,
    List<BacktestTrade> trades
) {
    BacktestEvaluation {
        status = status == null || status.isBlank() ? "INSUFFICIENT_DATA" : status.trim();
        source = source == null ? "" : source.trim();
        signalModel = signalModel == null ? "" : signalModel.trim();
        note = note == null ? "" : note.trim();
        trades = trades == null ? List.of() : List.copyOf(trades);
    }
}
