CREATE TABLE IF NOT EXISTS stockai_watchlist_alert_notification_preferences (
    email TEXT PRIMARY KEY REFERENCES stockai_users(email) ON DELETE CASCADE,
    local_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
