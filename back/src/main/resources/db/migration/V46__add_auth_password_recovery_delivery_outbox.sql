CREATE TABLE auth_password_recovery_delivery_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    login_id VARCHAR(80) NOT NULL,
    delivery_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_password_recovery_delivery_outbox_request_id UNIQUE (request_id),
    CONSTRAINT fk_auth_password_recovery_delivery_outbox_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id) ON DELETE CASCADE,
    CONSTRAINT chk_auth_password_recovery_delivery_outbox_status
        CHECK (delivery_status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'QUARANTINED')),
    CONSTRAINT chk_auth_password_recovery_delivery_outbox_retry_count
        CHECK (retry_count >= 0)
);

CREATE INDEX idx_auth_password_recovery_delivery_outbox_due_claim
    ON auth_password_recovery_delivery_outbox (available_at, id)
    WHERE delivery_status IN ('PENDING', 'FAILED');

COMMENT ON TABLE auth_password_recovery_delivery_outbox IS
    'password recovery provider delivery를 request transaction 밖 worker로 분리하는 durable queue입니다.';

COMMENT ON INDEX idx_auth_password_recovery_delivery_outbox_due_claim IS
    'password recovery delivery worker가 due row를 available_at,id 순서로 작은 batch claim할 때 Sort 없이 쓰는 partial index입니다.';
