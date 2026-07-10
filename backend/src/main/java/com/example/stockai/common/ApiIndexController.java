package com.example.stockai.common;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class ApiIndexController {
    @GetMapping
    ApiIndex index() {
        return new ApiIndex(
            "Stock AI API",
            "UP",
            List.of(
                "GET /app",
                "GET /api/v1/health",
                "GET /api/v1/markets",
                "GET /api/v1/stocks/{market}/search?query=AAPL",
                "GET /api/v1/stocks/{market}/{symbol}/summary",
                "GET /api/v1/stocks/{market}/{symbol}/prices",
                "GET /api/v1/stocks/{market}/{symbol}/technical-summary",
                "GET /api/v1/stocks/{market}/{symbol}/prediction",
                "POST /api/v1/ai/analysis",
                "POST /api/v1/ai/model-comparison",
                "POST /api/v1/ai/chat",
                "POST /api/v1/auth/register",
                "POST /api/v1/auth/login",
                "POST /api/v1/auth/google",
                "GET /api/v1/auth/me",
                "GET /api/v1/account/settings",
                "PUT /api/v1/account/settings",
                "POST /api/v1/documents/import",
                "POST /api/v1/documents/retrieve",
                "POST /api/v1/documents/source/news/fetch",
                "POST /api/v1/documents/source/financials/fetch",
                "POST /api/v1/documents/source/announcements/fetch",
                "POST /api/v1/documents/source/transcripts/fetch",
                "GET /api/v1/watchlist",
                "POST /api/v1/watchlist",
                "DELETE /api/v1/watchlist/{market}/{symbol}",
                "POST /api/v1/backtests"
            ),
            Instant.now()
        );
    }

    record ApiIndex(String name, String status, List<String> endpoints, Instant timestamp) {}
}
