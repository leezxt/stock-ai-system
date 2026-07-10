package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class DocumentIngestionServiceTest {
    @Test
    void cleansAndKeepsSingleChunkWhenContentIsShort() {
        DocumentIngestionService service = new DocumentIngestionService(120, 20);

        List<DocumentChunk> chunks = service.ingest(new DocumentImportRequest(
            "aapl",
            Market.US,
            DocumentType.NEWS,
            "Apple update",
            "Reuters",
            Instant.parse("2026-07-06T00:00:00Z"),
            " First line.\r\n\r\nSecond\t\tline. \r\n\r\n\r\n Third line. "
        ));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).symbol()).isEqualTo("AAPL");
        assertThat(chunks.get(0).content()).isEqualTo("First line.\n\nSecond line.\n\nThird line.");
    }

    @Test
    void splitsLongContentIntoOverlappingChunks() {
        DocumentIngestionService service = new DocumentIngestionService(100, 10);
        String content = "Alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau upsilon phi chi psi omega.";

        List<DocumentChunk> chunks = service.ingest(new DocumentImportRequest(
            "2330",
            Market.TW,
            DocumentType.COMPANY_ANNOUNCEMENT,
            "TSMC filing",
            "TWSE",
            Instant.parse("2026-07-06T00:00:00Z"),
            content
        ));

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(0).chunkId()).endsWith("-0");
        assertThat(chunks.get(1).chunkId()).endsWith("-1");
        assertThat(chunks.get(0).content().length()).isLessThanOrEqualTo(100);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content()).isNotBlank());
        assertThat(chunks.stream().map(DocumentChunk::content).reduce("", (a, b) -> a + " " + b))
            .contains("lambda")
            .contains("omega");
    }
}
