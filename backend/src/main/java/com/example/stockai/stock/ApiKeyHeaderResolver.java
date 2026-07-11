package com.example.stockai.stock;

import com.example.stockai.auth.AccountSettingsService;
import com.example.stockai.auth.AuthService;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
class ApiKeyHeaderResolver {
    private final AuthService authService;
    private final AccountSettingsService accountSettingsService;

    ApiKeyHeaderResolver(AuthService authService, AccountSettingsService accountSettingsService) {
        this.authService = authService;
        this.accountSettingsService = accountSettingsService;
    }

    String resolveOpenAi(String fallback) {
        return resolve("X-OpenAI-Api-Key", fallback, "OPENAI");
    }

    String resolveGemini(String fallback) {
        return resolve("X-Gemini-Api-Key", fallback, "GEMINI");
    }

    String resolveDeepSeek(String fallback) {
        return resolve("X-DeepSeek-Api-Key", fallback, "DEEPSEEK");
    }

    AccountSettingsService.CustomProviderSettings resolveCustomProvider() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return authService.optionalUser(servletAttributes.getRequest())
                .map(user -> accountSettingsService.customProviderSettings(user.email()))
                .orElseGet(() -> new AccountSettingsService.CustomProviderSettings("", "", ""));
        }
        return new AccountSettingsService.CustomProviderSettings("", "", "");
    }

    private String resolve(String headerName, String fallback, String provider) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String header = request.getHeader(headerName);
            if (header != null && !header.isBlank()) {
                return header.trim();
            }
            var user = authService.optionalUser(request);
            if (user.isPresent()) {
                String email = user.get().email();
                String storedKey = switch (provider) {
                    case "OPENAI" -> accountSettingsService.openAiApiKey(email);
                    case "GEMINI" -> accountSettingsService.geminiApiKey(email);
                    case "DEEPSEEK" -> accountSettingsService.deepSeekApiKey(email);
                    default -> "";
                };
                if (!storedKey.isBlank()) {
                    return storedKey;
                }
            }
        }
        return fallback == null ? "" : fallback.trim();
    }
}
