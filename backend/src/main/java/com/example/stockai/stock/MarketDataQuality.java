package com.example.stockai.stock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Describes how much dated market data was actually available for a derived
 * response.  Indicator values are still returned for backward compatibility,
 * but clients must use {@link #unavailableIndicators()} before presenting
 * them as complete calculations.
 */
public record MarketDataQuality(
    int sampleCount,
    int requiredSampleCount,
    String dataFrom,
    String dataTo,
    String source,
    String status,
    List<String> unavailableIndicators
) {
    public MarketDataQuality {
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must not be negative");
        }
        if (requiredSampleCount < 0) {
            throw new IllegalArgumentException("requiredSampleCount must not be negative");
        }
        dataFrom = normalizeNullable(dataFrom);
        dataTo = normalizeNullable(dataTo);
        source = normalize(source, "unknown");
        status = normalize(status, "UNKNOWN").toUpperCase(Locale.ROOT);
        unavailableIndicators = unavailableIndicators == null
            ? List.of()
            : List.copyOf(unavailableIndicators);
    }

    public static MarketDataQuality unknown() {
        return new MarketDataQuality(0, 0, null, null, "unknown", "UNKNOWN", List.of());
    }

    static MarketDataQuality forTechnical(List<PriceBar> bars, String fallbackSource) {
        List<PriceBar> validBars = validBars(bars);
        int count = validBars.size();
        List<String> unavailable = new ArrayList<>();
        addIfInsufficient(unavailable, "MA5", count, 5);
        addIfInsufficient(unavailable, "MA20", count, 20);
        addIfInsufficient(unavailable, "MA60", count, 60);
        addIfInsufficient(unavailable, "RSI14", count, 15);
        addIfInsufficient(unavailable, "MACD", count, 26);
        addIfInsufficient(unavailable, "ATR14", count, 15);
        return fromBars(validBars, fallbackSource, 60, unavailable);
    }

    static MarketDataQuality forPrediction(List<PriceBar> bars, String fallbackSource) {
        List<PriceBar> validBars = validBars(bars);
        int count = validBars.size();
        List<String> unavailable = new ArrayList<>();
        if (count < 2) {
            unavailable.addAll(List.of(
                "MOMENTUM",
                "VOLATILITY",
                "UP_PROBABILITY",
                "EXPECTED_RETURN",
                "RISK_LEVEL"
            ));
        } else if (count < 14) {
            unavailable.addAll(List.of("UP_PROBABILITY", "EXPECTED_RETURN"));
        }
        return fromBars(validBars, fallbackSource, 30, unavailable);
    }

    private static MarketDataQuality fromBars(
        List<PriceBar> validBars,
        String fallbackSource,
        int requiredSampleCount,
        List<String> unavailableIndicators
    ) {
        String source = validBars.stream()
            .map(PriceBar::source)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.joining(","));
        if (source.isBlank()) {
            source = normalize(fallbackSource, "unknown");
        }
        int count = validBars.size();
        String status;
        if (isMockSource(source)) {
            status = "MOCK";
        } else if (count == 0) {
            status = "INSUFFICIENT_DATA";
        } else if (count == 1) {
            status = "SNAPSHOT_ONLY";
        } else if (count < requiredSampleCount) {
            status = "PARTIAL";
        } else {
            status = "OK";
        }
        String dataFrom = validBars.isEmpty() ? null : validBars.get(0).date().toString();
        String dataTo = validBars.isEmpty() ? null : validBars.get(validBars.size() - 1).date().toString();
        return new MarketDataQuality(count, requiredSampleCount, dataFrom, dataTo, source, status, unavailableIndicators);
    }

    private static List<PriceBar> validBars(List<PriceBar> bars) {
        if (bars == null) {
            return List.of();
        }
        return bars.stream()
            .filter(Objects::nonNull)
            .filter(bar -> bar.close() != null && bar.close().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing(PriceBar::date))
            .toList();
    }

    private static void addIfInsufficient(List<String> target, String name, int actual, int required) {
        if (actual < required) {
            target.add(name);
        }
    }

    private static boolean isMockSource(String source) {
        return source == null || source.isBlank() || source.toLowerCase(Locale.ROOT).startsWith("mock");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
