CREATE TABLE auth_external_identity_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    actor_subject VARCHAR(120) NOT NULL,
    change_type VARCHAR(16) NOT NULL CHECK (change_type IN ('LINK', 'UNLINK')),
    target_user_id BIGINT NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    subject_hash CHAR(64) NOT NULL,
    reason_code VARCHAR(32) NOT NULL CHECK (reason_code IN (
        'FRAUD_REVIEW',
        'USER_REQUEST',
        'OPS_MANUAL',
        'ACCOUNT_CLOSURE',
        'LEGACY_FREE_TEXT'
    )),
    reason VARCHAR(200) NOT NULL,
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('SUCCESS')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auth_external_identity_audit_user
        FOREIGN KEY (target_user_id) REFERENCES bank_user (id)
);

CREATE INDEX idx_auth_external_identity_audit_request_id
    ON auth_external_identity_audit (request_id);
