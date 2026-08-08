package com.example.stockai.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.example.stockai.market.Market;

record StockRecord(
    String symbol,
    String name,
    Market market,
    String currency,
    BigDecimal lastPrice,
    BigDecimal changePercent,
    List<BigDecimal> prices,
    String source,
    List<PriceBar> priceHistory,
    Instant observedAt,
    List<CorporateAction> corporateActions,
    String adjustmentStatus
) {
    StockRecord(
        String symbol,
        String name,
        Market market,
        String currency,
        BigDecimal lastPrice,
        BigDecimal changePercent,
        List<BigDecimal> prices,
        String source
    ) {
        this(symbol, name, market, currency, lastPrice, changePercent, prices, source, List.of(), null, List.of(), defaultAdjustmentStatus(source));
    }

    StockRecord(
        String symbol,
        String name,
        Market market,
        String currency,
        BigDecimal lastPrice,
        BigDecimal changePercent,
        List<BigDecimal> prices,
        String source,
        List<PriceBar> priceHistory
    ) {
        this(symbol, name, market, currency, lastPrice, changePercent, prices, source, priceHistory, null, List.of(), defaultAdjustmentStatus(source));
    }

    StockRecord(
        String symbol,
        String name,
        Market market,
        String currency,
        BigDecimal lastPrice,
        BigDecimal changePercent,
        List<BigDecimal> prices,
        String source,
        List<PriceBar> priceHistory,
        Instant observedAt
    ) {
        this(symbol, name, market, currency, lastPrice, changePercent, prices, source, priceHistory, observedAt, List.of(), defaultAdjustmentStatus(source));
    }

    StockRecord(
        String symbol,
        String name,
        Market market,
        String currency,
        BigDecimal lastPrice,
        BigDecimal changePercent,
        List<BigDecimal> prices,
        String source,
        List<PriceBar> priceHistory,
        Instant observedAt,
        List<CorporateAction> corporateActions
    ) {
        this(symbol, name, market, currency, lastPrice, changePercent, prices, source, priceHistory, observedAt, corporateActions, defaultAdjustmentStatus(source));
    }

    StockRecord {
        prices = prices == null ? List.of() : List.copyOf(prices);
        source = source == null ? "" : source.trim();
        priceHistory = priceHistory == null ? List.of() : List.copyOf(priceHistory);
        corporateActions = corporateActions == null ? List.of() : List.copyOf(corporateActions);
        adjustmentStatus = adjustmentStatus == null || adjustmentStatus.isBlank()
            ? defaultAdjustmentStatus(source)
            : adjustmentStatus.trim().toUpperCase();
    }

    String timezone() {
        return market == Market.TW ? "Asia/Taipei" : "America/New_York";
    }

    private static String defaultAdjustmentStatus(String source) {
        String normalized = source == null ? "" : source.trim().toLowerCase();
        if (normalized.startsWith("mock")) return "MOCK";
        if (normalized.contains("realtime")) return "SNAPSHOT_ONLY";
        return "UNVERIFIED";
    }
}
