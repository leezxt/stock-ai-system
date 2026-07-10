package com.example.stockai.rag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ApiResponse;
import com.example.stockai.common.RequestGuard;
import com.example.stockai.market.Market;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/documents/source")
public class DocumentSourceController {
    private final DocumentSourceImportService documentSourceImportService;
    private final RequestGuard requestGuard;

    public DocumentSourceController(DocumentSourceImportService documentSourceImportService, RequestGuard requestGuard) {
        this.documentSourceImportService = documentSourceImportService;
        this.requestGuard = requestGuard;
    }

    @PostMapping("/news/fetch")
    ApiResponse<DocumentSourceImportService.SourceImportResponse> fetchNews(HttpServletRequest servletRequest, @RequestBody SourceFetchRequest request) {
        String ownerEmail = requestGuard.requireUser(servletRequest, "source-news", 10).email();
        return ApiResponse.of(documentSourceImportService.importNews(request.market(), request.symbol(), limitOrDefault(request.limit()), ownerEmail));
    }

    @PostMapping("/announcements/fetch")
    ApiResponse<DocumentSourceImportService.SourceImportResponse> fetchAnnouncements(HttpServletRequest servletRequest, @RequestBody SourceFetchRequest request) {
        String ownerEmail = requestGuard.requireUser(servletRequest, "source-announcements", 10).email();
        return ApiResponse.of(documentSourceImportService.importAnnouncements(request.market(), request.symbol(), limitOrDefault(request.limit()), ownerEmail));
    }

    @PostMapping("/transcripts/fetch")
    ApiResponse<DocumentSourceImportService.SourceImportResponse> fetchTranscripts(HttpServletRequest servletRequest, @RequestBody TranscriptFetchRequest request) {
        String ownerEmail = requestGuard.requireUser(servletRequest, "source-transcripts", 10).email();
        return ApiResponse.of(documentSourceImportService.importTranscript(request.market(), request.symbol(), request.quarter(), ownerEmail));
    }

    @PostMapping("/financials/fetch")
    ApiResponse<DocumentSourceImportService.SourceImportResponse> fetchFinancials(HttpServletRequest servletRequest, @RequestBody SourceFetchRequest request) {
        String ownerEmail = requestGuard.requireUser(servletRequest, "source-financials", 10).email();
        return ApiResponse.of(documentSourceImportService.importFinancials(request.market(), request.symbol(), limitOrDefault(request.limit()), ownerEmail));
    }

    private static int limitOrDefault(Integer limit) {
        return limit == null || limit <= 0 ? 5 : Math.min(limit, 10);
    }

    record SourceFetchRequest(Market market, String symbol, Integer limit) {}
    record TranscriptFetchRequest(Market market, String symbol, String quarter) {}
}
