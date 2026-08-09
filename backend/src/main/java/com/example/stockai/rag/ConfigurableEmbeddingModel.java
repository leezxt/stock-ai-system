package com.example.stockai.rag;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Selects one embedding family for the whole process. Switching provider is
 * intentionally explicit so hash and semantic vectors are never mixed in a
 * pgvector query.
 */
@Service
@Primary
public class ConfigurableEmbeddingModel implements EmbeddingModel {
    private final EmbeddingModel delegate;
    private final String provider;

    @Autowired
    public ConfigurableEmbeddingModel(
        HashEmbeddingModel hashEmbeddingModel,
        OpenAiEmbeddingModel openAiEmbeddingModel,
        @Value("${stockai.rag.embedding.provider:${STOCKAI_RAG_EMBEDDING_PROVIDER:hash}}") String provider
    ) {
        this((EmbeddingModel) hashEmbeddingModel, (EmbeddingModel) openAiEmbeddingModel, provider);
    }

    ConfigurableEmbeddingModel(
        EmbeddingModel hashEmbeddingModel,
        EmbeddingModel openAiEmbeddingModel,
        String provider
    ) {
        String normalized = provider == null || provider.isBlank()
            ? "hash"
            : provider.trim().toLowerCase(Locale.ROOT);
        this.provider = normalized;
        this.delegate = switch (normalized) {
            case "hash" -> hashEmbeddingModel;
            case "openai" -> openAiEmbeddingModel;
            default -> throw new IllegalArgumentException("stockai.rag.embedding.provider must be hash or openai");
        };
        if (this.delegate == null) {
            throw new IllegalArgumentException("embedding model must not be null");
        }
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public List<Double> embed(String text) {
        return delegate.embed(text);
    }

    public String provider() {
        return provider;
    }
}
