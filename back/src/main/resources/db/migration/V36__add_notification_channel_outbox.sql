-- 외부 channel delivery는 Kafka domain event outbox와 retry 상태를 분리합니다.
CREATE TABLE notification_channel_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    event_key VARCHAR(160) NOT NULL,
    payload JSONB NOT NULL,
    delivery_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_channel_outbox_notification
        FOREIGN KEY (notification_id) REFERENCES notification_inbox (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_channel_outbox_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_channel_outbox_account
        FOREIGN KEY (account_id) REFERENCES bank_account (id),
    CONSTRAINT uq_notification_channel_outbox_event_user_channel
        UNIQUE (event_key, user_id, channel),
    CONSTRAINT chk_notification_channel_outbox_category
        CHECK (category IN ('TRANSACTIONAL', 'SECURITY', 'MARKETING')),
    CONSTRAINT chk_notification_channel_outbox_channel
        CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT chk_notification_channel_outbox_status
        CHECK (delivery_status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    CONSTRAINT chk_notification_channel_outbox_retry_count
        CHECK (retry_count >= 0)
);

CREATE INDEX idx_notification_channel_outbox_queue
    ON notification_channel_outbox (delivery_status, available_at, id)
    WHERE delivery_status IN ('PENDING', 'FAILED');

CREATE INDEX idx_notification_channel_outbox_notification
    ON notification_channel_outbox (notification_id);

COMMENT ON TABLE notification_channel_outbox IS
    'notification EMAIL/SMS provider 발송 대상 queue. inbox ingest 재시도는 event_key/user/channel unique key로 중복 방지합니다.';

COMMENT ON INDEX idx_notification_channel_outbox_queue IS
    'provider worker가 PENDING/FAILED row를 available_at 순서로 작은 batch claim할 때 사용하는 queue index입니다.';
