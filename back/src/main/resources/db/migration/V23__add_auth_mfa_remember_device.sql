CREATE TABLE auth_mfa_remember_device (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    device_status VARCHAR(16) NOT NULL
        CHECK (device_status IN ('ACTIVE', 'REVOKED')),
    device_name VARCHAR(120) NOT NULL,
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_mfa_remember_device_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_mfa_remember_device_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id)
);

CREATE INDEX idx_auth_mfa_remember_device_user_status
    ON auth_mfa_remember_device (user_id ASC, device_status ASC, id ASC);
