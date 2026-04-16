CREATE TABLE auth_status_change_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    actor_subject VARCHAR(120) NOT NULL,
    change_type VARCHAR(32) NOT NULL
        CHECK (change_type IN ('USER_STATUS', 'MEMBERSHIP_STATUS')),
    target_user_id BIGINT NOT NULL,
    target_account_id BIGINT,
    before_status VARCHAR(16) NOT NULL,
    after_status VARCHAR(16) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('SUCCESS')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auth_status_change_audit_user
        FOREIGN KEY (target_user_id) REFERENCES bank_user (id),
    CONSTRAINT fk_auth_status_change_audit_account
        FOREIGN KEY (target_account_id) REFERENCES bank_account (id)
);

CREATE INDEX idx_auth_status_change_audit_request_id
    ON auth_status_change_audit (request_id);
