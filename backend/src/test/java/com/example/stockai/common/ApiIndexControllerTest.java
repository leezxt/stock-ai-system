package com.example.stockai.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiIndexControllerTest {
    @Test
    void indexListsMainEndpoints() {
        ApiIndexController.ApiIndex index = new ApiIndexController().index();

        assertThat(index.status()).isEqualTo("UP");
        assertThat(index.endpoints()).contains("GET /api/v1/health", "POST /api/v1/ai/chat", "POST /api/v1/documents/import");
    }
}
