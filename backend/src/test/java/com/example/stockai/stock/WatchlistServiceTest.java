package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.stockai.common.UserScopedFileLocator;
import com.example.stockai.market.Market;

class WatchlistServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsItemsToDisk() {
        Path file = tempDir.resolve("watchlist.txt");
        WatchlistService first = new WatchlistService(file);

        first.add(new WatchlistItem(Market.US, "TSLA"));

        WatchlistService second = new WatchlistService(file);
        assertThat(second.list()).extracting(WatchlistItem::symbol).contains("TSLA");
    }

    @Test
    void keepsUserWatchlistsSeparated() {
        Path file = tempDir.resolve("watchlist.txt");
        WatchlistService service = new WatchlistService(file);

        service.add("alpha@example.com", new WatchlistItem(Market.US, "TSLA"));
        service.add("beta@example.com", new WatchlistItem(Market.US, "MSFT"));

        assertThat(service.list("alpha@example.com")).extracting(WatchlistItem::symbol).contains("TSLA").doesNotContain("MSFT");
        assertThat(service.list("beta@example.com")).extracting(WatchlistItem::symbol).contains("MSFT").doesNotContain("TSLA");
    }

    @Test
    void loadsLegacyUserWatchlistFileAndMigratesOnWrite() throws Exception {
        Path file = tempDir.resolve("watchlist.txt");
        Path legacy = UserScopedFileLocator.resolveLegacy(tempDir, "watchlist-", ".txt", "legacy@example.com");
        Files.write(legacy, List.of("US:NVDA"), StandardCharsets.UTF_8);

        WatchlistService service = new WatchlistService(file);
        assertThat(service.list("legacy@example.com")).extracting(WatchlistItem::symbol).contains("NVDA");

        service.add("legacy@example.com", new WatchlistItem(Market.US, "TSLA"));

        WatchlistService reloaded = new WatchlistService(file);
        assertThat(reloaded.list("legacy@example.com")).extracting(WatchlistItem::symbol).contains("NVDA", "TSLA");
        assertThat(UserScopedFileLocator.resolve(tempDir, "watchlist-", ".txt", "legacy@example.com")).exists();
        assertThat(legacy).exists();
    }
}
