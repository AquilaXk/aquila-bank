CREATE TABLE auth_mfa_backup_code (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    code_status VARCHAR(16) NOT NULL
        CHECK (code_status IN ('ACTIVE', 'USED', 'SUPERSEDED')),
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_mfa_backup_code_hash UNIQUE (code_hash),
    CONSTRAINT fk_auth_mfa_backup_code_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id)
);

CREATE INDEX idx_auth_mfa_backup_code_user_status
    ON auth_mfa_backup_code (user_id ASC, code_status ASC, id ASC);
