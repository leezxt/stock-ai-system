package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.stockai.market.Market;

class TwseDisclosureSourceAdapterTest {
    @Test
    void parsesDisclosureRowsIntoImportRequests() {
        List<DocumentImportRequest> requests = TwseDisclosureSourceAdapter.parseDisclosureResponse(
            Map.of(
                "data", List.of(
                    Map.of(
                        "title", "台積電重大訊息",
                        "source", "MOPS",
                        "publishedAt", "2026-07-06T08:00:00Z",
                        "content", "董事會通過資本支出案。"
                    )
                )
            ),
            "2330.TW",
            Market.TW,
            3
        );

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).docType()).isEqualTo(DocumentType.COMPANY_ANNOUNCEMENT);
        assertThat(requests.get(0).source()).isEqualTo("MOPS");
        assertThat(requests.get(0).content()).contains("資本支出");
    }

    @Test
    void parsesMopsHtmlRowsIntoImportRequests() {
        String html = """
            <table>
              <tr><th>公司代號</th><th>公司簡稱</th><th>發言日期</th><th>發言時間</th><th>主旨</th></tr>
              <tr>
                <td>2330</td>
                <td>台積電</td>
                <td>115/07/10</td>
                <td>15:30:00</td>
                <td>公告本公司董事會通過資本支出案</td>
              </tr>
            </table>
            """;

        List<DocumentImportRequest> requests = TwseDisclosureSourceAdapter.parseMopsHtml(html, "2330.TW", Market.TW, 3);

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).symbol()).isEqualTo("2330.TW");
        assertThat(requests.get(0).docType()).isEqualTo(DocumentType.COMPANY_ANNOUNCEMENT);
        assertThat(requests.get(0).source()).isEqualTo("MOPS 重大訊息");
        assertThat(requests.get(0).title()).contains("台積電", "資本支出");
        assertThat(requests.get(0).content()).contains("115/07/10", "公告本公司董事會通過資本支出案");
    }
}
