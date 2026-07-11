package com.example.stockai.stock;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
class FallbackAiProviderAdapter implements AiProviderAdapter {
    private final OpenAiProviderAdapter openAiProviderAdapter;
    private final GeminiProviderAdapter geminiProviderAdapter;
    private final DeepSeekProviderAdapter deepSeekProviderAdapter;
    private final CustomProviderAdapter customProviderAdapter;
    private final MockAiProviderAdapter mockAiProviderAdapter;

    FallbackAiProviderAdapter(
        OpenAiProviderAdapter openAiProviderAdapter,
        GeminiProviderAdapter geminiProviderAdapter,
        DeepSeekProviderAdapter deepSeekProviderAdapter,
        CustomProviderAdapter customProviderAdapter,
        MockAiProviderAdapter mockAiProviderAdapter
    ) {
        this.openAiProviderAdapter = openAiProviderAdapter;
        this.geminiProviderAdapter = geminiProviderAdapter;
        this.deepSeekProviderAdapter = deepSeekProviderAdapter;
        this.customProviderAdapter = customProviderAdapter;
        this.mockAiProviderAdapter = mockAiProviderAdapter;
    }

    @Override
    public AiProviderResult analyze(StockRecord stock, RagContext context, String provider, int index) {
        if ("GEMINI".equalsIgnoreCase(provider)) {
            return geminiProviderAdapter.tryAnalyze(stock, context, provider)
                .orElseGet(() -> mockAiProviderAdapter.analyzeWithSource(
                    stock,
                    context,
                    provider,
                    index,
                    geminiProviderAdapter.hasApiKey() ? "mock-ai:gemini-live-failed" : "mock-ai:no-gemini-key"
                ));
        }
        if ("DEEPSEEK".equalsIgnoreCase(provider)) {
            return deepSeekProviderAdapter.tryAnalyze(stock, context, provider)
                .orElseGet(() -> mockAiProviderAdapter.analyzeWithSource(
                    stock,
                    context,
                    provider,
                    index,
                    deepSeekProviderAdapter.hasApiKey() ? "mock-ai:deepseek-live-failed" : "mock-ai:no-deepseek-key"
                ));
        }
        if ("CUSTOM".equalsIgnoreCase(provider)) {
            return customProviderAdapter.tryAnalyze(stock, context, provider)
                .orElseGet(() -> mockAiProviderAdapter.analyzeWithSource(
                    stock, context, provider, index,
                    customProviderAdapter.isConfigured() ? "mock-ai:custom-live-failed" : "mock-ai:no-custom-provider"
                ));
        }
        return openAiProviderAdapter.tryAnalyze(stock, context, provider)
            .orElseGet(() -> mockAiProviderAdapter.analyzeWithSource(
                stock,
                context,
                provider,
                index,
                openAiProviderAdapter.fallbackSource()
            ));
    }

    @Override
    public AiChatResult chatMessage(StockRecord stock, RagContext context, String provider, String message) {
        if ("GEMINI".equalsIgnoreCase(provider)) {
            return geminiProviderAdapter.tryChatMessage(stock, context, provider, message)
                .orElseGet(() -> mockAiProviderAdapter.chatMessageWithSource(
                    stock,
                    context,
                    provider,
                    message,
                    geminiProviderAdapter.hasApiKey() ? "mock-ai:gemini-live-failed" : "mock-ai:no-gemini-key"
                ));
        }
        if ("DEEPSEEK".equalsIgnoreCase(provider)) {
            return deepSeekProviderAdapter.tryChatMessage(stock, context, provider, message)
                .orElseGet(() -> mockAiProviderAdapter.chatMessageWithSource(
                    stock,
                    context,
                    provider,
                    message,
                    deepSeekProviderAdapter.hasApiKey() ? "mock-ai:deepseek-live-failed" : "mock-ai:no-deepseek-key"
                ));
        }
        if ("CUSTOM".equalsIgnoreCase(provider)) {
            return customProviderAdapter.tryChatMessage(context, provider, message)
                .orElseGet(() -> mockAiProviderAdapter.chatMessageWithSource(
                    stock, context, provider, message,
                    customProviderAdapter.isConfigured() ? "mock-ai:custom-live-failed" : "mock-ai:no-custom-provider"
                ));
        }
        return openAiProviderAdapter.tryChatMessage(stock, context, provider, message)
            .orElseGet(() -> mockAiProviderAdapter.chatMessageWithSource(
                stock,
                context,
                provider,
                message,
                openAiProviderAdapter.fallbackSource()
            ));
    }
}
