package com.example.stockai.common;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ExternalApiKeyHeaderResolver {
    public String resolveAlphaVantage(String fallback) {
        return resolve("X-AlphaVantage-Api-Key", fallback);
    }

    public String resolveFinMindToken(String fallback) {
        return resolve("X-FinMind-Api-Token", fallback);
    }

    public String resolveFmp(String fallback) {
        return resolve("X-FMP-Api-Key", fallback);
    }

    private String resolve(String headerName, String fallback) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String header = request.getHeader(headerName);
            if (header != null && !header.isBlank()) {
                return header.trim();
            }
        }
        return fallback == null ? "" : fallback.trim();
    }
}
