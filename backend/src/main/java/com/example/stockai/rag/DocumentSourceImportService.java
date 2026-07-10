package com.example.stockai.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.example.stockai.market.Market;
import com.example.stockai.market.SymbolNormalizer;

@Service
public class DocumentSourceImportService {
    private final AlphaVantageNewsSourceAdapter alphaVantageNewsSourceAdapter;
    private final FmpNewsSourceAdapter fmpNewsSourceAdapter;
    private final YahooFinanceNewsSourceAdapter yahooFinanceNewsSourceAdapter;
    private final YahooFinanceFinancialsSourceAdapter yahooFinanceFinancialsSourceAdapter;
    private final FinMindTwNewsSourceAdapter finMindTwNewsSourceAdapter;
    private final FinMindTwFinancialReportSourceAdapter finMindTwFinancialReportSourceAdapter;
    private final AlphaVantageTranscriptSourceAdapter alphaVantageTranscriptSourceAdapter;
    private final FmpTranscriptSourceAdapter fmpTranscriptSourceAdapter;
    private final TwseDisclosureSourceAdapter twseDisclosureSourceAdapter;
    private final DocumentIngestionService documentIngestionService;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final VectorStore vectorStore;

    public DocumentSourceImportService(
        AlphaVantageNewsSourceAdapter alphaVantageNewsSourceAdapter,
        FmpNewsSourceAdapter fmpNewsSourceAdapter,
        YahooFinanceNewsSourceAdapter yahooFinanceNewsSourceAdapter,
        YahooFinanceFinancialsSourceAdapter yahooFinanceFinancialsSourceAdapter,
        FinMindTwNewsSourceAdapter finMindTwNewsSourceAdapter,
        FinMindTwFinancialReportSourceAdapter finMindTwFinancialReportSourceAdapter,
        AlphaVantageTranscriptSourceAdapter alphaVantageTranscriptSourceAdapter,
        FmpTranscriptSourceAdapter fmpTranscriptSourceAdapter,
        TwseDisclosureSourceAdapter twseDisclosureSourceAdapter,
        DocumentIngestionService documentIngestionService,
        DocumentEmbeddingService documentEmbeddingService,
        VectorStore vectorStore
    ) {
        this.alphaVantageNewsSourceAdapter = alphaVantageNewsSourceAdapter;
        this.fmpNewsSourceAdapter = fmpNewsSourceAdapter;
        this.yahooFinanceNewsSourceAdapter = yahooFinanceNewsSourceAdapter;
        this.yahooFinanceFinancialsSourceAdapter = yahooFinanceFinancialsSourceAdapter;
        this.finMindTwNewsSourceAdapter = finMindTwNewsSourceAdapter;
        this.finMindTwFinancialReportSourceAdapter = finMindTwFinancialReportSourceAdapter;
        this.alphaVantageTranscriptSourceAdapter = alphaVantageTranscriptSourceAdapter;
        this.fmpTranscriptSourceAdapter = fmpTranscriptSourceAdapter;
        this.twseDisclosureSourceAdapter = twseDisclosureSourceAdapter;
        this.documentIngestionService = documentIngestionService;
        this.documentEmbeddingService = documentEmbeddingService;
        this.vectorStore = vectorStore;
    }

    public SourceImportResponse importNews(Market market, String symbol, int limit) {
        return importNews(market, symbol, limit, "");
    }

    public SourceImportResponse importNews(Market market, String symbol, int limit, String ownerEmail) {
        final Market normalizedMarket = requireMarket(market);
        final String normalizedSymbol = normalizeSymbol(normalizedMarket, symbol);
        final int normalizedLimit = normalizeLimit(limit);
        if (normalizedMarket == Market.TW) {
            List<DocumentImportRequest> finMindDocuments = fetchSafely(() -> finMindTwNewsSourceAdapter.fetch(normalizedMarket, normalizedSymbol, normalizedLimit));
            if (!finMindDocuments.isEmpty()) {
                return importDocuments(finMindDocuments, normalizedMarket, normalizedSymbol, "finmind-news-adapter", ownerEmail);
            }
            return importDocuments(fetchSafely(() -> yahooFinanceNewsSourceAdapter.fetch(normalizedMarket, normalizedSymbol, normalizedLimit)), normalizedMarket, normalizedSymbol, "yahoo-finance-news-adapter", ownerEmail);
        }
        List<DocumentImportRequest> alphaVantageDocuments = fetchSafely(() -> alphaVantageNewsSourceAdapter.fetch(normalizedMarket, normalizedSymbol, normalizedLimit));
        if (!alphaVantageDocuments.isEmpty()) {
            return importDocuments(alphaVantageDocuments, normalizedMarket, normalizedSymbol, "alpha-vantage-news-adapter", ownerEmail);
        }
        List<DocumentImportRequest> fmpDocuments = fetchSafely(() -> fmpNewsSourceAdapter.fetch(normalizedMarket, normalizedSymbol, normalizedLimit));
        if (!fmpDocuments.isEmpty()) {
            return importDocuments(fmpDocuments, normalizedMarket, normalizedSymbol, "fmp-news-adapter", ownerEmail);
        }
        return importDocuments(fetchSafely(() -> yahooFinanceNewsSourceAdapter.fetch(normalizedMarket, normalizedSymbol, normalizedLimit)), normalizedMarket, normalizedSymbol, "yahoo-finance-news-adapter", ownerEmail);
    }

    public SourceImportResponse importAnnouncements(Market market, String symbol, int limit) {
        return importAnnouncements(market, symbol, limit, "");
    }

