package com.example.stockai.stock;

record WatchlistAlertNotificationPreferenceResponse(
    WatchlistAlertNotificationPreferences preferences,
    boolean externalChannelsAvailable,
    String externalBlockReason,
    String source
) {
    WatchlistAlertNotificationPreferenceResponse {
        preferences = preferences == null ? WatchlistAlertNotificationPreferences.defaults() : preferences;
        externalBlockReason = externalBlockReason == null || externalBlockReason.isBlank()
            ? "目前只支援 LOCAL_ONLY；Email／推播尚未設定且不會外送。" : externalBlockReason.trim();
        source = source == null || source.isBlank() ? "watchlist-alert-notification-preferences-v1" : source.trim();
    }
}
