package com.example.stockai.stock;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.stockai.common.AtomicFileWriter;
import com.example.stockai.common.UserScopedFileLocator;
import com.example.stockai.market.Market;
import com.example.stockai.market.SymbolNormalizer;

/**
 * Stores user-owned alert rules and evaluates them against the same market
 * service used by the dashboard. Rules are deliberately deterministic; no AI
 * output is used as a hidden trigger.
 */
@Service
class WatchlistAlertService {
    private static final String SOURCE = "watchlist-alerts-v2";
    private static final String NOTIFICATION_SOURCE = "watchlist-alert-notifications-v1";
    private static final String PREFERENCES_SOURCE = "watchlist-alert-notification-preferences-v1";
    private final Path file;
    private final JdbcTemplate jdbcTemplate;
    private final WatchlistService watchlistService;
    private final StockService stockService;

    WatchlistAlertService() {
        this(
            Path.of(System.getProperty("stockai.watchlist.file", "data/watchlist.txt")),
            null,
            null,
            null
        );
    }

    @Autowired
    WatchlistAlertService(
        Optional<JdbcTemplate> jdbcTemplate,
        WatchlistService watchlistService,
        StockService stockService
    ) {
        this(
            Path.of(System.getProperty("stockai.watchlist.file", "data/watchlist.txt")),
            jdbcTemplate.orElse(null),
            watchlistService,
            stockService
        );
    }

    WatchlistAlertService(Path file, WatchlistService watchlistService, StockService stockService) {
        this(file, null, watchlistService, stockService);
    }

    private WatchlistAlertService(Path file, JdbcTemplate jdbcTemplate, WatchlistService watchlistService, StockService stockService) {
        this.file = file;
        this.jdbcTemplate = jdbcTemplate;
        this.watchlistService = watchlistService;
        this.stockService = stockService;
    }

