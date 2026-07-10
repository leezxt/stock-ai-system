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
        return new AiChatResult(
            "根據 " + stock.symbol() + " 目前資料：" + message + "。"
                + context.evidenceSummary()
                + " 重點是價格是否守住 MA20、波動是否擴大，以及模型分歧是否升高。此回覆僅供研究，不代表投資建議。",
            source
        );
    }
}
