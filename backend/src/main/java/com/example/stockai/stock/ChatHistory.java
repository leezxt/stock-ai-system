package com.example.stockai.stock;

import java.util.List;

final class ChatHistory {
    static final int MAX_TURNS = 8;
    static final int MAX_TOTAL_CHARACTERS = 8_000;

    private ChatHistory() {}

    static List<ChatTurn> normalize(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        if (history.size() > MAX_TURNS) {
            throw new IllegalArgumentException("history must contain at most " + MAX_TURNS + " turns");
        }
        int totalCharacters = 0;
        for (ChatTurn turn : history) {
            if (turn == null) {
                throw new IllegalArgumentException("history must not contain null turns");
            }
            totalCharacters += turn.content().length();
            if (totalCharacters > MAX_TOTAL_CHARACTERS) {
                throw new IllegalArgumentException("history is too long");
            }
        }
        return List.copyOf(history);
    }

    static String toPromptBlock(List<ChatTurn> history) {
        List<ChatTurn> normalized = normalize(history);
        if (normalized.isEmpty()) {
            return "- 無先前對話";
        }
        return normalized.stream()
            .map(turn -> "<turn role=\"" + escapeAttribute(turn.role()) + "\">\n"
                + escapeText(turn.content())
                + "\n</turn>")
            .reduce((left, right) -> left + System.lineSeparator() + right)
            .orElse("- 無先前對話");
    }

    static String escapeText(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    static String escapeAttribute(String value) {
        return escapeText(value).replace("\"", "&quot;");
    }
}
