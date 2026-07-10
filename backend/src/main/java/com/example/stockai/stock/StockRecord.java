package com.example.stockai.stock;

import java.math.BigDecimal;
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
    String source
) {
    String timezone() {
        return market == Market.TW ? "Asia/Taipei" : "America/New_York";
    }
}
