package com.example.stockai.stock;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.example.stockai.market.Market;

@Service
@Primary
class FallbackMarketDataProvider implements MarketDataProvider {
    private final MockMarketDataProvider mockMarketDataProvider;
    private final TwseRealtimeMarketDataProvider twseRealtimeMarketDataProvider;
    private final FinMindTwMarketDataProvider finMindTwMarketDataProvider;
    private final YahooFinanceUsMarketDataProvider yahooFinanceUsMarketDataProvider;
    private final AlphaVantageMarketDataProvider alphaVantageMarketDataProvider;
    private final TwseMarketDataProvider twseMarketDataProvider;

    FallbackMarketDataProvider(
        MockMarketDataProvider mockMarketDataProvider,
        TwseRealtimeMarketDataProvider twseRealtimeMarketDataProvider,
        FinMindTwMarketDataProvider finMindTwMarketDataProvider,
        YahooFinanceUsMarketDataProvider yahooFinanceUsMarketDataProvider,
        AlphaVantageMarketDataProvider alphaVantageMarketDataProvider,
        TwseMarketDataProvider twseMarketDataProvider
    ) {
        this.mockMarketDataProvider = mockMarketDataProvider;
        this.twseRealtimeMarketDataProvider = twseRealtimeMarketDataProvider;
        this.finMindTwMarketDataProvider = finMindTwMarketDataProvider;
        this.yahooFinanceUsMarketDataProvider = yahooFinanceUsMarketDataProvider;
        this.alphaVantageMarketDataProvider = alphaVantageMarketDataProvider;
        this.twseMarketDataProvider = twseMarketDataProvider;
    }

    @Override
    public List<Market> markets() {
        return mockMarketDataProvider.markets();
    }

    @Override
    public List<StockRecord> search(Market market, String query) {
        if (query == null || query.isBlank()) {
            return mockMarketDataProvider.search(market, query);
        }
        if (market == Market.TW) {
            List<StockRecord> realtimeResults = twseRealtimeMarketDataProvider.trySearch(market, query).orElse(List.of());
            if (!realtimeResults.isEmpty()) {
                return realtimeResults;
            }
            List<StockRecord> finMindResults = finMindTwMarketDataProvider.trySearch(market, query).orElse(List.of());
            if (!finMindResults.isEmpty()) {
                return finMindResults;
            }
            List<StockRecord> twResults = twseMarketDataProvider.trySearch(market, query).orElse(List.of());
            if (!twResults.isEmpty()) {
                return twResults;
            }
        }
        List<StockRecord> results = mockMarketDataProvider.search(market, query);
        if (!results.isEmpty()) {
            return results;
        }
        if (market == Market.US) {
            List<StockRecord> yahooResults = yahooFinanceUsMarketDataProvider.trySearch(market, query).orElse(List.of());
            if (!yahooResults.isEmpty()) {
                return yahooResults;
            }
        }
        if (market != Market.US) {
            return List.of(mockMarketDataProvider.get(market, query));
        }
        return alphaVantageMarketDataProvider.tryGet(market, query.trim().toUpperCase(), null)
            .map(List::of)
            .orElse(List.of(mockMarketDataProvider.get(market, query)));
    }

    @Override
    public StockRecord get(Market market, String symbol) {
        StockRecord fallback = null;
        try {
            fallback = mockMarketDataProvider.get(market, symbol);
        } catch (IllegalArgumentException ignored) {
        }
        StockRecord realtime = twseRealtimeMarketDataProvider.tryGet(market, symbol, fallback).orElse(null);
        if (realtime != null) {
            return realtime;
        }
        StockRecord finMindReal = finMindTwMarketDataProvider.tryGet(market, symbol, fallback).orElse(null);
        if (finMindReal != null) {
            return finMindReal;
        }
        StockRecord twReal = twseMarketDataProvider.tryGet(market, symbol, fallback).orElse(null);
        if (twReal != null) {
            return twReal;
        }
        StockRecord yahooReal = yahooFinanceUsMarketDataProvider.tryGet(market, symbol, fallback).orElse(null);
        if (yahooReal != null) {
            return yahooReal;
        }
        StockRecord real = alphaVantageMarketDataProvider.tryGet(market, symbol.toUpperCase(), fallback).orElse(null);
        if (real != null) {
            return real;
        }
        if (fallback != null) {
            return fallback;
        }
        return mockMarketDataProvider.get(market, symbol);
    }
}
