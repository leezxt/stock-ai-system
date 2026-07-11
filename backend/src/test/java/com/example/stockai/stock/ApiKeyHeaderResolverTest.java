package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.stockai.auth.AccountSettingsService;
import com.example.stockai.auth.AuthService;
import com.example.stockai.auth.UserStore;

class ApiKeyHeaderResolverTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void resolvesDeepSeekHeaderBeforeFallback() {
        ApiKeyHeaderResolver resolver = resolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-DeepSeek-Api-Key", " deepseek-header ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(resolver.resolveDeepSeek("deepseek-fallback")).isEqualTo("deepseek-header");
    }

    @Test
    void resolvesStoredDeepSeekKeyForLoggedInUser() {
        AuthService authService = new AuthService(new UserStore(tempDir.resolve("users.txt")), "test-secret-that-is-at-least-32-bytes", 3600);
        AccountSettingsService accountSettingsService = new AccountSettingsService(tempDir.resolve("account-settings"));
        AuthService.AuthResult auth = authService.register("deepseek@example.test", "Passw0rd!-secure");
        accountSettingsService.update(
            auth.user().email(),
            new AccountSettingsService.AccountSettingsUpdate("DEEPSEEK", null, null, "stored-deepseek", null, null, null)
        );
        ApiKeyHeaderResolver resolver = new ApiKeyHeaderResolver(authService, accountSettingsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + auth.token());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(resolver.resolveDeepSeek("deepseek-fallback")).isEqualTo("stored-deepseek");
    }

    private ApiKeyHeaderResolver resolver() {
        return new ApiKeyHeaderResolver(
            new AuthService(new UserStore(tempDir.resolve("users.txt")), "test-secret-that-is-at-least-32-bytes", 3600),
            new AccountSettingsService(tempDir.resolve("account-settings"))
        );
    }
}
