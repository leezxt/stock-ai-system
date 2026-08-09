package com.example.stockai.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Replays close-only historical bars without looking ahead. The score and
 * probability are technical momentum proxies, deliberately not current-date
 * AI outputs, so the result is honest about what can be evaluated from prices.
 */
@Service
class HistoricalBacktestEngine {
    private static final String SOURCE = "historical-price-replay-v1";
    private static final String SIGNAL_MODEL = "technical-momentum-replay-v1";
    private static final int LOOKBACK_BARS = 5;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    BacktestEvaluation evaluate(StockRecord stock, MockFeatureController.Strategy strategy) {
        List<PriceBar> bars = normalizedBars(stock == null ? List.of() : stock.priceHistory());
        int minimumBars = LOOKBACK_BARS + strategy.holdingDays() + 1;
        if (bars.size() < minimumBars) {
            return empty("INSUFFICIENT_DATA", bars.size(), "需要至少 " + minimumBars + " 個有日期的收盤價，實際只有 " + bars.size() + " 個。");
        }

        List<BacktestTrade> trades = new ArrayList<>();
        BigDecimal equity = ONE;
        BigDecimal peak = ONE;
        BigDecimal maxDrawdown = ZERO;
        int cursor = LOOKBACK_BARS - 1;
        while (cursor + 1 < bars.size()) {
            Signal signal = signalAt(bars, cursor);
            if (!passes(signal, strategy)) {
                cursor++;
                continue;
            }
            int entryIndex = cursor + 1;
            int latestExitIndex = Math.min(bars.size() - 1, entryIndex + strategy.holdingDays());
            if (latestExitIndex <= entryIndex) {
                break;
            }
            BigDecimal rawEntry = bars.get(entryIndex).close();
            BigDecimal entryPrice = rawEntry.multiply(ONE.add(strategy.slippageRate()));
            int exitIndex = latestExitIndex;
            String exitReason = "HORIZON";
            for (int candidate = entryIndex + 1; candidate <= latestExitIndex; candidate++) {
                BigDecimal candidateNet = netReturn(
                    rawEntry,
                    bars.get(candidate).close(),
                    strategy
                );
                if (strategy.takeProfitRate().signum() > 0 && candidateNet.compareTo(strategy.takeProfitRate()) >= 0) {
                    exitIndex = candidate;
                    exitReason = "TAKE_PROFIT";
                    break;
                }
                if (strategy.stopLossRate().signum() > 0 && candidateNet.compareTo(strategy.stopLossRate().negate()) <= 0) {
                    exitIndex = candidate;
                    exitReason = "STOP_LOSS";
                    break;
                }
            }

            PriceBar exitBar = bars.get(exitIndex);
            BigDecimal grossReturn = exitBar.close().divide(rawEntry, 10, RoundingMode.HALF_UP).subtract(ONE);
            BigDecimal netReturn = netReturn(rawEntry, exitBar.close(), strategy);
            BigDecimal costRate = grossReturn.subtract(netReturn);
            boolean win = netReturn.signum() > 0;
            BacktestTrade trade = new BacktestTrade(
                bars.get(entryIndex).date(),
                exitBar.date(),
                scale(entryPrice),
                scale(exitBar.close().multiply(ONE.subtract(strategy.slippageRate()))),
                scale(grossReturn),
                scale(costRate),
                scale(netReturn),
                win,
                exitReason
            );
            trades.add(trade);
            equity = equity.multiply(ONE.add(netReturn));
            peak = peak.max(equity);
            maxDrawdown = maxDrawdown.min(equity.divide(peak, 10, RoundingMode.HALF_UP).subtract(ONE));
            cursor = exitIndex;
        }

        BigDecimal totalReturn = equity.subtract(ONE);
        BigDecimal winRate = trades.isEmpty()
            ? ZERO
            : BigDecimal.valueOf(trades.stream().filter(BacktestTrade::win).count())
                .divide(BigDecimal.valueOf(trades.size()), 8, RoundingMode.HALF_UP);
        BigDecimal sharpe = sharpe(trades);
        return new BacktestEvaluation(
            trades.isEmpty() ? "NO_SIGNALS" : "COMPLETED",
            SOURCE,
            SIGNAL_MODEL,
            bars.size(),
            trades.size(),
            scale(totalReturn),
            scale(winRate),
            scale(maxDrawdown),
            scale(sharpe),
            "",
            trades
        );
    }

