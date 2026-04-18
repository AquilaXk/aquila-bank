ALTER TABLE auth_refresh_token_session
    ADD COLUMN device_name VARCHAR(120),
    ADD COLUMN ip_address VARCHAR(64);
