package com.example.stockai.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionService {
    private final int chunkSize;
    private final int overlapSize;

    public DocumentIngestionService() {
        this(800, 120);
    }

    DocumentIngestionService(int chunkSize, int overlapSize) {
        if (chunkSize < 100) {
            throw new IllegalArgumentException("chunkSize must be at least 100");
        }
        if (overlapSize < 0 || overlapSize >= chunkSize) {
            throw new IllegalArgumentException("overlapSize must be between 0 and chunkSize - 1");
        }
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
    }

    public List<DocumentChunk> ingest(DocumentImportRequest request) {
        String cleaned = clean(request.content());
        List<String> chunks = split(cleaned);
        List<DocumentChunk> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            result.add(new DocumentChunk(
                chunkId(request, i),
                request.symbol(),
                request.market(),
                request.docType(),
                request.title(),
                request.source(),
                request.publishedAt(),
                chunks.get(i)
            ));
        }
        return result;
    }

    String clean(String content) {
        return content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replaceAll("[\\t\\x0B\\f ]+", " ")
            .replaceAll(" *\n *", "\n")
            .replaceAll("\n{3,}", "\n\n")
            .trim();
    }

    List<String> split(String cleaned) {
        if (cleaned.length() <= chunkSize) {
            return List.of(cleaned);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < cleaned.length()) {
            int end = Math.min(start + chunkSize, cleaned.length());
            if (end < cleaned.length()) {
                int softEnd = findSoftEnd(cleaned, start, end);
                if (softEnd > start) {
                    end = softEnd;
                }
            }
            String chunk = cleaned.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= cleaned.length()) {
                break;
            }
            start = Math.max(end - overlapSize, start + 1);
        }
        return chunks;
    }

    private int findSoftEnd(String text, int start, int hardEnd) {
        int minEnd = start + (chunkSize * 3 / 5);
        int newline = text.lastIndexOf("\n\n", hardEnd - 1);
        if (newline >= minEnd) {
            return newline;
        }
        int space = text.lastIndexOf(' ', hardEnd - 1);
        if (space >= minEnd) {
            return space;
        }
        return hardEnd;
    }

    private static String chunkId(DocumentImportRequest request, int index) {
        return request.market().name() + "-" + request.symbol() + "-" + request.publishedAt().toEpochMilli() + "-" + index;
    }
}
