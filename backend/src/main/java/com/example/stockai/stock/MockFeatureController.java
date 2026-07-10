package com.example.stockai.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ApiResponse;
import com.example.stockai.common.RequestGuard;
import com.example.stockai.market.Market;
import com.example.stockai.market.SymbolNormalizer;
import com.example.stockai.rag.RetrievedDocument;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1")
public class MockFeatureController {
    private static final List<String> DEFAULT_PROVIDERS = List.of("OPENAI", "GEMINI", "DEEPSEEK", "MIMO");
    private static final List<String> ALLOWED_PROVIDERS = List.of("OPENAI", "GEMINI", "DEEPSEEK", "MIMO");

    private final AiProviderAdapter aiProviderAdapter;
    private final RagContextService ragContextService;
    private final StockService stockService;
    private final WatchlistService watchlistService;
    private final RequestGuard requestGuard;

    public MockFeatureController(
        AiProviderAdapter aiProviderAdapter,
        RagContextService ragContextService,
        StockService stockService,
        WatchlistService watchlistService,
        RequestGuard requestGuard
    ) {
        this.aiProviderAdapter = aiProviderAdapter;
        this.ragContextService = ragContextService;
        this.stockService = stockService;
        this.watchlistService = watchlistService;
        this.requestGuard = requestGuard;
    }

    @PostMapping("/ai/analysis")
    ApiResponse<AiAnalysisResponse> analysis(HttpServletRequest servletRequest, @RequestBody AiRequest request) {
        String ownerEmail = requestGuard.requireUser(servletRequest, "ai-analysis", 20).email();
        StockRecord stock = stockService.get(request.market(), request.symbol());
        RagContext context = ragContextService.buildAnalysisContext(stock, request.horizonDays(), ownerEmail);
        AiProviderResult result = aiProviderAdapter.analyze(stock, context, providerOrDefault(request.provider()), 0);
        return ApiResponse.of(new AiAnalysisResponse(
            stock.symbol(),
            stock.market(),
            result.provider(),
            result.source(),
            result.trend(),
            result.aiScore(),
            result.bullishProbability(),
            result.riskLevel(),
            context.bullishReasons(),
            context.bearishRisks(),
            context.watchPoints(),
            result.summary() + " 模型輸出僅供研究，不代表投資建議。",
            context.evidence(),
            Instant.now()
        ));
    }

    @PostMapping("/ai/model-comparison")
    ApiResponse<ModelComparisonResponse> modelComparison(HttpServletRequest servletRequest, @RequestBody ModelComparisonRequest request) {
        String ownerEmail = requestGuard.requireUser(servletRequest, "ai-model-comparison", 8).email();
        StockRecord stock = stockService.get(request.market(), request.symbol());
        RagContext context = ragContextService.buildAnalysisContext(stock, request.horizonDays(), ownerEmail);
        List<String> providers = normalizeProviders(request.providers());
        List<AiProviderResult> results = new ArrayList<>();
        for (int i = 0; i < providers.size(); i++) {
            results.add(aiProviderAdapter.analyze(stock, context, providers.get(i), i));
        }
        BigDecimal size = new BigDecimal(results.size());
        BigDecimal consensusScore = results.stream()
            .map(item -> new BigDecimal(item.aiScore()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(size, 4, RoundingMode.HALF_UP);
        BigDecimal averageBullishProbability = results.stream()
            .map(AiProviderResult::bullishProbability)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(size, 4, RoundingMode.HALF_UP);
        BigDecimal spread = results.stream()
            .map(item -> new BigDecimal(item.aiScore()))
            .reduce(BigDecimal.ZERO, BigDecimal::max)
            .subtract(results.stream().map(item -> new BigDecimal(item.aiScore())).reduce(new BigDecimal("100"), BigDecimal::min));
        String divergenceLevel = spread.intValue() >= 12 ? "HIGH" : spread.intValue() >= 7 ? "MEDIUM" : "LOW";
        return ApiResponse.of(new ModelComparisonResponse(
            stock.symbol(),
            stock.market(),
            request.horizonDays(),
            consensusScore,
            consensusScore.intValue() >= 70 ? "偏多" : consensusScore.intValue() >= 60 ? "中性偏多" : "震盪",
            averageBullishProbability,
            divergenceLevel,
            divergenceLevel.equals("HIGH") ? "模型對短線動能分歧較大，需降低部位。" : "模型分歧仍在可控範圍。",
            results,
            Instant.now()
        ));
    }

    @PostMapping("/ai/chat")
    ApiResponse<AiChatResponse> chat(HttpServletRequest servletRequest, @RequestBody AiChatRequest request) {
        String ownerEmail = requestGuard.requireUser(servletRequest, "ai-chat", 30).email();
        String message = requireText(request.message(), "message", 2_000);
        StockRecord stock = stockService.get(request.market(), request.symbol());
        String provider = providerOrDefault(request.provider());
        RagContext context = ragContextService.buildChatContext(stock, 5, message, ownerEmail);
        AiChatResult result = aiProviderAdapter.chatMessage(stock, context, provider, message);
        return ApiResponse.of(new AiChatResponse(
            stock.market(),
            stock.symbol(),
            provider,
            result.source(),
            result.message(),
            context.evidence(),
            Instant.now()
        ));
    }

    @PostMapping({"/backtest", "/backtests"})
    ApiResponse<BacktestResponse> backtest(HttpServletRequest servletRequest, @RequestBody BacktestRequest request) {
        String ownerEmail = requestGuard.requireUser(servletRequest, "backtest", 10).email();
        StockRecord stock = stockService.get(request.market(), request.symbol());
        RagContext context = ragContextService.buildAnalysisContext(stock, 5, ownerEmail);
        BigDecimal edge = new BigDecimal(aiProviderAdapter.analyze(stock, context, "OPENAI", 0).aiScore())
            .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("0.60"));
        return ApiResponse.of(new BacktestResponse(
            stock.symbol(),
            stock.market(),
            new BigDecimal("0.148").add(edge.divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP)),
            clamp(new BigDecimal("0.58").add(edge.divide(new BigDecimal("4"), 4, RoundingMode.HALF_UP)), new BigDecimal("0.35"), new BigDecimal("0.72")),
            new BigDecimal("-0.098"),
            new BigDecimal("1.05").add(edge),
            42,
            "heuristic-simulation-v1",
            Instant.now()
        ));
    }

