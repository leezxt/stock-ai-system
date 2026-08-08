package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

import com.example.stockai.market.Market;

class StockServiceTest {
    private final StockService stockService = new StockService(
        new FallbackMarketDataProvider(
            new MockMarketDataProvider(),
            new TwseRealtimeMarketDataProvider(
                java.net.http.HttpClient.newHttpClient(),
                JsonParserFactory.getJsonParser(),
                "",
                "Mozilla/5.0"
            ),
            new FinMindTwMarketDataProvider(
                java.net.http.HttpClient.newHttpClient(),
                JsonParserFactory.getJsonParser(),
                "",
                ""
            ),
            new YahooFinanceUsMarketDataProvider(
                java.net.http.HttpClient.newHttpClient(),
                JsonParserFactory.getJsonParser(),
                "",
                "",
                "Mozilla/5.0"
            ),
            new AlphaVantageMarketDataProvider(
                java.net.http.HttpClient.newHttpClient(),
                JsonParserFactory.getJsonParser(),
                "",
                "https://www.alphavantage.co/query"
            ),
            new TwseMarketDataProvider(
                java.net.http.HttpClient.newHttpClient(),
                JsonParserFactory.getJsonParser(),
                "http://127.0.0.1:1/twse"
            )
        )
    );

    @Test
    void returnsTwSummaryWithNormalizedSymbol() {
        StockSummaryResponse summary = stockService.summary(Market.TW, "2330");

        assertThat(summary.symbol()).isEqualTo("2330.TW");
        assertThat(summary.currency()).isEqualTo("TWD");
        assertThat(summary.source()).isEqualTo("mock");
        assertThat(summary.priceSnapshot().lastPrice()).isNotNull();
    }

    @Test
    void summaryUsesProviderObservationTimeInsteadOfRequestTime() {
        Instant observedAt = Instant.parse("2026-07-10T05:30:00Z");
        StockRecord record = new StockRecord(
            "2330.TW",
            "台積電",
            Market.TW,
            "TWD",
            new BigDecimal("1050"),
            BigDecimal.ZERO,
            List.of(new BigDecimal("1050")),
            "twse-realtime",
            List.of(new PriceBar(LocalDate.of(2026, 7, 10), new BigDecimal("1050"), "twse-realtime")),
            observedAt
        );

        StockSummaryResponse summary = new StockService(new FixedMarketDataProvider(record))
            .summary(Market.TW, "2330.TW");

        assertThat(summary.priceSnapshot().updatedAt()).isEqualTo(observedAt);
    }

