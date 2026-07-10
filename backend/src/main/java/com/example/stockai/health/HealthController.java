package com.example.stockai.health;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ExternalApiKeyHeaderResolver;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private static final String DEFAULT_TWSE_DISCLOSURE_URL = "https://mopsov.twse.com.tw/mops/web/ajax_t05st01?firstin=1&TYPEK=all&co_id={symbol}&year={rocYear}&month=&b_date=&e_date=";

    @Value("${stockai.openai.api-key:${OPENAI_API_KEY:}}")
    private String openAiApiKey = "";

    @Value("${stockai.openai.model:${OPENAI_MODEL:gpt-5.5}}")
    private String openAiModel = "gpt-5.5";

    @Value("${stockai.gemini.api-key:${GEMINI_API_KEY:}}")
    private String geminiApiKey = "";

    @Value("${stockai.gemini.model:${GEMINI_MODEL:gemini-2.0-flash}}")
    private String geminiModel = "gemini-2.0-flash";

    @Value("${stockai.deepseek.api-key:${DEEPSEEK_API_KEY:}}")
    private String deepSeekApiKey = "";

    @Value("${stockai.deepseek.model:${DEEPSEEK_MODEL:deepseek-chat}}")
    private String deepSeekModel = "deepseek-chat";

    @Value("${stockai.mimo.api-key:${MIMO_API_KEY:}}")
    private String mimoApiKey = "";

    @Value("${stockai.mimo.model:${MIMO_MODEL:mimo-v2.5-pro}}")
    private String mimoModel = "mimo-v2.5-pro";

    @Value("${stockai.alpha-vantage.api-key:${ALPHAVANTAGE_API_KEY:}}")
    private String alphaVantageApiKey = "";

    @Value("${stockai.fmp.api-key:${FMP_API_KEY:}}")
    private String fmpApiKey = "";

    @Value("${stockai.fmp.base-url:https://financialmodelingprep.com/stable}")
    private String fmpBaseUrl = "https://financialmodelingprep.com/stable";

    @Value("${stockai.finmind.base-url:${FINMIND_BASE_URL:https://api.finmindtrade.com/api/v4}}")
    private String finMindBaseUrl = "https://api.finmindtrade.com/api/v4";

    @Value("${stockai.finmind.token:${FINMIND_API_TOKEN:}}")
    private String finMindToken = "";

    @Value("${stockai.yahoo.chart-url:https://query1.finance.yahoo.com/v8/finance/chart}")
    private String yahooChartUrl = "https://query1.finance.yahoo.com/v8/finance/chart";

    @Value("${stockai.yahoo.news-rss-url:https://feeds.finance.yahoo.com/rss/2.0/headline}")
    private String yahooNewsRssUrl = "https://feeds.finance.yahoo.com/rss/2.0/headline";

    @Value("${stockai.yahoo.quote-summary-url:https://query2.finance.yahoo.com/v10/finance/quoteSummary}")
    private String yahooQuoteSummaryUrl = "https://query2.finance.yahoo.com/v10/finance/quoteSummary";

    @Value("${stockai.twse.stock-day-all-url:https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL}")
    private String twseStockDayAllUrl = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL";

    @Value("${stockai.twse.realtime-url:https://mis.twse.com.tw/stock/api/getStockInfo.jsp}")
    private String twseRealtimeUrl = "https://mis.twse.com.tw/stock/api/getStockInfo.jsp";

    @Value("${stockai.twse.disclosure-url:" + DEFAULT_TWSE_DISCLOSURE_URL + "}")
    private String twseDisclosureUrl = DEFAULT_TWSE_DISCLOSURE_URL;

    @Autowired(required = false)
    private ExternalApiKeyHeaderResolver externalApiKeyHeaderResolver;

    @GetMapping
    HealthResponse health() {
        boolean alphaVantageConfigured = !isBlank(resolveAlphaVantageApiKey());
        boolean finMindTokenConfigured = !isBlank(resolveFinMindToken());
        boolean fmpConfigured = !isBlank(resolveFmpApiKey()) && !isBlank(fmpBaseUrl);
        return new HealthResponse("UP", Instant.now(), new ProviderReadiness(
            !isBlank(openAiApiKey),
            isBlank(openAiModel) ? "gpt-5.5" : openAiModel.trim(),
            !isBlank(geminiApiKey),
            isBlank(geminiModel) ? "gemini-2.0-flash" : geminiModel.trim(),
            !isBlank(deepSeekApiKey),
            isBlank(deepSeekModel) ? "deepseek-chat" : deepSeekModel.trim(),
            !isBlank(mimoApiKey),
            isBlank(mimoModel) ? "mimo-v2.5-pro" : mimoModel.trim(),
            alphaVantageConfigured,
            alphaVantageConfigured,
            alphaVantageConfigured,
            fmpConfigured,
            fmpConfigured,
            fmpConfigured,
            fmpBaseUrl,
            !isBlank(finMindBaseUrl),
            !isBlank(finMindBaseUrl),
            !isBlank(finMindBaseUrl),
            finMindTokenConfigured,
            finMindBaseUrl,
            !isBlank(yahooChartUrl),
            yahooChartUrl,
            !isBlank(yahooNewsRssUrl),
            yahooNewsRssUrl,
            !isBlank(yahooQuoteSummaryUrl),
            yahooQuoteSummaryUrl,
            !isBlank(twseRealtimeUrl),
            twseRealtimeUrl,
            !isBlank(twseStockDayAllUrl),
            twseStockDayAllUrl,
            !isBlank(twseDisclosureUrl),
            twseDisclosureUrl
        ));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveAlphaVantageApiKey() {
        return externalApiKeyHeaderResolver == null
            ? alphaVantageApiKey
            : externalApiKeyHeaderResolver.resolveAlphaVantage(alphaVantageApiKey);
    }

    private String resolveFinMindToken() {
        return externalApiKeyHeaderResolver == null
            ? finMindToken
            : externalApiKeyHeaderResolver.resolveFinMindToken(finMindToken);
    }

    private String resolveFmpApiKey() {
        return externalApiKeyHeaderResolver == null
            ? fmpApiKey
            : externalApiKeyHeaderResolver.resolveFmp(fmpApiKey);
    }

    public record HealthResponse(String status, Instant timestamp, ProviderReadiness providers) {}

    public record ProviderReadiness(
        boolean openAiConfigured,
        String openAiModel,
        boolean geminiConfigured,
        String geminiModel,
        boolean deepSeekConfigured,
        String deepSeekModel,
        boolean mimoConfigured,
        String mimoModel,
        boolean alphaVantageConfigured,
        boolean alphaVantageNewsConfigured,
        boolean alphaVantageTranscriptConfigured,
        boolean fmpConfigured,
        boolean fmpNewsConfigured,
        boolean fmpTranscriptConfigured,
        String fmpBaseUrl,
        boolean finMindConfigured,
        boolean finMindNewsConfigured,
        boolean finMindFinancialsConfigured,
        boolean finMindTokenConfigured,
        String finMindBaseUrl,
        boolean yahooFinanceUsConfigured,
        String yahooFinanceUsChartUrl,
        boolean yahooFinanceNewsConfigured,
        String yahooFinanceNewsRssUrl,
        boolean yahooFinanceFinancialsConfigured,
        String yahooFinanceQuoteSummaryUrl,
        boolean twseRealtimeConfigured,
        String twseRealtimeUrl,
        boolean twseEndpointConfigured,
        String twseStockDayAllUrl,
        boolean twseDisclosureConfigured,
        String twseDisclosureUrl
    ) {}
}
