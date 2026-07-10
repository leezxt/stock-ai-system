package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class DocumentSourceImportServiceTest {
    @Test
    void importsFetchedNewsIntoVectorStore() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        DocumentSourceImportService service = new DocumentSourceImportService(
            new AlphaVantageNewsSourceAdapter(null, null, "", "") {
                @Override
                List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
                    return List.of(new DocumentImportRequest(
                        symbol,
                        market,
                        DocumentType.NEWS,
                        "Apple guidance raised",
                        "Reuters",
                        Instant.parse("2026-07-06T00:00:00Z"),
                        "Apple raised guidance after stronger demand."
                    ));
                }
            },
            disabledFmpNewsAdapter(),
            new YahooFinanceNewsSourceAdapter(null, ""),
            new YahooFinanceFinancialsSourceAdapter(null, null, ""),
            new FinMindTwNewsSourceAdapter(null, null, "", ""),
            new FinMindTwFinancialReportSourceAdapter(null, null, "", ""),
            new AlphaVantageTranscriptSourceAdapter(null, null, "", ""),
            disabledFmpTranscriptAdapter(),
            new TwseDisclosureSourceAdapter(null, null, ""),
            new DocumentIngestionService(),
            new DocumentEmbeddingService(new HashEmbeddingModel()),
            vectorStore
        );

        DocumentSourceImportService.SourceImportResponse response = service.importNews(Market.US, "AAPL", 3);
        List<RetrievedDocument> hits = new DocumentRetriever(new HashEmbeddingModel(), vectorStore).retrieve(
            new DocumentRetrieveRequest("guidance demand", 3, "AAPL", Market.US, DocumentType.NEWS, null, null)
        );

        assertThat(response.documentCount()).isEqualTo(1);
        assertThat(response.chunkCount()).isGreaterThan(0);
        assertThat(hits).hasSize(1);
    }

    @Test
    void fallsBackToYahooFinanceNewsWhenAlphaVantageHasNoDocuments() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        DocumentSourceImportService service = new DocumentSourceImportService(
            new AlphaVantageNewsSourceAdapter(null, null, "", ""),
            disabledFmpNewsAdapter(),
            new YahooFinanceNewsSourceAdapter(null, "") {
                @Override
                List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
                    return List.of(new DocumentImportRequest(
                        symbol,
                        market,
                        DocumentType.NEWS,
                        "Broadcom AI demand lifts outlook",
                        "Yahoo Finance",
                        Instant.parse("2026-07-10T00:00:00Z"),
                        "Broadcom shares rose as investors focused on AI networking demand."
                    ));
                }
            },
            new YahooFinanceFinancialsSourceAdapter(null, null, ""),
            new FinMindTwNewsSourceAdapter(null, null, "", ""),
            new FinMindTwFinancialReportSourceAdapter(null, null, "", ""),
            new AlphaVantageTranscriptSourceAdapter(null, null, "", ""),
            disabledFmpTranscriptAdapter(),
            new TwseDisclosureSourceAdapter(null, null, ""),
            new DocumentIngestionService(),
            new DocumentEmbeddingService(new HashEmbeddingModel()),
            vectorStore
        );

        DocumentSourceImportService.SourceImportResponse response = service.importNews(Market.US, "AVGO", 3);
        List<RetrievedDocument> hits = new DocumentRetriever(new HashEmbeddingModel(), vectorStore).retrieve(
            new DocumentRetrieveRequest("AI networking demand", 3, "AVGO", Market.US, DocumentType.NEWS, null, null)
        );

        assertThat(response.adapter()).isEqualTo("yahoo-finance-news-adapter");
        assertThat(response.documentCount()).isEqualTo(1);
        assertThat(response.chunkCount()).isGreaterThan(0);
        assertThat(hits).hasSize(1);
    }

    @Test
    void fallsBackToYahooFinanceNewsWhenAlphaVantageThrowsRuntimeException() {
        DocumentSourceImportService service = new DocumentSourceImportService(
            new AlphaVantageNewsSourceAdapter(null, null, "", "") {
                @Override
                List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
                    throw new IllegalStateException("unexpected alpha vantage payload");
                }
            },
            disabledFmpNewsAdapter(),
            new YahooFinanceNewsSourceAdapter(null, "") {
                @Override
                List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
                    return List.of(new DocumentImportRequest(
                        symbol,
                        market,
                        DocumentType.NEWS,
                        "Broadcom fallback news",
                        "Yahoo Finance",
                        Instant.parse("2026-07-10T00:00:00Z"),
                        "Yahoo Finance fallback still provides a usable news document."
                    ));
                }
            },
            new YahooFinanceFinancialsSourceAdapter(null, null, ""),
            new FinMindTwNewsSourceAdapter(null, null, "", ""),
            new FinMindTwFinancialReportSourceAdapter(null, null, "", ""),
            new AlphaVantageTranscriptSourceAdapter(null, null, "", ""),
            disabledFmpTranscriptAdapter(),
            new TwseDisclosureSourceAdapter(null, null, ""),
            new DocumentIngestionService(),
            new DocumentEmbeddingService(new HashEmbeddingModel()),
            new InMemoryVectorStore()
        );

        DocumentSourceImportService.SourceImportResponse response = service.importNews(Market.US, "AVGO", 3);

        assertThat(response.adapter()).isEqualTo("yahoo-finance-news-adapter");
        assertThat(response.documentCount()).isEqualTo(1);
        assertThat(response.chunkCount()).isGreaterThan(0);
    }

    @Test
    void returnsEmptyNewsResponseWhenAllNewsSourcesThrowRuntimeException() {
        DocumentSourceImportService service = new DocumentSourceImportService(
            new AlphaVantageNewsSourceAdapter(null, null, "", "") {
                @Override
                List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
                    throw new IllegalStateException("unexpected alpha vantage payload");
                }
            },
            disabledFmpNewsAdapter(),
            new YahooFinanceNewsSourceAdapter(null, "") {
                @Override
                List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
                    throw new IllegalStateException("unexpected yahoo rss payload");
                }
            },
            new YahooFinanceFinancialsSourceAdapter(null, null, ""),
            new FinMindTwNewsSourceAdapter(null, null, "", ""),
            new FinMindTwFinancialReportSourceAdapter(null, null, "", ""),
            new AlphaVantageTranscriptSourceAdapter(null, null, "", ""),
            disabledFmpTranscriptAdapter(),
            new TwseDisclosureSourceAdapter(null, null, ""),
            new DocumentIngestionService(),
            new DocumentEmbeddingService(new HashEmbeddingModel()),
            new InMemoryVectorStore()
        );

        DocumentSourceImportService.SourceImportResponse response = service.importNews(Market.US, "AVGO", 3);

        assertThat(response.adapter()).isEqualTo("yahoo-finance-news-adapter");
        assertThat(response.documentCount()).isZero();
        assertThat(response.chunkCount()).isZero();
        assertThat(response.titles()).isEmpty();
    }

    @Test
    void fallsBackToFmpNewsWhenAlphaVantageHasNoDocuments() {
        DocumentSourceImportService service = new DocumentSourceImportService(
            new AlphaVantageNewsSourceAdapter(null, null, "", ""),
            new FmpNewsSourceAdapter(null, null, "", "") {
                @Override
                List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
                    return List.of(new DocumentImportRequest(
                        symbol,
                        market,
                        DocumentType.NEWS,
                        "Apple supplier demand improves",
                        "FMP News",
                        Instant.parse("2026-07-10T00:00:00Z"),
                        "Financial Modeling Prep returned a usable market news document."
                    ));
                }
            },
            new YahooFinanceNewsSourceAdapter(null, ""),
            new YahooFinanceFinancialsSourceAdapter(null, null, ""),
            new FinMindTwNewsSourceAdapter(null, null, "", ""),
            new FinMindTwFinancialReportSourceAdapter(null, null, "", ""),
            new AlphaVantageTranscriptSourceAdapter(null, null, "", ""),
            disabledFmpTranscriptAdapter(),
            new TwseDisclosureSourceAdapter(null, null, ""),
            new DocumentIngestionService(),
            new DocumentEmbeddingService(new HashEmbeddingModel()),
            new InMemoryVectorStore()
        );

        DocumentSourceImportService.SourceImportResponse response = service.importNews(Market.US, "AAPL", 3);

        assertThat(response.adapter()).isEqualTo("fmp-news-adapter");
        assertThat(response.documentCount()).isEqualTo(1);
        assertThat(response.chunkCount()).isGreaterThan(0);
    }

    @Test
    void fallsBackToFmpTranscriptWhenAlphaVantageHasNoTranscript() {
        DocumentSourceImportService service = new DocumentSourceImportService(
            new AlphaVantageNewsSourceAdapter(null, null, "", ""),
            disabledFmpNewsAdapter(),
            new YahooFinanceNewsSourceAdapter(null, ""),
            new YahooFinanceFinancialsSourceAdapter(null, null, ""),
            new FinMindTwNewsSourceAdapter(null, null, "", ""),
            new FinMindTwFinancialReportSourceAdapter(null, null, "", ""),
            new AlphaVantageTranscriptSourceAdapter(null, null, "", ""),
            new FmpTranscriptSourceAdapter(null, null, "", "") {
                @Override
                Optional<DocumentImportRequest> fetch(Market market, String symbol, String quarter) {
                    return Optional.of(new DocumentImportRequest(
                        symbol,
                        market,
                        DocumentType.EARNINGS_TRANSCRIPT,
                        "AAPL earnings call 2026Q2",
                        "FMP earnings transcript",
                        Instant.parse("2026-07-10T00:00:00Z"),
                        "Operator: Welcome. CEO: Demand remained healthy."
                    ));
                }
            },
            new TwseDisclosureSourceAdapter(null, null, ""),
            new DocumentIngestionService(),
            new DocumentEmbeddingService(new HashEmbeddingModel()),
            new InMemoryVectorStore()
        );

        DocumentSourceImportService.SourceImportResponse response = service.importTranscript(Market.US, "AAPL", "2026Q2");

        assertThat(response.adapter()).isEqualTo("fmp-earnings-transcript-adapter");
        assertThat(response.documentCount()).isEqualTo(1);
        assertThat(response.chunkCount()).isGreaterThan(0);
    }

    @Test
    void importsFetchedTwNewsIntoVectorStore() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        DocumentSourceImportService service = new DocumentSourceImportService(
            new AlphaVantageNewsSourceAdapter(null, null, "", ""),
            disabledFmpNewsAdapter(),
            new YahooFinanceNewsSourceAdapter(null, ""),
            new YahooFinanceFinancialsSourceAdapter(null, null, ""),
            new FinMindTwNewsSourceAdapter(null, null, "", "") {
                @Override
                List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
                    return List.of(new DocumentImportRequest(
                        symbol,
                        market,
                        DocumentType.NEWS,
                        "台積電法說前市場觀望",
                        "工商時報",
                        Instant.parse("2026-07-09T00:00:00Z"),
                        "投資人聚焦法說與 AI 訂單變化。"
                    ));
                }
            },
            new FinMindTwFinancialReportSourceAdapter(null, null, "", ""),
            new AlphaVantageTranscriptSourceAdapter(null, null, "", ""),
            disabledFmpTranscriptAdapter(),
            new TwseDisclosureSourceAdapter(null, null, ""),
            new DocumentIngestionService(),
            new DocumentEmbeddingService(new HashEmbeddingModel()),
            vectorStore
        );

        DocumentSourceImportService.SourceImportResponse response = service.importNews(Market.TW, "2330", 3);
        List<RetrievedDocument> hits = new DocumentRetriever(new HashEmbeddingModel(), vectorStore).retrieve(
            new DocumentRetrieveRequest("AI 訂單 法說", 3, "2330.TW", Market.TW, DocumentType.NEWS, null, null)
        );

        assertThat(response.adapter()).isEqualTo("finmind-news-adapter");
        assertThat(response.documentCount()).isEqualTo(1);
        assertThat(hits).hasSize(1);
    }

    @Test
    void fallsBackToYahooFinanceNewsForTwNews() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        DocumentSourceImportService service = new DocumentSourceImportService(
            new AlphaVantageNewsSourceAdapter(null, null, "", ""),
            disabledFmpNewsAdapter(),
            new YahooFinanceNewsSourceAdapter(null, "") {
                @Override
                List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
                    return List.of(new DocumentImportRequest(
                        symbol,
                        market,
                        DocumentType.NEWS,
                        "台積電供應鏈受 AI 需求帶動",
                        "Yahoo Finance",
                        Instant.parse("2026-07-10T00:00:00Z"),
                        "市場關注 AI 伺服器需求與台積電產能展望。"
                    ));
                }
            },
            new YahooFinanceFinancialsSourceAdapter(null, null, ""),
            new FinMindTwNewsSourceAdapter(null, null, "", ""),
            new FinMindTwFinancialReportSourceAdapter(null, null, "", ""),
            new AlphaVantageTranscriptSourceAdapter(null, null, "", ""),
            disabledFmpTranscriptAdapter(),
            new TwseDisclosureSourceAdapter(null, null, ""),
            new DocumentIngestionService(),
            new DocumentEmbeddingService(new HashEmbeddingModel()),
            vectorStore
        );

        DocumentSourceImportService.SourceImportResponse response = service.importNews(Market.TW, "2330", 3);

        assertThat(response.adapter()).isEqualTo("yahoo-finance-news-adapter");
        assertThat(response.documentCount()).isEqualTo(1);
        assertThat(response.chunkCount()).isGreaterThan(0);
    }

    @Test
    void importsFetchedTwFinancialsIntoVectorStore() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        DocumentSourceImportService service = new DocumentSourceImportService(
            new AlphaVantageNewsSourceAdapter(null, null, "", ""),
            disabledFmpNewsAdapter(),
            new YahooFinanceNewsSourceAdapter(null, ""),
            new YahooFinanceFinancialsSourceAdapter(null, null, ""),
            new FinMindTwNewsSourceAdapter(null, null, "", ""),
            new FinMindTwFinancialReportSourceAdapter(null, null, "", "") {
                @Override
                List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
                    return List.of(new DocumentImportRequest(
                        symbol,
                        market,
                        DocumentType.FINANCIAL_REPORT,
                        "2330 月營收 2026-06",
                        "finmind-month-revenue",
                        Instant.parse("2026-07-01T00:00:00Z"),
                        "revenue: 100000000\nrevenue_year: 2026\nrevenue_month: 06"
                    ));
            }
            },
            new AlphaVantageTranscriptSourceAdapter(null, null, "", ""),
            disabledFmpTranscriptAdapter(),
            new TwseDisclosureSourceAdapter(null, null, ""),
            new DocumentIngestionService(),
            new DocumentEmbeddingService(new HashEmbeddingModel()),
            vectorStore
        );

        DocumentSourceImportService.SourceImportResponse response = service.importFinancials(Market.TW, "2330", 3);
        List<RetrievedDocument> hits = new DocumentRetriever(new HashEmbeddingModel(), vectorStore).retrieve(
            new DocumentRetrieveRequest("月營收 revenue", 3, "2330.TW", Market.TW, DocumentType.FINANCIAL_REPORT, null, null)
        );

        assertThat(response.adapter()).isEqualTo("finmind-financials-adapter");
        assertThat(response.documentCount()).isEqualTo(1);
        assertThat(hits).hasSize(1);
    }

    @Test
    void fallsBackToYahooFinanceFinancialsWhenFinMindHasNoDocuments() {
        DocumentSourceImportService service = new DocumentSourceImportService(
            new AlphaVantageNewsSourceAdapter(null, null, "", ""),
            disabledFmpNewsAdapter(),
            new YahooFinanceNewsSourceAdapter(null, ""),
            new YahooFinanceFinancialsSourceAdapter(null, null, "") {
                @Override
                List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
                    return List.of(new DocumentImportRequest(
                        symbol,
                        market,
                        DocumentType.FINANCIAL_REPORT,
                        "AVGO Yahoo Finance 損益表",
                        "yahoo-finance-financials",
                        Instant.parse("2026-07-10T00:00:00Z"),
                        "totalRevenue: 100000\nnetIncome: 20000"
                    ));
                }
            },
            new FinMindTwNewsSourceAdapter(null, null, "", ""),
            new FinMindTwFinancialReportSourceAdapter(null, null, "", ""),
            new AlphaVantageTranscriptSourceAdapter(null, null, "", ""),
            disabledFmpTranscriptAdapter(),
            new TwseDisclosureSourceAdapter(null, null, ""),
            new DocumentIngestionService(),
            new DocumentEmbeddingService(new HashEmbeddingModel()),
            new InMemoryVectorStore()
        );

        DocumentSourceImportService.SourceImportResponse response = service.importFinancials(Market.US, "AVGO", 3);

        assertThat(response.adapter()).isEqualTo("yahoo-finance-financials-adapter");
        assertThat(response.documentCount()).isEqualTo(1);
        assertThat(response.chunkCount()).isGreaterThan(0);
    }

    @Test
    void fallsBackToYahooFinanceNewsForTranscriptAndAnnouncements() {
        DocumentSourceImportService service = new DocumentSourceImportService(
            new AlphaVantageNewsSourceAdapter(null, null, "", ""),
            disabledFmpNewsAdapter(),
            new YahooFinanceNewsSourceAdapter(null, "") {
                @Override
                List<DocumentImportRequest> fetch(Market market, String symbol, int limit) {
                    return List.of(new DocumentImportRequest(
                        symbol,
                        market,
                        DocumentType.NEWS,
                        symbol + " earnings update",
                        "Yahoo Finance",
                        Instant.parse("2026-07-10T00:00:00Z"),
                        "Management commentary and investor reaction were summarized by public news."
                    ));
                }
            },
            new YahooFinanceFinancialsSourceAdapter(null, null, ""),
            new FinMindTwNewsSourceAdapter(null, null, "", ""),
            new FinMindTwFinancialReportSourceAdapter(null, null, "", ""),
            new AlphaVantageTranscriptSourceAdapter(null, null, "", ""),
            disabledFmpTranscriptAdapter(),
            new TwseDisclosureSourceAdapter(null, null, ""),
            new DocumentIngestionService(),
            new DocumentEmbeddingService(new HashEmbeddingModel()),
            new InMemoryVectorStore()
        );

        DocumentSourceImportService.SourceImportResponse transcript = service.importTranscript(Market.US, "AVGO", "2026Q2");
        DocumentSourceImportService.SourceImportResponse announcement = service.importAnnouncements(Market.TW, "2330", 3);

        assertThat(transcript.adapter()).isEqualTo("yahoo-finance-transcript-fallback");
        assertThat(transcript.documentCount()).isEqualTo(1);
        assertThat(announcement.adapter()).isEqualTo("yahoo-finance-announcement-fallback");
        assertThat(announcement.documentCount()).isEqualTo(1);
    }

    private static FmpNewsSourceAdapter disabledFmpNewsAdapter() {
        return new FmpNewsSourceAdapter(null, null, "", "");
    }

    private static FmpTranscriptSourceAdapter disabledFmpTranscriptAdapter() {
        return new FmpTranscriptSourceAdapter(null, null, "", "");
    }
}
