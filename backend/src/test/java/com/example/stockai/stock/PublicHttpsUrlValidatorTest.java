package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PublicHttpsUrlValidatorTest {
    @Test
    void acceptsPublicHttpsEndpoint() {
        assertThat(PublicHttpsUrlValidator.validate("https://api.openai.com/v1/chat/completions").getHost())
            .isEqualTo("api.openai.com");
    }

    @Test
    void rejectsLocalAndInsecureEndpoints() {
        assertThatThrownBy(() -> PublicHttpsUrlValidator.validate("http://example.com/v1/chat/completions"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicHttpsUrlValidator.validate("https://127.0.0.1/v1/chat/completions"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicHttpsUrlValidator.validate("https://localhost/v1/chat/completions"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
