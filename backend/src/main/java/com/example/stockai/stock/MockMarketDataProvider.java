package com.example.stockai.stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.stockai.market.Market;
import com.example.stockai.market.SymbolNormalizer;

@Service
class MockMarketDataProvider implements MarketDataProvider {
    private final Map<Market, Map<String, StockRecord>> stocks = Map.of(
        Market.US, Map.of(
            "AAPL", stock("AAPL", "Apple Inc.", Market.US, "USD", "217.63", "2.14", "204", "207", "206", "211", "210", "214", "216", "215", "218"),
            "NVDA", stock("NVDA", "NVIDIA Corp.", Market.US, "USD", "164.29", "3.72", "144", "148", "151", "150", "156", "158", "162", "160", "164"),
            "TSLA", stock("TSLA", "Tesla Inc.", Market.US, "USD", "318.76", "-1.86", "334", "329", "326", "322", "319", "321", "317", "320", "319")
        ),
        Market.TW, Map.of(
            "2330.TW", stock("2330.TW", "台積電", Market.TW, "TWD", "1035", "1.47", "965", "972", "988", "996", "1008", "1015", "1022", "1018", "1035"),
            "2454.TW", stock("2454.TW", "聯發科", Market.TW, "TWD", "1320", "-0.75", "1365", "1350", "1342", "1330", "1328", "1316", "1322", "1310", "1320"),
            "2317.TW", stock("2317.TW", "鴻海", Market.TW, "TWD", "184.5", "0.82", "176", "178", "181", "180", "182", "183", "185", "184", "184.5")
        )
    );

    @Override
    public List<Market> markets() {
        return List.of(Market.US, Market.TW);
    }

    @Override
    public List<StockRecord> search(Market market, String query) {
        String q = query == null ? "" : query.trim().toUpperCase();
        List<StockRecord> results = stocks.getOrDefault(market, Map.of()).values().stream()
            .filter(stock -> stock.symbol().contains(q) || stock.name().contains(q))
            .toList();
        return results;
    }

    @Override
    public StockRecord get(Market market, String symbol) {
        String normalized = SymbolNormalizer.normalize(market, symbol);
        StockRecord stock = stocks.getOrDefault(market, Map.of()).get(normalized);
        return stock != null ? stock : generatedStock(market, normalized);
    }

    private static StockRecord generatedStock(Market market, String symbol) {
        String normalized = SymbolNormalizer.normalize(market, symbol);
        int seed = Math.abs((market.name() + ":" + normalized).hashCode());
        BigDecimal basePrice = market == Market.TW
            ? BigDecimal.valueOf(40 + (seed % 1400))
            : BigDecimal.valueOf(20 + (seed % 480));
        BigDecimal changePercent = BigDecimal.valueOf((seed % 900) - 450L)
            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        List<BigDecimal> prices = java.util.stream.IntStream.rangeClosed(0, 8)
            .mapToObj(i -> basePrice
                .add(BigDecimal.valueOf(i - 4L).multiply(BigDecimal.valueOf((seed % 7) + 1L)))
                .add(BigDecimal.valueOf(((seed / (i + 1)) % 5) - 2L)))
            .toList();
        BigDecimal lastPrice = prices.get(prices.size() - 1);
        return new StockRecord(
            normalized,
            generatedName(market, normalized),
            market,
            market == Market.TW ? "TWD" : "USD",
            lastPrice,
            changePercent,
            prices,
            "mock-generated"
        );
    }

    private static String generatedName(Market market, String symbol) {
        return market == Market.TW ? "TW " + symbol : symbol + " Corp.";
    }

    private static StockRecord stock(String symbol, String name, Market market, String currency, String lastPrice, String changePercent, String... prices) {
        return new StockRecord(
            symbol,
            name,
            market,
            currency,
            new BigDecimal(lastPrice),
            new BigDecimal(changePercent),
            java.util.Arrays.stream(prices).map(BigDecimal::new).toList(),
            "mock"
        );
    }
}
