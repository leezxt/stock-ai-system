package com.example.stockai.rag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ApiResponse;
import com.example.stockai.market.Market;

@RestController
@RequestMapping("/api/v1/documents/source")
public class DocumentSourceController {
    private final DocumentSourceImportService documentSourceImportService;

    public DocumentSourceController(DocumentSourceImportService documentSourceImportService) {
        this.documentSourceImportService = documentSourceImportService;
    }

    @PostMapping("/news/fetch")
    ApiResponse<DocumentSourceImportService.SourceImportResponse> fetchNews(@RequestBody SourceFetchRequest request) {
        return ApiResponse.of(documentSourceImportService.importNews(request.market(), request.symbol(), limitOrDefault(request.limit())));
    }

    @PostMapping("/announcements/fetch")
    ApiResponse<DocumentSourceImportService.SourceImportResponse> fetchAnnouncements(@RequestBody SourceFetchRequest request) {
        return ApiResponse.of(documentSourceImportService.importAnnouncements(request.market(), request.symbol(), limitOrDefault(request.limit())));
    }

    @PostMapping("/transcripts/fetch")
    ApiResponse<DocumentSourceImportService.SourceImportResponse> fetchTranscripts(@RequestBody TranscriptFetchRequest request) {
        return ApiResponse.of(documentSourceImportService.importTranscript(request.market(), request.symbol(), request.quarter()));
    }

    @PostMapping("/financials/fetch")
    ApiResponse<DocumentSourceImportService.SourceImportResponse> fetchFinancials(@RequestBody SourceFetchRequest request) {
        return ApiResponse.of(documentSourceImportService.importFinancials(request.market(), request.symbol(), limitOrDefault(request.limit())));
    }

    private static int limitOrDefault(Integer limit) {
        return limit == null || limit <= 0 ? 5 : limit;
    }

    record SourceFetchRequest(Market market, String symbol, Integer limit) {}
    record TranscriptFetchRequest(Market market, String symbol, String quarter) {}
}
