package com.example.stockai.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.stockai.market.Market;

@Service
public class StockService {
    private final MarketDataProvider marketDataProvider;
    private final StockSnapshotStore stockSnapshotStore;
    private final PredictionModel predictionModel;

    public StockService(MarketDataProvider marketDataProvider) {
        this(marketDataProvider, null, new LocalLogisticPredictionModel());
    }

    @Autowired
    public StockService(
        MarketDataProvider marketDataProvider,
        Optional<StockSnapshotStore> stockSnapshotStore,
        Optional<PredictionModel> predictionModel
    ) {
        this(marketDataProvider, stockSnapshotStore.orElse(null), predictionModel.orElseGet(LocalLogisticPredictionModel::new));
    }

    StockService(MarketDataProvider marketDataProvider, StockSnapshotStore stockSnapshotStore, PredictionModel predictionModel) {
        this.marketDataProvider = marketDataProvider;
        this.stockSnapshotStore = stockSnapshotStore;
        this.predictionModel = predictionModel == null ? new LocalLogisticPredictionModel() : predictionModel;
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
            new StockSummaryResponse.PriceSnapshot(stock.symbol(), stock.market(), stock.lastPrice(), stock.changePercent(), stock.currency(), observationTime(stock))
        );
    }

    PriceHistoryResponse prices(Market market, String symbol) {
        StockRecord stock = get(market, symbol);
        ZoneId marketZone = ZoneId.of(stock.timezone());
        List<PriceBar> bars = oneMonthTradingBars(stock, marketZone);
        List<PriceHistoryResponse.PricePoint> points = bars.stream()
            .map(bar -> new PriceHistoryResponse.PricePoint(bar.date().toString(), bar.close(), bar.source()))
            .toList();
        return new PriceHistoryResponse(points);
    }

    DataLineageResponse dataLineage(Market market, String symbol) {
        StockRecord stock = get(market, symbol);
        List<PriceBar> bars = oneMonthTradingBars(stock, ZoneId.of(stock.timezone()));
        List<String> sources = bars.stream()
            .map(PriceBar::source)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList();
        List<String> findings = new ArrayList<>();
        Set<java.time.LocalDate> seenDates = new HashSet<>();
        boolean duplicateDate = false;
        boolean invalidClose = false;
        for (PriceBar bar : bars) {
            if (!seenDates.add(bar.date())) duplicateDate = true;
            if (bar.close() == null || bar.close().signum() <= 0) invalidClose = true;
        }
        if (bars.isEmpty()) findings.add("NO_PRICE_HISTORY");
        if (bars.size() == 1) findings.add("SNAPSHOT_ONLY");
        if (duplicateDate) findings.add("DUPLICATE_DATE");
        if (invalidClose) findings.add("INVALID_CLOSE");
        if (sources.size() > 1) findings.add("MIXED_PRICE_SOURCES");
        if (stock.adjustmentStatus().equals("UNVERIFIED")) findings.add("ADJUSTMENT_NOT_VERIFIED");
        if (stock.corporateActions().isEmpty()) {
            findings.add(stock.adjustmentStatus().equals("ADJUSTED_CLOSE") ? "NO_ACTIONS_IN_WINDOW" : "CORPORATE_ACTION_FEED_NOT_AVAILABLE");
        }
        if (stock.source().toLowerCase().startsWith("mock")) findings.add("MOCK_SOURCE");
        String integrityStatus = duplicateDate || invalidClose || bars.isEmpty()
            ? "INVALID"
            : stock.source().toLowerCase().startsWith("mock") ? "MOCK"
            : findings.isEmpty() ? "OK" : "PARTIAL";
        String dataFrom = bars.stream().map(PriceBar::date).min(Comparator.naturalOrder()).map(Object::toString).orElse(null);
        String dataTo = bars.stream().map(PriceBar::date).max(Comparator.naturalOrder()).map(Object::toString).orElse(null);
        return new DataLineageResponse(
            stock.symbol(),
            stock.market(),
            stock.source(),
            stock.adjustmentStatus(),
            observationTime(stock),
            bars.size(),
            dataFrom,
            dataTo,
            sources,
            integrityStatus,
            findings,
            stock.corporateActions(),
            MarketDataQuality.forTechnical(bars, stock.source()),
            MarketDataQuality.forPrediction(bars, stock.source())
        );
    }

    TechnicalSummaryResponse technicalSummary(Market market, String symbol) {
        StockRecord stock = get(market, symbol);
        List<PriceBar> bars = oneMonthTradingBars(stock, ZoneId.of(stock.timezone()));
        List<BigDecimal> prices = closePrices(bars);
        return new TechnicalSummaryResponse(
            stock.symbol(),
            stock.market(),
            movingAverage(prices, 5),
            movingAverage(prices, 20),
            movingAverage(prices, 60),
            rsi(prices, 14),
            ema(prices, 12).compareTo(ema(prices, 26)) >= 0 ? "BULLISH" : "BEARISH",
            averageCloseRange(prices, 14),
            Instant.now(),
            MarketDataQuality.forTechnical(bars, stock.source())
        );
    }

    PredictionResponse prediction(Market market, String symbol, int horizonDays) {
        StockRecord stock = get(market, symbol);
        List<PriceBar> bars = oneMonthTradingBars(stock, ZoneId.of(stock.timezone()));
        List<BigDecimal> prices = closePrices(bars);
        PredictionModel.PredictionEstimate estimate = predictionModel.predict(bars, horizonDays)
            .orElseGet(() -> heuristicEstimate(prices, horizonDays));
        return new PredictionResponse(
            stock.symbol(),
            stock.market(),
            horizonDays,
            estimate.upProbability(),
            estimate.expectedReturn(),
            estimate.volatility(),
            estimate.riskLevel(),
            estimate.modelVersion(),
            Instant.now(),
            MarketDataQuality.forPrediction(bars, stock.source())
        );
    }

    private static PredictionModel.PredictionEstimate heuristicEstimate(List<BigDecimal> prices, int horizonDays) {
        BigDecimal momentum = prices.size() < 2 || prices.get(0).signum() == 0
            ? BigDecimal.ZERO
            : prices.get(prices.size() - 1).divide(prices.get(0), 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
        BigDecimal volatility = closeReturnVolatility(prices);
        BigDecimal upProbability = clamp(new BigDecimal("0.50").add(momentum.multiply(new BigDecimal("2.0"))), new BigDecimal("0.30"), new BigDecimal("0.70"));
        return new PredictionModel.PredictionEstimate(
            upProbability,
            momentum.multiply(BigDecimal.valueOf(Math.min(horizonDays, 60))).divide(BigDecimal.valueOf(Math.max(1, prices.size())), 8, RoundingMode.HALF_UP),
            volatility,
            volatility.compareTo(new BigDecimal("0.03")) > 0 ? "HIGH" : volatility.compareTo(new BigDecimal("0.015")) > 0 ? "MEDIUM" : "LOW",
            "heuristic-momentum-v1"
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

    private static List<PriceBar> oneMonthTradingBars(StockRecord stock, ZoneId zoneId) {
        LocalDate end = LocalDate.now(zoneId);
        LocalDate start = end.minusMonths(1);
        List<PriceBar> history = stock.priceHistory() == null ? List.of() : stock.priceHistory().stream()
            .filter(bar -> bar != null && !bar.date().isBefore(start) && !bar.date().isAfter(end))
            .sorted(java.util.Comparator.comparing(PriceBar::date))
            .toList();
        if (!history.isEmpty()) {
            return history;
        }

        List<BigDecimal> prices = stock.prices() == null ? List.of() : stock.prices().stream()
            .filter(value -> value != null)
            .toList();
        if (prices.isEmpty()) {
            return List.of(new PriceBar(end, BigDecimal.ZERO, stock.source()));
        }

        // A real provider without dated bars is still only a quote snapshot.
        // Do not manufacture a month of history from that one observation.
        if (!isMockSource(stock.source())) {
            return List.of(new PriceBar(end, prices.get(prices.size() - 1), stock.source()));
        }

        int targetSize = recentTradingDates(0, zoneId).size();
        List<BigDecimal> expanded = expandPrices(prices, targetSize);
        List<LocalDate> dates = recentTradingDates(expanded.size(), zoneId);
        return IntStream.range(0, expanded.size())
            .mapToObj(i -> new PriceBar(dates.get(i), expanded.get(i), stock.source()))
            .toList();
    }

    private static List<BigDecimal> closePrices(List<PriceBar> bars) {
        return bars.stream().map(PriceBar::close).toList();
    }

    private static boolean isMockSource(String source) {
        return source == null || source.isBlank() || source.startsWith("mock");
    }

    /**
     * Returns the market/provider observation time, not the time at which this
     * request happened to reach the backend. Historical daily sources only
     * provide a trading date, so their timestamp is normalized to that date's
     * market timezone at midnight. Mock records intentionally fall back to the
     * current time because they have no upstream observation.
     */
    private static Instant observationTime(StockRecord stock) {
        if (stock.observedAt() != null) {
            return stock.observedAt();
        }
        if (stock.priceHistory() != null && !stock.priceHistory().isEmpty()) {
            LocalDate latestDate = stock.priceHistory().stream()
                .filter(java.util.Objects::nonNull)
                .map(PriceBar::date)
                .max(LocalDate::compareTo)
                .orElse(null);
            if (latestDate != null) {
                return latestDate.atStartOfDay(ZoneId.of(stock.timezone())).toInstant();
            }
        }
        return Instant.now();
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
