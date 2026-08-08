package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConfigurableEmbeddingModelTest {
    private final EmbeddingModel hash = new FixedModel("hash-embedding-v1");
    private final EmbeddingModel openAi = new FixedModel("openai-text-embedding-3-small-dim16");

    @Test
    void defaultsToExplicitHashProvider() {
        ConfigurableEmbeddingModel model = new ConfigurableEmbeddingModel(hash, openAi, "hash");

        assertThat(model.provider()).isEqualTo("hash");
        assertThat(model.modelName()).isEqualTo("hash-embedding-v1");
    }

    @Test
    void selectsOpenAiProviderWithoutMixingVectorFamilies() {
        ConfigurableEmbeddingModel model = new ConfigurableEmbeddingModel(hash, openAi, "openai");

        assertThat(model.provider()).isEqualTo("openai");
        assertThat(model.modelName()).isEqualTo("openai-text-embedding-3-small-dim16");
    }

    @Test
    void rejectsUnknownProvider() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ConfigurableEmbeddingModel(hash, openAi, "local-model"))
            .withMessageContaining("hash or openai");
    }

    private record FixedModel(String modelName) implements EmbeddingModel {
        @Override public List<Double> embed(String text) { return List.of(1.0d); }
    }
}
