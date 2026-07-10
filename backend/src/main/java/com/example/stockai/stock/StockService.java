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
        ZoneId marketZone = ZoneId.of(stock.timezone());
        List<BigDecimal> prices = oneMonthTradingPrices(stock.prices(), marketZone);
        List<LocalDate> dates = recentTradingDates(prices.size(), marketZone);
        List<PriceHistoryResponse.PricePoint> points = IntStream.range(0, prices.size())
            .mapToObj(i -> new PriceHistoryResponse.PricePoint(dates.get(i).toString(), prices.get(i)))
            .toList();
        return new PriceHistoryResponse(points);
    }

    TechnicalSummaryResponse technicalSummary(Market market, String symbol) {
        StockRecord stock = get(market, symbol);
        List<BigDecimal> prices = oneMonthTradingPrices(stock.prices(), ZoneId.of(stock.timezone()));
        return new TechnicalSummaryResponse(
            stock.symbol(),
            stock.market(),
            movingAverage(prices, 5),
            movingAverage(prices, 20),
            movingAverage(prices, 60),
            rsi(prices, 14),
            ema(prices, 12).compareTo(ema(prices, 26)) >= 0 ? "BULLISH" : "BEARISH",
            averageCloseRange(prices, 14),
            Instant.now()
        );
    }

    PredictionResponse prediction(Market market, String symbol, int horizonDays) {
        StockRecord stock = get(market, symbol);
        List<BigDecimal> prices = oneMonthTradingPrices(stock.prices(), ZoneId.of(stock.timezone()));
        BigDecimal momentum = prices.size() < 2 || prices.get(0).signum() == 0
            ? BigDecimal.ZERO
            : prices.get(prices.size() - 1).divide(prices.get(0), 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
        BigDecimal volatility = closeReturnVolatility(prices);
        BigDecimal upProbability = clamp(new BigDecimal("0.50").add(momentum.multiply(new BigDecimal("2.0"))), new BigDecimal("0.30"), new BigDecimal("0.70"));
        return new PredictionResponse(
            stock.symbol(),
            stock.market(),
            horizonDays,
            upProbability,
            momentum.multiply(BigDecimal.valueOf(Math.min(horizonDays, 60))).divide(BigDecimal.valueOf(Math.max(1, prices.size())), 8, RoundingMode.HALF_UP),
            volatility,
            volatility.compareTo(new BigDecimal("0.03")) > 0 ? "HIGH" : volatility.compareTo(new BigDecimal("0.015")) > 0 ? "MEDIUM" : "LOW",
            "heuristic-momentum-v1",
            Instant.now()
        );
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.max(min).min(max);
    }

    private static BigDecimal movingAverage(List<BigDecimal> prices, int window) {
        int start = Math.max(0, prices.size() - window);
        return prices.subList(start, prices.size()).stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(prices.size() - start), 6, RoundingMode.HALF_UP)
            .stripTrailingZeros();
    }

    private static BigDecimal ema(List<BigDecimal> prices, int window) {
        double alpha = 2.0 / (window + 1.0);
        double value = prices.get(0).doubleValue();
        for (int i = 1; i < prices.size(); i++) {
            value = alpha * prices.get(i).doubleValue() + (1.0 - alpha) * value;
        }
        return BigDecimal.valueOf(value);
    }

    private static BigDecimal rsi(List<BigDecimal> prices, int window) {
        int start = Math.max(1, prices.size() - window);
        double gains = 0;
        double losses = 0;
        for (int i = start; i < prices.size(); i++) {
            double delta = prices.get(i).subtract(prices.get(i - 1)).doubleValue();
            if (delta >= 0) gains += delta; else losses -= delta;
        }
        if (gains == 0 && losses == 0) return new BigDecimal("50");
        if (losses == 0) return new BigDecimal("100");
        return BigDecimal.valueOf(100.0 - (100.0 / (1.0 + gains / losses))).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageCloseRange(List<BigDecimal> prices, int window) {
        int start = Math.max(1, prices.size() - window);
        if (prices.size() < 2) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (int i = start; i < prices.size(); i++) {
            total = total.add(prices.get(i).subtract(prices.get(i - 1)).abs());
        }
        return total.divide(BigDecimal.valueOf(prices.size() - start), 6, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static BigDecimal closeReturnVolatility(List<BigDecimal> prices) {
        if (prices.size() < 2) return BigDecimal.ZERO;
        double[] returns = new double[prices.size() - 1];
        double mean = 0;
        for (int i = 1; i < prices.size(); i++) {
            double previous = prices.get(i - 1).doubleValue();
            returns[i - 1] = previous == 0 ? 0 : prices.get(i).doubleValue() / previous - 1.0;
            mean += returns[i - 1];
        }
        mean /= returns.length;
        double variance = 0;
        for (double value : returns) variance += Math.pow(value - mean, 2);
        return BigDecimal.valueOf(Math.sqrt(variance / returns.length)).setScale(8, RoundingMode.HALF_UP);
    }

    private static List<BigDecimal> oneMonthTradingPrices(List<BigDecimal> rawPrices, ZoneId zoneId) {
        List<BigDecimal> prices = rawPrices == null ? List.of() : rawPrices.stream()
            .filter(value -> value != null)
            .toList();
        int targetSize = recentTradingDates(0, zoneId).size();
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
