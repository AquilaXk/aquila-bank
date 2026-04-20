CREATE TABLE auth_password_recovery_token (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    login_id VARCHAR(80) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    token_ciphertext VARCHAR(512) NOT NULL,
    token_nonce VARCHAR(64) NOT NULL,
    token_status VARCHAR(16) NOT NULL
        CHECK (token_status IN ('PENDING', 'USED', 'EXPIRED', 'SUPERSEDED')),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_password_recovery_token_request_id UNIQUE (request_id),
    CONSTRAINT uq_auth_password_recovery_token_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_password_recovery_token_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id)
);

CREATE INDEX idx_auth_password_recovery_token_status_expiry
    ON auth_password_recovery_token (token_status, expires_at ASC, user_id ASC);
