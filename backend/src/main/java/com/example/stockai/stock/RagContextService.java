package com.example.stockai.stock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.stockai.rag.DocumentRetrieveRequest;
import com.example.stockai.rag.DocumentRetriever;

@Service
class RagContextService {
    private final StockService stockService;
    private final DocumentRetriever documentRetriever;

    RagContextService(StockService stockService, DocumentRetriever documentRetriever) {
        this.stockService = stockService;
        this.documentRetriever = documentRetriever;
    }

    RagContext buildAnalysisContext(StockRecord stock, Integer horizonDays) {
        return buildAnalysisContext(stock, horizonDays, "");
    }

    RagContext buildAnalysisContext(StockRecord stock, Integer horizonDays, String ownerEmail) {
        return build(stock, horizonOrDefault(horizonDays), stock.symbol() + " technical outlook earnings guidance risk", ownerEmail);
    }

    RagContext buildChatContext(StockRecord stock, Integer horizonDays, String message) {
        return buildChatContext(stock, horizonDays, message, "");
    }

    RagContext buildChatContext(StockRecord stock, Integer horizonDays, String message, String ownerEmail) {
        RagContext context = build(stock, horizonOrDefault(horizonDays), stock.symbol() + " " + message, ownerEmail);
        return context.withChatToolPlan(ChatToolRouter.plan(context, message));
    }

    private RagContext build(StockRecord stock, int horizonDays, String queryText, String ownerEmail) {
        return new RagContext(
            stock,
            stockService.technicalSummary(stock.market(), stock.symbol()),
            stockService.prediction(stock.market(), stock.symbol(), horizonDays),
            retrieveEvidence(stock, queryText, ownerEmail)
        );
    }

    private List<com.example.stockai.rag.RetrievedDocument> retrieveEvidence(StockRecord stock, String queryText, String ownerEmail) {
        return documentRetriever.retrieve(new DocumentRetrieveRequest(
            queryText,
            3,
            stock.symbol(),
            stock.market(),
            null,
            Instant.now().minus(365, ChronoUnit.DAYS),
            Instant.now()
        ), ownerEmail);
    }

    private static int horizonOrDefault(Integer horizonDays) {
        if (horizonDays == null) {
            return 5;
        }
        if (horizonDays < 1 || horizonDays > 60) {
            throw new IllegalArgumentException("horizonDays must be between 1 and 60");
        }
        return horizonDays;
    }
}
