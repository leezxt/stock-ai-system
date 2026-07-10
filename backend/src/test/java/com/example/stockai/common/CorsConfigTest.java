package com.example.stockai.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class CorsConfigTest {
    @Test
    void allowsPutForApiCorsMappings() {
        WebMvcConfigurer configurer = new CorsConfig().corsConfigurer("https://stock.example.com");
        TestCorsRegistry registry = new TestCorsRegistry();
        configurer.addCorsMappings(registry);

        CorsConfiguration configuration = registry.configurations().get("/api/**");

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedMethods()).contains("PUT");
    }

    private static class TestCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
