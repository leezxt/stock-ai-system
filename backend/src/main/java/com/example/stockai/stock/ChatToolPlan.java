package com.example.stockai.stock;

import java.util.List;

/**
 * Bounded intent and tool plan for one stock research question.
 */
record ChatToolPlan(
    String intent,
    String answerStatus,
    String scopeNote,
    List<AiToolCall> tools
) {
    ChatToolPlan {
        intent = intent == null ? "GENERAL_RESEARCH" : intent.trim().toUpperCase();
        answerStatus = answerStatus == null ? "UNKNOWN" : answerStatus.trim().toUpperCase();
        scopeNote = scopeNote == null ? "" : scopeNote.trim();
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    static ChatToolPlan empty() {
        return new ChatToolPlan("GENERAL_RESEARCH", "UNKNOWN", "", List.of());
    }

    boolean isOutOfScope() {
        return "OUT_OF_SCOPE".equals(answerStatus);
    }

    String promptBlock() {
        if (tools.isEmpty() && scopeNote.isBlank()) {
            return "<chat_tool_plan intent=\"" + intent + "\" answerStatus=\"" + answerStatus + "\">\n- 無工具結果\n</chat_tool_plan>";
        }
        StringBuilder block = new StringBuilder()
            .append("<chat_tool_plan intent=\"").append(ChatHistory.escapeAttribute(intent))
            .append("\" answerStatus=\"").append(ChatHistory.escapeAttribute(answerStatus)).append("\">\n");
        if (!scopeNote.isBlank()) {
            block.append("scopeNote=").append(ChatHistory.escapeText(scopeNote)).append('\n');
        }
        for (AiToolCall tool : tools) {
            block.append("<tool name=\"").append(ChatHistory.escapeAttribute(tool.name()))
                .append("\" status=\"").append(ChatHistory.escapeAttribute(tool.status()))
                .append("\" source=\"").append(ChatHistory.escapeAttribute(tool.source())).append("\">\n")
                .append(ChatHistory.escapeText(tool.summary())).append('\n');
            if (!tool.citationIds().isEmpty()) {
                block.append("citationIds=").append(ChatHistory.escapeText(String.join(",", tool.citationIds()))).append('\n');
            }
            block.append("</tool>\n");
        }
        return block.append("</chat_tool_plan>").toString();
    }
}
