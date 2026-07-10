package com.example.stockai.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ApiResponse;
import com.example.stockai.common.RequestGuard;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/account/settings")
public class AccountSettingsController {
    private final AccountSettingsService accountSettingsService;
    private final RequestGuard requestGuard;

    public AccountSettingsController(AccountSettingsService accountSettingsService, RequestGuard requestGuard) {
        this.accountSettingsService = accountSettingsService;
        this.requestGuard = requestGuard;
    }

    @GetMapping
    ApiResponse<AccountSettingsService.AccountSettingsView> get(HttpServletRequest request) {
        String email = requestGuard.requireUser(request, "account-settings-read", 60).email();
        return ApiResponse.of(accountSettingsService.get(email));
    }

    @PutMapping
    ApiResponse<AccountSettingsService.AccountSettingsView> update(
        HttpServletRequest servletRequest,
        @RequestBody AccountSettingsService.AccountSettingsUpdate request
    ) {
        String email = requestGuard.requireUser(servletRequest, "account-settings-write", 20).email();
        return ApiResponse.of(accountSettingsService.update(email, request));
    }
}
