package com.example.stockai.stock;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.stockai.auth.AccountSettingsService;

@Service
class CustomProviderAdapter {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final ApiKeyHeaderResolver settingsResolver;

    @Autowired
    CustomProviderAdapter(ApiKeyHeaderResolver settingsResolver) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), JsonParserFactory.getJsonParser(), settingsResolver);
    }

    CustomProviderAdapter(HttpClient httpClient, JsonParser jsonParser, ApiKeyHeaderResolver settingsResolver) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.settingsResolver = settingsResolver;
    }

    Optional<AiProviderResult> tryAnalyze(StockRecord stock, RagContext context, String provider) {
        AccountSettingsService.CustomProviderSettings settings = settingsResolver.resolveCustomProvider();
        if (!supports(provider) || !settings.configured()) {
            return Optional.empty();
        }
        String payload = """
            {"model":%s,"messages":[
              {"role":"system","content":"你是股票分析助理。只回傳 JSON，欄位固定為 trend, aiScore, bullishProbability, riskLevel, summary"},
              {"role":"user","content":%s}
            ],"temperature":0.3,"response_format":{"type":"json_object"}}
            """.formatted(json(settings.name()), json(context.analysisPrompt()));
        try {
            return sendText(settings, payload).map(text -> toAnalysisResult(stock, settings.name(), text));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    Optional<AiChatResult> tryChatMessage(RagContext context, String provider, String message) {
        AccountSettingsService.CustomProviderSettings settings = settingsResolver.resolveCustomProvider();
        if (!supports(provider) || !settings.configured()) {
            return Optional.empty();
        }
        String payload = """
            {"model":%s,"messages":[
              {"role":"system","content":"你是股票分析助理。請用繁體中文在 120 字內回答，結尾提醒這不是投資建議。"},
              {"role":"user","content":%s}
            ],"temperature":0.4}
            """.formatted(json(settings.name()), json(context.chatPrompt(message)));
        try {
            return sendText(settings, payload).map(text -> new AiChatResult(text, "custom-chat-completions"));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    boolean isConfigured() {
        return settingsResolver.resolveCustomProvider().configured();
    }

    private Optional<String> sendText(AccountSettingsService.CustomProviderSettings settings, String payload) {
        URI uri = PublicHttpsUrlValidator.validate(settings.url());
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(TIMEOUT)
            .header("Authorization", "Bearer " + settings.apiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return Optional.empty();
            Map<String, Object> parsed = jsonParser.parseMap(response.body());
            Object choices = parsed.get("choices");
            if (!(choices instanceof List<?> items) || items.isEmpty() || !(items.get(0) instanceof Map<?, ?> choice)) return Optional.empty();
            Object message = choice.get("message");
            if (!(message instanceof Map<?, ?> messageMap)) return Optional.empty();
            Object content = messageMap.get("content");
            return content == null || String.valueOf(content).isBlank() ? Optional.empty() : Optional.of(String.valueOf(content));
        } catch (IOException ex) {
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private AiProviderResult toAnalysisResult(StockRecord stock, String name, String text) {
        Map<String, Object> content = jsonParser.parseMap(text);
        int score = ((Number) content.getOrDefault("aiScore", 50)).intValue();
        BigDecimal probability = new BigDecimal(String.valueOf(content.getOrDefault("bullishProbability", 0.5))).setScale(4, RoundingMode.HALF_UP);
        return new AiProviderResult(name, String.valueOf(content.getOrDefault("trend", "中性")), score, probability,
            String.valueOf(content.getOrDefault("riskLevel", "MEDIUM")),
            String.valueOf(content.getOrDefault("summary", stock.symbol() + " 目前資料不足，建議保守觀察。")),
            "custom-chat-completions");
    }

    private static boolean supports(String provider) {
        return "CUSTOM".equalsIgnoreCase(provider);
    }

    private static String json(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n") + '"';
    }
}
