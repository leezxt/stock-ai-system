package com.example.stockai.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ApiResponse;
import com.example.stockai.common.RequestGuard;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final RequestGuard requestGuard;

    public AuthController(AuthService authService, GoogleTokenVerifier googleTokenVerifier, RequestGuard requestGuard) {
        this.authService = authService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.requestGuard = requestGuard;
    }

    @PostMapping("/register")
    ResponseEntity<ApiResponse<AuthSessionResponse>> register(HttpServletRequest servletRequest, @RequestBody AuthRequest request) {
        requestGuard.checkAnonymous(servletRequest, "register", 5);
        return sessionResponse(servletRequest, authService.register(request.email(), request.password()));
    }

    @PostMapping("/login")
    ResponseEntity<ApiResponse<AuthSessionResponse>> login(HttpServletRequest servletRequest, @RequestBody AuthRequest request) {
        requestGuard.checkAnonymous(servletRequest, "login", 10);
        return sessionResponse(servletRequest, authService.login(request.email(), request.password()));
    }

    @PostMapping("/google")
    ResponseEntity<ApiResponse<AuthSessionResponse>> google(HttpServletRequest servletRequest, @RequestBody GoogleAuthRequest request) {
        requestGuard.checkAnonymous(servletRequest, "google-login", 10);
        GoogleTokenVerifier.GoogleProfile profile = googleTokenVerifier.verify(request.credential());
        return sessionResponse(servletRequest, authService.loginGoogleVerifiedEmail(profile.email()));
    }

    @GetMapping("/google/config")
    ApiResponse<GoogleConfig> googleConfig() {
        return ApiResponse.of(new GoogleConfig(googleTokenVerifier.configured(), googleTokenVerifier.clientId()));
    }

    @GetMapping("/me")
    ApiResponse<AuthService.AuthUser> me(HttpServletRequest request) {
        return ApiResponse.of(requestGuard.requireUser(request, "auth-me", 120));
    }

    @PostMapping("/logout")
    ResponseEntity<ApiResponse<Boolean>> logout(HttpServletRequest request) {
        requestGuard.requireUser(request, "logout", 30);
        ResponseCookie cookie = ResponseCookie.from(AuthService.SESSION_COOKIE, "")
            .httpOnly(true)
            .secure(isSecure(request))
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(ApiResponse.of(true));
    }

    private static ResponseEntity<ApiResponse<AuthSessionResponse>> sessionResponse(HttpServletRequest request, AuthService.AuthResult result) {
        long maxAge = Math.max(0, result.expiresAt() - java.time.Instant.now().getEpochSecond());
        ResponseCookie cookie = ResponseCookie.from(AuthService.SESSION_COOKIE, result.token())
            .httpOnly(true)
            .secure(isSecure(request))
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofSeconds(maxAge))
            .build();
        AuthSessionResponse response = new AuthSessionResponse(result.user(), result.expiresAt());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(ApiResponse.of(response));
    }

    private static boolean isSecure(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }

    record AuthRequest(String email, String password) {}
    record GoogleAuthRequest(String credential) {}
    record GoogleConfig(boolean configured, String clientId) {}
    record AuthSessionResponse(AuthService.AuthUser user, long expiresAt) {}
}
