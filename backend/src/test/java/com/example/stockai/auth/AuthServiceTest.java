package com.example.stockai.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void registerAndLoginIssueUsableToken() {
        AuthService service = new AuthService(new UserStore(tempDir.resolve("users.txt")), "test-secret", 3600);

        AuthService.AuthResult registered = service.register("demo@example.com", "secret123");
        AuthService.AuthResult loggedIn = service.login("demo@example.com", "secret123");

        assertThat(registered.user().email()).isEqualTo("demo@example.com");
        assertThat(loggedIn.token()).startsWith("ZGVtb0BleGFtcGxlLmNvbXw");
        assertThat(service.requireUser("Bearer " + loggedIn.token()).email()).isEqualTo("demo@example.com");
    }

    @Test
    void rejectsInvalidPassword() {
        AuthService service = new AuthService(new UserStore(tempDir.resolve("users.txt")), "test-secret", 3600);
        service.register("demo@example.com", "secret123");

        assertThatThrownBy(() -> service.login("demo@example.com", "wrongpass"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401");
    }

    @Test
    void googleLoginCreatesGoogleUser() {
        AuthService service = new AuthService(new UserStore(tempDir.resolve("users.txt")), "test-secret", 3600);

        AuthService.AuthResult result = service.loginGoogleVerifiedEmail("google@example.com");

        assertThat(result.user().email()).isEqualTo("google@example.com");
        assertThat(result.user().authProvider()).isEqualTo("GOOGLE");
        assertThat(service.requireUser("Bearer " + result.token()).authProvider()).isEqualTo("GOOGLE");
    }
}
