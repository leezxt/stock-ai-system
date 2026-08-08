package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.stockai.auth.UserStore;

class WatchlistAlertSchedulerTest {
    @Test
    void evaluatesAllAccountsAndReportsLocalOnlyRun() {
        UserStore users = mock(UserStore.class);
        WatchlistAlertService alerts = mock(WatchlistAlertService.class);
        when(users.listEmails()).thenReturn(List.of("alice@example.com", "bob@example.com"));
        WatchlistAlertCenterResponse response = new WatchlistAlertCenterResponse(
            List.of(), List.of(newEvaluation()), 1, 1, List.of(), Instant.now(), "test"
        );
        when(alerts.evaluate(anyString())).thenReturn(response);

        WatchlistAlertScheduler scheduler = new WatchlistAlertScheduler(users, alerts, true, 1_000, "LOCAL_ONLY");
        WatchlistAlertSchedulerStatus status = scheduler.runNow();

        assertThat(status.enabled()).isTrue();
        assertThat(status.notificationMode()).isEqualTo("LOCAL_ONLY");
        assertThat(status.lastEvaluatedUsers()).isEqualTo(2);
        assertThat(status.lastEvaluatedRules()).isEqualTo(2);
        assertThat(status.lastNewTriggerCount()).isEqualTo(2);
        assertThat(status.lastErrorCount()).isZero();
        assertThat(status.lastCompletedAt()).isNotNull();
        verify(alerts).evaluate("alice@example.com");
        verify(alerts).evaluate("bob@example.com");
    }

    @Test
    void isolatesOneAccountFailureAndDoesNotRunWhenDisabled() {
        UserStore users = mock(UserStore.class);
        WatchlistAlertService alerts = mock(WatchlistAlertService.class);
        when(users.listEmails()).thenReturn(List.of("alice@example.com", "bob@example.com"));
        when(alerts.evaluate("alice@example.com")).thenReturn(new WatchlistAlertCenterResponse(
            List.of(), List.of(), 0, 0, List.of(), Instant.now(), "test"
        ));
        when(alerts.evaluate("bob@example.com")).thenThrow(new IllegalStateException("provider unavailable"));

        WatchlistAlertScheduler scheduler = new WatchlistAlertScheduler(users, alerts, true, 1_000, "LOCAL_ONLY");
        WatchlistAlertSchedulerStatus status = scheduler.runNow();
        assertThat(status.lastEvaluatedUsers()).isEqualTo(1);
        assertThat(status.lastErrorCount()).isEqualTo(1);
        assertThat(status.lastError()).contains("provider unavailable");

        WatchlistAlertScheduler disabled = new WatchlistAlertScheduler(users, alerts, false, 1_000, "LOCAL_ONLY");
        WatchlistAlertSchedulerStatus disabledStatus = disabled.runNow();
        assertThat(disabledStatus.lastStartedAt()).isNull();
    }

    private static WatchlistAlertEvaluation newEvaluation() {
        return new WatchlistAlertEvaluation(
            "rule", com.example.stockai.market.Market.US, "AAPL", "Apple", WatchlistAlertCondition.PRICE_ABOVE,
            null, null, "", "TRIGGERED", true, true, Instant.now(), "triggered", "test", "MOCK", Instant.now()
        );
    }
}
