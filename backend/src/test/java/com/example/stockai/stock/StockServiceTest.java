package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

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
    void returnsPrediction() {
        PredictionResponse prediction = stockService.prediction(Market.US, "AAPL", 5);

        assertThat(prediction.symbol()).isEqualTo("AAPL");
        assertThat(prediction.horizonDays()).isEqualTo(5);
        assertThat(prediction.riskLevel()).isIn("LOW", "MEDIUM", "HIGH");
        assertThat(prediction.modelVersion()).isEqualTo("heuristic-momentum-v1");
        assertThat(prediction.upProbability()).isBetween(new java.math.BigDecimal("0.30"), new java.math.BigDecimal("0.70"));
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
}
