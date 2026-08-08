package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.stockai.market.Market;

class WatchlistAlertServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsOwnRulesAndEvaluatesPriceAndQuality() {
        Path watchlistFile = tempDir.resolve("watchlist.txt");
        WatchlistService watchlist = new WatchlistService(watchlistFile);
        watchlist.add("alice@example.com", new WatchlistItem(Market.US, "AAPL"));
        StockService stocks = new StockService(new MockMarketDataProvider());
        WatchlistAlertService service = new WatchlistAlertService(watchlistFile, watchlist, stocks);

        WatchlistAlertRule priceRule = service.add(
            "alice@example.com", Market.US, "AAPL", WatchlistAlertCondition.PRICE_ABOVE, new BigDecimal("200"), true
        );
        WatchlistAlertRule qualityRule = service.add(
            "alice@example.com", Market.US, "AAPL", WatchlistAlertCondition.DATA_QUALITY_NOT_OK, null, true
        );

        WatchlistAlertCenterResponse response = service.evaluate("alice@example.com");

        assertThat(response.rules()).extracting(WatchlistAlertRule::id).containsExactly(priceRule.id(), qualityRule.id());
        assertThat(response.triggeredCount()).isEqualTo(2);
        assertThat(response.newTriggerCount()).isEqualTo(2);
        assertThat(response.evaluations()).extracting(WatchlistAlertEvaluation::status)
            .containsExactly("TRIGGERED", "TRIGGERED");
        assertThat(response.evaluations()).extracting(WatchlistAlertEvaluation::newlyTriggered)
            .containsExactly(true, true);
        assertThat(response.recentEvents()).hasSize(2);
        WatchlistAlertNotificationResponse notifications = service.listNotifications("alice@example.com", 20);
        assertThat(notifications.notifications()).hasSize(2);
        assertThat(notifications.unreadCount()).isEqualTo(2);
        WatchlistAlertNotification readNotification = service.setNotificationRead(
            "alice@example.com", notifications.notifications().get(0).id(), true
        );
        assertThat(readNotification.state()).isEqualTo("READ");
        assertThat(service.listNotifications("alice@example.com", 20).unreadCount()).isEqualTo(1);
        WatchlistAlertNotificationCleanupResponse cleanup = service.clearReadNotifications("alice@example.com");
        assertThat(cleanup.deleted()).isEqualTo(1);
        assertThat(cleanup.localOnly()).isTrue();
        assertThat(response.evaluations().get(0).source()).isEqualTo("mock");
        assertThat(response.evaluations().get(1).dataStatus()).isEqualTo("MOCK");

        WatchlistAlertCenterResponse refreshed = service.evaluate("alice@example.com");
        assertThat(refreshed.triggeredCount()).isEqualTo(2);
        assertThat(refreshed.newTriggerCount()).isZero();
        assertThat(refreshed.evaluations()).extracting(WatchlistAlertEvaluation::newlyTriggered)
            .containsExactly(false, false);
        assertThat(refreshed.recentEvents()).hasSize(2);
        assertThat(service.listNotifications("alice@example.com", 20).notifications()).hasSize(1);
        WatchlistAlertNotificationPreferenceResponse preferences = service.updateNotificationPreferences(
            "alice@example.com", false, true, true
        );
        assertThat(preferences.preferences().localEnabled()).isFalse();
        assertThat(preferences.preferences().emailEnabled()).isTrue();
        assertThat(preferences.externalChannelsAvailable()).isFalse();

        WatchlistAlertService reloaded = new WatchlistAlertService(watchlistFile, watchlist, stocks);
        assertThat(reloaded.list("alice@example.com")).extracting(WatchlistAlertRule::id)
            .containsExactly(priceRule.id(), qualityRule.id());
        assertThat(reloaded.notificationPreferences("alice@example.com").preferences().localEnabled()).isFalse();
        assertThat(reloaded.list("bob@example.com")).isEmpty();
    }

    @Test
    void rejectsRuleForSymbolOutsideWatchlistAndSupportsDisableDelete() {
        Path watchlistFile = tempDir.resolve("watchlist.txt");
        WatchlistService watchlist = new WatchlistService(watchlistFile);
        StockService stocks = new StockService(new MockMarketDataProvider());
        WatchlistAlertService service = new WatchlistAlertService(watchlistFile, watchlist, stocks);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.add(
            "alice@example.com", Market.US, "TSLA", WatchlistAlertCondition.PRICE_BELOW, new BigDecimal("300"), true
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("watchlist");

        watchlist.add("alice@example.com", new WatchlistItem(Market.US, "AAPL"));
        WatchlistAlertRule rule = service.add(
            "alice@example.com", Market.US, "AAPL", WatchlistAlertCondition.PRICE_ABOVE, new BigDecimal("0"), true
        );
        assertThat(service.setEnabled("alice@example.com", rule.id(), false).enabled()).isFalse();
        assertThat(service.evaluate("alice@example.com").evaluations().get(0).status()).isEqualTo("DISABLED");
        assertThat(service.evaluate("alice@example.com").newTriggerCount()).isZero();
        service.setEnabled("alice@example.com", rule.id(), true);
        WatchlistAlertCenterResponse reenabled = service.evaluate("alice@example.com");
        assertThat(reenabled.newTriggerCount()).isEqualTo(1);
        assertThat(reenabled.evaluations().get(0).newlyTriggered()).isTrue();
        assertThat(reenabled.recentEvents()).hasSize(2);
        assertThat(service.listNotifications("alice@example.com", 20).notifications()).hasSize(1);
        service.delete("alice@example.com", rule.id());
        assertThat(service.list("alice@example.com")).isEmpty();
        assertThat(service.evaluate("alice@example.com").recentEvents()).isEmpty();
        assertThat(service.listNotifications("alice@example.com", 20).notifications()).isEmpty();
    }
}
