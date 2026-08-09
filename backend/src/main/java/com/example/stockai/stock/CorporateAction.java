package com.example.stockai.stock;

import java.time.LocalDate;

/**
 * A provider-supplied corporate action. Empty lists are intentional when the
 * provider did not return an event feed; the application never infers an
 * action from a price gap.
 */
public record CorporateAction(
    LocalDate date,
    String type,
    String description,
    String source
) {
    public CorporateAction {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        type = type == null || type.isBlank() ? "UNKNOWN" : type.trim().toUpperCase();
        description = description == null ? "" : description.trim();
        source = source == null || source.isBlank() ? "unknown" : source.trim();
    }
}
