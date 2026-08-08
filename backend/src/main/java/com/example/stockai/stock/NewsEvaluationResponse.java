package com.example.stockai.stock;

import java.time.Instant;
import java.util.List;

import com.example.stockai.market.Market;

/**
 * A deterministic, evidence-linked news signal. It is deliberately separate
 * from the AI score so callers can see which indexed articles drove the
 * result.
 */
public record NewsEvaluationResponse(
    Market market,
    String symbol,
    int articleCount,
    int bullishCount,
    int bearishCount,
    int neutralCount,
    int overallScore,
    String overallLabel,
    int confidence,
    Instant latestPublishedAt,
    Instant evaluatedAt,
    String source,
    List<NewsArticleScore> articles
) {
    public NewsEvaluationResponse {
        if (market == null) {
            throw new IllegalArgumentException("market must not be null");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (articleCount < 0 || bullishCount < 0 || bearishCount < 0 || neutralCount < 0) {
            throw new IllegalArgumentException("article counts must not be negative");
        }
        if (bullishCount + bearishCount + neutralCount != articleCount) {
            throw new IllegalArgumentException("article counts must add up to articleCount");
        }
        if (overallScore < -100 || overallScore > 100) {
            throw new IllegalArgumentException("overallScore must be between -100 and 100");
        }
        if (confidence < 0 || confidence > 100) {
            throw new IllegalArgumentException("confidence must be between 0 and 100");
        }
        symbol = symbol.trim();
        overallLabel = overallLabel == null || overallLabel.isBlank() ? "INSUFFICIENT_DATA" : overallLabel.trim();
        source = source == null ? "" : source.trim();
        articles = articles == null ? List.of() : List.copyOf(articles);
    }

    public record NewsArticleScore(
        String chunkId,
        String title,
        String source,
        Instant publishedAt,
        String snippet,
        double relevanceScore,
        int sentimentScore,
        String sentiment,
        int impactScore,
        int confidence,
        String rationale
    ) {
        public NewsArticleScore {
            if (chunkId == null || chunkId.isBlank()) {
                throw new IllegalArgumentException("chunkId must not be blank");
            }
            if (sentimentScore < -100 || sentimentScore > 100) {
                throw new IllegalArgumentException("sentimentScore must be between -100 and 100");
            }
            if (impactScore < 0 || impactScore > 100 || confidence < 0 || confidence > 100) {
                throw new IllegalArgumentException("impactScore and confidence must be between 0 and 100");
            }
            if (!Double.isFinite(relevanceScore) || relevanceScore < 0d || relevanceScore > 1d) {
                throw new IllegalArgumentException("relevanceScore must be between 0 and 1");
            }
            chunkId = chunkId.trim();
            title = title == null ? "" : title.trim();
            source = source == null ? "" : source.trim();
            snippet = snippet == null ? "" : snippet.trim();
            sentiment = sentiment == null || sentiment.isBlank() ? "NEUTRAL" : sentiment.trim();
            rationale = rationale == null ? "" : rationale.trim();
        }
    }
}
