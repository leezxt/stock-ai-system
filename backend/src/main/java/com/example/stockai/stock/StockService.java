package com.example.stockai.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.stockai.market.Market;

@Service
public class StockService {
    private final MarketDataProvider marketDataProvider;
    private final StockSnapshotStore stockSnapshotStore;

    public StockService(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
        this.stockSnapshotStore = null;
    }

    @Autowired
    public StockService(MarketDataProvider marketDataProvider, Optional<StockSnapshotStore> stockSnapshotStore) {
        this(marketDataProvider, stockSnapshotStore.orElse(null));
    }

    private StockService(MarketDataProvider marketDataProvider, StockSnapshotStore stockSnapshotStore) {
        this.marketDataProvider = marketDataProvider;
        this.stockSnapshotStore = stockSnapshotStore;
    }

    List<Market> markets() {
        return marketDataProvider.markets();
    }

    List<StockRecord> search(Market market, String query) {
        return marketDataProvider.search(market, query);
    }

    StockRecord get(Market market, String symbol) {
        StockRecord stock = marketDataProvider.get(market, symbol);
        if (stockSnapshotStore != null) {
            stockSnapshotStore.save(stock);
        }
        return stock;
    }

    StockSummaryResponse summary(Market market, String symbol) {
        StockRecord stock = get(market, symbol);
        return new StockSummaryResponse(
            stock.symbol(),
            stock.name(),
            stock.market(),
            stock.currency(),
            stock.timezone(),
            stock.source(),
            new StockSummaryResponse.PriceSnapshot(stock.symbol(), stock.market(), stock.lastPrice(), stock.changePercent(), stock.currency(), Instant.now())
        );
    }

    PriceHistoryResponse prices(Market market, String symbol) {
        StockRecord stock = get(market, symbol);
        List<BigDecimal> prices = oneMonthTradingPrices(stock.prices());
        List<LocalDate> dates = recentTradingDates(prices.size(), ZoneId.of(stock.timezone()));
        List<PriceHistoryResponse.PricePoint> points = IntStream.range(0, prices.size())
            .mapToObj(i -> new PriceHistoryResponse.PricePoint(dates.get(i).toString(), prices.get(i)))
            .toList();
        return new PriceHistoryResponse(points);
    }

    TechnicalSummaryResponse technicalSummary(Market market, String symbol) {
        StockRecord stock = get(market, symbol);
        return new TechnicalSummaryResponse(
            stock.symbol(),
            stock.market(),
            stock.lastPrice().multiply(new BigDecimal("0.99")),
            stock.lastPrice().multiply(new BigDecimal("0.965")),
            stock.lastPrice().multiply(new BigDecimal("0.928")),
            new BigDecimal("62.5"),
            stock.changePercent().signum() >= 0 ? "BULLISH" : "NEUTRAL",
            stock.lastPrice().multiply(new BigDecimal("0.026")),
            Instant.now()
        );
    }

    PredictionResponse prediction(Market market, String symbol, int horizonDays) {
        StockRecord stock = get(market, symbol);
        BigDecimal upProbability = clamp(new BigDecimal("0.58").add(stock.changePercent().divide(new BigDecimal("40"))), new BigDecimal("0.42"), new BigDecimal("0.78"));
        return new PredictionResponse(
            stock.symbol(),
            stock.market(),
            horizonDays,
            upProbability,
            stock.changePercent().divide(new BigDecimal("100")).multiply(new BigDecimal("0.8")),
            new BigDecimal("0.032"),
            stock.changePercent().abs().compareTo(new BigDecimal("2.5")) > 0 ? "HIGH" : "MEDIUM",
            "mock-xgboost-v0.1",
            Instant.now()
        );
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.max(min).min(max);
    }

    private static List<BigDecimal> oneMonthTradingPrices(List<BigDecimal> rawPrices) {
        List<BigDecimal> prices = rawPrices == null ? List.of() : rawPrices.stream()
            .filter(value -> value != null)
            .toList();
        int targetSize = recentTradingDates(0, ZoneId.systemDefault()).size();
        if (prices.isEmpty()) {
            return List.of(BigDecimal.ZERO);
        }
        if (prices.size() >= 15) {
            return prices.size() > targetSize ? prices.subList(prices.size() - targetSize, prices.size()) : prices;
        }
        return expandPrices(prices, targetSize);
    }

    private static List<BigDecimal> expandPrices(List<BigDecimal> prices, int targetSize) {
        if (prices.size() >= targetSize) {
            return prices;
        }
        if (prices.size() == 1) {
            return IntStream.range(0, targetSize).mapToObj(i -> prices.get(0)).toList();
        }
        return IntStream.range(0, targetSize)
            .mapToObj(i -> {
                double position = i * (prices.size() - 1.0) / Math.max(1, targetSize - 1);
                int leftIndex = (int) Math.floor(position);
                int rightIndex = Math.min(prices.size() - 1, leftIndex + 1);
                BigDecimal left = prices.get(leftIndex);
                BigDecimal right = prices.get(rightIndex);
                BigDecimal ratio = BigDecimal.valueOf(position - leftIndex);
                return left.add(right.subtract(left).multiply(ratio)).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
            })
            .toList();
    }

    private static List<LocalDate> recentTradingDates(int preferredSize, ZoneId zoneId) {
        LocalDate end = LocalDate.now(zoneId);
        LocalDate start = end.minusMonths(1);
        List<LocalDate> dates = start.datesUntil(end.plusDays(1))
            .filter(StockService::isWeekday)
            .toList();
        if (preferredSize <= 0 || preferredSize >= dates.size()) {
            return dates;
        }
        return dates.subList(dates.size() - preferredSize, dates.size());
    }

    private static boolean isWeekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }
}
