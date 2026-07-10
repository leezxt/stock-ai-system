package com.example.stockai.rag;

public record VectorSearchHit(
    VectorDocument document,
    double score
) {}
