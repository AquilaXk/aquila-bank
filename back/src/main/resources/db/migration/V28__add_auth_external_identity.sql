CREATE TABLE auth_external_identity (
    provider_id VARCHAR(64) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (provider_id, subject),
    CONSTRAINT uq_auth_external_identity_provider_user UNIQUE (provider_id, user_id),
    CONSTRAINT fk_auth_external_identity_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id)
);

CREATE INDEX idx_auth_external_identity_user
    ON auth_external_identity (user_id);
