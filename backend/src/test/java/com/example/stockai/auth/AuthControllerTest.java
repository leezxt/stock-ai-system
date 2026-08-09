package com.example.stockai.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.stockai.common.RequestGuard;

import jakarta.servlet.http.Cookie;

class AuthControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void registrationUsesHttpOnlyCookieWithoutReturningToken() {
        AuthService authService = new AuthService(new UserStore(tempDir.resolve("users.txt")), "test-secret-that-is-at-least-32-bytes", 3600);
        AuthController controller = new AuthController(authService, new GoogleTokenVerifier("google-client"), new RequestGuard(authService));
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = controller.register(request, new AuthController.AuthRequest("demo@example.com", "secret-password-123"));

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("stockai_session=", "HttpOnly", "SameSite=Lax");
        assertThat(response.getBody().data().user().email()).isEqualTo("demo@example.com");
        assertThat(response.getBody().toString()).doesNotContain("Bearer", "token=");

        String token = setCookie.substring("stockai_session=".length(), setCookie.indexOf(';'));
        MockHttpServletRequest authenticated = new MockHttpServletRequest();
        authenticated.setCookies(new Cookie(AuthService.SESSION_COOKIE, token));
        assertThat(controller.me(authenticated).data().email()).isEqualTo("demo@example.com");
    }

    @Test
    void googleConfigExposesOnlyPublicClientConfiguration() {
        AuthService authService = new AuthService(new UserStore(tempDir.resolve("users-config.txt")), "test-secret-that-is-at-least-32-bytes", 3600);
        AuthController controller = new AuthController(authService, new GoogleTokenVerifier("public-client-id"), new RequestGuard(authService));

        var config = controller.googleConfig().data();

        assertThat(config.configured()).isTrue();
        assertThat(config.clientId()).isEqualTo("public-client-id");
    }
}
