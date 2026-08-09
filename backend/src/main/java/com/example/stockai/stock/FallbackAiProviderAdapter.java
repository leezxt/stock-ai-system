package com.example.stockai.stock;

import java.util.Optional;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.example.stockai.common.RequestIdFilter;

@Service
@Primary
class FallbackAiProviderAdapter implements AiProviderAdapter {
    private static final Logger log = LoggerFactory.getLogger(FallbackAiProviderAdapter.class);

    private final OpenAiProviderAdapter openAiProviderAdapter;
    private final GeminiProviderAdapter geminiProviderAdapter;
    private final DeepSeekProviderAdapter deepSeekProviderAdapter;
    private final MimoProviderAdapter mimoProviderAdapter;
    private final MockAiProviderAdapter mockAiProviderAdapter;
    private final AiProviderTelemetry telemetry;

    FallbackAiProviderAdapter(
        OpenAiProviderAdapter openAiProviderAdapter,
        GeminiProviderAdapter geminiProviderAdapter,
        DeepSeekProviderAdapter deepSeekProviderAdapter,
        MimoProviderAdapter mimoProviderAdapter,
        MockAiProviderAdapter mockAiProviderAdapter
    ) {
        this(openAiProviderAdapter, geminiProviderAdapter, deepSeekProviderAdapter, mimoProviderAdapter, mockAiProviderAdapter, new AiProviderTelemetry());
    }

    @Autowired
    FallbackAiProviderAdapter(
        OpenAiProviderAdapter openAiProviderAdapter,
        GeminiProviderAdapter geminiProviderAdapter,
        DeepSeekProviderAdapter deepSeekProviderAdapter,
        MimoProviderAdapter mimoProviderAdapter,
        MockAiProviderAdapter mockAiProviderAdapter,
        AiProviderTelemetry telemetry
    ) {
        this.openAiProviderAdapter = openAiProviderAdapter;
        this.geminiProviderAdapter = geminiProviderAdapter;
        this.deepSeekProviderAdapter = deepSeekProviderAdapter;
        this.mimoProviderAdapter = mimoProviderAdapter;
        this.mockAiProviderAdapter = mockAiProviderAdapter;
        this.telemetry = telemetry;
    }

    @Override
    public AiProviderResult analyze(StockRecord stock, RagContext context, String provider, int index) {
        if ("GEMINI".equalsIgnoreCase(provider)) {
            return runAnalysis(provider,
                () -> geminiProviderAdapter.tryAnalyze(stock, context, provider),
                () -> fallbackAnalysis(stock, context, provider, index,
                    geminiProviderAdapter.hasApiKey() ? "mock-ai:gemini-live-failed" : "mock-ai:no-gemini-key"));
        }
        if ("DEEPSEEK".equalsIgnoreCase(provider)) {
            return runAnalysis(provider,
                () -> deepSeekProviderAdapter.tryAnalyze(stock, context, provider),
                () -> fallbackAnalysis(stock, context, provider, index,
                    deepSeekProviderAdapter.hasApiKey() ? "mock-ai:deepseek-live-failed" : "mock-ai:no-deepseek-key"));
        }
        if ("MIMO".equalsIgnoreCase(provider)) {
            return runAnalysis(provider,
                () -> mimoProviderAdapter.tryAnalyze(stock, context, provider),
                () -> fallbackAnalysis(stock, context, provider, index,
                    mimoProviderAdapter.hasApiKey() ? "mock-ai:mimo-live-failed" : "mock-ai:no-mimo-key"));
        }
        return runAnalysis(provider,
            () -> openAiProviderAdapter.tryAnalyze(stock, context, provider),
            () -> fallbackAnalysis(stock, context, provider, index, openAiProviderAdapter.fallbackSource()));
    }

    @Override
    public AiChatResult chatMessage(StockRecord stock, RagContext context, String provider, String message) {
        return chatMessage(stock, context, provider, message, List.of());
    }

    @Override
    public AiChatResult chatMessage(StockRecord stock, RagContext context, String provider, String message, List<ChatTurn> history) {
        if ("GEMINI".equalsIgnoreCase(provider)) {
            return runChat(provider,
                () -> geminiProviderAdapter.tryChatMessage(stock, context, provider, message, history),
                () -> fallbackChat(stock, context, provider, message,
                    geminiProviderAdapter.hasApiKey() ? "mock-ai:gemini-live-failed" : "mock-ai:no-gemini-key"));
        }
        if ("DEEPSEEK".equalsIgnoreCase(provider)) {
            return runChat(provider,
                () -> deepSeekProviderAdapter.tryChatMessage(stock, context, provider, message, history),
                () -> fallbackChat(stock, context, provider, message,
                    deepSeekProviderAdapter.hasApiKey() ? "mock-ai:deepseek-live-failed" : "mock-ai:no-deepseek-key"));
        }
        if ("MIMO".equalsIgnoreCase(provider)) {
            return runChat(provider,
                () -> mimoProviderAdapter.tryChatMessage(stock, context, provider, message, history),
                () -> fallbackChat(stock, context, provider, message,
                    mimoProviderAdapter.hasApiKey() ? "mock-ai:mimo-live-failed" : "mock-ai:no-mimo-key"));
        }
        return runChat(provider,
            () -> openAiProviderAdapter.tryChatMessage(stock, context, provider, message, history),
            () -> fallbackChat(stock, context, provider, message, openAiProviderAdapter.fallbackSource()));
    }

    private AiProviderResult runAnalysis(String provider, Supplier<Optional<AiProviderResult>> liveCall, Supplier<AiProviderResult> fallback) {
        long started = System.nanoTime();
        try {
            Optional<AiProviderResult> result = liveCall.get();
            telemetry.record(provider, "analysis", result.isPresent(), false, elapsedMillis(started));
            return result.orElseGet(fallback);
        } catch (RuntimeException ex) {
            telemetry.record(provider, "analysis", false, true, elapsedMillis(started));
            log.warn("AI analysis provider raised {} provider={} requestId={}", ex.getClass().getSimpleName(), provider, requestId());
            return fallback.get();
        }
    }

    private AiChatResult runChat(String provider, Supplier<Optional<AiChatResult>> liveCall, Supplier<AiChatResult> fallback) {
        long started = System.nanoTime();
        try {
            Optional<AiChatResult> result = liveCall.get();
            telemetry.record(provider, "chat", result.isPresent(), false, elapsedMillis(started));
            return result.orElseGet(fallback);
        } catch (RuntimeException ex) {
            telemetry.record(provider, "chat", false, true, elapsedMillis(started));
            log.warn("AI chat provider raised {} provider={} requestId={}", ex.getClass().getSimpleName(), provider, requestId());
            return fallback.get();
        }
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private AiProviderResult fallbackAnalysis(StockRecord stock, RagContext context, String provider, int index, String source) {
        log.warn("AI analysis fallback provider={} source={} symbol={} market={} requestId={}", provider, source, stock.symbol(), stock.market(), requestId());
        return mockAiProviderAdapter.analyzeWithSource(stock, context, provider, index, source);
    }

    private AiChatResult fallbackChat(StockRecord stock, RagContext context, String provider, String message, String source) {
        log.warn("AI chat fallback provider={} source={} symbol={} market={} requestId={}", provider, source, stock.symbol(), stock.market(), requestId());
        return mockAiProviderAdapter.chatMessageWithSource(stock, context, provider, message, source);
    }

    private static String requestId() {
        String value = MDC.get(RequestIdFilter.ATTRIBUTE);
        return value == null || value.isBlank() ? "-" : value;
    }
}