    synchronized List<WatchlistAlertRule> list(String userEmail) {
        if (jdbcTemplate != null) {
            return jdbcTemplate.query("""
                SELECT id, market, symbol, condition, threshold, enabled, created_at, updated_at
                FROM stockai_watchlist_alerts
                WHERE email = ?
                ORDER BY created_at, id
                """, (rs, rowNum) -> new WatchlistAlertRule(
                    rs.getString("id"),
                    Market.valueOf(rs.getString("market")),
                    rs.getString("symbol"),
                    WatchlistAlertCondition.valueOf(rs.getString("condition")),
                    rs.getBigDecimal("threshold"),
                    rs.getBoolean("enabled"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                ), normalizeEmail(userEmail));
        }
        return read(fileForUserLoad(userEmail));
    }

    synchronized WatchlistAlertRule add(
        String userEmail,
        Market market,
        String symbol,
        WatchlistAlertCondition condition,
        BigDecimal threshold,
        boolean enabled
    ) {
        String normalizedEmail = normalizeEmail(userEmail);
        String normalizedSymbol = SymbolNormalizer.normalize(market, symbol);
        if (watchlistService == null || !watchlistService.contains(normalizedEmail, market, normalizedSymbol)) {
            throw new IllegalArgumentException("alert symbol must already be in the watchlist");
        }
        validateThreshold(condition, threshold);
        BigDecimal normalizedThreshold = condition == WatchlistAlertCondition.DATA_QUALITY_NOT_OK ? null : threshold;
        List<WatchlistAlertRule> existing = list(normalizedEmail);
        Optional<WatchlistAlertRule> duplicate = existing.stream()
            .filter(rule -> rule.market() == market
                && rule.symbol().equals(normalizedSymbol)
                && rule.condition() == condition
                && java.util.Objects.equals(rule.threshold(), normalizedThreshold))
            .findFirst();
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        Instant now = Instant.now();
        WatchlistAlertRule rule = new WatchlistAlertRule(
            UUID.randomUUID().toString(), market, normalizedSymbol, condition, normalizedThreshold, enabled, now, now
        );
        if (jdbcTemplate != null) {
            jdbcTemplate.update("""
                INSERT INTO stockai_watchlist_alerts
                    (id, email, market, symbol, condition, threshold, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rule.id(), normalizedEmail, rule.market().name(), rule.symbol(), rule.condition().name(),
                rule.threshold(), rule.enabled(), Timestamp.from(rule.createdAt()), Timestamp.from(rule.updatedAt()));
        } else {
            List<WatchlistAlertRule> rules = new ArrayList<>(existing);
            rules.add(rule);
            write(fileForUserSave(normalizedEmail), rules);
        }
        return rule;
    }

    synchronized WatchlistAlertRule setEnabled(String userEmail, String id, boolean enabled) {
        WatchlistAlertRule rule = find(userEmail, id)
            .orElseThrow(() -> new IllegalArgumentException("alert rule not found"));
        WatchlistAlertRule updated = rule.withEnabled(enabled);
        if (jdbcTemplate != null) {
            jdbcTemplate.update("""
                UPDATE stockai_watchlist_alerts
                SET enabled = ?, updated_at = ?
                WHERE email = ? AND id = ?
                """, updated.enabled(), Timestamp.from(updated.updatedAt()), normalizeEmail(userEmail), id);
        } else {
            replace(userEmail, updated);
        }
        return updated;
    }

    synchronized void delete(String userEmail, String id) {
        if (jdbcTemplate != null) {
            String normalizedEmail = normalizeEmail(userEmail);
            jdbcTemplate.update("DELETE FROM stockai_watchlist_alert_notifications WHERE email = ? AND rule_id = ?", normalizedEmail, id);
            jdbcTemplate.update("DELETE FROM stockai_watchlist_alert_events WHERE email = ? AND rule_id = ?", normalizedEmail, id);
            jdbcTemplate.update("DELETE FROM stockai_watchlist_alerts WHERE email = ? AND id = ?", normalizedEmail, id);
            return;
        }
        List<WatchlistAlertRule> remaining = list(userEmail).stream()
            .filter(rule -> !rule.id().equals(id))
            .toList();
        write(fileForUserSave(userEmail), remaining);
        List<WatchlistAlertEvent> remainingEvents = readEvents(fileForUserLoad(userEmail)).stream()
            .filter(event -> !event.ruleId().equals(id))
            .toList();
        writeEvents(fileForUserEventsSave(userEmail), remainingEvents);
        List<WatchlistAlertNotification> remainingNotifications = readNotifications(fileForUserNotificationsLoad(userEmail)).stream()
            .filter(notification -> !notification.ruleId().equals(id))
            .toList();
        writeNotifications(fileForUserNotificationsSave(userEmail), remainingNotifications);
    }

    synchronized WatchlistAlertCenterResponse evaluate(String userEmail) {
        String normalizedEmail = normalizeEmail(userEmail);
        Instant evaluatedAt = Instant.now();
        Set<String> activeWatchlist = watchlistService == null
            ? Set.of()
            : watchlistService.list(normalizedEmail).stream()
                .map(item -> item.market().name() + ":" + item.symbol())
                .collect(java.util.stream.Collectors.toSet());
        List<WatchlistAlertRule> rules = list(normalizedEmail).stream()
            .filter(rule -> activeWatchlist.contains(rule.market().name() + ":" + rule.symbol()))
            .toList();
        Map<String, StockRecord> stockCache = new HashMap<>();
        Map<String, PredictionResponse> predictionCache = new HashMap<>();
        Map<String, DataLineageResponse> lineageCache = new HashMap<>();
        List<WatchlistAlertEvent> history = new ArrayList<>(readEvents(normalizedEmail));
        Map<String, WatchlistAlertEvent> latestByRule = new HashMap<>();
        Map<String, Instant> lastTriggeredAtByRule = new HashMap<>();
        for (WatchlistAlertEvent event : history) {
            latestByRule.putIfAbsent(event.ruleId(), event);
            if ("TRIGGERED".equals(event.status())) {
                lastTriggeredAtByRule.putIfAbsent(event.ruleId(), event.createdAt());
            }
        }
        List<WatchlistAlertEvaluation> evaluations = new ArrayList<>();
        List<WatchlistAlertEvent> newEvents = new ArrayList<>();
        int newTriggerCount = 0;
        for (WatchlistAlertRule rule : rules) {
            WatchlistAlertEvaluation raw = evaluateRule(rule, stockCache, predictionCache, lineageCache);
            WatchlistAlertEvent previous = latestByRule.get(rule.id());
            boolean statusChanged = previous == null || !previous.status().equals(raw.status());
            Instant lastTriggeredAt = lastTriggeredAtByRule.get(rule.id());
            boolean newlyTriggered = raw.triggered() && (previous == null || !"TRIGGERED".equals(previous.status()));
            if (statusChanged) {
                WatchlistAlertEvent event = toEvent(rule, raw, evaluatedAt);
                newEvents.add(event);
                latestByRule.put(rule.id(), event);
                if ("TRIGGERED".equals(event.status())) {
                    lastTriggeredAt = event.createdAt();
                }
            }
            if (newlyTriggered) {
                newTriggerCount++;
            }
            evaluations.add(raw.withHistory(newlyTriggered, lastTriggeredAt));
        }
        if (!newEvents.isEmpty()) {
            appendEvents(normalizedEmail, history, newEvents);
            appendNotifications(normalizedEmail, newEvents);
            history.addAll(newEvents);
        }
        history.sort(Comparator.comparing(WatchlistAlertEvent::createdAt).reversed()
            .thenComparing(WatchlistAlertEvent::id, Comparator.reverseOrder()));
        int triggered = (int) evaluations.stream().filter(WatchlistAlertEvaluation::triggered).count();
        return new WatchlistAlertCenterResponse(
            rules, evaluations, triggered, newTriggerCount, history.stream().limit(20).toList(), evaluatedAt, SOURCE
        );
    }

    synchronized WatchlistAlertNotificationResponse listNotifications(String userEmail, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        List<WatchlistAlertNotification> notifications = readNotifications(normalizeEmail(userEmail));
        int unreadCount = (int) notifications.stream().filter(item -> "UNREAD".equals(item.state())).count();
        return new WatchlistAlertNotificationResponse(notifications.stream().limit(limit).toList(), unreadCount, NOTIFICATION_SOURCE);
    }

    synchronized WatchlistAlertNotification setNotificationRead(String userEmail, String id, boolean read) {
        String normalizedEmail = normalizeEmail(userEmail);
        WatchlistAlertNotification notification = readNotifications(normalizedEmail).stream()
            .filter(item -> item.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("alert notification not found"));
        WatchlistAlertNotification updated = notification.withRead(read);
        if (jdbcTemplate != null) {
            jdbcTemplate.update("""
                UPDATE stockai_watchlist_alert_notifications
                SET state = ?, read_at = ?
                WHERE email = ? AND id = ?
                """, updated.state(), toTimestamp(updated.readAt()), normalizedEmail, id);
        } else {
            List<WatchlistAlertNotification> notifications = readNotifications(normalizedEmail).stream()
                .map(item -> item.id().equals(id) ? updated : item)
                .toList();
            writeNotifications(fileForUserNotificationsSave(normalizedEmail), notifications);
        }
        return updated;
    }

    synchronized WatchlistAlertNotificationCleanupResponse clearReadNotifications(String userEmail) {
        String normalizedEmail = normalizeEmail(userEmail);
        if (jdbcTemplate != null) {
            int deleted = jdbcTemplate.update(
                "DELETE FROM stockai_watchlist_alert_notifications WHERE email = ? AND state = 'READ'",
                normalizedEmail
            );
            return new WatchlistAlertNotificationCleanupResponse(deleted, true);
        }
        List<WatchlistAlertNotification> existing = readNotifications(normalizedEmail);
        List<WatchlistAlertNotification> remaining = existing.stream()
            .filter(notification -> !"READ".equals(notification.state()))
            .toList();
        int deleted = existing.size() - remaining.size();
        if (deleted > 0) {
            writeNotifications(fileForUserNotificationsSave(normalizedEmail), remaining);
        }
        return new WatchlistAlertNotificationCleanupResponse(deleted, true);
    }

    synchronized WatchlistAlertNotificationPreferenceResponse notificationPreferences(String userEmail) {
        return new WatchlistAlertNotificationPreferenceResponse(
            readNotificationPreferences(normalizeEmail(userEmail)), false,
            "目前只支援 LOCAL_ONLY；Email／推播尚未設定且不會外送。", PREFERENCES_SOURCE
        );
    }

    synchronized WatchlistAlertNotificationPreferenceResponse updateNotificationPreferences(
        String userEmail,
        Boolean localEnabled,
        Boolean emailEnabled,
        Boolean pushEnabled
    ) {
        String normalizedEmail = normalizeEmail(userEmail);
        WatchlistAlertNotificationPreferences current = readNotificationPreferences(normalizedEmail);
        if (localEnabled == null && emailEnabled == null && pushEnabled == null) {
            throw new IllegalArgumentException("at least one notification preference is required");
        }
        WatchlistAlertNotificationPreferences updated = new WatchlistAlertNotificationPreferences(
            localEnabled == null ? current.localEnabled() : localEnabled,
            emailEnabled == null ? current.emailEnabled() : emailEnabled,
            pushEnabled == null ? current.pushEnabled() : pushEnabled,
            Instant.now()
        );
        writeNotificationPreferences(normalizedEmail, updated);
        return new WatchlistAlertNotificationPreferenceResponse(
            updated, false, "目前只支援 LOCAL_ONLY；Email／推播尚未設定且不會外送。", PREFERENCES_SOURCE
        );
    }

    private WatchlistAlertEvent toEvent(WatchlistAlertRule rule, WatchlistAlertEvaluation evaluation, Instant createdAt) {
        return new WatchlistAlertEvent(
            UUID.randomUUID().toString(), rule.id(), rule.market(), rule.symbol(), rule.condition(), evaluation.status(),
            evaluation.currentValue(), evaluation.message(), evaluation.source(), evaluation.dataStatus(),
            evaluation.observedAt(), createdAt
        );
    }

    private WatchlistAlertEvaluation evaluateRule(
        WatchlistAlertRule rule,
        Map<String, StockRecord> stockCache,
        Map<String, PredictionResponse> predictionCache,
        Map<String, DataLineageResponse> lineageCache
    ) {
        if (!rule.enabled()) {
            return new WatchlistAlertEvaluation(
                rule.id(), rule.market(), rule.symbol(), rule.symbol(), rule.condition(), rule.threshold(),
                null, "", "DISABLED", false, false, null, "規則已停用", "unknown", "DISABLED", null
            );
        }
        String key = rule.market().name() + ":" + rule.symbol();
        try {
            StockRecord stock = stockCache.computeIfAbsent(key, ignored -> stockService.get(rule.market(), rule.symbol()));
            String dataStatus = derivedDataStatus(stock);
            BigDecimal currentValue = null;
            String currentLabel = "";
            boolean triggered;
            String message;
            if (rule.condition() == WatchlistAlertCondition.RISK_AT_LEAST) {
                PredictionResponse prediction = predictionCache.computeIfAbsent(key, ignored -> stockService.prediction(rule.market(), rule.symbol(), 5));
                if (prediction.riskLevel() == null || prediction.dataQuality().unavailableIndicators().contains("RISK_LEVEL")) {
                    return unavailable(rule, stock, dataStatus, "預測資料不足，無法評估風險警示");
                }
                currentValue = BigDecimal.valueOf(riskRank(prediction.riskLevel()));
                currentLabel = prediction.riskLevel();
                triggered = currentValue.compareTo(rule.threshold()) >= 0;
                message = triggered
                    ? rule.symbol() + " 風險為 " + prediction.riskLevel() + "，達到警示門檻"
                    : rule.symbol() + " 風險為 " + prediction.riskLevel() + "，尚未達到門檻";
            } else if (rule.condition() == WatchlistAlertCondition.DATA_QUALITY_NOT_OK) {
                DataLineageResponse lineage = lineageCache.computeIfAbsent(key, ignored -> stockService.dataLineage(rule.market(), rule.symbol()));
                dataStatus = lineage.integrityStatus();
                currentLabel = lineage.adjustmentStatus() + " / " + lineage.integrityStatus();
                triggered = !"OK".equalsIgnoreCase(lineage.integrityStatus());
                message = triggered
                    ? rule.symbol() + " 資料品質為 " + lineage.integrityStatus() + "，請先確認來源與調整狀態"
                    : rule.symbol() + " 資料品質正常";
            } else {
                currentValue = switch (rule.condition()) {
                    case PRICE_ABOVE, PRICE_BELOW -> stock.lastPrice();
                    case CHANGE_PCT_ABOVE, CHANGE_PCT_BELOW -> stock.changePercent();
                    default -> null;
                };
                if (currentValue == null) {
                    return unavailable(rule, stock, dataStatus, "行情資料不足，無法評估警示");
                }
                triggered = switch (rule.condition()) {
                    case PRICE_ABOVE, CHANGE_PCT_ABOVE -> currentValue.compareTo(rule.threshold()) >= 0;
                    case PRICE_BELOW, CHANGE_PCT_BELOW -> currentValue.compareTo(rule.threshold()) <= 0;
                    default -> false;
                };
                String unit = rule.condition().name().startsWith("CHANGE") ? "%" : "";
                message = triggered
                    ? rule.symbol() + " 目前值 " + display(currentValue) + unit + " 已達警示門檻 " + display(rule.threshold()) + unit
                    : rule.symbol() + " 目前值 " + display(currentValue) + unit + " 尚未達到門檻 " + display(rule.threshold()) + unit;
            }
            return new WatchlistAlertEvaluation(
                rule.id(), rule.market(), rule.symbol(), stock.name(), rule.condition(), rule.threshold(),
                currentValue, currentLabel, triggered ? "TRIGGERED" : "NORMAL", triggered, false, null, message,
                stock.source(), dataStatus, stock.observedAt()
            );
        } catch (RuntimeException ex) {
            return new WatchlistAlertEvaluation(
                rule.id(), rule.market(), rule.symbol(), rule.symbol(), rule.condition(), rule.threshold(),
                null, "", "DATA_UNAVAILABLE", false, false, null, "目前無法取得行情：" + safeMessage(ex), "unknown", "UNAVAILABLE", null
            );
        }
    }

    private static WatchlistAlertEvaluation unavailable(WatchlistAlertRule rule, StockRecord stock, String dataStatus, String message) {
        return new WatchlistAlertEvaluation(
            rule.id(), rule.market(), rule.symbol(), stock.name(), rule.condition(), rule.threshold(),
            null, "", "DATA_UNAVAILABLE", false, false, null, message, stock.source(), dataStatus, stock.observedAt()
        );
    }

    private Optional<WatchlistAlertRule> find(String userEmail, String id) {
        return list(userEmail).stream().filter(rule -> rule.id().equals(id)).findFirst();
    }

    private void replace(String userEmail, WatchlistAlertRule updated) {
        List<WatchlistAlertRule> rules = list(userEmail).stream()
            .map(rule -> rule.id().equals(updated.id()) ? updated : rule)
            .toList();
        write(fileForUserSave(userEmail), rules);
    }

    private List<WatchlistAlertRule> read(Path target) {
        if (!Files.exists(target)) {
            return List.of();
        }
        try {
            List<WatchlistAlertRule> rules = new ArrayList<>();
            for (String line : Files.readAllLines(target, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|", -1);
                if (parts.length != 8) {
                    continue;
                }
                try {
                    rules.add(new WatchlistAlertRule(
                        parts[0], Market.valueOf(parts[1]), parts[2], WatchlistAlertCondition.valueOf(parts[3]),
                        parts[4].isBlank() ? null : new BigDecimal(parts[4]), Boolean.parseBoolean(parts[5]),
                        Instant.parse(parts[6]), Instant.parse(parts[7])
                    ));
                } catch (RuntimeException ignored) {
                    // Ignore a malformed individual rule and keep the center usable.
                }
            }
            return rules.stream().sorted(Comparator.comparing(WatchlistAlertRule::createdAt)).toList();
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read watchlist alerts: " + target, ex);
        }
    }

    private void write(Path target, List<WatchlistAlertRule> rules) {
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            List<String> lines = rules.stream().map(rule -> String.join("|",
                rule.id(), rule.market().name(), rule.symbol(), rule.condition().name(),
                rule.threshold() == null ? "" : rule.threshold().toPlainString(), Boolean.toString(rule.enabled()),
                rule.createdAt().toString(), rule.updatedAt().toString()
            )).toList();
            AtomicFileWriter.writeLines(target, lines);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot write watchlist alerts: " + target, ex);
        }
    }

    private List<WatchlistAlertEvent> readEvents(String userEmail) {
        String normalizedEmail = normalizeEmail(userEmail);
        if (jdbcTemplate != null) {
            return jdbcTemplate.query("""
                SELECT id, rule_id, market, symbol, condition, status, current_value, message,
                       source, data_status, observed_at, created_at
                FROM stockai_watchlist_alert_events
                WHERE email = ?
                ORDER BY created_at DESC, id DESC
                """, (rs, rowNum) -> new WatchlistAlertEvent(
                    rs.getString("id"), rs.getString("rule_id"), Market.valueOf(rs.getString("market")),
                    rs.getString("symbol"), WatchlistAlertCondition.valueOf(rs.getString("condition")),
                    rs.getString("status"), rs.getBigDecimal("current_value"), rs.getString("message"),
                    rs.getString("source"), rs.getString("data_status"),
                    timestampToInstant(rs.getTimestamp("observed_at")), timestampToInstant(rs.getTimestamp("created_at"))
                ), normalizedEmail);
        }
        return readEvents(fileForUserEventsLoad(normalizedEmail));
    }

    private List<WatchlistAlertEvent> readEvents(Path target) {
        if (!Files.exists(target)) {
            return List.of();
        }
        try {
            List<WatchlistAlertEvent> events = new ArrayList<>();
            for (String line : Files.readAllLines(target, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|", -1);
                if (parts.length != 12) {
                    continue;
                }
                try {
                    events.add(new WatchlistAlertEvent(
                        parts[0], parts[1], Market.valueOf(parts[2]), parts[3], WatchlistAlertCondition.valueOf(parts[4]),
                        parts[5], parts[6].isBlank() ? null : new BigDecimal(parts[6]), decodeText(parts[7]), parts[8], parts[9],
                        parts[10].isBlank() ? null : Instant.parse(parts[10]), Instant.parse(parts[11])
                    ));
                } catch (RuntimeException ignored) {
                    // Ignore one malformed history row and keep alert evaluation available.
                }
            }
            return events.stream()
                .sorted(Comparator.comparing(WatchlistAlertEvent::createdAt).reversed()
                    .thenComparing(WatchlistAlertEvent::id, Comparator.reverseOrder()))
                .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read watchlist alert events: " + target, ex);
        }
    }

    private void appendEvents(String userEmail, List<WatchlistAlertEvent> existing, List<WatchlistAlertEvent> additions) {
        if (jdbcTemplate != null) {
            String normalizedEmail = normalizeEmail(userEmail);
            for (WatchlistAlertEvent event : additions) {
                jdbcTemplate.update("""
                    INSERT INTO stockai_watchlist_alert_events
                        (id, email, rule_id, market, symbol, condition, status, current_value, message,
                         source, data_status, observed_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    event.id(), normalizedEmail, event.ruleId(), event.market().name(), event.symbol(), event.condition().name(),
                    event.status(), event.currentValue(), event.message(), event.source(), event.dataStatus(),
                    toTimestamp(event.observedAt()), toTimestamp(event.createdAt()));
            }
            return;
        }
        List<WatchlistAlertEvent> merged = new ArrayList<>(existing);
        merged.addAll(additions);
        writeEvents(fileForUserEventsSave(userEmail), merged);
    }

    private void appendNotifications(String userEmail, List<WatchlistAlertEvent> events) {
        List<WatchlistAlertEvent> triggered = events.stream()
            .filter(event -> "TRIGGERED".equals(event.status()))
            .toList();
        if (triggered.isEmpty()) {
            return;
        }
        String normalizedEmail = normalizeEmail(userEmail);
        if (!readNotificationPreferences(normalizedEmail).localEnabled()) {
            return;
        }
        if (jdbcTemplate != null) {
            for (WatchlistAlertEvent event : triggered) {
                WatchlistAlertNotification notification = notificationFrom(event);
                jdbcTemplate.update("""
                    INSERT INTO stockai_watchlist_alert_notifications
                        (id, email, event_id, rule_id, market, symbol, condition, title, message,
                         channel, state, source, data_status, created_at, read_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (email, event_id, channel) DO NOTHING
                    """,
                    notification.id(), normalizedEmail, notification.eventId(), notification.ruleId(), notification.market().name(),
                    notification.symbol(), notification.condition().name(), notification.title(), notification.message(),
                    notification.channel(), notification.state(), notification.source(), notification.dataStatus(),
                    toTimestamp(notification.createdAt()), toTimestamp(notification.readAt()));
            }
            return;
        }
        List<WatchlistAlertNotification> existing = readNotifications(fileForUserNotificationsLoad(normalizedEmail));
        List<WatchlistAlertNotification> merged = new ArrayList<>(existing);
        for (WatchlistAlertEvent event : triggered) {
            boolean duplicate = existing.stream().anyMatch(item -> item.eventId().equals(event.id()) && "LOCAL_ONLY".equals(item.channel()));
            if (!duplicate) {
                WatchlistAlertNotification notification = notificationFrom(event);
                merged.add(notification);
                existing = new ArrayList<>(merged);
            }
        }
        writeNotifications(fileForUserNotificationsSave(normalizedEmail), merged);
    }

    private static WatchlistAlertNotification notificationFrom(WatchlistAlertEvent event) {
        return new WatchlistAlertNotification(
            UUID.randomUUID().toString(), event.id(), event.ruleId(), event.market(), event.symbol(), event.condition(),
            event.symbol() + " 警示觸發", event.message(), "LOCAL_ONLY", "UNREAD", event.source(), event.dataStatus(),
            event.createdAt(), null
        );
    }

    private List<WatchlistAlertNotification> readNotifications(String userEmail) {
        String normalizedEmail = normalizeEmail(userEmail);
        if (jdbcTemplate != null) {
            return jdbcTemplate.query("""
                SELECT id, event_id, rule_id, market, symbol, condition, title, message, channel, state,
                       source, data_status, created_at, read_at
                FROM stockai_watchlist_alert_notifications
                WHERE email = ?
                ORDER BY created_at DESC, id DESC
                """, (rs, rowNum) -> new WatchlistAlertNotification(
                    rs.getString("id"), rs.getString("event_id"), rs.getString("rule_id"),
                    Market.valueOf(rs.getString("market")), rs.getString("symbol"),
                    WatchlistAlertCondition.valueOf(rs.getString("condition")), rs.getString("title"),
                    rs.getString("message"), rs.getString("channel"), rs.getString("state"),
                    rs.getString("source"), rs.getString("data_status"), timestampToInstant(rs.getTimestamp("created_at")),
                    timestampToInstant(rs.getTimestamp("read_at"))
                ), normalizedEmail);
        }
        return readNotifications(fileForUserNotificationsLoad(normalizedEmail));
    }

    private List<WatchlistAlertNotification> readNotifications(Path target) {
        if (!Files.exists(target)) {
            return List.of();
        }
        try {
            List<WatchlistAlertNotification> notifications = new ArrayList<>();
            for (String line : Files.readAllLines(target, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|", -1);
                if (parts.length != 14) {
                    continue;
                }
                try {
                    notifications.add(new WatchlistAlertNotification(
                        parts[0], parts[1], parts[2], Market.valueOf(parts[3]), parts[4],
                        WatchlistAlertCondition.valueOf(parts[5]), decodeText(parts[6]), decodeText(parts[7]),
                        parts[8], parts[9], parts[10], parts[11], Instant.parse(parts[12]),
                        parts[13].isBlank() ? null : Instant.parse(parts[13])
                    ));
                } catch (RuntimeException ignored) {
                    // Ignore one malformed local notification and keep the feed usable.
                }
            }
            return notifications.stream()
                .sorted(Comparator.comparing(WatchlistAlertNotification::createdAt).reversed()
                    .thenComparing(WatchlistAlertNotification::id, Comparator.reverseOrder()))
                .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read watchlist alert notifications: " + target, ex);
        }
    }

    private void writeNotifications(Path target, List<WatchlistAlertNotification> notifications) {
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            List<String> lines = notifications.stream().map(notification -> String.join("|",
                notification.id(), notification.eventId(), notification.ruleId(), notification.market().name(), notification.symbol(),
                notification.condition().name(), encodeText(notification.title()), encodeText(notification.message()),
                notification.channel(), notification.state(), notification.source(), notification.dataStatus(),
                notification.createdAt().toString(), notification.readAt() == null ? "" : notification.readAt().toString()
            )).toList();
            AtomicFileWriter.writeLines(target, lines);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot write watchlist alert notifications: " + target, ex);
        }
    }

    private WatchlistAlertNotificationPreferences readNotificationPreferences(String userEmail) {
        String normalizedEmail = normalizeEmail(userEmail);
        if (jdbcTemplate != null) {
            return jdbcTemplate.query("""
                SELECT local_enabled, email_enabled, push_enabled, updated_at
                FROM stockai_watchlist_alert_notification_preferences
                WHERE email = ?
                """, (rs, rowNum) -> new WatchlistAlertNotificationPreferences(
                    rs.getBoolean("local_enabled"), rs.getBoolean("email_enabled"), rs.getBoolean("push_enabled"),
                    timestampToInstant(rs.getTimestamp("updated_at"))
                ), normalizedEmail).stream().findFirst().orElseGet(WatchlistAlertNotificationPreferences::defaults);
        }
        return readNotificationPreferences(fileForUserPreferencesLoad(normalizedEmail));
    }

    private WatchlistAlertNotificationPreferences readNotificationPreferences(Path target) {
        if (!Files.exists(target)) {
            return WatchlistAlertNotificationPreferences.defaults();
        }
        try {
            Map<String, String> values = new HashMap<>();
            for (String line : Files.readAllLines(target, StandardCharsets.UTF_8)) {
                int separator = line.indexOf('=');
                if (separator <= 0) continue;
                values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
            return new WatchlistAlertNotificationPreferences(
                parseBoolean(values.get("localEnabled"), true),
                parseBoolean(values.get("emailEnabled"), false),
                parseBoolean(values.get("pushEnabled"), false),
                parseInstant(values.get("updatedAt"), Instant.now())
            );
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read watchlist alert notification preferences: " + target, ex);
        }
    }

    private void writeNotificationPreferences(String userEmail, WatchlistAlertNotificationPreferences preferences) {
        String normalizedEmail = normalizeEmail(userEmail);
        if (jdbcTemplate != null) {
            jdbcTemplate.update("""
                INSERT INTO stockai_watchlist_alert_notification_preferences
                    (email, local_enabled, email_enabled, push_enabled, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (email) DO UPDATE SET
                    local_enabled = EXCLUDED.local_enabled,
                    email_enabled = EXCLUDED.email_enabled,
                    push_enabled = EXCLUDED.push_enabled,
                    updated_at = EXCLUDED.updated_at
                """, normalizedEmail, preferences.localEnabled(), preferences.emailEnabled(), preferences.pushEnabled(),
                toTimestamp(preferences.updatedAt()));
            return;
        }
        try {
            Path target = fileForUserPreferencesSave(normalizedEmail);
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            AtomicFileWriter.writeLines(target, List.of(
                "localEnabled=" + preferences.localEnabled(),
                "emailEnabled=" + preferences.emailEnabled(),
                "pushEnabled=" + preferences.pushEnabled(),
                "updatedAt=" + preferences.updatedAt()
            ));
        } catch (IOException ex) {
            throw new IllegalStateException("cannot write watchlist alert notification preferences", ex);
        }
    }

    private Path fileForUserPreferencesLoad(String userEmail) {
        Path current = fileForUserPreferencesSave(userEmail);
        if (Files.exists(current)) return current;
        Path parent = file.getParent() == null ? Path.of("data") : file.getParent();
        Path legacy = UserScopedFileLocator.resolveLegacy(parent, "watchlist-alert-notification-preferences-", ".properties", userEmail);
        return Files.exists(legacy) ? legacy : current;
    }

    private Path fileForUserPreferencesSave(String userEmail) {
        Path parent = file.getParent() == null ? Path.of("data") : file.getParent();
        return UserScopedFileLocator.resolve(parent, "watchlist-alert-notification-preferences-", ".properties", userEmail);
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private Path fileForUserNotificationsLoad(String userEmail) {
        Path current = fileForUserNotificationsSave(userEmail);
        if (Files.exists(current)) return current;
        Path parent = file.getParent() == null ? Path.of("data") : file.getParent();
        Path legacy = UserScopedFileLocator.resolveLegacy(parent, "watchlist-alert-notifications-", ".txt", userEmail);
        return Files.exists(legacy) ? legacy : current;
    }

    private Path fileForUserNotificationsSave(String userEmail) {
        Path parent = file.getParent() == null ? Path.of("data") : file.getParent();
        return UserScopedFileLocator.resolve(parent, "watchlist-alert-notifications-", ".txt", userEmail);
    }

    private void writeEvents(Path target, List<WatchlistAlertEvent> events) {
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            List<String> lines = events.stream().map(event -> String.join("|",
                event.id(), event.ruleId(), event.market().name(), event.symbol(), event.condition().name(), event.status(),
                event.currentValue() == null ? "" : event.currentValue().toPlainString(), encodeText(event.message()),
                event.source(), event.dataStatus(), event.observedAt() == null ? "" : event.observedAt().toString(),
                event.createdAt().toString()
            )).toList();
            AtomicFileWriter.writeLines(target, lines);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot write watchlist alert events: " + target, ex);
        }
    }

    private Path fileForUserEventsLoad(String userEmail) {
        Path current = fileForUserEventsSave(userEmail);
        if (Files.exists(current)) return current;
        Path parent = file.getParent() == null ? Path.of("data") : file.getParent();
        Path legacy = UserScopedFileLocator.resolveLegacy(parent, "watchlist-alert-events-", ".txt", userEmail);
        return Files.exists(legacy) ? legacy : current;
    }

    private Path fileForUserEventsSave(String userEmail) {
        Path parent = file.getParent() == null ? Path.of("data") : file.getParent();
        return UserScopedFileLocator.resolve(parent, "watchlist-alert-events-", ".txt", userEmail);
    }

    private static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant timestampToInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // Permit hand-authored legacy rows that stored plain text.
            return value;
        }
    }

