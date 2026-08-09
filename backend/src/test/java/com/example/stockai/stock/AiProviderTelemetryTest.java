package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiProviderTelemetryTest {
    @Test
    void snapshotsSafeCountersWithoutRequestContent() {
        AiProviderTelemetry telemetry = new AiProviderTelemetry();

        telemetry.record("openai", "chat", true, false, 12);
        telemetry.record("OPENAI", "chat", false, true, 8);

        AiProviderTelemetry.Stats stats = telemetry.snapshot().get("OPENAI.CHAT");
        assertThat(stats.requests()).isEqualTo(2);
        assertThat(stats.liveSuccesses()).isEqualTo(1);
        assertThat(stats.fallbacks()).isEqualTo(1);
        assertThat(stats.exceptions()).isEqualTo(1);
        assertThat(stats.totalLatencyMillis()).isEqualTo(20);
    }
}
