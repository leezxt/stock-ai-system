package com.example.stockai.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import com.example.stockai.rag.DocumentType;
import com.example.stockai.rag.RetrievedDocument;

/**
 * Routes a question to deterministic backend facts before an LLM is called.
 * It is intentionally a small, auditable classifier rather than a second AI
 * model, so unsupported questions fail closed and source provenance remains
 * visible in both real and fallback modes.
 */
final class ChatToolRouter {
    private static final List<String> OUT_OF_SCOPE_MARKERS = List.of(
        "天氣", "weather", "食譜", "recipe", "歌詞", "lyrics", "寫程式", "程式碼", "code", "program", "debug", "翻譯", "translate", "旅遊", "travel", "星座", "horoscope"
    );

    private ChatToolRouter() {}

    static ChatToolPlan plan(RagContext context, String message) {
        String question = message == null ? "" : message.trim();
        if (question.isBlank()) {
            return new ChatToolPlan("UNKNOWN", "OUT_OF_SCOPE", "問題不可為空白。", List.of());
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        if (OUT_OF_SCOPE_MARKERS.stream().anyMatch(normalized::contains)) {
            return new ChatToolPlan(
                "OUT_OF_SCOPE",
                "OUT_OF_SCOPE",
                "目前只支援股票價格、技術面、預測、風險、新聞、財報與回測研究。",
                List.of()
            );
        }

        Set<String> intents = new LinkedHashSet<>();
        addIfMatches(intents, "PRICE", normalized, "價格", "股價", "收盤", "行情", "報價", "漲跌", "即時", "price", "quote", "close", "market");
        addIfMatches(intents, "TECHNICAL", normalized, "技術", "均線", "ma5", "ma20", "ma60", "rsi", "macd", "atr", "支撐", "壓力", "突破", "technical", "moving average", "support", "resistance");
        addIfMatches(intents, "PREDICTION", normalized, "預測", "上漲", "下跌", "機率", "未來", "走勢", "動能", "適合", "追", "買", "賣", "prediction", "forecast", "probability", "future", "trend", "momentum");
        addIfMatches(intents, "RISK", normalized, "風險", "停損", "部位", "曝險", "波動", "risk", "stop loss", "position", "exposure", "volatility");
        addIfMatches(intents, "NEWS", normalized, "新聞", "消息", "公告", "法說", "輿情", "事件", "供應鏈", "news", "announcement", "event", "sentiment", "supply chain");
        addIfMatches(intents, "FUNDAMENTALS", normalized, "財報", "營收", "毛利", "獲利", "eps", "本益比", "估值", "基本面", "financial", "revenue", "margin", "earnings", "valuation", "fundamental");
        addIfMatches(intents, "BACKTEST", normalized, "回測", "策略", "績效", "勝率", "回撤", "sharpe", "backtest", "strategy", "performance", "drawdown");
        if (intents.isEmpty()) {
            intents.add("GENERAL_RESEARCH");
        }

        List<AiToolCall> tools = new ArrayList<>();
        tools.add(priceTool(context));
        if (containsAny(intents, "TECHNICAL", "RISK", "GENERAL_RESEARCH")) {
            tools.add(technicalTool(context));
        }
        if (containsAny(intents, "PREDICTION", "RISK", "GENERAL_RESEARCH")) {
            tools.add(predictionTool(context));
        }
        if (intents.contains("NEWS")) {
            tools.add(documentTool(context, "news_documents", "新聞／公告證據", document ->
                document.docType() == DocumentType.NEWS || document.docType() == DocumentType.COMPANY_ANNOUNCEMENT));
        }
        if (intents.contains("FUNDAMENTALS")) {
            tools.add(documentTool(context, "financial_documents", "財報／研究報告證據", document ->
                document.docType() == DocumentType.FINANCIAL_REPORT || document.docType() == DocumentType.RESEARCH_REPORT));
        }
        if (intents.contains("GENERAL_RESEARCH") && !context.evidence().isEmpty()) {
            tools.add(documentTool(context, "rag_documents", "相關文件證據", document -> true));
        }
        if (intents.contains("BACKTEST")) {
            tools.add(new AiToolCall(
                "backtest_guidance",
                "NEEDS_PARAMETERS",
                "回測需要明確的策略門檻、持有天數、交易成本與停利／停損條件；請使用策略回測頁或在問題中提供這些條件。",
                "historical-price-replay-v1",
                Instant.now(),
                List.of()
            ));
        }

        String answerStatus = tools.stream().anyMatch(tool -> "INSUFFICIENT_DATA".equals(tool.status()))
            ? tools.stream().anyMatch(tool -> "READY".equals(tool.status())) ? "PARTIAL" : "INSUFFICIENT_DATA"
            : tools.stream().anyMatch(tool -> "NEEDS_PARAMETERS".equals(tool.status())) ? "PARTIAL" : "READY";
        return new ChatToolPlan(
            String.join(",", intents),
            answerStatus,
            "工具結果是 backend 依目前標的與帳號可見資料查詢的事實；文件內容仍須以引用與發布時間核對。",
            tools
        );
    }

    private static AiToolCall priceTool(RagContext context) {
        StockRecord stock = context.stock();
        String summary = "最新價=" + value(stock.lastPrice()) + "，漲跌=" + value(stock.changePercent()) + "%";
        return new AiToolCall("market_quote", "READY", summary, stock.source(), stock.observedAt(), List.of());
    }

    private static AiToolCall technicalTool(RagContext context) {
        TechnicalSummaryResponse technical = context.technicalSummary();
        MarketDataQuality quality = technical.dataQuality();
        String summary = "MA5=" + metric(quality, "MA5", technical.ma5())
            + "，MA20=" + metric(quality, "MA20", technical.ma20())
            + "，MA60=" + metric(quality, "MA60", technical.ma60())
            + "，RSI14=" + metric(quality, "RSI14", technical.rsi14())
            + "，MACD=" + metric(quality, "MACD", technical.macdSignal())
            + "，ATR14=" + metric(quality, "ATR14", technical.atr14())
            + "；品質=" + quality.status() + "（" + quality.sampleCount() + "/" + quality.requiredSampleCount() + "）";
        String status = quality.status().equals("UNKNOWN") || quality.sampleCount() == 0 ? "INSUFFICIENT_DATA" : "READY";
        return new AiToolCall("technical_indicators", status, summary, quality.source(), technical.updatedAt(), List.of());
    }

    private static AiToolCall predictionTool(RagContext context) {
        PredictionResponse prediction = context.prediction();
        MarketDataQuality quality = prediction.dataQuality();
        String summary = "模型=" + prediction.modelVersion()
            + "，預測=" + prediction.horizonDays() + "日"
            + "，上漲機率=" + metric(quality, "UP_PROBABILITY", percent(prediction.upProbability()))
            + "，預期報酬=" + metric(quality, "EXPECTED_RETURN", percent(prediction.expectedReturn()))
            + "，波動=" + metric(quality, "VOLATILITY", percent(prediction.volatility()))
            + "，風險=" + metric(quality, "RISK_LEVEL", prediction.riskLevel())
            + "；品質=" + quality.status() + "（" + quality.sampleCount() + "/" + quality.requiredSampleCount() + "）";
        String status = quality.status().equals("UNKNOWN") || quality.sampleCount() == 0 ? "INSUFFICIENT_DATA" : "READY";
        return new AiToolCall("prediction_model", status, summary, quality.source(), prediction.generatedAt(), List.of());
    }

    private static AiToolCall documentTool(RagContext context, String name, String label, Predicate<RetrievedDocument> filter) {
        List<RetrievedDocument> documents = context.evidence().stream().filter(filter).toList();
        if (documents.isEmpty()) {
            return new AiToolCall(name, "INSUFFICIENT_DATA", "沒有符合條件且目前帳號可見的文件證據。", "rag", null, List.of());
        }
        String summary = label + "=" + documents.size() + " 筆；最高相關文件=" + documents.get(0).title()
            + "（" + documents.get(0).source() + "，發布=" + documents.get(0).publishedAt() + "）";
        return new AiToolCall(name, "READY", summary, "rag", documents.get(0).publishedAt(), documents.stream().map(RetrievedDocument::chunkId).toList());
    }

    private static void addIfMatches(Set<String> intents, String intent, String question, String... markers) {
        if (containsAny(question, markers)) {
            intents.add(intent);
        }
    }

    private static boolean containsAny(Set<String> values, String... expected) {
        for (String item : expected) {
            if (values.contains(item)) return true;
        }
        return false;
    }

    private static boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String metric(MarketDataQuality quality, String name, Object value) {
        return quality.unavailableIndicators().contains(name) ? "UNAVAILABLE" : value(value);
    }

    private static String value(Object value) {
        return value == null ? "UNAVAILABLE" : String.valueOf(value);
    }

    private static String percent(BigDecimal value) {
        return value == null ? "UNAVAILABLE" : value.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%";
    }
}
