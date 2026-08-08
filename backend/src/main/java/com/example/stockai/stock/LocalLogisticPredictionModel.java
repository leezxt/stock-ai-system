package com.example.stockai.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Small, deterministic supervised model that runs without a Python service or
 * external model endpoint. It learns next-bar direction from lagged returns,
 * volatility, RSI and local trend using only the supplied history. This is a
 * baseline model, not a claim of investment-grade forecasting.
 */
final class LocalLogisticPredictionModel implements PredictionModel {
    static final String MODEL_VERSION = "local-logistic-v1";
    private static final int LOOKBACK = 5;
    private static final int FEATURE_COUNT = 5;
    private static final int MIN_EXAMPLES = 8;
    private static final int EPOCHS = 320;
    private static final double LEARNING_RATE = 0.22;
    private static final double L2 = 0.001;

    @Override
    public Optional<PredictionEstimate> predict(List<PriceBar> rawBars, int horizonDays) {
        List<Double> prices = rawBars == null ? List.of() : rawBars.stream()
            .filter(bar -> bar != null && bar.close() != null && bar.close().signum() > 0)
            .sorted(Comparator.comparing(PriceBar::date))
            .map(bar -> bar.close().doubleValue())
            .filter(Double::isFinite)
            .toList();
        if (prices.size() < LOOKBACK + MIN_EXAMPLES + 1) {
            return Optional.empty();
        }

        List<TrainingExample> examples = new ArrayList<>();
        for (int index = LOOKBACK; index < prices.size() - 1; index++) {
            double[] features = features(prices, index);
            if (features == null) continue;
            double nextReturn = safeReturn(prices.get(index), prices.get(index + 1));
            examples.add(new TrainingExample(features, nextReturn > 0 ? 1.0 : 0.0, nextReturn));
        }
        if (examples.size() < MIN_EXAMPLES) {
            return Optional.empty();
        }

        Standardization standardization = Standardization.from(examples);
        double[][] x = new double[examples.size()][FEATURE_COUNT];
        double[] y = new double[examples.size()];
        for (int row = 0; row < examples.size(); row++) {
            x[row] = standardization.apply(examples.get(row).features());
            y[row] = examples.get(row).label();
        }

        double[] weights = new double[FEATURE_COUNT + 1];
        for (int epoch = 0; epoch < EPOCHS; epoch++) {
            double[] gradient = new double[weights.length];
            for (int row = 0; row < x.length; row++) {
                double probability = sigmoid(score(weights, x[row]));
                double error = probability - y[row];
                gradient[0] += error;
                for (int feature = 0; feature < FEATURE_COUNT; feature++) {
                    gradient[feature + 1] += error * x[row][feature];
                }
            }
            for (int index = 0; index < weights.length; index++) {
                double regularization = index == 0 ? 0 : L2 * weights[index];
                weights[index] -= LEARNING_RATE * (gradient[index] / x.length + regularization);
            }
        }

        double[] latest = features(prices, prices.size() - 1);
        if (latest == null) return Optional.empty();
        double probability = sigmoid(score(weights, standardization.apply(latest)));
        probability = clamp(probability, 0.05, 0.95);
        double averageUp = examples.stream().filter(example -> example.label() > 0.5)
            .mapToDouble(TrainingExample::forwardReturn).average().orElse(0.0);
        double averageDown = examples.stream().filter(example -> example.label() <= 0.5)
            .mapToDouble(TrainingExample::forwardReturn).average().orElse(0.0);
        double expectedDailyReturn = probability * averageUp + (1.0 - probability) * averageDown;
        int horizon = Math.max(1, Math.min(horizonDays, 60));
        double expectedReturn = clamp(Math.pow(Math.max(0.01, 1.0 + expectedDailyReturn), horizon) - 1.0, -0.50, 0.50);
        double volatility = volatility(prices);
        String riskLevel = volatility > 0.03 ? "HIGH" : volatility > 0.015 ? "MEDIUM" : "LOW";
        return Optional.of(new PredictionEstimate(
            decimal(probability, 8),
            decimal(expectedReturn, 8),
            decimal(volatility, 8),
            riskLevel,
            MODEL_VERSION
        ));
    }

