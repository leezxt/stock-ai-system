package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

import com.example.stockai.market.Market;

class FallbackMarketDataProviderTest {
    @Test
    void searchPrefersTwseResultsOverMockCatalog() {
        FallbackMarketDataProvider provider = new FallbackMarketDataProvider(
            new MockMarketDataProvider(),
            disabledRealtimeProvider(),
            new FinMindTwMarketDataProvider(
                HttpClient.newHttpClient(),
                JsonParserFactory.getJsonParser(),
                "",
                ""
            ),
            new YahooFinanceUsMarketDataProvider(
                HttpClient.newHttpClient(),
                JsonParserFactory.getJsonParser(),
                "",
                "",
                "Mozilla/5.0"
            ),
            new AlphaVantageMarketDataProvider(
                HttpClient.newHttpClient(),
                JsonParserFactory.getJsonParser(),
                "",
                "https://www.alphavantage.co/query"
            ),
            new StubTwseMarketDataProvider()
        );

        List<StockRecord> results = provider.search(Market.TW, "2330");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).symbol()).isEqualTo("2330.TW");
        assertThat(results.get(0).source()).isEqualTo("twse-openapi");
        assertThat(results.get(0).lastPrice()).isEqualByComparingTo("1100");
    }

    @Test
    void searchFallsBackToGeneratedRecordWhenNoRealSourceMatches() {
        FallbackMarketDataProvider provider = new FallbackMarketDataProvider(
            new MockMarketDataProvider(),
            disabledRealtimeProvider(),
            new FinMindTwMarketDataProvider(
                HttpClient.newHttpClient(),
                JsonParserFactory.getJsonParser(),
                "",
                ""
            ),
            new YahooFinanceUsMarketDataProvider(
                HttpClient.newHttpClient(),
                JsonParserFactory.getJsonParser(),
                "",
                "",
                "Mozilla/5.0"
            ),
            new AlphaVantageMarketDataProvider(
                HttpClient.newHttpClient(),
                JsonParserFactory.getJsonParser(),
                "",
                "https://www.alphavantage.co/query"
            ),
            new StubTwseMarketDataProvider()
        );

        List<StockRecord> results = provider.search(Market.TW, "1101");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).symbol()).isEqualTo("1101.TW");
        assertThat(results.get(0).source()).isEqualTo("mock-generated");
    }

    @Test
    void prefersHistoricalTaiwanSeriesBeforeRealtimeQuote() {
        MockMarketDataProvider mockProvider = new MockMarketDataProvider();
        TwseRealtimeMarketDataProvider realtimeProvider = mock(TwseRealtimeMarketDataProvider.class);
        FinMindTwMarketDataProvider finMindProvider = mock(FinMindTwMarketDataProvider.class);
        YahooFinanceUsMarketDataProvider yahooProvider = mock(YahooFinanceUsMarketDataProvider.class);
        AlphaVantageMarketDataProvider alphaProvider = mock(AlphaVantageMarketDataProvider.class);
        TwseMarketDataProvider twseProvider = mock(TwseMarketDataProvider.class);
        FallbackMarketDataProvider provider = new FallbackMarketDataProvider(
            mockProvider,
            realtimeProvider,
            finMindProvider,
            yahooProvider,
            alphaProvider,
            twseProvider
        );

        StockRecord fallback = mockProvider.get(Market.TW, "2330.TW");
        StockRecord historical = new StockRecord(
            "2330.TW", "台積電", Market.TW, "TWD", new BigDecimal("1050"), new BigDecimal("1.2"),
            List.of(new BigDecimal("1030"), new BigDecimal("1045"), new BigDecimal("1050")), "finmind"
        );
        StockRecord current = new StockRecord(
            "2330.TW", "台積電", Market.TW, "TWD", new BigDecimal("1052"), new BigDecimal("1.4"),
            List.of(new BigDecimal("1045"), new BigDecimal("1050"), new BigDecimal("1052")), "twse-realtime"
        );
        when(finMindProvider.tryGet(Market.TW, "2330.TW", fallback)).thenReturn(Optional.of(historical));
        when(realtimeProvider.tryGet(Market.TW, "2330.TW", historical)).thenReturn(Optional.of(current));

        StockRecord result = provider.get(Market.TW, "2330.TW");

        assertThat(result.source()).isEqualTo("twse-realtime");
        assertThat(result.prices()).containsExactly(
            new BigDecimal("1045"), new BigDecimal("1050"), new BigDecimal("1052")
        );
        verify(realtimeProvider).tryGet(Market.TW, "2330.TW", historical);
        verify(twseProvider, never()).tryGet(eq(Market.TW), eq("2330.TW"), isNull(StockRecord.class));
    }

    @Test
    void realtimeOnlyTaiwanFallbackDoesNotAppendToMockHistory() {
        MockMarketDataProvider mockProvider = new MockMarketDataProvider();
        TwseRealtimeMarketDataProvider realtimeProvider = mock(TwseRealtimeMarketDataProvider.class);
        FinMindTwMarketDataProvider finMindProvider = mock(FinMindTwMarketDataProvider.class);
        YahooFinanceUsMarketDataProvider yahooProvider = mock(YahooFinanceUsMarketDataProvider.class);
        AlphaVantageMarketDataProvider alphaProvider = mock(AlphaVantageMarketDataProvider.class);
        TwseMarketDataProvider twseProvider = mock(TwseMarketDataProvider.class);
        FallbackMarketDataProvider provider = new FallbackMarketDataProvider(
            mockProvider,
            realtimeProvider,
            finMindProvider,
            yahooProvider,
            alphaProvider,
            twseProvider
        );

        StockRecord fallback = mockProvider.get(Market.TW, "2330.TW");
        StockRecord current = new StockRecord(
            "2330.TW", "台積電", Market.TW, "TWD", new BigDecimal("1052"), new BigDecimal("1.4"),
            List.of(new BigDecimal("1052")), "twse-realtime"
        );
        when(finMindProvider.tryGet(Market.TW, "2330.TW", fallback)).thenReturn(Optional.empty());
        when(realtimeProvider.tryGet(Market.TW, "2330.TW", null)).thenReturn(Optional.of(current));

        StockRecord result = provider.get(Market.TW, "2330.TW");

        assertThat(result.prices()).containsExactly(new BigDecimal("1052"));
        verify(realtimeProvider).tryGet(Market.TW, "2330.TW", null);
        verify(twseProvider, never()).tryGet(eq(Market.TW), eq("2330.TW"), isNull(StockRecord.class));
    }

    private static TwseRealtimeMarketDataProvider disabledRealtimeProvider() {
        return new TwseRealtimeMarketDataProvider(
            HttpClient.newHttpClient(),
            JsonParserFactory.getJsonParser(),
            "",
            "Mozilla/5.0"
        );
    }

    private static final class StubTwseMarketDataProvider extends TwseMarketDataProvider {
        StubTwseMarketDataProvider() {
            super(HttpClient.newHttpClient(), JsonParserFactory.getJsonParser(), "http://127.0.0.1:1/twse");
        }

        @Override
        Optional<List<StockRecord>> trySearch(Market market, String query) {
            if (market == Market.TW && "2330".equals(query)) {
                return Optional.of(List.of(new StockRecord(
                    "2330.TW",
                    "台積電(即時)",
                    Market.TW,
                    "TWD",
                    new BigDecimal("1100"),
                    new BigDecimal("1.25"),
                    List.of(new BigDecimal("1100")),
                    "twse-openapi"
                )));
            }
            return Optional.of(List.of());
        }
    }
}
