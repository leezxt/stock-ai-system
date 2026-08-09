package com.example.stockai.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;

/**
 * Optional OpenAI embedding provider. The vector schema is currently fixed at
 * 16 dimensions, so the API request asks text-embedding-3-small for a 16-value
 * projection. It fails closed when the provider is selected but unavailable;
 * the configurable wrapper keeps hash embeddings as the explicit default.
 */
@Service
public class OpenAiEmbeddingModel implements EmbeddingModel {
    static final int VECTOR_DIMENSION = 16;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;
    private final JsonParser jsonParser;
    private final String apiKey;
    private final String embeddingsUrl;
    private final String model;
    private final int dimension;

    @Autowired
    public OpenAiEmbeddingModel(
        @Value("${stockai.rag.embedding.api-key:${stockai.openai.api-key:${OPENAI_API_KEY:}}}") String apiKey,
        @Value("${stockai.rag.embedding.url:https://api.openai.com/v1/embeddings}") String embeddingsUrl,
        @Value("${stockai.rag.embedding.model:${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}}") String model,
        @Value("${stockai.rag.embedding.dimension:16}") int dimension
    ) {
        this(
            HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
            JsonParserFactory.getJsonParser(),
            apiKey,
            embeddingsUrl,
            model,
            dimension
        );
    }

    OpenAiEmbeddingModel(
        HttpClient httpClient,
        JsonParser jsonParser,
        String apiKey,
        String embeddingsUrl,
        String model,
        int dimension
    ) {
        if (dimension != VECTOR_DIMENSION) {
            throw new IllegalArgumentException("OpenAI embedding dimension must remain " + VECTOR_DIMENSION + " until the pgvector migration changes");
        }
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.embeddingsUrl = embeddingsUrl == null || embeddingsUrl.isBlank()
            ? "https://api.openai.com/v1/embeddings"
            : embeddingsUrl.trim();
        this.model = model == null || model.isBlank() ? "text-embedding-3-small" : model.trim();
        this.dimension = dimension;
    }

    @Override
    public String modelName() {
        return "openai-" + model + "-dim" + dimension;
    }

    @Override
    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI embedding API key is not configured");
        }

        String payload = "{\"model\":" + jsonString(model)
            + ",\"input\":" + jsonString(text.trim())
            + ",\"dimensions\":" + dimension + "}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(embeddingsUrl))
            .timeout(TIMEOUT)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI embedding provider returned HTTP " + response.statusCode());
            }
            return parseEmbedding(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("OpenAI embedding provider is unreachable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI embedding request was interrupted", ex);
        }
    }

    boolean configured() {
        return !apiKey.isBlank();
    }

    int dimension() {
        return dimension;
    }

    private List<Double> parseEmbedding(String body) {
        Map<String, Object> parsed = jsonParser.parseMap(body == null ? "" : body);
        Object data = parsed.get("data");
        if (!(data instanceof List<?> items) || items.isEmpty() || !(items.get(0) instanceof Map<?, ?> item)) {
            throw new IllegalStateException("OpenAI embedding response did not contain data");
        }
        Object rawEmbedding = item.get("embedding");
        if (!(rawEmbedding instanceof List<?> values) || values.size() != dimension) {
            throw new IllegalStateException("OpenAI embedding response dimension mismatch");
        }
        List<Double> embedding = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof Number number)) {
                throw new IllegalStateException("OpenAI embedding response contained a non-numeric value");
            }
            double normalized = number.doubleValue();
            if (!Double.isFinite(normalized)) {
                throw new IllegalStateException("OpenAI embedding response contained a non-finite value");
            }
            embedding.add(normalized);
        }
        return List.copyOf(embedding);
    }

    private static String jsonString(String value) {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n") + "\"";
    }
}