    private static double[] features(List<Double> prices, int index) {
        if (index < LOOKBACK || index >= prices.size()) return null;
        double current = prices.get(index);
        double previous = prices.get(index - 1);
        double fiveAgo = prices.get(index - LOOKBACK);
        if (!(current > 0) || !(previous > 0) || !(fiveAgo > 0)) return null;
        double oneDayReturn = safeReturn(previous, current);
        double fiveDayReturn = safeReturn(fiveAgo, current);
        double recentVolatility = volatility(prices.subList(index - LOOKBACK, index + 1));
        double recentRsi = rsi(prices, index, LOOKBACK);
        double localTrend = fiveDayReturn / LOOKBACK;
        return new double[] { oneDayReturn, fiveDayReturn, recentVolatility, (recentRsi - 50.0) / 50.0, localTrend };
    }

    private static double score(double[] weights, double[] features) {
        double result = weights[0];
        for (int index = 0; index < FEATURE_COUNT; index++) result += weights[index + 1] * features[index];
        return result;
    }

    private static double sigmoid(double value) {
        if (value >= 0) {
            double z = Math.exp(-value);
            return 1.0 / (1.0 + z);
        }
        double z = Math.exp(value);
        return z / (1.0 + z);
    }

    private static double safeReturn(double from, double to) {
        return from == 0 ? 0.0 : to / from - 1.0;
    }

    private static double volatility(List<Double> prices) {
        if (prices == null || prices.size() < 2) return 0.0;
        double[] returns = new double[prices.size() - 1];
        double mean = 0.0;
        for (int index = 1; index < prices.size(); index++) {
            returns[index - 1] = safeReturn(prices.get(index - 1), prices.get(index));
            mean += returns[index - 1];
        }
        mean /= returns.length;
        final double average = mean;
        double variance = Arrays.stream(returns).map(value -> Math.pow(value - average, 2)).average().orElse(0.0);
        return Math.sqrt(Math.max(0.0, variance));
    }

    private static double rsi(List<Double> prices, int index, int window) {
        int start = Math.max(1, index - window + 1);
        double gains = 0.0;
        double losses = 0.0;
        for (int cursor = start; cursor <= index; cursor++) {
            double delta = prices.get(cursor) - prices.get(cursor - 1);
            if (delta >= 0) gains += delta; else losses -= delta;
        }
        if (gains == 0 && losses == 0) return 50.0;
        if (losses == 0) return 100.0;
        return 100.0 - (100.0 / (1.0 + gains / losses));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private record TrainingExample(double[] features, double label, double forwardReturn) {}

    private record Standardization(double[] means, double[] deviations) {
        static Standardization from(List<TrainingExample> examples) {
            double[] means = new double[FEATURE_COUNT];
            for (TrainingExample example : examples) {
                for (int feature = 0; feature < FEATURE_COUNT; feature++) means[feature] += example.features()[feature];
            }
            for (int feature = 0; feature < FEATURE_COUNT; feature++) means[feature] /= examples.size();
            double[] deviations = new double[FEATURE_COUNT];
            for (TrainingExample example : examples) {
                for (int feature = 0; feature < FEATURE_COUNT; feature++) {
                    deviations[feature] += Math.pow(example.features()[feature] - means[feature], 2);
                }
            }
            for (int feature = 0; feature < FEATURE_COUNT; feature++) {
                deviations[feature] = Math.sqrt(deviations[feature] / examples.size());
                if (!Double.isFinite(deviations[feature]) || deviations[feature] < 1e-9) deviations[feature] = 1.0;
            }
            return new Standardization(means, deviations);
        }

        double[] apply(double[] values) {
            double[] standardized = new double[FEATURE_COUNT];
            for (int feature = 0; feature < FEATURE_COUNT; feature++) {
                standardized[feature] = (values[feature] - means[feature]) / deviations[feature];
            }
            return standardized;
        }
    }
}
