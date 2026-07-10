package com.example.stockai.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsAuthProviderForLocalAndGoogleUsers() throws Exception {
        Path file = tempDir.resolve("users.txt");
        UserStore store = new UserStore(file);

        store.create("local@example.com", "salt", "hash");
        store.findOrCreateGoogle("google@example.com");

        UserStore reloaded = new UserStore(file);
        assertThat(reloaded.find("local@example.com")).get().extracting(UserRecord::authProvider).isEqualTo("LOCAL");
        assertThat(reloaded.find("google@example.com")).get().extracting(UserRecord::authProvider).isEqualTo("GOOGLE");
        assertThat(Files.readString(file)).contains("local@example.com").contains("google@example.com");
    }
}
