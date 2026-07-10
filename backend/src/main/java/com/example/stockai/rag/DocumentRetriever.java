package com.example.stockai.rag;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class DocumentRetriever {
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;

    public DocumentRetriever(EmbeddingModel embeddingModel, VectorStore vectorStore) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }

    public List<RetrievedDocument> retrieve(DocumentRetrieveRequest request) {
        VectorSearchQuery query = new VectorSearchQuery(
            embeddingModel.embed(request.queryText()),
            request.topK(),
            request.symbol(),
            request.market(),
            request.docType(),
            request.publishedFrom(),
            request.publishedTo()
        );
        return vectorStore.search(query).stream()
            .map(hit -> toRetrievedDocument(hit.document(), hit.score()))
            .toList();
    }

    private static RetrievedDocument toRetrievedDocument(VectorDocument document, double score) {
        DocumentChunk chunk = document.chunk();
        return new RetrievedDocument(
            chunk.chunkId(),
            chunk.symbol(),
            chunk.market(),
            chunk.docType(),
            chunk.title(),
            chunk.source(),
            chunk.publishedAt(),
            snippet(chunk.content()),
            score
        );
    }

    private static String snippet(String content) {
        String normalized = content.replace('\n', ' ').trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180).trim() + "...";
    }
}
