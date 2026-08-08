package com.example.stockai.stock;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.stockai.auth.UserStore;

/**
 * Optional account-wide evaluator. Disabled by default so local development
 * never makes background market-provider calls unless explicitly enabled.
 * Trigger transitions are persisted by WatchlistAlertService; delivery stays
 * LOCAL_ONLY and no email, push, or broker action is attempted.
 */
@Component
class WatchlistAlertScheduler {
    private final UserStore userStore;
    private final WatchlistAlertService alertService;
    private final boolean enabled;
    private final long fixedDelayMs;
    private final String notificationMode;
    private WatchlistAlertSchedulerStatus status;

    WatchlistAlertScheduler(
        UserStore userStore,
        WatchlistAlertService alertService,
        @Value("${stockai.watchlist.alerts.scheduler.enabled:false}") boolean enabled,
        @Value("${stockai.watchlist.alerts.scheduler.fixed-delay-ms:900000}") long fixedDelayMs,
        @Value("${stockai.watchlist.alerts.notification-mode:LOCAL_ONLY}") String notificationMode
    ) {
        if (fixedDelayMs < 1000) {
            throw new IllegalArgumentException("watchlist alert scheduler fixed delay must be at least 1000 ms");
        }
        this.userStore = userStore;
        this.alertService = alertService;
        this.enabled = enabled;
        this.fixedDelayMs = fixedDelayMs;
        this.notificationMode = notificationMode == null || notificationMode.isBlank()
            ? "LOCAL_ONLY" : notificationMode.trim().toUpperCase();
        this.status = idleStatus();
    }

    @Scheduled(
        fixedDelayString = "${stockai.watchlist.alerts.scheduler.fixed-delay-ms:900000}",
        initialDelayString = "${stockai.watchlist.alerts.scheduler.initial-delay-ms:60000}"
    )
    void scheduledRun() {
        if (enabled) {
            runNow();
        }
    }

    synchronized WatchlistAlertSchedulerStatus runNow() {
        if (!enabled) {
            return status;
        }
        Instant started = Instant.now();
        int evaluatedUsers = 0;
        int evaluatedRules = 0;
        int newTriggers = 0;
        int errors = 0;
        String lastError = "";
        List<String> emails;
        try {
            emails = userStore.listEmails();
        } catch (RuntimeException ex) {
            status = new WatchlistAlertSchedulerStatus(
                enabled, fixedDelayMs, notificationMode, started, Instant.now(), 0, 0, 0, 1, safeMessage(ex)
            );
            return status;
        }
        for (String email : emails) {
            try {
                WatchlistAlertCenterResponse response = alertService.evaluate(email);
                evaluatedUsers++;
                evaluatedRules += response.evaluations().size();
                newTriggers += response.newTriggerCount();
            } catch (RuntimeException ex) {
                errors++;
                lastError = safeMessage(ex);
            }
        }
        status = new WatchlistAlertSchedulerStatus(
            enabled, fixedDelayMs, notificationMode, started, Instant.now(),
            evaluatedUsers, evaluatedRules, newTriggers, errors, lastError
        );
        return status;
    }

    synchronized WatchlistAlertSchedulerStatus status() {
        return status;
    }

    private WatchlistAlertSchedulerStatus idleStatus() {
        return new WatchlistAlertSchedulerStatus(
            enabled, fixedDelayMs, notificationMode, null, null, 0, 0, 0, 0, ""
        );
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