    private Path fileForUserLoad(String userEmail) {
        Path current = fileForUserSave(userEmail);
        if (Files.exists(current)) return current;
        Path parent = file.getParent() == null ? Path.of("data") : file.getParent();
        Path legacy = UserScopedFileLocator.resolveLegacy(parent, "watchlist-alerts-", ".txt", userEmail);
        return Files.exists(legacy) ? legacy : current;
    }

    private Path fileForUserSave(String userEmail) {
        Path parent = file.getParent() == null ? Path.of("data") : file.getParent();
        return UserScopedFileLocator.resolve(parent, "watchlist-alerts-", ".txt", userEmail);
    }

    private static void validateThreshold(WatchlistAlertCondition condition, BigDecimal threshold) {
        if (condition == null) throw new IllegalArgumentException("alert condition is required");
        if (condition == WatchlistAlertCondition.DATA_QUALITY_NOT_OK) return;
        if (threshold == null) throw new IllegalArgumentException("alert threshold is required");
        if (condition == WatchlistAlertCondition.RISK_AT_LEAST
            && (threshold.compareTo(BigDecimal.ONE) < 0 || threshold.compareTo(BigDecimal.valueOf(3)) > 0)) {
            throw new IllegalArgumentException("risk threshold must be 1, 2, or 3");
        }
        if ((condition == WatchlistAlertCondition.CHANGE_PCT_ABOVE || condition == WatchlistAlertCondition.CHANGE_PCT_BELOW)
            && threshold.abs().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("change threshold must be between -100 and 100");
        }
    }

    private static String derivedDataStatus(StockRecord stock) {
        if (stock == null) return "UNAVAILABLE";
        if (stock.source() != null && stock.source().toLowerCase().startsWith("mock")) return "MOCK";
        if ("SNAPSHOT_ONLY".equalsIgnoreCase(stock.adjustmentStatus())) return "SNAPSHOT_ONLY";
        int count = stock.priceHistory() == null ? 0 : stock.priceHistory().size();
        return count == 0 ? "INSUFFICIENT_DATA" : count == 1 ? "SNAPSHOT_ONLY" : "PARTIAL";
    }

    private static int riskRank(String risk) {
        return switch (risk == null ? "" : risk.trim().toUpperCase()) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static String display(BigDecimal value) {
        return value == null ? "--" : value.stripTrailingZeros().toPlainString();
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
