package com.example.stockai.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
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
    private final HistoricalBacktestEngine historicalBacktestEngine;

    public MockFeatureController(
        AiProviderAdapter aiProviderAdapter,
        RagContextService ragContextService,
        StockService stockService,
        WatchlistService watchlistService,
        RequestGuard requestGuard
    ) {
        this(aiProviderAdapter, ragContextService, stockService, watchlistService, requestGuard, new HistoricalBacktestEngine());
    }

    @Autowired
    public MockFeatureController(
        AiProviderAdapter aiProviderAdapter,
        RagContextService ragContextService,
        StockService stockService,
        WatchlistService watchlistService,
        RequestGuard requestGuard,
        HistoricalBacktestEngine historicalBacktestEngine
    ) {
        this.aiProviderAdapter = aiProviderAdapter;
        this.ragContextService = ragContextService;
        this.stockService = stockService;
        this.watchlistService = watchlistService;
        this.requestGuard = requestGuard;
        this.historicalBacktestEngine = historicalBacktestEngine;
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
        ChatToolPlan toolPlan = context.chatToolPlan();
        AiChatResult result = toolPlan.isOutOfScope()
            ? new AiChatResult(toolPlan.scopeNote(), "unavailable-ai:out-of-scope")
            : aiProviderAdapter.chatMessage(stock, context, provider, message, request.history());
        return ApiResponse.of(new AiChatResponse(
            stock.market(),
            stock.symbol(),
            provider,
            result.source(),
            result.message(),
            context.evidence(),
            result.citationIds(),
            Instant.now(),
            toolPlan.intent(),
            toolPlan.answerStatus(),
            toolPlan.tools()
        ));
    }

    @PostMapping({"/backtest", "/backtests"})
    ApiResponse<BacktestResponse> backtest(HttpServletRequest servletRequest, @RequestBody BacktestRequest request) {
        requestGuard.requireUser(servletRequest, "backtest", 10);
        StockRecord stock = stockService.get(request.market(), request.symbol());
        Strategy strategy = normalizeStrategy(request.strategy());
        BacktestEvaluation evaluation = historicalBacktestEngine.evaluate(stock, strategy);
        List<String> priceSources = priceSources(stock);
        return ApiResponse.of(new BacktestResponse(
            stock.symbol(),
            stock.market(),
            evaluation.totalReturn(),
            evaluation.winRate(),
            evaluation.maxDrawdown(),
            evaluation.sharpeRatio(),
            evaluation.completedTradeCount(),
            conditionDetails(strategy, evaluation.signalModel(), priceSources),
            evaluation.status(),
            evaluation.signalModel(),
            evaluation.dataPointCount(),
            evaluation.note(),
            evaluation.trades(),
            priceSources,
            evaluation.source(),
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

    private static Strategy normalizeStrategy(Strategy requested) {
        if (requested == null) {
            return new Strategy(new BigDecimal("70"), new BigDecimal("0.60"), "MEDIUM", 5, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal minAiScore = requested.minAiScore() == null ? new BigDecimal("70") : requested.minAiScore();
        BigDecimal minUpProbability = requested.minUpProbability() == null ? new BigDecimal("0.60") : requested.minUpProbability();
        if (minAiScore.compareTo(BigDecimal.ZERO) < 0 || minAiScore.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("strategy.minAiScore must be between 0 and 100");
        }
        if (minUpProbability.compareTo(BigDecimal.ZERO) < 0 || minUpProbability.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("strategy.minUpProbability must be between 0 and 1");
        }
        String maxRiskLevel = requested.maxRiskLevel() == null || requested.maxRiskLevel().isBlank()
            ? "MEDIUM"
            : requested.maxRiskLevel().trim().toUpperCase(Locale.ROOT);
        if (!List.of("LOW", "MEDIUM", "HIGH").contains(maxRiskLevel)) {
            throw new IllegalArgumentException("strategy.maxRiskLevel must be LOW, MEDIUM, or HIGH");
        }
        int holdingDays = requested.holdingDays() == null ? 5 : requested.holdingDays();
        if (holdingDays < 1 || holdingDays > 60) {
            throw new IllegalArgumentException("strategy.holdingDays must be between 1 and 60");
        }
        BigDecimal commissionRate = nonNegativeRate(requested.commissionRate(), "commissionRate");
        BigDecimal taxRate = nonNegativeRate(requested.taxRate(), "taxRate");
        BigDecimal slippageRate = nonNegativeRate(requested.slippageRate(), "slippageRate");
        BigDecimal takeProfitRate = nonNegativeRate(requested.takeProfitRate(), "takeProfitRate");
        BigDecimal stopLossRate = nonNegativeRate(requested.stopLossRate(), "stopLossRate");
        if (takeProfitRate.compareTo(new BigDecimal("2")) > 0 || stopLossRate.compareTo(new BigDecimal("2")) > 0) {
            throw new IllegalArgumentException("strategy takeProfitRate and stopLossRate must be between 0 and 2");
        }
        if (commissionRate.compareTo(new BigDecimal("0.05")) > 0 || taxRate.compareTo(new BigDecimal("0.05")) > 0 || slippageRate.compareTo(new BigDecimal("0.05")) > 0) {
            throw new IllegalArgumentException("strategy cost rates must be between 0 and 0.05");
        }
        return new Strategy(minAiScore, minUpProbability, maxRiskLevel, holdingDays, commissionRate, taxRate, slippageRate, takeProfitRate, stopLossRate);
    }

    private static BigDecimal nonNegativeRate(BigDecimal value, String field) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException("strategy." + field + " must not be negative");
        }
        return normalized;
    }

    private static List<String> priceSources(StockRecord stock) {
        List<String> sources = stock.priceHistory().stream()
            .map(PriceBar::source)
            .filter(source -> source != null && !source.isBlank())
            .distinct()
            .toList();
        if (!sources.isEmpty()) {
            return sources;
        }
        if (stock.source() != null && !stock.source().isBlank()) {
            return List.of(stock.source());
        }
        return List.of("unknown");
    }

    private static BacktestConditions conditionDetails(Strategy strategy, String signalModel, List<String> priceSources) {
        String minScore = strategy.minAiScore().stripTrailingZeros().toPlainString();
        String minProbability = strategy.minUpProbability().stripTrailingZeros().toPlainString();
        String commission = strategy.commissionRate().stripTrailingZeros().toPlainString();
        String tax = strategy.taxRate().stripTrailingZeros().toPlainString();
        String slippage = strategy.slippageRate().stripTrailingZeros().toPlainString();
        String takeProfit = strategy.takeProfitRate().signum() == 0 ? "停用" : strategy.takeProfitRate().stripTrailingZeros().toPlainString();
        String stopLoss = strategy.stopLossRate().signum() == 0 ? "停用" : strategy.stopLossRate().stripTrailingZeros().toPlainString();
        return new BacktestConditions(
            strategy.holdingDays(),
            signalModel,
            strategy.minAiScore(),
            strategy.minUpProbability(),
            strategy.maxRiskLevel(),
            "technical signal score (proxy) >= " + minScore + " AND upProbability >= " + minProbability + " AND risk <= " + strategy.maxRiskLevel(),
            "固定持有 " + strategy.holdingDays() + " 個交易日；停利=" + takeProfit + "、停損=" + stopLoss + "；未設定反向訊號出場。",
            "單一標的、單次訊號名義部位；未進行資金再平衡。",
            "單邊手續費率=" + commission + "、賣出稅率=" + tax + "、單邊滑價率=" + slippage + "；未估計流動性衝擊。",
            "priceSources=" + String.join(",", priceSources) + "；使用目前 provider 可用的有日期收盤價與技術動能代理訊號，不呼叫現在日期的 AI，也不使用未來資料。"
        );
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

    record AiRequest(Market market, String symbol, String provider, Integer horizonDays) {}
    record ModelComparisonRequest(Market market, String symbol, List<String> providers, Integer horizonDays) {}
    record AiChatRequest(Market market, String symbol, String provider, String message, List<ChatTurn> history) {
        AiChatRequest(Market market, String symbol, String provider, String message) {
            this(market, symbol, provider, message, List.of());
        }

        AiChatRequest {
            history = ChatHistory.normalize(history);
        }
    }
    record BacktestRequest(Market market, String symbol, Strategy strategy) {}
    record Strategy(
        BigDecimal minAiScore,
        BigDecimal minUpProbability,
        String maxRiskLevel,
        Integer holdingDays,
        BigDecimal commissionRate,
        BigDecimal taxRate,
        BigDecimal slippageRate,
        BigDecimal takeProfitRate,
        BigDecimal stopLossRate
    ) {
        Strategy(BigDecimal minAiScore, BigDecimal minUpProbability, String maxRiskLevel) {
            this(minAiScore, minUpProbability, maxRiskLevel, null, null, null, null, null, null);
        }
    }
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
        List<String> citationIds,
        Instant generatedAt,
        String intent,
        String answerStatus,
        List<AiToolCall> tools
    ) {}
    record BacktestResponse(
        String symbol,
        Market market,
        BigDecimal totalReturn,
        BigDecimal winRate,
        BigDecimal maxDrawdown,
        BigDecimal sharpeRatio,
        int tradeCount,
        BacktestConditions conditions,
        String status,
        String signalModel,
        int dataPointCount,
        String note,
        List<BacktestTrade> trades,
        List<String> priceSources,
        String source,
        Instant generatedAt
    ) {}
    record BacktestConditions(
        int horizonDays,
        String signalModel,
        BigDecimal minAiScore,
        BigDecimal minUpProbability,
        String maxRiskLevel,
        String entryRule,
        String exitRule,
        String positionSizing,
        String costModel,
        String dataScope
    ) {}
}
