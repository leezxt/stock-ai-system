package com.example.stockai.rag;

import java.util.List;

public interface VectorStore {
    void upsert(List<VectorDocument> documents);

    List<VectorSearchHit> search(VectorSearchQuery query);
}
