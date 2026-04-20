ALTER TABLE auth_refresh_token_session
    ADD COLUMN device_binding_hash VARCHAR(64);
