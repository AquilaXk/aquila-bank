CREATE TABLE auth_totp_credential (
    user_id BIGINT PRIMARY KEY,
    credential_status VARCHAR(16) NOT NULL
        CHECK (credential_status IN ('PENDING', 'ACTIVE')),
    secret_nonce VARCHAR(64) NOT NULL,
    secret_ciphertext VARCHAR(512) NOT NULL,
    pending_expires_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auth_totp_credential_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id)
);

CREATE INDEX idx_auth_totp_credential_status_expiry
    ON auth_totp_credential (credential_status, pending_expires_at ASC, user_id ASC);

CREATE TABLE auth_totp_login_challenge (
    user_id BIGINT PRIMARY KEY,
    challenge_id VARCHAR(64) NOT NULL,
    challenge_status VARCHAR(16) NOT NULL
        CHECK (challenge_status IN ('PENDING', 'VERIFIED', 'FAILED', 'EXPIRED')),
    attempt_count INT NOT NULL DEFAULT 0,
    device_name VARCHAR(120),
    ip_address VARCHAR(64),
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_totp_login_challenge_id UNIQUE (challenge_id),
    CONSTRAINT fk_auth_totp_login_challenge_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id)
);

CREATE INDEX idx_auth_totp_login_challenge_status_expiry
    ON auth_totp_login_challenge (challenge_status, expires_at ASC, user_id ASC);
