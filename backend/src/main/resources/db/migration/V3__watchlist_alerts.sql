CREATE TABLE IF NOT EXISTS stockai_watchlist_alerts (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL REFERENCES stockai_users(email) ON DELETE CASCADE,
    market TEXT NOT NULL,
    symbol TEXT NOT NULL,
    condition TEXT NOT NULL,
    threshold NUMERIC,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (email, market, symbol, condition, threshold)
);

CREATE INDEX IF NOT EXISTS stockai_watchlist_alerts_owner_idx
ON stockai_watchlist_alerts (email, enabled, created_at);
