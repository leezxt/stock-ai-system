package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class DocumentChunkTest {
    @Test
    void normalizesSymbolAndTrimsText() {
        DocumentChunk chunk = new DocumentChunk(
            " chunk-1 ",
            "2330",
            Market.TW,
            DocumentType.NEWS,
            " TSMC revenue update ",
            " TWSE ",
            Instant.parse("2026-07-06T00:00:00Z"),
            " Revenue grew 12% year over year. "
        );

        assertThat(chunk.chunkId()).isEqualTo("chunk-1");
        assertThat(chunk.symbol()).isEqualTo("2330.TW");
        assertThat(chunk.title()).isEqualTo("TSMC revenue update");
        assertThat(chunk.source()).isEqualTo("TWSE");
        assertThat(chunk.content()).isEqualTo("Revenue grew 12% year over year.");
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> new DocumentChunk(
            "chunk-2",
            "AAPL",
            Market.US,
            DocumentType.COMPANY_ANNOUNCEMENT,
            "Apple filing",
            "SEC",
            Instant.parse("2026-07-06T00:00:00Z"),
            " "
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("content must not be blank");
    }

    @Test
    void rejectsUnboundedImportAndRetrieveRequests() {
        assertThatThrownBy(() -> new DocumentImportRequest(
            "AAPL", Market.US, DocumentType.NEWS, "Title", "Source", Instant.now(), "x".repeat(200_001)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("200000");

        assertThatThrownBy(() -> new DocumentRetrieveRequest("query", 21, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 1 and 20");
    }
}
