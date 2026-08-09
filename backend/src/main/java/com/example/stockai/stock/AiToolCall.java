package com.example.stockai.stock;

import java.time.Instant;
import java.util.List;

/**
 * Structured, deterministic evidence produced by a backend chat tool.
 *
 * This is deliberately separate from the language-model response: the UI and
 * audit logs can show which source was actually queried even when the model is
 * unavailable or returns no document citation.
 */
record AiToolCall(
    String name,
    String status,
    String summary,
    String source,
    Instant observedAt,
    List<String> citationIds
) {
    AiToolCall {
        name = name == null ? "unknown" : name.trim();
        status = status == null ? "UNKNOWN" : status.trim().toUpperCase();
        summary = summary == null ? "" : summary.trim();
        source = source == null ? "unknown" : source.trim();
        citationIds = citationIds == null ? List.of() : List.copyOf(citationIds);
    }
}