    @GetMapping("/watchlist")
    ApiResponse<List<StockRecord>> watchlist(HttpServletRequest request) {
        String email = requestGuard.requireUser(request, "watchlist-read", 60).email();
        List<StockRecord> data = watchlistService.list(email).stream()
            .map(item -> stockService.get(item.market(), item.symbol()))
            .toList();
        return ApiResponse.of(data);
    }

    @PostMapping("/watchlist")
    ApiResponse<StockRecord> addWatchlist(
        HttpServletRequest servletRequest,
        @RequestBody WatchlistItem request
    ) {
        String userEmail = requestGuard.requireUser(servletRequest, "watchlist-write", 30).email();
        StockRecord stock = stockService.get(request.market(), request.symbol());
        WatchlistItem item = new WatchlistItem(stock.market(), stock.symbol());
        watchlistService.add(userEmail, item);
        return ApiResponse.of(stock);
    }

    @DeleteMapping("/watchlist/{market}/{symbol}")
    ApiResponse<DeleteWatchlistResponse> deleteWatchlist(
        HttpServletRequest servletRequest,
        @PathVariable Market market,
        @PathVariable String symbol
    ) {
        String userEmail = requestGuard.requireUser(servletRequest, "watchlist-write", 30).email();
        String normalized = SymbolNormalizer.normalize(market, symbol);
        watchlistService.delete(userEmail, market, normalized);
        return ApiResponse.of(new DeleteWatchlistResponse(market, normalized, true));
    }

    private static String providerOrDefault(String provider) {
        String normalized = provider == null || provider.isBlank() ? "OPENAI" : provider.trim().toUpperCase();
        if (!ALLOWED_PROVIDERS.contains(normalized)) {
            throw new IllegalArgumentException("unsupported provider");
        }
        return normalized;
    }

    private static List<String> normalizeProviders(List<String> requested) {
        List<String> providers = requested == null || requested.isEmpty() ? DEFAULT_PROVIDERS : requested;
        if (providers.size() > 4) {
            throw new IllegalArgumentException("providers must not contain more than 4 items");
        }
        return providers.stream().map(MockFeatureController::providerOrDefault).distinct().toList();
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.max(min).min(max);
    }

    record AiRequest(Market market, String symbol, String provider, Integer horizonDays) {}
    record ModelComparisonRequest(Market market, String symbol, List<String> providers, Integer horizonDays) {}
    record AiChatRequest(Market market, String symbol, String provider, String message) {}
    record BacktestRequest(Market market, String symbol, Strategy strategy) {}
    record Strategy(BigDecimal minAiScore, BigDecimal minUpProbability, String maxRiskLevel) {}
    record DeleteWatchlistResponse(Market market, String symbol, boolean deleted) {}
    record AiAnalysisResponse(
        String symbol,
        Market market,
        String provider,
        String source,
        String trend,
        int aiScore,
        BigDecimal bullishProbability,
        String riskLevel,
        List<String> bullishReasons,
        List<String> bearishRisks,
        List<String> watchPoints,
        String conclusion,
        List<RetrievedDocument> evidence,
        Instant generatedAt
    ) {}
    record ModelComparisonResponse(
        String symbol,
        Market market,
        Integer horizonDays,
        BigDecimal consensusScore,
        String consensusTrend,
        BigDecimal averageBullishProbability,
        String divergenceLevel,
        String divergenceReason,
        List<AiProviderResult> results,
        Instant generatedAt
    ) {}
    record AiChatResponse(
        Market market,
        String symbol,
        String provider,
        String source,
        String message,
        List<RetrievedDocument> evidence,
        Instant generatedAt
    ) {}
    record BacktestResponse(
        String symbol,
        Market market,
        BigDecimal totalReturn,
        BigDecimal winRate,
        BigDecimal maxDrawdown,
        BigDecimal sharpeRatio,
        int tradeCount,
        String source,
        Instant generatedAt
    ) {}
}
