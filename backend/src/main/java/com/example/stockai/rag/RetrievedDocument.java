package com.example.stockai.rag;

import java.time.Instant;

import com.example.stockai.market.Market;

public record RetrievedDocument(
    String chunkId,
    String symbol,
    Market market,
    DocumentType docType,
    String title,
    String source,
    Instant publishedAt,
    String snippet,
    double score
) {}
