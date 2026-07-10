package com.example.stockai.auth;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.stockai.common.AtomicFileWriter;
import com.example.stockai.common.UserScopedFileLocator;

@Service
public class AccountSettingsService {
    private final Path root;
    private final JdbcTemplate jdbcTemplate;

    public AccountSettingsService() {
        this(Path.of(System.getProperty("stockai.account-settings.dir", "data/account-settings")), null);
    }

    @Autowired
    public AccountSettingsService(Optional<JdbcTemplate> jdbcTemplate) {
        this(Path.of(System.getProperty("stockai.account-settings.dir", "data/account-settings")), jdbcTemplate.orElse(null));
    }

    public AccountSettingsService(Path root) {
        this(root, null);
    }

    private AccountSettingsService(Path root, JdbcTemplate jdbcTemplate) {
        this.root = root;
        this.jdbcTemplate = jdbcTemplate;
    }

    public synchronized AccountSettingsView get(String email) {
        return toView(load(email));
    }

    public synchronized AccountSettingsView update(String email, AccountSettingsUpdate update) {
        Properties properties = load(email);
        putIfPresent(properties, "preferredProvider", update.preferredProvider() == null ? null : normalizeProvider(update.preferredProvider()));
        putIfPresent(properties, "openAiApiKey", update.openAiApiKey() == null ? null : normalizeSecret(update.openAiApiKey()));
        putIfPresent(properties, "geminiApiKey", update.geminiApiKey() == null ? null : normalizeSecret(update.geminiApiKey()));
        putIfPresent(properties, "deepSeekApiKey", update.deepSeekApiKey() == null ? null : normalizeSecret(update.deepSeekApiKey()));
        putIfPresent(properties, "mimoApiKey", update.mimoApiKey() == null ? null : normalizeSecret(update.mimoApiKey()));
        properties.setProperty("updatedAt", Instant.now().toString());
        save(email, properties);
        return toView(properties);
    }

    public synchronized String openAiApiKey(String email) {
        return normalizeSecret(load(email).getProperty("openAiApiKey"));
    }

    public synchronized String geminiApiKey(String email) {
        return normalizeSecret(load(email).getProperty("geminiApiKey"));
    }

    public synchronized String deepSeekApiKey(String email) {
        return normalizeSecret(load(email).getProperty("deepSeekApiKey"));
    }

    public synchronized String mimoApiKey(String email) {
        return normalizeSecret(load(email).getProperty("mimoApiKey"));
    }

    private AccountSettingsView toView(Properties properties) {
        return new AccountSettingsView(
            normalizeProvider(properties.getProperty("preferredProvider")),
            !normalizeSecret(properties.getProperty("openAiApiKey")).isBlank(),
            !normalizeSecret(properties.getProperty("geminiApiKey")).isBlank(),
            !normalizeSecret(properties.getProperty("deepSeekApiKey")).isBlank(),
            !normalizeSecret(properties.getProperty("mimoApiKey")).isBlank(),
            properties.getProperty("updatedAt", "")
        );
    }

    private Properties load(String email) {
        if (jdbcTemplate != null) {
            return loadFromDatabase(email);
        }
        Properties properties = new Properties();
        Path file = fileForLoad(email);
        if (!Files.exists(file)) {
            return properties;
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("cannot load account settings: " + file, ex);
        }
    }

    private void save(String email, Properties properties) {
        if (jdbcTemplate != null) {
            saveToDatabase(email, properties);
            return;
        }
        Path file = fileForSave(email);
        try {
            Files.createDirectories(root);
            StringWriter writer = new StringWriter();
            properties.store(writer, null);
            AtomicFileWriter.writeString(file, writer.toString());
        } catch (IOException ex) {
            throw new IllegalStateException("cannot save account settings: " + file, ex);
        }
    }

    private Properties loadFromDatabase(String email) {
        Properties properties = new Properties();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT preferred_provider, openai_api_key, gemini_api_key, deepseek_api_key, mimo_api_key, updated_at
            FROM stockai_account_settings
            WHERE email = ?
            """, email);
        if (rows.isEmpty()) {
            return properties;
        }
        Map<String, Object> row = rows.get(0);
        properties.setProperty("preferredProvider", string(row.get("preferred_provider")));
        properties.setProperty("openAiApiKey", string(row.get("openai_api_key")));
        properties.setProperty("geminiApiKey", string(row.get("gemini_api_key")));
        properties.setProperty("deepSeekApiKey", string(row.get("deepseek_api_key")));
        properties.setProperty("mimoApiKey", string(row.get("mimo_api_key")));
        properties.setProperty("updatedAt", instantString(row.get("updated_at")));
        return properties;
    }

    private void saveToDatabase(String email, Properties properties) {
        Instant updatedAt = Instant.parse(properties.getProperty("updatedAt", Instant.now().toString()));
        jdbcTemplate.update("""
            INSERT INTO stockai_account_settings (
                email, preferred_provider, openai_api_key, gemini_api_key, deepseek_api_key, mimo_api_key, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (email) DO UPDATE SET
                preferred_provider = EXCLUDED.preferred_provider,
                openai_api_key = EXCLUDED.openai_api_key,
                gemini_api_key = EXCLUDED.gemini_api_key,
                deepseek_api_key = EXCLUDED.deepseek_api_key,
                mimo_api_key = EXCLUDED.mimo_api_key,
                updated_at = EXCLUDED.updated_at
            """,
            email,
            normalizeProvider(properties.getProperty("preferredProvider")),
            normalizeSecret(properties.getProperty("openAiApiKey")),
            normalizeSecret(properties.getProperty("geminiApiKey")),
            normalizeSecret(properties.getProperty("deepSeekApiKey")),
            normalizeSecret(properties.getProperty("mimoApiKey")),
            updatedAt
        );
    }

    private Path fileForLoad(String email) {
        Path current = fileForSave(email);
        if (Files.exists(current)) {
            return current;
        }
        Path legacy = UserScopedFileLocator.resolveLegacy(root, "", ".properties", email);
        return Files.exists(legacy) ? legacy : current;
    }

    private Path fileForSave(String email) {
        return UserScopedFileLocator.resolve(root, "", ".properties", email);
    }

    private static void putIfPresent(Properties properties, String key, String value) {
        if (value == null) {
            return;
        }
        if (value.isBlank()) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
    }

    private static String normalizeSecret(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "";
        }
        String normalized = provider.trim().toUpperCase();
        return switch (normalized) {
            case "OPENAI", "GEMINI", "CLAUDE", "DEEPSEEK", "MIMO" -> normalized;
            default -> "";
        };
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String instantString(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().toString();
        }
        return value == null ? "" : value.toString();
    }

    public record AccountSettingsUpdate(String preferredProvider, String openAiApiKey, String geminiApiKey, String deepSeekApiKey, String mimoApiKey) {}

    public record AccountSettingsView(
        String preferredProvider,
        boolean hasOpenAiApiKey,
        boolean hasGeminiApiKey,
        boolean hasDeepSeekApiKey,
        boolean hasMimoApiKey,
        String updatedAt
    ) {}
}
