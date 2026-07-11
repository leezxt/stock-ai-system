package com.example.stockai.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.stockai.common.UserScopedFileLocator;

class AccountSettingsServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsStoredKeysWithoutReturningSecrets() throws Exception {
        AccountSettingsService service = new AccountSettingsService(tempDir);

        AccountSettingsService.AccountSettingsView saved = service.update(
            "demo@example.com",
            new AccountSettingsService.AccountSettingsUpdate("DEEPSEEK", "openai-secret", "gemini-secret", "deepseek-secret")
        );
        AccountSettingsService.AccountSettingsView loaded = service.get("demo@example.com");

        assertThat(saved.preferredProvider()).isEqualTo("DEEPSEEK");
        assertThat(loaded.hasOpenAiApiKey()).isTrue();
        assertThat(loaded.hasGeminiApiKey()).isTrue();
        assertThat(loaded.hasDeepSeekApiKey()).isTrue();
        assertThat(service.openAiApiKey("demo@example.com")).isEqualTo("openai-secret");
        assertThat(service.geminiApiKey("demo@example.com")).isEqualTo("gemini-secret");
        assertThat(service.deepSeekApiKey("demo@example.com")).isEqualTo("deepseek-secret");
        String persisted = Files.readString(UserScopedFileLocator.resolve(tempDir, "", ".properties", "demo@example.com"));
        assertThat(persisted).contains("enc\\:v1\\:").doesNotContain("openai-secret", "gemini-secret", "deepseek-secret");
    }

    @Test
    void keepsExistingKeysWhenUpdateOmitsThem() {
        AccountSettingsService service = new AccountSettingsService(tempDir);
        service.update("demo@example.com", new AccountSettingsService.AccountSettingsUpdate("OPENAI", "openai-secret", "", "deepseek-secret"));

        AccountSettingsService.AccountSettingsView updated = service.update(
            "demo@example.com",
            new AccountSettingsService.AccountSettingsUpdate("GEMINI", null, null, null)
        );

        assertThat(updated.preferredProvider()).isEqualTo("GEMINI");
        assertThat(service.openAiApiKey("demo@example.com")).isEqualTo("openai-secret");
        assertThat(service.deepSeekApiKey("demo@example.com")).isEqualTo("deepseek-secret");
    }

    @Test
    void readsLegacyFileAndWritesFutureUpdatesToShaPath() throws Exception {
        Path legacy = UserScopedFileLocator.resolveLegacy(tempDir, "", ".properties", "legacy@example.com");
        Properties properties = new Properties();
        properties.setProperty("preferredProvider", "OPENAI");
        properties.setProperty("openAiApiKey", "legacy-openai");
        properties.setProperty("updatedAt", "2026-07-10T00:00:00Z");
        Files.createDirectories(tempDir);
        try (var writer = Files.newBufferedWriter(legacy)) {
            properties.store(writer, null);
        }

        AccountSettingsService service = new AccountSettingsService(tempDir);
        AccountSettingsService.AccountSettingsView loaded = service.get("legacy@example.com");
        assertThat(loaded.preferredProvider()).isEqualTo("OPENAI");
        assertThat(service.openAiApiKey("legacy@example.com")).isEqualTo("legacy-openai");

        service.update("legacy@example.com", new AccountSettingsService.AccountSettingsUpdate("GEMINI", null, "gemini-secret", null));

        Path current = UserScopedFileLocator.resolve(tempDir, "", ".properties", "legacy@example.com");
        assertThat(current).exists();
        assertThat(service.geminiApiKey("legacy@example.com")).isEqualTo("gemini-secret");
    }
}
