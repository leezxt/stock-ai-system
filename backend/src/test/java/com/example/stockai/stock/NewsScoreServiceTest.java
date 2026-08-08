package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;
import com.example.stockai.rag.DocumentRetrieveRequest;
import com.example.stockai.rag.DocumentRetriever;
import com.example.stockai.rag.HashEmbeddingModel;
import com.example.stockai.rag.InMemoryVectorStore;
import com.example.stockai.rag.RetrievedDocument;

class NewsScoreServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");

    @Test
    void evaluatesIndexedNewsWithExplainableBullishAndBearishSignals() {
        NewsScoreService service = new NewsScoreService(fakeRetriever(List.of(
            document("bullish", "台積電上調展望", "需求強勁，營收成長並擴產。", "CNA", "2026-08-07T00:00:00Z", 0.90d),
            document("bearish", "供應鏈風險升高", "公司下調指引，受到關稅與裁員風險影響。", "Reuters", "2026-08-06T00:00:00Z", 0.85d),
            document("neutral", "公司例行公告", "董事會公告例行會議日期。", "TWSE", "2026-07-20T00:00:00Z", 0.70d)
        )), Clock.fixed(NOW, ZoneOffset.UTC));

        NewsEvaluationResponse response = service.evaluate(Market.TW, "2330", 5, "demo@example.com");

        assertThat(response.symbol()).isEqualTo("2330.TW");
        assertThat(response.articleCount()).isEqualTo(3);
        assertThat(response.bullishCount()).isEqualTo(1);
        assertThat(response.bearishCount()).isEqualTo(1);
        assertThat(response.neutralCount()).isEqualTo(1);
        assertThat(response.overallScore()).isBetween(-100, 100);
        assertThat(response.confidence()).isBetween(0, 100);
        assertThat(response.source()).isEqualTo("rag-news-score-v1");
        assertThat(response.articles()).extracting(NewsEvaluationResponse.NewsArticleScore::sentiment)
            .containsExactly("BULLISH", "BEARISH", "NEUTRAL");
        assertThat(response.articles().get(0).rationale()).contains("上調", "成長");
    }

    @Test
    void returnsInsufficientDataWhenNoNewsIsIndexed() {
        NewsScoreService service = new NewsScoreService(fakeRetriever(List.of()), Clock.fixed(NOW, ZoneOffset.UTC));

        NewsEvaluationResponse response = service.evaluate(Market.US, "AAPL", 5, "demo@example.com");

        assertThat(response.articleCount()).isZero();
        assertThat(response.overallLabel()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(response.confidence()).isZero();
        assertThat(response.source()).isEqualTo("no-indexed-news");
    }

    @Test
    void rejectsInvalidLimit() {
        NewsScoreService service = new NewsScoreService(fakeRetriever(List.of()), Clock.fixed(NOW, ZoneOffset.UTC));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.evaluate(Market.US, "AAPL", 11, "demo@example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("limit");
    }

    private static DocumentRetriever fakeRetriever(List<RetrievedDocument> documents) {
        return new DocumentRetriever(new HashEmbeddingModel(), new InMemoryVectorStore()) {
            @Override
            public List<RetrievedDocument> retrieve(DocumentRetrieveRequest request, String ownerEmail) {
                return documents;
            }
        };
    }

    private static RetrievedDocument document(
        String id,
        String title,
        String snippet,
        String source,
        String publishedAt,
        double score
    ) {
        return new RetrievedDocument(
            id,
            "2330.TW",
            Market.TW,
            com.example.stockai.rag.DocumentType.NEWS,
            title,
            source,
            Instant.parse(publishedAt),
            snippet,
            score
        );
    }
}
