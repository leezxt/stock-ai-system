CREATE TABLE IF NOT EXISTS stockai_watchlist_alert_events (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL REFERENCES stockai_users(email) ON DELETE CASCADE,
    rule_id TEXT NOT NULL,
    market TEXT NOT NULL,
    symbol TEXT NOT NULL,
    condition TEXT NOT NULL,
    status TEXT NOT NULL,
    current_value NUMERIC,
    message TEXT NOT NULL,
    source TEXT NOT NULL,
    data_status TEXT NOT NULL,
    observed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS stockai_watchlist_alert_events_owner_idx
ON stockai_watchlist_alert_events (email, rule_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS stockai_watchlist_alert_events_recent_idx
ON stockai_watchlist_alert_events (email, created_at DESC, id DESC);
