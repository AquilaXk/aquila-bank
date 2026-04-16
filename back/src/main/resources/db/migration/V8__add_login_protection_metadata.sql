ALTER TABLE bank_user
    ADD COLUMN failed_login_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_login_failed_at TIMESTAMPTZ,
    ADD COLUMN login_locked_until TIMESTAMPTZ,
    ADD COLUMN last_login_succeeded_at TIMESTAMPTZ;
