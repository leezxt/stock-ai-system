package com.example.stockai.stock;

import java.util.List;

import com.example.stockai.market.Market;

interface MarketDataProvider {
    List<Market> markets();

    List<StockRecord> search(Market market, String query);

    StockRecord get(Market market, String symbol);
}
