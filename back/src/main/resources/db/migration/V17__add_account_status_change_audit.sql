CREATE TABLE account_status_change_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    actor_subject VARCHAR(120) NOT NULL,
    target_account_id BIGINT NOT NULL,
    before_status VARCHAR(16) NOT NULL
        CHECK (before_status IN ('ACTIVE', 'LOCKED', 'CLOSED')),
    after_status VARCHAR(16) NOT NULL
        CHECK (after_status IN ('ACTIVE', 'LOCKED', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_status_change_audit_account
        FOREIGN KEY (target_account_id) REFERENCES bank_account (id)
);

CREATE INDEX idx_account_status_change_audit_request_id
    ON account_status_change_audit (request_id);
