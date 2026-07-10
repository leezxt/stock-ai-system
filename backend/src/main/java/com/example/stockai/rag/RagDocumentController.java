package com.example.stockai.rag;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ApiResponse;

@RestController
@RequestMapping("/api/v1/documents")
public class RagDocumentController {
    private final DocumentIngestionService documentIngestionService;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final DocumentRetriever documentRetriever;
    private final VectorStore vectorStore;

    public RagDocumentController(
        DocumentIngestionService documentIngestionService,
        DocumentEmbeddingService documentEmbeddingService,
        DocumentRetriever documentRetriever,
        VectorStore vectorStore
    ) {
        this.documentIngestionService = documentIngestionService;
        this.documentEmbeddingService = documentEmbeddingService;
        this.documentRetriever = documentRetriever;
        this.vectorStore = vectorStore;
    }

    @PostMapping("/import")
    ApiResponse<ImportResponse> importDocument(@RequestBody DocumentImportRequest request) {
        List<DocumentChunk> chunks = documentIngestionService.ingest(request);
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
    ApiResponse<List<RetrievedDocument>> retrieve(@RequestBody DocumentRetrieveRequest request) {
        return ApiResponse.of(documentRetriever.retrieve(request));
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
