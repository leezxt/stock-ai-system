package com.example.stockai.stock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.stockai.market.Market;
import com.example.stockai.market.SymbolNormalizer;
import com.example.stockai.rag.DocumentRetrieveRequest;
import com.example.stockai.rag.DocumentRetriever;
import com.example.stockai.rag.DocumentType;
import com.example.stockai.rag.RetrievedDocument;

/**
 * Produces an explainable news signal from indexed NEWS documents. This is a
 * deterministic screening score, not a claim that a keyword model predicts
 * the stock price.
 */
@Service
public class NewsScoreService {
    private static final String SCORE_VERSION = "rag-news-score-v1";
    private static final int MAX_ARTICLES = 10;
    private static final List<Signal> BULLISH_SIGNALS = List.of(
        new Signal("超預期", 4), new Signal("上調", 3), new Signal("成長", 2),
        new Signal("改善", 2), new Signal("強勁", 2), new Signal("利多", 3),
        new Signal("獲利", 2), new Signal("擴產", 2), new Signal("回購", 3),
        new Signal("看好", 2), new Signal("升級", 2), new Signal("突破", 2),
        new Signal("beat", 3), new Signal("strong", 2), new Signal("growth", 2),
        new Signal("improve", 2), new Signal("upgrade", 2), new Signal("bullish", 3),
        new Signal("surge", 3), new Signal("record", 2), new Signal("profit", 2),
        new Signal("raised guidance", 4), new Signal("demand", 1)
    );
    private static final List<Signal> BEARISH_SIGNALS = List.of(
        new Signal("低於預期", 4), new Signal("下調", 3), new Signal("衰退", 3),
        new Signal("下滑", 2), new Signal("利空", 3), new Signal("虧損", 3),
        new Signal("風險", 2), new Signal("減產", 3), new Signal("裁員", 2),
        new Signal("訴訟", 3), new Signal("調查", 3), new Signal("制裁", 3),
        new Signal("關稅", 2), new Signal("跌破", 2), new Signal("降評", 3),
        new Signal("miss", 3), new Signal("weak", 2), new Signal("decline", 2),
        new Signal("drop", 2), new Signal("downgrade", 3), new Signal("bearish", 3),
        new Signal("loss", 3), new Signal("lawsuit", 3), new Signal("investigation", 3),
        new Signal("recall", 3), new Signal("tariff", 2), new Signal("sanction", 3),
        new Signal("cut guidance", 4), new Signal("risk", 2)
    );

    private final DocumentRetriever documentRetriever;
    private final Clock clock;

    @Autowired
    public NewsScoreService(DocumentRetriever documentRetriever) {
        this(documentRetriever, Clock.systemUTC());
    }

