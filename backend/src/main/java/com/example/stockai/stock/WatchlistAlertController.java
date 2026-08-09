package com.example.stockai.stock;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ApiResponse;
import com.example.stockai.common.RequestGuard;
import com.example.stockai.market.Market;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1")
public class WatchlistAlertController {
    private final WatchlistAlertService alertService;
    private final WatchlistAlertScheduler alertScheduler;
    private final RequestGuard requestGuard;

    public WatchlistAlertController(
        WatchlistAlertService alertService,
        WatchlistAlertScheduler alertScheduler,
        RequestGuard requestGuard
    ) {
        this.alertService = alertService;
        this.alertScheduler = alertScheduler;
        this.requestGuard = requestGuard;
    }

    @GetMapping("/watchlist/alerts")
    ApiResponse<WatchlistAlertCenterResponse> list(HttpServletRequest request) {
        String email = requestGuard.requireUser(request, "watchlist-alert-read", 30).email();
        return ApiResponse.of(alertService.evaluate(email));
    }

    @GetMapping("/watchlist/alerts/scheduler")
    ApiResponse<WatchlistAlertSchedulerStatus> schedulerStatus(HttpServletRequest request) {
        requestGuard.requireUser(request, "watchlist-alert-scheduler-read", 30);
        return ApiResponse.of(alertScheduler.status());
    }

    @GetMapping("/watchlist/alerts/notifications")
    ApiResponse<WatchlistAlertNotificationResponse> notifications(
        HttpServletRequest request,
        @RequestParam(defaultValue = "20") int limit
    ) {
        String email = requestGuard.requireUser(request, "watchlist-alert-notification-read", 30).email();
        return ApiResponse.of(alertService.listNotifications(email, limit));
    }

    @PutMapping("/watchlist/alerts/notifications/{id}")
    ApiResponse<WatchlistAlertNotification> setNotificationRead(
        HttpServletRequest request,
        @PathVariable String id,
        @RequestBody WatchlistAlertNotificationReadRequest body
    ) {
        String email = requestGuard.requireUser(request, "watchlist-alert-notification-write", 30).email();
        if (body == null || body.read() == null) {
            throw new IllegalArgumentException("read is required");
        }
        return ApiResponse.of(alertService.setNotificationRead(email, id, body.read()));
    }

    @DeleteMapping("/watchlist/alerts/notifications/read")
    ApiResponse<WatchlistAlertNotificationCleanupResponse> clearReadNotifications(HttpServletRequest request) {
        String email = requestGuard.requireUser(request, "watchlist-alert-notification-cleanup", 30).email();
        return ApiResponse.of(alertService.clearReadNotifications(email));
    }

    @GetMapping("/watchlist/alerts/notification-preferences")
    ApiResponse<WatchlistAlertNotificationPreferenceResponse> notificationPreferences(HttpServletRequest request) {
        String email = requestGuard.requireUser(request, "watchlist-alert-preference-read", 30).email();
        return ApiResponse.of(alertService.notificationPreferences(email));
    }

    @PutMapping("/watchlist/alerts/notification-preferences")
    ApiResponse<WatchlistAlertNotificationPreferenceResponse> updateNotificationPreferences(
        HttpServletRequest request,
        @RequestBody WatchlistAlertNotificationPreferenceRequest body
    ) {
        String email = requestGuard.requireUser(request, "watchlist-alert-preference-write", 30).email();
        if (body == null) {
            throw new IllegalArgumentException("notification preference body is required");
        }
        return ApiResponse.of(alertService.updateNotificationPreferences(
            email, body.localEnabled(), body.emailEnabled(), body.pushEnabled()
        ));
    }

    @PostMapping("/watchlist/alerts")
    ApiResponse<WatchlistAlertRule> create(
        HttpServletRequest request,
        @RequestBody WatchlistAlertRequest body
    ) {
        String email = requestGuard.requireUser(request, "watchlist-alert-write", 30).email();
        boolean enabled = body.enabled() == null || body.enabled();
        return ApiResponse.of(alertService.add(email, body.market(), body.symbol(), body.condition(), body.threshold(), enabled));
    }

    @PutMapping("/watchlist/alerts/{id}")
    ApiResponse<WatchlistAlertRule> setEnabled(
        HttpServletRequest request,
        @PathVariable String id,
        @RequestBody WatchlistAlertEnabledRequest body
    ) {
        String email = requestGuard.requireUser(request, "watchlist-alert-write", 30).email();
        if (body == null || body.enabled() == null) {
            throw new IllegalArgumentException("enabled is required");
        }
        return ApiResponse.of(alertService.setEnabled(email, id, body.enabled()));
    }

    @DeleteMapping("/watchlist/alerts/{id}")
    ApiResponse<DeleteWatchlistAlertResponse> delete(
        HttpServletRequest request,
        @PathVariable String id
    ) {
        String email = requestGuard.requireUser(request, "watchlist-alert-write", 30).email();
        alertService.delete(email, id);
        return ApiResponse.of(new DeleteWatchlistAlertResponse(id, true));
    }

    record WatchlistAlertRequest(
        Market market,
        String symbol,
        WatchlistAlertCondition condition,
        BigDecimal threshold,
        Boolean enabled
    ) {}

    record WatchlistAlertEnabledRequest(Boolean enabled) {}

    record WatchlistAlertNotificationReadRequest(Boolean read) {}

    record WatchlistAlertNotificationPreferenceRequest(
        Boolean localEnabled,
        Boolean emailEnabled,
        Boolean pushEnabled
    ) {}

    record DeleteWatchlistAlertResponse(String id, boolean deleted) {}
}
