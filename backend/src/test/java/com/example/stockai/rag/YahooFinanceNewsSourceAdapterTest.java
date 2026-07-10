package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class YahooFinanceNewsSourceAdapterTest {
    @Test
    void parsesRssItemsIntoImportRequests() throws Exception {
        String rss = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <rss version="2.0">
              <channel>
                <item>
                  <title>Broadcom AI demand lifts outlook</title>
                  <link>https://finance.yahoo.com/news/avgo-ai</link>
                  <description>Investors focused on AI networking demand.</description>
                  <pubDate>Fri, 10 Jul 2026 02:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
            """;

        List<DocumentImportRequest> requests = YahooFinanceNewsSourceAdapter.parseRss(rss, "AVGO", Market.US, 5);

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).symbol()).isEqualTo("AVGO");
        assertThat(requests.get(0).docType()).isEqualTo(DocumentType.NEWS);
        assertThat(requests.get(0).title()).isEqualTo("Broadcom AI demand lifts outlook");
        assertThat(requests.get(0).source()).isEqualTo("Yahoo Finance");
        assertThat(requests.get(0).publishedAt()).isEqualTo(Instant.parse("2026-07-10T02:00:00Z"));
        assertThat(requests.get(0).content()).contains("https://finance.yahoo.com/news/avgo-ai");
    }
}
