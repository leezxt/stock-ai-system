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
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;

@Service
class OpenAiProviderAdapter {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final ApiKeyHeaderResolver apiKeyHeaderResolver;
    private final String apiKey;
    private final String responsesUrl;
    private final String model;
    private volatile String lastFailureSource = "mock-ai:openai-live-failed";

    @Autowired
    OpenAiProviderAdapter(
        ApiKeyHeaderResolver apiKeyHeaderResolver,
        @Value("${stockai.openai.api-key:${OPENAI_API_KEY:}}") String apiKey,
        @Value("${stockai.openai.responses-url:https://api.openai.com/v1/responses}") String responsesUrl,
        @Value("${stockai.openai.model:${OPENAI_MODEL:gpt-5.5}}") String model
    ) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), JsonParserFactory.getJsonParser(), apiKeyHeaderResolver, apiKey, responsesUrl, model);
    }

    OpenAiProviderAdapter(HttpClient httpClient, JsonParser jsonParser, ApiKeyHeaderResolver apiKeyHeaderResolver, String apiKey, String responsesUrl, String model) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.apiKeyHeaderResolver = apiKeyHeaderResolver;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.responsesUrl = responsesUrl;
        this.model = model == null || model.isBlank() ? "gpt-5.5" : model.trim();
    }

    Optional<AiProviderResult> tryAnalyze(StockRecord stock, RagContext context, String provider) {
        String resolvedApiKey = resolvedApiKey();
        if (!supports(provider) || resolvedApiKey.isBlank()) {
            return Optional.empty();
        }

        String payload = """
            {
              "model": %s,
              "input": [
                {
                  "role": "system",
                  "content": [
                    {
                      "type": "input_text",
                      "text": "你是股票分析助理。僅能輸出 JSON，禁止額外文字。"
                    }
                  ]
                },
                {
                  "role": "user",
                  "content": [
                    {
                      "type": "input_text",
                      "text": %s
                    }
                  ]
                }
              ],
              "text": {
                "format": {
                  "type": "json_schema",
                  "name": "stock_analysis",
                  "schema": {
                    "type": "object",
                    "additionalProperties": false,
                    "properties": {
                      "trend": {"type": "string"},
                      "aiScore": {"type": "integer", "minimum": 0, "maximum": 100},
                      "bullishProbability": {"type": "number", "minimum": 0, "maximum": 1},
                      "riskLevel": {"type": "string"},
                      "summary": {"type": "string"}
                    },
                    "required": ["trend", "aiScore", "bullishProbability", "riskLevel", "summary"]
                  }
                }
              }
            }
            """;

        String prompt = toJsonString(context.analysisPrompt());
        try {
            return sendJson(String.format(payload, toJsonString(model), prompt), resolvedApiKey)
                .map(body -> toAnalysisResult(stock, provider, body));
        } catch (RuntimeException ex) {
            lastFailureSource = "mock-ai:openai-response-parse-error";
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
              "input": [
                {
                  "role": "system",
                  "content": [
                    {
                      "type": "input_text",
                      "text": "你是股票分析助理。請用繁體中文在 120 字內回答，避免條列，結尾提醒這不是投資建議。"
                    }
                  ]
                },
                {
                  "role": "user",
                  "content": [
                    {
                      "type": "input_text",
                      "text": %s
                    }
                  ]
                }
              ]
            }
            """;

        String prompt = toJsonString(context.chatPrompt(message));
        try {
            return sendJson(String.format(payload, toJsonString(model), prompt), resolvedApiKey)
                .flatMap(this::extractOutputText)
                .map(text -> new AiChatResult(text, "openai-responses"));
        } catch (RuntimeException ex) {
            lastFailureSource = "mock-ai:openai-response-parse-error";
            return Optional.empty();
        }
    }

    private Optional<String> sendJson(String payload, String resolvedApiKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(responsesUrl))
            .timeout(TIMEOUT)
            .header("Authorization", "Bearer " + resolvedApiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                lastFailureSource = "mock-ai:openai-live-failed";
                return Optional.ofNullable(response.body());
            }
            lastFailureSource = httpFailureSource(response.statusCode(), response.body());
            return Optional.empty();
        } catch (IOException ex) {
            lastFailureSource = "mock-ai:openai-network-error";
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            lastFailureSource = "mock-ai:openai-interrupted";
            return Optional.empty();
        }
    }

    boolean hasApiKey() {
        return !resolvedApiKey().isBlank();
    }

    String fallbackSource() {
        return hasApiKey() ? lastFailureSource : "mock-ai:no-openai-key";
    }

    private AiProviderResult toAnalysisResult(StockRecord stock, String provider, String body) {
        Map<String, Object> parsed = jsonParser.parseMap(body);
        String json = extractStructuredText(parsed).orElseThrow();
        Map<String, Object> content = jsonParser.parseMap(json);
        int aiScore = ((Number) content.getOrDefault("aiScore", 50)).intValue();
        BigDecimal bullishProbability = toBigDecimal(content.get("bullishProbability")).setScale(4, RoundingMode.HALF_UP);
        return new AiProviderResult(
            provider.toUpperCase(),
            String.valueOf(content.getOrDefault("trend", "中性")),
            aiScore,
            bullishProbability,
            String.valueOf(content.getOrDefault("riskLevel", "MEDIUM")),
            String.valueOf(content.getOrDefault("summary", stock.symbol() + " 目前資料不足，建議保守觀察。")),
            "openai-responses"
        );
    }

    private Optional<String> extractOutputText(String body) {
        Map<String, Object> parsed = jsonParser.parseMap(body);
        Optional<String> outputText = extractStructuredText(parsed);
        if (outputText.isPresent()) {
            return outputText;
        }
        Object output = parsed.get("output");
        if (!(output instanceof List<?> items)) {
            return Optional.empty();
        }
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            Object content = itemMap.get("content");
            if (!(content instanceof List<?> contentItems)) {
                continue;
            }
            for (Object contentItem : contentItems) {
                if (!(contentItem instanceof Map<?, ?> part)) {
                    continue;
                }
                Object text = part.get("text");
                if (text != null) {
                    return Optional.of(String.valueOf(text));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractStructuredText(Map<String, Object> parsed) {
        Object text = parsed.get("output_text");
        if (text != null && !String.valueOf(text).isBlank()) {
            return Optional.of(String.valueOf(text));
        }
        Object output = parsed.get("output");
        if (!(output instanceof List<?> items)) {
            return Optional.empty();
        }
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            Object content = itemMap.get("content");
            if (!(content instanceof List<?> contentItems)) {
                continue;
            }
            for (Object contentItem : contentItems) {
                if (!(contentItem instanceof Map<?, ?> part)) {
                    continue;
                }
                Object itemText = part.get("text");
                if (itemText != null && !String.valueOf(itemText).isBlank()) {
                    return Optional.of(String.valueOf(itemText));
                }
            }
        }
        return Optional.empty();
    }

    private boolean supports(String provider) {
        return "OPENAI".equalsIgnoreCase(provider);
    }

    private String resolvedApiKey() {
        return apiKeyHeaderResolver.resolveOpenAi(apiKey);
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

    private String httpFailureSource(int statusCode, String body) {
        return "mock-ai:openai-http-" + statusCode + errorCode(body).map(code -> "-" + code).orElse("");
    }

    private Optional<String> errorCode(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            Object error = jsonParser.parseMap(body).get("error");
            if (error instanceof Map<?, ?> errorMap) {
                Object code = errorMap.get("code");
                if (code == null || String.valueOf(code).isBlank()) {
                    code = errorMap.get("type");
                }
                return sanitizeErrorCode(code);
            }
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<String> sanitizeErrorCode(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String sanitized = Pattern.compile("[^a-zA-Z0-9_-]")
            .matcher(String.valueOf(value).trim())
            .replaceAll("-");
        return sanitized.isBlank() ? Optional.empty() : Optional.of(sanitized);
    }
}
