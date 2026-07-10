package com.example.stockai.rag;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ApiResponse;
import com.example.stockai.common.RequestGuard;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/documents")
public class RagDocumentController {
    private final DocumentIngestionService documentIngestionService;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final DocumentRetriever documentRetriever;
    private final VectorStore vectorStore;
    private final RequestGuard requestGuard;

    public RagDocumentController(
        DocumentIngestionService documentIngestionService,
        DocumentEmbeddingService documentEmbeddingService,
        DocumentRetriever documentRetriever,
        VectorStore vectorStore,
        RequestGuard requestGuard
    ) {
        this.documentIngestionService = documentIngestionService;
        this.documentEmbeddingService = documentEmbeddingService;
        this.documentRetriever = documentRetriever;
        this.vectorStore = vectorStore;
        this.requestGuard = requestGuard;
    }

    @PostMapping("/import")
    ApiResponse<ImportResponse> importDocument(HttpServletRequest servletRequest, @RequestBody DocumentImportRequest request) {
        String ownerEmail = requestGuard.requireUser(servletRequest, "rag-import", 20).email();
        List<DocumentChunk> chunks = documentIngestionService.ingest(request, ownerEmail);
        vectorStore.upsert(documentEmbeddingService.embed(chunks));
        return ApiResponse.of(new ImportResponse(
            request.symbol(),
            request.market().name(),
            request.docType().name(),
            chunks.size(),
            chunks.stream().map(DocumentChunk::chunkId).toList(),
            Instant.now()
        ));
    }

    @PostMapping("/retrieve")
    ApiResponse<List<RetrievedDocument>> retrieve(HttpServletRequest servletRequest, @RequestBody DocumentRetrieveRequest request) {
        String ownerEmail = requestGuard.requireUser(servletRequest, "rag-retrieve", 60).email();
        return ApiResponse.of(documentRetriever.retrieve(request, ownerEmail));
    }

    record ImportResponse(
        String symbol,
        String market,
        String docType,
        int chunksImported,
        List<String> chunkIds,
        Instant indexedAt
    ) {}
}
