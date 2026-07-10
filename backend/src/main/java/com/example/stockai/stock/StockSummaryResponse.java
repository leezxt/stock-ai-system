package com.example.stockai.stock;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.stockai.market.Market;

public record StockSummaryResponse(
    String symbol,
    String name,
    Market market,
    String currency,
    String timezone,
    String source,
    PriceSnapshot priceSnapshot
) {
    public record PriceSnapshot(
        String symbol,
        Market market,
        BigDecimal lastPrice,
        BigDecimal changePercent,
        String currency,
        Instant updatedAt
    ) {}
}
