package com.example.stockai.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

@Service
class MockAiProviderAdapter implements AiProviderAdapter {
    @Override
    public AiProviderResult analyze(StockRecord stock, RagContext context, String provider, int index) {
        return analyzeWithSource(stock, context, provider, index, "mock-ai");
    }

    AiProviderResult analyzeWithSource(StockRecord stock, RagContext context, String provider, int index, String source) {
        int base = 66 + stock.symbol().length() % 8 + index * 2;
        int providerPenalty = "DEEPSEEK".equalsIgnoreCase(provider) ? 5 : 0;
        int score = Math.max(35, Math.min(90, base - providerPenalty));
        BigDecimal bullishProbability = new BigDecimal(score)
            .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("0.06"))
            .add(new BigDecimal(index).multiply(new BigDecimal("0.01")));
        String riskLevel = score < 62 || ("DEEPSEEK".equalsIgnoreCase(provider) && stock.changePercent().signum() < 0) ? "HIGH" : "MEDIUM";
        return new AiProviderResult(
            provider.toUpperCase(),
            score > 72 ? "偏多" : score > 62 ? "中性偏多" : "震盪",
            score,
            bullishProbability,
            riskLevel,
            "趨勢仍有支撐，需觀察量能延續。" + context.evidenceSummary(),
            source
        );
    }

    @Override
    public AiChatResult chatMessage(StockRecord stock, RagContext context, String provider, String message) {
        return chatMessageWithSource(stock, context, provider, message, "mock-ai");
    }

    AiChatResult chatMessageWithSource(StockRecord stock, RagContext context, String provider, String message, String source) {
        String question = message == null ? "" : message.trim();
        TechnicalSummaryResponse technical = context.technicalSummary();
        PredictionResponse prediction = context.prediction();
        String ma20 = technical.dataQuality().unavailableIndicators().contains("MA20")
            ? "資料不足"
            : technical.ma20().toString();
        String rsi14 = technical.dataQuality().unavailableIndicators().contains("RSI14")
            ? "資料不足"
            : technical.rsi14().toString();
        String upProbability = prediction.dataQuality().unavailableIndicators().contains("UP_PROBABILITY")
            ? "資料不足"
            : prediction.upProbability().toString();
        String riskLevel = prediction.dataQuality().unavailableIndicators().contains("RISK_LEVEL")
            ? "資料不足"
            : prediction.riskLevel();
        return new AiChatResult(
            "你問的是：「" + question + "」。目前問答不限制快捷問題，會先依問題意圖使用可用資料。"
                + "目前 " + stock.symbol() + " 最新價 " + stock.lastPrice()
                + "，漲跌 " + stock.changePercent() + "%；MA20 為 " + ma20
                + "，RSI14 為 " + rsi14 + "，模型 " + prediction.horizonDays()
                + " 日上漲機率為 " + upProbability + "，風險等級為 " + riskLevel + "。"
                + " 行情資料狀態為 " + technical.dataQuality().status() + "，實際樣本 "
                + technical.dataQuality().sampleCount() + " 筆（" + technical.dataQuality().source() + "）。"
                + " " + context.evidenceSummary()
                + " 工具意圖為 " + context.chatToolPlan().intent() + "，工具狀態為 " + context.chatToolPlan().answerStatus() + "。"
                + " 目前為 " + source + " 降級回覆，未使用即時模型；若要取得針對任意問題的生成式分析，請設定並驗證所選 AI provider。"
                + "此回覆僅供研究，不代表投資建議。",
            source
        );
    }
}
