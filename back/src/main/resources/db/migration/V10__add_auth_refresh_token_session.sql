CREATE TABLE auth_refresh_token_session (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    session_status VARCHAR(16) NOT NULL
        CHECK (session_status IN ('ACTIVE', 'ROTATED', 'REVOKED')),
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    rotated_at TIMESTAMPTZ,
    replaced_by_session_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_refresh_token_session_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_refresh_token_session_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id),
    CONSTRAINT fk_auth_refresh_token_session_replaced_by
        FOREIGN KEY (replaced_by_session_id) REFERENCES auth_refresh_token_session (id)
);

CREATE INDEX idx_auth_refresh_token_session_user_status_expires
    ON auth_refresh_token_session (user_id, session_status, expires_at DESC, id DESC);