    @Test
    void returnsPrediction() {
        PredictionResponse prediction = stockService.prediction(Market.US, "AAPL", 5);

        assertThat(prediction.symbol()).isEqualTo("AAPL");
        assertThat(prediction.horizonDays()).isEqualTo(5);
        assertThat(prediction.riskLevel()).isIn("LOW", "MEDIUM", "HIGH");
        assertThat(prediction.modelVersion()).isEqualTo("local-logistic-v1");
        assertThat(prediction.upProbability()).isBetween(new java.math.BigDecimal("0.05"), new java.math.BigDecimal("0.95"));
        assertThat(prediction.dataQuality().status()).isEqualTo("MOCK");
        assertThat(prediction.dataQuality().sampleCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void technicalSummaryReportsPartialHistoryAndUnavailableIndicators() {
        LocalDate end = LocalDate.now(ZoneId.of("Asia/Taipei"));
        StockRecord record = new StockRecord(
            "2330.TW",
            "台積電",
            Market.TW,
            "TWD",
            new BigDecimal("110"),
            BigDecimal.ZERO,
            List.of(new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("110")),
            "finmind",
            List.of(
                new PriceBar(end.minusDays(3), new BigDecimal("100"), "finmind"),
                new PriceBar(end.minusDays(1), new BigDecimal("105"), "finmind"),
                new PriceBar(end, new BigDecimal("110"), "finmind")
            )
        );

        TechnicalSummaryResponse response = new StockService(new FixedMarketDataProvider(record))
            .technicalSummary(Market.TW, "2330.TW");

        assertThat(response.dataQuality().sampleCount()).isEqualTo(3);
        assertThat(response.dataQuality().requiredSampleCount()).isEqualTo(60);
        assertThat(response.dataQuality().status()).isEqualTo("PARTIAL");
        assertThat(response.dataQuality().dataFrom()).isEqualTo(end.minusDays(3).toString());
        assertThat(response.dataQuality().dataTo()).isEqualTo(end.toString());
        assertThat(response.dataQuality().source()).isEqualTo("finmind");
        assertThat(response.dataQuality().unavailableIndicators())
            .containsExactly("MA5", "MA20", "MA60", "RSI14", "MACD", "ATR14");
    }

    @Test
    void predictionMarksRealtimeQuoteAsSnapshotOnly() {
        LocalDate end = LocalDate.now(ZoneId.of("Asia/Taipei"));
        StockRecord record = new StockRecord(
            "2330.TW",
            "台積電",
            Market.TW,
            "TWD",
            new BigDecimal("1052"),
            BigDecimal.ZERO,
            List.of(new BigDecimal("1052")),
            "twse-realtime",
            List.of(new PriceBar(end, new BigDecimal("1052"), "twse-realtime"))
        );

        PredictionResponse response = new StockService(new FixedMarketDataProvider(record))
            .prediction(Market.TW, "2330.TW", 5);

        assertThat(response.dataQuality().sampleCount()).isEqualTo(1);
        assertThat(response.dataQuality().status()).isEqualTo("SNAPSHOT_ONLY");
        assertThat(response.dataQuality().unavailableIndicators())
            .contains("UP_PROBABILITY", "EXPECTED_RETURN", "RISK_LEVEL");
    }

    @Test
    void returnsGeneratedFallbackForUnknownUsSymbol() {
        StockSummaryResponse summary = stockService.summary(Market.US, "MSFT");

        assertThat(summary.symbol()).isEqualTo("MSFT");
        assertThat(summary.source()).isEqualTo("mock-generated");
        assertThat(summary.priceSnapshot().lastPrice()).isNotNull();
    }

    @Test
    void returnsGeneratedFallbackForUnknownTwSymbol() {
        StockSummaryResponse summary = stockService.summary(Market.TW, "1101");

        assertThat(summary.symbol()).isEqualTo("1101.TW");
        assertThat(summary.source()).isEqualTo("mock-generated");
        assertThat(summary.priceSnapshot().lastPrice()).isNotNull();
    }

    @Test
    void returnsGeneratedFallbackForTwEtfSymbol() {
        StockSummaryResponse summary = stockService.summary(Market.TW, "009819.TW");
        PriceHistoryResponse prices = stockService.prices(Market.TW, "009819.TW");

        assertThat(summary.symbol()).isEqualTo("009819.TW");
        assertThat(summary.currency()).isEqualTo("TWD");
        assertThat(summary.source()).isEqualTo("mock-generated");
        assertThat(prices.prices()).isNotEmpty();
    }

    @Test
    void preservesProviderTradingDatesAndSources() {
        LocalDate end = LocalDate.now(ZoneId.of("Asia/Taipei"));
        StockRecord record = new StockRecord(
            "2330.TW",
            "台積電",
            Market.TW,
            "TWD",
            new BigDecimal("110"),
            BigDecimal.ZERO,
            List.of(new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("110")),
            "finmind",
            List.of(
                new PriceBar(end.minusDays(3), new BigDecimal("100"), "finmind"),
                new PriceBar(end.minusDays(1), new BigDecimal("105"), "finmind"),
                new PriceBar(end, new BigDecimal("110"), "finmind")
            )
        );
        StockService service = new StockService(new FixedMarketDataProvider(record));

        PriceHistoryResponse response = service.prices(Market.TW, "2330.TW");

        assertThat(response.prices()).extracting(PriceHistoryResponse.PricePoint::date)
            .containsExactly(end.minusDays(3).toString(), end.minusDays(1).toString(), end.toString());
        assertThat(response.prices()).extracting(PriceHistoryResponse.PricePoint::source)
            .containsOnly("finmind");
    }

    @Test
    void doesNotExpandRealtimeOnlyQuoteIntoFakeHistory() {
        LocalDate end = LocalDate.now(ZoneId.of("Asia/Taipei"));
        StockRecord record = new StockRecord(
            "2330.TW",
            "台積電",
            Market.TW,
            "TWD",
            new BigDecimal("1052"),
            BigDecimal.ZERO,
            List.of(new BigDecimal("1052")),
            "twse-realtime",
            List.of(new PriceBar(end, new BigDecimal("1052"), "twse-realtime"))
        );
        StockService service = new StockService(new FixedMarketDataProvider(record));

        PriceHistoryResponse response = service.prices(Market.TW, "2330.TW");

        assertThat(response.prices()).containsExactly(
            new PriceHistoryResponse.PricePoint(end.toString(), new BigDecimal("1052"), "twse-realtime")
        );
    }

    @Test
    void dataLineageReportsSourcesAdjustmentAndCorporateActions() {
        LocalDate end = LocalDate.now(ZoneId.of("America/New_York"));
        StockRecord record = new StockRecord(
            "AAPL",
            "Apple",
            Market.US,
            "USD",
            new BigDecimal("200"),
            new BigDecimal("1.2"),
            List.of(new BigDecimal("198"), new BigDecimal("200")),
            "yahoo-finance",
            List.of(
                new PriceBar(end.minusDays(1), new BigDecimal("198"), "yahoo-finance"),
                new PriceBar(end, new BigDecimal("200"), "yahoo-finance")
            ),
            Instant.parse("2026-08-08T00:00:00Z"),
            List.of(new CorporateAction(end.minusDays(1), "DIVIDEND", "股利 0.25", "yahoo-finance-events")),
            "ADJUSTED_CLOSE"
        );

        DataLineageResponse response = new StockService(new FixedMarketDataProvider(record))
            .dataLineage(Market.US, "AAPL");

        assertThat(response.source()).isEqualTo("yahoo-finance");
        assertThat(response.adjustmentStatus()).isEqualTo("ADJUSTED_CLOSE");
        assertThat(response.pricePointCount()).isEqualTo(2);
        assertThat(response.priceSources()).containsExactly("yahoo-finance");
        assertThat(response.integrityStatus()).isEqualTo("OK");
        assertThat(response.corporateActions()).extracting(CorporateAction::type).containsExactly("DIVIDEND");
        assertThat(response.findings()).isEmpty();
    }

    private static final class FixedMarketDataProvider implements MarketDataProvider {
        private final StockRecord record;

        private FixedMarketDataProvider(StockRecord record) {
            this.record = record;
        }

        @Override
        public List<Market> markets() {
            return List.of(record.market());
        }

        @Override
        public List<StockRecord> search(Market market, String query) {
            return List.of(record);
        }

        @Override
        public StockRecord get(Market market, String symbol) {
            return record;
        }
    }
}
