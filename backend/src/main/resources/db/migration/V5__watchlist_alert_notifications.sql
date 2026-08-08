CREATE TABLE IF NOT EXISTS stockai_watchlist_alert_notifications (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL REFERENCES stockai_users(email) ON DELETE CASCADE,
    event_id TEXT NOT NULL,
    rule_id TEXT NOT NULL,
    market TEXT NOT NULL,
    symbol TEXT NOT NULL,
    condition TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    channel TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
    state TEXT NOT NULL DEFAULT 'UNREAD',
    source TEXT NOT NULL,
    data_status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ,
    UNIQUE (email, event_id, channel)
);

CREATE INDEX IF NOT EXISTS stockai_watchlist_alert_notifications_owner_idx
ON stockai_watchlist_alert_notifications (email, state, created_at DESC, id DESC);
