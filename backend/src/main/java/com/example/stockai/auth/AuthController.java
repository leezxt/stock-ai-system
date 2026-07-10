package com.example.stockai.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthController(AuthService authService, GoogleTokenVerifier googleTokenVerifier) {
        this.authService = authService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    @PostMapping("/register")
    ApiResponse<AuthService.AuthResult> register(@RequestBody AuthRequest request) {
        return ApiResponse.of(authService.register(request.email(), request.password()));
    }

    @PostMapping("/login")
    ApiResponse<AuthService.AuthResult> login(@RequestBody AuthRequest request) {
        return ApiResponse.of(authService.login(request.email(), request.password()));
    }

    @PostMapping("/google")
    ApiResponse<AuthService.AuthResult> google(@RequestBody GoogleAuthRequest request) {
        GoogleTokenVerifier.GoogleProfile profile = googleTokenVerifier.verify(request.credential());
        return ApiResponse.of(authService.loginGoogleVerifiedEmail(profile.email()));
    }

    @GetMapping("/me")
    ApiResponse<AuthService.AuthUser> me(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.of(authService.requireUser(authorization));
    }

    record AuthRequest(String email, String password) {}
    record GoogleAuthRequest(String credential) {}
}
