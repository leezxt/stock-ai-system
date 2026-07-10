package com.example.stockai.database;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.core.env.Environment;

final class DatabaseSettings {
    private DatabaseSettings() {}

    static String resolveUrl(Environment environment) {
        return firstNonBlank(
            environment.getProperty("stockai.database.url"),
            environment.getProperty("STOCKAI_DATABASE_URL"),
            environment.getProperty("DATABASE_URL")
        );
    }

    static String resolveUsername(Environment environment, String rawUrl) {
        String configured = firstNonBlank(
            environment.getProperty("stockai.database.username"),
            environment.getProperty("STOCKAI_DATABASE_USERNAME"),
            environment.getProperty("PGUSER")
        );
        if (!configured.isBlank()) {
            return configured;
        }
        String userInfo = userInfo(rawUrl);
        if (userInfo.isBlank()) {
            return "";
        }
        int colon = userInfo.indexOf(':');
        return decode(colon >= 0 ? userInfo.substring(0, colon) : userInfo);
    }

    static String resolvePassword(Environment environment, String rawUrl) {
        String configured = firstNonBlank(
            environment.getProperty("stockai.database.password"),
            environment.getProperty("STOCKAI_DATABASE_PASSWORD"),
            environment.getProperty("PGPASSWORD")
        );
        if (!configured.isBlank()) {
            return configured;
        }
        String userInfo = userInfo(rawUrl);
        int colon = userInfo.indexOf(':');
        return colon >= 0 ? decode(userInfo.substring(colon + 1)) : "";
    }

    static String toJdbcUrl(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.startsWith("jdbc:")) {
            return trimmed;
        }
        if (!trimmed.startsWith("postgres://") && !trimmed.startsWith("postgresql://")) {
            return trimmed;
        }
        URI uri = URI.create(trimmed);
        String host = uri.getHost();
        int port = uri.getPort();
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        String query = uri.getRawQuery() == null || uri.getRawQuery().isBlank() ? "" : "?" + uri.getRawQuery();
        return "jdbc:postgresql://" + host + (port > 0 ? ":" + port : "") + path + query;
    }

    private static String userInfo(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.startsWith("jdbc:")) {
            return "";
        }
        String trimmed = rawUrl.trim();
        if (!trimmed.startsWith("postgres://") && !trimmed.startsWith("postgresql://")) {
            return "";
        }
        String userInfo = URI.create(trimmed).getRawUserInfo();
        return userInfo == null ? "" : userInfo;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