    public SourceImportResponse importAnnouncements(Market market, String symbol, int limit, String ownerEmail) {
        final Market normalizedMarket = requireMarket(market);
        final String normalizedSymbol = normalizeSymbol(normalizedMarket, symbol);
        final int normalizedLimit = normalizeLimit(limit);
        List<DocumentImportRequest> disclosureDocuments = fetchSafely(() -> twseDisclosureSourceAdapter.fetch(normalizedMarket, normalizedSymbol, normalizedLimit));
        if (!disclosureDocuments.isEmpty()) {
            return importDocuments(disclosureDocuments, normalizedMarket, normalizedSymbol, "twse-disclosure-adapter", ownerEmail);
        }
        return importDocuments(
            convertDocuments(fetchSafely(() -> yahooFinanceNewsSourceAdapter.fetch(normalizedMarket, normalizedSymbol, normalizedLimit)), DocumentType.COMPANY_ANNOUNCEMENT, "yahoo-finance-announcement-fallback"),
            normalizedMarket,
            normalizedSymbol,
            "yahoo-finance-announcement-fallback",
            ownerEmail
        );
    }

    public SourceImportResponse importTranscript(Market market, String symbol, String quarter) {
        return importTranscript(market, symbol, quarter, "");
    }

    public SourceImportResponse importTranscript(Market market, String symbol, String quarter, String ownerEmail) {
        final Market normalizedMarket = requireMarket(market);
        final String normalizedSymbol = normalizeSymbol(normalizedMarket, symbol);
        List<DocumentImportRequest> transcriptDocuments = fetchOneSafely(() -> alphaVantageTranscriptSourceAdapter.fetch(normalizedMarket, normalizedSymbol, quarter)).stream().toList();
        if (!transcriptDocuments.isEmpty()) {
            return importDocuments(transcriptDocuments, normalizedMarket, normalizedSymbol, "earnings-call-adapter", ownerEmail);
        }
        transcriptDocuments = fetchOneSafely(() -> fmpTranscriptSourceAdapter.fetch(normalizedMarket, normalizedSymbol, quarter)).stream().toList();
        if (!transcriptDocuments.isEmpty()) {
            return importDocuments(transcriptDocuments, normalizedMarket, normalizedSymbol, "fmp-earnings-transcript-adapter", ownerEmail);
        }
        return importDocuments(
            convertDocuments(fetchSafely(() -> yahooFinanceNewsSourceAdapter.fetch(normalizedMarket, normalizedSymbol, 5)), DocumentType.EARNINGS_TRANSCRIPT, "yahoo-finance-transcript-fallback"),
            normalizedMarket,
            normalizedSymbol,
            "yahoo-finance-transcript-fallback",
            ownerEmail
        );
    }

    public SourceImportResponse importFinancials(Market market, String symbol, int limit) {
        return importFinancials(market, symbol, limit, "");
    }

    public SourceImportResponse importFinancials(Market market, String symbol, int limit, String ownerEmail) {
        final Market normalizedMarket = requireMarket(market);
        final String normalizedSymbol = normalizeSymbol(normalizedMarket, symbol);
        final int normalizedLimit = normalizeLimit(limit);
        List<DocumentImportRequest> finMindDocuments = fetchSafely(() -> finMindTwFinancialReportSourceAdapter.fetch(normalizedMarket, normalizedSymbol, normalizedLimit));
        if (!finMindDocuments.isEmpty()) {
            return importDocuments(finMindDocuments, normalizedMarket, normalizedSymbol, "finmind-financials-adapter", ownerEmail);
        }
        return importDocuments(fetchSafely(() -> yahooFinanceFinancialsSourceAdapter.fetch(normalizedMarket, normalizedSymbol, normalizedLimit)), normalizedMarket, normalizedSymbol, "yahoo-finance-financials-adapter", ownerEmail);
    }

    private SourceImportResponse importDocuments(List<DocumentImportRequest> documents, Market market, String symbol, String adapter, String ownerEmail) {
        documents = documents == null ? List.of() : documents.stream().filter(Objects::nonNull).toList();
        List<DocumentChunk> chunks = new ArrayList<>();
        for (DocumentImportRequest document : documents) {
            chunks.addAll(documentIngestionService.ingest(document, ownerEmail));
        }
        vectorStore.upsert(documentEmbeddingService.embed(chunks));
        List<String> titles = documents.stream().map(DocumentImportRequest::title).toList();
        return new SourceImportResponse(
            market,
            symbol,
            adapter,
            documents.size(),
            chunks.size(),
            titles
        );
    }

    private List<DocumentImportRequest> convertDocuments(List<DocumentImportRequest> documents, DocumentType docType, String source) {
        return documents.stream()
            .map(document -> new DocumentImportRequest(
                document.symbol(),
                document.market(),
                docType,
                document.title(),
                source,
                document.publishedAt(),
                document.content()
            ))
            .toList();
    }

    private static List<DocumentImportRequest> fetchSafely(Supplier<List<DocumentImportRequest>> fetcher) {
        try {
            List<DocumentImportRequest> documents = fetcher.get();
            return documents == null ? List.of() : documents;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static Optional<DocumentImportRequest> fetchOneSafely(Supplier<Optional<DocumentImportRequest>> fetcher) {
        try {
            Optional<DocumentImportRequest> document = fetcher.get();
            return document == null ? Optional.empty() : document;
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static Market requireMarket(Market market) {
        if (market == null) {
            throw new IllegalArgumentException("market must not be null");
        }
        return market;
    }

    private static String normalizeSymbol(Market market, String symbol) {
        return SymbolNormalizer.normalize(market, symbol);
    }

    private static int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 10));
    }

    public record SourceImportResponse(
        Market market,
        String symbol,
        String adapter,
        int documentCount,
        int chunkCount,
        List<String> titles
    ) {}
}
