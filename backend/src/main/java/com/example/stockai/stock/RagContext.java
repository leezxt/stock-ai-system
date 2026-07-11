package com.example.stockai.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.example.stockai.rag.RetrievedDocument;

record RagContext(
    StockRecord stock,
    TechnicalSummaryResponse technicalSummary,
    PredictionResponse prediction,
    List<RetrievedDocument> evidence
) {
    RagContext {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    List<String> bullishReasons() {
        List<String> reasons = new ArrayList<>();
        if (stock.lastPrice().compareTo(technicalSummary.ma20()) >= 0) {
            reasons.add("股價仍在 MA20 之上，短線結構未破壞");
        }
        reasons.add("模型預測 " + prediction.horizonDays() + " 日上漲機率 " + percent(prediction.upProbability()));
        if (!evidence.isEmpty()) {
            RetrievedDocument top = evidence.get(0);
            reasons.add("文件證據聚焦 " + top.title() + " / " + top.source());
        }
        return reasons;
    }

    List<String> bearishRisks() {
        List<String> risks = new ArrayList<>();
        if (technicalSummary.rsi14().compareTo(new BigDecimal("70")) >= 0) {
            risks.add("RSI 偏高，短線容易出現過熱拉回");
        } else {
            risks.add("波動仍需留意，ATR14 約 " + technicalSummary.atr14().setScale(2, RoundingMode.HALF_UP));
        }
        risks.add("模型風險等級為 " + prediction.riskLevel());
        if (evidence.isEmpty()) {
            risks.add("目前尚未匯入新聞或財報證據，消息面仍有盲區");
        }
        return risks;
    }

    List<String> watchPoints() {
        List<String> points = new ArrayList<>();
        points.add("MACD 訊號：" + technicalSummary.macdSignal());
        points.add("關注 MA20 " + technicalSummary.ma20().setScale(2, RoundingMode.HALF_UP) + " 是否失守");
        points.add(evidence.isEmpty() ? "補齊新聞、財報、公告後可提高 RAG 判讀品質" : "最新文件筆數：" + evidence.size());
        return points;
    }

    String evidenceSummary() {
        if (evidence.isEmpty()) {
            return "目前無文件證據，先以技術面與模型預測為主。";
        }
        RetrievedDocument top = evidence.get(0);
        return "已檢索 " + evidence.size() + " 筆文件，最高相關證據為 " + top.title() + "（" + top.source() + "）。";
    }

    String analysisPrompt() {
        return """
            股票資料:
            - symbol=%s
            - market=%s
            - currency=%s
            - lastPrice=%s
            - changePercent=%s
            - recentPrices=%s

            技術面:
            - ma5=%s
            - ma20=%s
            - ma60=%s
            - rsi14=%s
            - macdSignal=%s
            - atr14=%s

            模型預測:
            - horizonDays=%s
            - upProbability=%s
            - expectedReturn=%s
            - volatility=%s
            - riskLevel=%s
            - modelVersion=%s

            檢索證據:
            %s
            """.formatted(
            stock.symbol(),
            stock.market(),
            stock.currency(),
            stock.lastPrice(),
            stock.changePercent(),
            stock.prices(),
            technicalSummary.ma5(),
            technicalSummary.ma20(),
            technicalSummary.ma60(),
            technicalSummary.rsi14(),
            technicalSummary.macdSignal(),
            technicalSummary.atr14(),
            prediction.horizonDays(),
            prediction.upProbability(),
            prediction.expectedReturn(),
            prediction.volatility(),
            prediction.riskLevel(),
            prediction.modelVersion(),
            evidenceBlock()
        );
    }

    String chatPrompt(String message) {
        return analysisPrompt() + System.lineSeparator()
            + "問答規則: 使用者可以詢問目前股票或其他一般問題。若問題與股票無關，直接回答一般知識，"
            + "不要硬套用股票資料；若涉及即時數值但上下文沒有可靠資料，必須清楚說明資料限制，不得編造。"
            + System.lineSeparator() + "使用者問題: " + message;
    }

    private String evidenceBlock() {
        if (evidence.isEmpty()) {
            return "- 無可用文件證據";
        }
        return evidence.stream()
            .map(item -> "- [" + item.docType() + "] " + item.title() + " / " + item.source() + " / " + item.snippet())
            .reduce((left, right) -> left + System.lineSeparator() + right)
            .orElse("- 無可用文件證據");
    }

    private static String percent(BigDecimal value) {
        return value.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) + "%";
    }
}
