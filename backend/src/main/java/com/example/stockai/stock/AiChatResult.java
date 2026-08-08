package com.example.stockai.stock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.stockai.rag.RetrievedDocument;

record AiChatResult(String message, String source, List<String> citationIds) {
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[([^]\\s]{1,200})\\]");

    AiChatResult(String message, String source) {
        this(message, source, List.of());
    }

    static AiChatResult fromEvidence(String message, String source, List<RetrievedDocument> evidence) {
        Set<String> allowed = evidence == null
            ? Set.of()
            : evidence.stream().map(RetrievedDocument::chunkId).collect(java.util.stream.Collectors.toSet());
        Set<String> citations = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(message == null ? "" : message);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (allowed.contains(candidate)) {
                citations.add(candidate);
            }
        }
        return new AiChatResult(message == null ? "" : message, source, List.copyOf(citations));
    }
}
