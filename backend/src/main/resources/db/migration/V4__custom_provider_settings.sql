ALTER TABLE stockai_account_settings
    ADD COLUMN IF NOT EXISTS custom_provider_name TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS custom_provider_api_key TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS custom_provider_url TEXT NOT NULL DEFAULT '';
