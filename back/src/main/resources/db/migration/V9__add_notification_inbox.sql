-- notification inbox read model은 consumer 이후 제품 API가 바로 조회할 수 있는 최소 shape 로 둡니다.
CREATE TABLE notification_inbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL,
    event_key VARCHAR(80) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    title VARCHAR(120) NOT NULL,
    message VARCHAR(280) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_inbox_event_key UNIQUE (event_key),
    CONSTRAINT fk_notification_inbox_account
        FOREIGN KEY (account_id) REFERENCES bank_account (id)
);

CREATE INDEX idx_notification_inbox_account_cursor
    ON notification_inbox (account_id, created_at DESC, id DESC);

CREATE INDEX idx_notification_inbox_account_unread
    ON notification_inbox (account_id, read_at, id DESC);
