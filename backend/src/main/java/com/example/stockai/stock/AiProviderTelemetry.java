package com.example.stockai.stock;

import java.util.Map;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.stereotype.Component;

/**
 * Process-local provider counters. The counters intentionally exclude prompts,
 * user identifiers, and API response bodies so health diagnostics cannot leak
 * request content or credentials.
 */
@Component
public class AiProviderTelemetry {
    private final ConcurrentMap<String, Counters> counters = new ConcurrentHashMap<>();

    public void record(String provider, String operation, boolean liveSuccess, boolean exception, long elapsedMillis) {
        String key = normalize(provider) + "." + normalize(operation);
        Counters current = counters.computeIfAbsent(key, ignored -> new Counters());
        current.requests.increment();
        current.totalLatencyMillis.add(Math.max(0L, elapsedMillis));
        if (liveSuccess) {
            current.liveSuccesses.increment();
        } else {
            current.fallbacks.increment();
        }
        if (exception) {
            current.exceptions.increment();
        }
    }

    public Map<String, Stats> snapshot() {
        Map<String, Stats> result = new TreeMap<>();
        counters.forEach((key, value) -> result.put(key, value.snapshot()));
        return Map.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record Stats(
        long requests,
        long liveSuccesses,
        long fallbacks,
        long exceptions,
        long totalLatencyMillis
    ) {}

    private static final class Counters {
        private final LongAdder requests = new LongAdder();
        private final LongAdder liveSuccesses = new LongAdder();
        private final LongAdder fallbacks = new LongAdder();
        private final LongAdder exceptions = new LongAdder();
        private final LongAdder totalLatencyMillis = new LongAdder();

        private Stats snapshot() {
            return new Stats(
                requests.sum(),
                liveSuccesses.sum(),
                fallbacks.sum(),
                exceptions.sum(),
                totalLatencyMillis.sum()
            );
        }
    }
}
