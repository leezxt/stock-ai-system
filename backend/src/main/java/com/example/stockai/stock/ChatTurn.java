package com.example.stockai.stock;

import java.util.Locale;

/**
 * One client-supplied turn used as bounded context for the next question.
 * History is never treated as a system instruction.
 */
public record ChatTurn(String role, String content) {
    static final int MAX_CONTENT_LENGTH = 2_000;

    public ChatTurn {
        role = normalizeRole(role);
        content = normalizeContent(content);
    }

    private static String normalizeRole(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!"user".equals(normalized) && !"assistant".equals(normalized)) {
            throw new IllegalArgumentException("history role must be user or assistant");
        }
        return normalized;
    }

    private static String normalizeContent(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("history content must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("history content is too long");
        }
        return normalized;
    }
}