    NewsScoreService(DocumentRetriever documentRetriever, Clock clock) {
        this.documentRetriever = Objects.requireNonNull(documentRetriever, "documentRetriever must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public NewsEvaluationResponse evaluate(Market market, String symbol, int limit, String ownerEmail) {
        if (market == null) {
            throw new IllegalArgumentException("market must not be null");
        }
        if (limit < 1 || limit > MAX_ARTICLES) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_ARTICLES);
        }
        String normalizedSymbol = SymbolNormalizer.normalize(market, symbol);
        Instant evaluatedAt = Instant.now(clock);
        List<RetrievedDocument> retrieved = documentRetriever.retrieve(
            new DocumentRetrieveRequest(
                normalizedSymbol + " 新聞 news sentiment 情緒 outlook guidance risk earnings",
                Math.min(20, Math.max(limit, limit * 2)),
                normalizedSymbol,
                market,
                DocumentType.NEWS,
                evaluatedAt.minus(Duration.ofDays(180)),
                evaluatedAt
            ),
            ownerEmail
        );
        List<RetrievedDocument> articles = deduplicate(retrieved).stream().limit(limit).toList();
        List<NewsEvaluationResponse.NewsArticleScore> scores = articles.stream()
            .map(article -> scoreArticle(article, evaluatedAt))
            .toList();
        return aggregate(market, normalizedSymbol, scores, evaluatedAt);
    }

    private static List<RetrievedDocument> deduplicate(List<RetrievedDocument> documents) {
        Map<String, RetrievedDocument> unique = new LinkedHashMap<>();
        for (RetrievedDocument document : documents == null ? List.<RetrievedDocument>of() : documents) {
            if (document == null) {
                continue;
            }
            String key = (document.title() + "|" + document.source() + "|" + document.publishedAt()).toLowerCase(Locale.ROOT);
            RetrievedDocument previous = unique.get(key);
            if (previous == null || document.score() > previous.score()) {
                unique.put(key, document);
            }
        }
        return List.copyOf(unique.values());
    }

    private static NewsEvaluationResponse.NewsArticleScore scoreArticle(RetrievedDocument article, Instant evaluatedAt) {
        String text = (article.title() + " " + article.snippet()).toLowerCase(Locale.ROOT);
        List<String> bullish = matchedTerms(text, BULLISH_SIGNALS);
        List<String> bearish = matchedTerms(text, BEARISH_SIGNALS);
        int bullishWeight = totalWeight(text, BULLISH_SIGNALS);
        int bearishWeight = totalWeight(text, BEARISH_SIGNALS);
        int sentimentScore = clamp((bullishWeight - bearishWeight) * 12, -100, 100);
        String sentiment = sentimentScore >= 20 ? "BULLISH" : sentimentScore <= -20 ? "BEARISH" : "NEUTRAL";
        double relevance = clamp(article.score(), 0d, 1d);
        int matches = bullish.size() + bearish.size();
        int recencyBoost = recencyBoost(article.publishedAt(), evaluatedAt);
        int impact = clamp((int) Math.round(30d + Math.abs(sentimentScore) * 0.35d + relevance * 25d + recencyBoost), 0, 100);
        int confidence = clamp(20 + matches * 10 + (int) Math.round(relevance * 30d), 0, 100);
        return new NewsEvaluationResponse.NewsArticleScore(
            article.chunkId(),
            article.title(),
            article.source(),
            article.publishedAt(),
            article.snippet(),
            round(relevance, 3),
            sentimentScore,
            sentiment,
            impact,
            confidence,
            rationale(bullish, bearish, matches)
        );
    }

    private static NewsEvaluationResponse aggregate(
        Market market,
        String symbol,
        List<NewsEvaluationResponse.NewsArticleScore> articles,
        Instant evaluatedAt
    ) {
        int bullish = (int) articles.stream().filter(article -> "BULLISH".equals(article.sentiment())).count();
        int bearish = (int) articles.stream().filter(article -> "BEARISH".equals(article.sentiment())).count();
        int neutral = articles.size() - bullish - bearish;
        if (articles.isEmpty()) {
            return new NewsEvaluationResponse(
                market, symbol, 0, 0, 0, 0, 0, "INSUFFICIENT_DATA", 0,
                null, evaluatedAt, "no-indexed-news", List.of()
            );
        }
        double totalWeight = articles.stream().mapToDouble(article -> Math.max(1, article.impactScore())).sum();
        double weightedScore = articles.stream()
            .mapToDouble(article -> article.sentimentScore() * Math.max(1, article.impactScore()))
            .sum() / totalWeight;
        int overallScore = clamp((int) Math.round(weightedScore), -100, 100);
        int confidence = clamp((int) Math.round(articles.stream()
            .mapToDouble(article -> article.confidence() * Math.max(1, article.impactScore()))
            .sum() / totalWeight), 0, 100);
        Instant latest = articles.stream()
            .map(NewsEvaluationResponse.NewsArticleScore::publishedAt)
            .filter(Objects::nonNull)
            .max(Instant::compareTo)
            .orElse(null);
        return new NewsEvaluationResponse(
            market,
            symbol,
            articles.size(),
            bullish,
            bearish,
            neutral,
            overallScore,
            overallScore >= 15 ? "BULLISH" : overallScore <= -15 ? "BEARISH" : "NEUTRAL",
            confidence,
            latest,
            evaluatedAt,
            SCORE_VERSION,
            articles
        );
    }

    private static List<String> matchedTerms(String text, List<Signal> signals) {
        List<String> matches = new ArrayList<>();
        for (Signal signal : signals) {
            if (text.contains(signal.term())) {
                matches.add(signal.term());
            }
        }
        return List.copyOf(matches);
    }

    private static int totalWeight(String text, List<Signal> signals) {
        return signals.stream().filter(signal -> text.contains(signal.term())).mapToInt(Signal::weight).sum();
    }

    private static String rationale(List<String> bullish, List<String> bearish, int matches) {
        if (matches == 0) {
            return "未命中明確的多空詞，先判定為中性；請搭配原文與發布來源複核。";
        }
        StringJoiner joiner = new StringJoiner("；");
        if (!bullish.isEmpty()) {
            joiner.add("偏多詞：" + String.join("、", bullish));
        }
        if (!bearish.isEmpty()) {
            joiner.add("偏空詞：" + String.join("、", bearish));
        }
        return joiner + "。此為規則式新聞訊號，不是價格預測。";
    }

    private static int recencyBoost(Instant publishedAt, Instant evaluatedAt) {
        if (publishedAt == null) {
            return 0;
        }
        Duration age = Duration.between(publishedAt, evaluatedAt);
        if (age.isNegative() || age.compareTo(Duration.ofDays(7)) <= 0) {
            return 20;
        }
        if (age.compareTo(Duration.ofDays(30)) <= 0) {
            return 10;
        }
        return 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private record Signal(String term, int weight) {
        private Signal {
            term = term.toLowerCase(Locale.ROOT);
        }
    }
}
