package com.example.stockai.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ApiResponse;

@RestController
@RequestMapping("/api/v1/account/settings")
public class AccountSettingsController {
    private final AuthService authService;
    private final AccountSettingsService accountSettingsService;

    public AccountSettingsController(AuthService authService, AccountSettingsService accountSettingsService) {
        this.authService = authService;
        this.accountSettingsService = accountSettingsService;
    }

    @GetMapping
    ApiResponse<AccountSettingsService.AccountSettingsView> get(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.of(accountSettingsService.get(authService.requireUser(authorization).email()));
    }

    @PutMapping
    ApiResponse<AccountSettingsService.AccountSettingsView> update(
        @RequestHeader("Authorization") String authorization,
        @RequestBody AccountSettingsService.AccountSettingsUpdate request
    ) {
        return ApiResponse.of(accountSettingsService.update(authService.requireUser(authorization).email(), request));
    }
}
