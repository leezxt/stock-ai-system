package com.example.stockai.common;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.stockai.auth.AuthService;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RequestGuard {
    private static final int MAX_TRACKED_KEYS = 20_000;

    private final AuthService authService;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RequestGuard(AuthService authService) {
        this.authService = authService;
    }

    public AuthService.AuthUser requireUser(HttpServletRequest request, String action, int requestsPerMinute) {
        AuthService.AuthUser user = authService.requireUser(request);
        check(user.email() + ":" + clientIp(request) + ":" + action, requestsPerMinute);
        return user;
    }

    public void checkAnonymous(HttpServletRequest request, String action, int requestsPerMinute) {
        check(clientIp(request) + ":" + action, requestsPerMinute);
    }

    private void check(String key, int limit) {
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || current.minute != minute) {
                return new Window(minute);
            }
            return current;
        });
        if (window.count.incrementAndGet() > Math.max(1, limit)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate limit exceeded");
        }
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute < minute - 1);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
