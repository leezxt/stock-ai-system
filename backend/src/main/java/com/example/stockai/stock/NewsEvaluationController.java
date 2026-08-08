package com.example.stockai.stock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ApiResponse;
import com.example.stockai.common.RequestGuard;
import com.example.stockai.market.Market;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/stocks")
public class NewsEvaluationController {
    private final NewsScoreService newsScoreService;
    private final RequestGuard requestGuard;

    public NewsEvaluationController(NewsScoreService newsScoreService, RequestGuard requestGuard) {
        this.newsScoreService = newsScoreService;
        this.requestGuard = requestGuard;
    }

    @GetMapping("/{market}/{symbol}/news-score")
    ApiResponse<NewsEvaluationResponse> score(
        HttpServletRequest servletRequest,
        @PathVariable Market market,
        @PathVariable String symbol,
        @RequestParam(defaultValue = "5") int limit
    ) {
        String ownerEmail = requestGuard.requireUser(servletRequest, "news-score", 30).email();
        return ApiResponse.of(newsScoreService.evaluate(market, symbol, limit, ownerEmail));
    }
}
