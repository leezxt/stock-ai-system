package com.example.stockai.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTest {
    @Test
    void returnsHealth() {
        HealthController.HealthResponse response = new HealthController().health();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.providers().openAiConfigured()).isFalse();
        assertThat(response.providers().openAiModel()).isEqualTo("gpt-5.5");
        assertThat(response.providers().deepSeekConfigured()).isFalse();
        assertThat(response.providers().deepSeekModel()).isEqualTo("deepseek-chat");
        assertThat(response.providers().mimoConfigured()).isFalse();
        assertThat(response.providers().mimoModel()).isEqualTo("mimo-v2.5-pro");
        assertThat(response.providers().alphaVantageNewsConfigured()).isFalse();
        assertThat(response.providers().alphaVantageTranscriptConfigured()).isFalse();
        assertThat(response.providers().fmpConfigured()).isFalse();
        assertThat(response.providers().fmpNewsConfigured()).isFalse();
        assertThat(response.providers().fmpTranscriptConfigured()).isFalse();
        assertThat(response.providers().fmpBaseUrl()).contains("financialmodelingprep.com");
        assertThat(response.providers().finMindNewsConfigured()).isTrue();
        assertThat(response.providers().finMindFinancialsConfigured()).isTrue();
        assertThat(response.providers().twseRealtimeConfigured()).isTrue();
        assertThat(response.providers().twseEndpointConfigured()).isTrue();
        assertThat(response.providers().twseDisclosureConfigured()).isTrue();
        assertThat(response.providers().twseDisclosureUrl()).contains("ajax_t05st01");
    }
}