    private static BacktestEvaluation empty(String status, int dataPointCount, String reason) {
        return new BacktestEvaluation(
            status,
            SOURCE,
            SIGNAL_MODEL,
            dataPointCount,
            0,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            reason,
            List.of()
        );
    }

    private static List<PriceBar> normalizedBars(List<PriceBar> input) {
        return (input == null ? List.<PriceBar>of() : input).stream()
            .filter(bar -> bar != null && bar.close() != null && bar.close().signum() > 0)
            .sorted(Comparator.comparing(PriceBar::date))
            .toList();
    }

    private static Signal signalAt(List<PriceBar> bars, int index) {
        BigDecimal current = bars.get(index).close();
        BigDecimal previous = bars.get(index - LOOKBACK_BARS + 1).close();
        BigDecimal momentum = current.divide(previous, 10, RoundingMode.HALF_UP).subtract(ONE);
        BigDecimal shortAverage = average(bars, index - 2, index);
        BigDecimal longAverage = average(bars, index - LOOKBACK_BARS + 1, index);
        BigDecimal movingSpread = shortAverage.divide(longAverage, 10, RoundingMode.HALF_UP).subtract(ONE);
        BigDecimal volatility = averageAbsoluteReturn(bars, index - LOOKBACK_BARS + 1, index);
        BigDecimal score = clamp(new BigDecimal("50").add(momentum.multiply(new BigDecimal("400"))).add(movingSpread.multiply(new BigDecimal("300"))), ZERO, new BigDecimal("100"));
        BigDecimal probability = clamp(new BigDecimal("0.50").add(momentum.multiply(new BigDecimal("4"))).add(movingSpread.multiply(new BigDecimal("2"))), new BigDecimal("0.30"), new BigDecimal("0.70"));
        String risk = volatility.compareTo(new BigDecimal("0.04")) > 0 ? "HIGH" : volatility.compareTo(new BigDecimal("0.02")) > 0 ? "MEDIUM" : "LOW";
        return new Signal(score, probability, risk);
    }

    private static boolean passes(Signal signal, MockFeatureController.Strategy strategy) {
        return signal.score().compareTo(strategy.minAiScore()) >= 0
            && signal.upProbability().compareTo(strategy.minUpProbability()) >= 0
            && riskRank(signal.riskLevel()) <= riskRank(strategy.maxRiskLevel());
    }

    private static BigDecimal netReturn(BigDecimal rawEntry, BigDecimal rawExit, MockFeatureController.Strategy strategy) {
        BigDecimal entry = rawEntry.multiply(ONE.add(strategy.slippageRate()));
        BigDecimal exit = rawExit.multiply(ONE.subtract(strategy.slippageRate()));
        BigDecimal invested = entry.multiply(ONE.add(strategy.commissionRate()));
        BigDecimal proceeds = exit.multiply(ONE.subtract(strategy.commissionRate()).subtract(strategy.taxRate()));
        return proceeds.divide(invested, 10, RoundingMode.HALF_UP).subtract(ONE);
    }

    private static BigDecimal sharpe(List<BacktestTrade> trades) {
        if (trades.size() < 2) {
            return ZERO;
        }
        double mean = trades.stream().mapToDouble(trade -> trade.netReturn().doubleValue()).average().orElse(0d);
        double variance = trades.stream().mapToDouble(trade -> Math.pow(trade.netReturn().doubleValue() - mean, 2)).sum() / trades.size();
        return variance == 0d ? ZERO : BigDecimal.valueOf(mean / Math.sqrt(variance) * Math.sqrt(252d / 5d));
    }

    private static BigDecimal average(List<PriceBar> bars, int start, int end) {
        BigDecimal total = ZERO;
        for (int i = start; i <= end; i++) {
            total = total.add(bars.get(i).close());
        }
        return total.divide(BigDecimal.valueOf(end - start + 1L), 10, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageAbsoluteReturn(List<PriceBar> bars, int start, int end) {
        if (start >= end) {
            return ZERO;
        }
        BigDecimal total = ZERO;
        int count = 0;
        for (int i = start + 1; i <= end; i++) {
            BigDecimal previous = bars.get(i - 1).close();
            total = total.add(bars.get(i).close().divide(previous, 10, RoundingMode.HALF_UP).subtract(ONE).abs());
            count++;
        }
        return total.divide(BigDecimal.valueOf(count), 10, RoundingMode.HALF_UP);
    }

    private static int riskRank(String risk) {
        return switch (risk == null ? "" : risk) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            default -> 4;
        };
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.max(min).min(max);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private record Signal(BigDecimal score, BigDecimal upProbability, String riskLevel) {}
}
