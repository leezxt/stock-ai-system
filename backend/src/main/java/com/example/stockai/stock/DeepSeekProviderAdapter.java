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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;

@Service
class DeepSeekProviderAdapter {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final ApiKeyHeaderResolver apiKeyHeaderResolver;
    private final String apiKey;
    private final String chatCompletionsUrl;
    private final String model;

    @Autowired
    DeepSeekProviderAdapter(
        ApiKeyHeaderResolver apiKeyHeaderResolver,
        @Value("${stockai.deepseek.api-key:${DEEPSEEK_API_KEY:}}") String apiKey,
        @Value("${stockai.deepseek.chat-completions-url:https://api.deepseek.com/chat/completions}") String chatCompletionsUrl,
        @Value("${stockai.deepseek.model:${DEEPSEEK_MODEL:deepseek-chat}}") String model
    ) {
        this(
            HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
            JsonParserFactory.getJsonParser(),
            apiKeyHeaderResolver,
            apiKey,
            chatCompletionsUrl,
            model
        );
    }

    DeepSeekProviderAdapter(HttpClient httpClient, JsonParser jsonParser, String apiKey, String chatCompletionsUrl, String model) {
        this(httpClient, jsonParser, null, apiKey, chatCompletionsUrl, model);
    }

    DeepSeekProviderAdapter(HttpClient httpClient, JsonParser jsonParser, ApiKeyHeaderResolver apiKeyHeaderResolver, String apiKey, String chatCompletionsUrl, String model) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.apiKeyHeaderResolver = apiKeyHeaderResolver;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.chatCompletionsUrl = chatCompletionsUrl;
        this.model = model == null || model.isBlank() ? "deepseek-chat" : model.trim();
    }

    Optional<AiProviderResult> tryAnalyze(StockRecord stock, RagContext context, String provider) {
        String resolvedApiKey = resolvedApiKey();
        if (!supports(provider) || resolvedApiKey.isBlank()) {
            return Optional.empty();
        }
        String payload = """
            {
              "model": %s,
              "messages": [
                {
                  "role": "system",
                  "content": "你是股票分析助理。只回傳 JSON，禁止額外文字。JSON 欄位固定為: trend, aiScore, bullishProbability, riskLevel, summary"
                },
                {
                  "role": "user",
                  "content": %s
                }
              ],
              "temperature": 0.3,
              "response_format": {
                "type": "json_object"
              }
            }
            """;
        try {
            return sendText(String.format(payload, toJsonString(model), toJsonString(context.analysisPrompt())), resolvedApiKey)
                .map(text -> toAnalysisResult(stock, provider, text));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    Optional<AiChatResult> tryChatMessage(StockRecord stock, RagContext context, String provider, String message) {
        String resolvedApiKey = resolvedApiKey();
        if (!supports(provider) || resolvedApiKey.isBlank()) {
            return Optional.empty();
        }
        String payload = """
            {
              "model": %s,
              "messages": [
                {
                  "role": "system",
                  "content": "你是股票分析助理。請用繁體中文在 120 字內回答，避免條列，結尾提醒這不是投資建議。"
                },
                {
                  "role": "user",
                  "content": %s
                }
              ],
              "temperature": 0.4
            }
            """;
        try {
            return sendText(String.format(payload, toJsonString(model), toJsonString(context.chatPrompt(message))), resolvedApiKey)
                .map(text -> new AiChatResult(text, "deepseek-chat-completions"));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<String> sendText(String payload, String resolvedApiKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(chatCompletionsUrl))
            .timeout(TIMEOUT)
            .header("Authorization", "Bearer " + resolvedApiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            Map<String, Object> parsed = jsonParser.parseMap(response.body());
            Object choices = parsed.get("choices");
            if (!(choices instanceof List<?> items) || items.isEmpty()) {
                return Optional.empty();
            }
            Object first = items.get(0);
            if (!(first instanceof Map<?, ?> choice)) {
                return Optional.empty();
            }
            Object message = choice.get("message");
            if (!(message instanceof Map<?, ?> messageMap)) {
                return Optional.empty();
            }
            Object content = messageMap.get("content");
            if (content == null || String.valueOf(content).isBlank()) {
                return Optional.empty();
            }
            return Optional.of(String.valueOf(content));
        } catch (IOException ex) {
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private AiProviderResult toAnalysisResult(StockRecord stock, String provider, String text) {
        Map<String, Object> content = jsonParser.parseMap(text);
        int aiScore = ((Number) content.getOrDefault("aiScore", 50)).intValue();
        BigDecimal bullishProbability = toBigDecimal(content.get("bullishProbability")).setScale(4, RoundingMode.HALF_UP);
        return new AiProviderResult(
            provider.toUpperCase(),
            String.valueOf(content.getOrDefault("trend", "中性")),
            aiScore,
            bullishProbability,
            String.valueOf(content.getOrDefault("riskLevel", "MEDIUM")),
            String.valueOf(content.getOrDefault("summary", stock.symbol() + " 目前資料不足，建議保守觀察。")),
            "deepseek-chat-completions"
        );
    }

    private boolean supports(String provider) {
        return "DEEPSEEK".equalsIgnoreCase(provider);
    }

    boolean hasApiKey() {
        return !resolvedApiKey().isBlank();
    }

    private String resolvedApiKey() {
        return apiKeyHeaderResolver == null ? apiKey : apiKeyHeaderResolver.resolveDeepSeek(apiKey);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        return new BigDecimal(String.valueOf(value == null ? "0.5" : value));
    }

    private String toJsonString(String value) {
        return '"' + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n") + '"';
    }
}
