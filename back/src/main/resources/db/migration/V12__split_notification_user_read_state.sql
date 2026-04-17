-- JWT user 읽음은 공동 계좌 사용자별로 분리하고, 기존 notification_inbox.read_at 는 account-scoped 의미로 남깁니다.
CREATE TABLE notification_user_read_state (
    user_id BIGINT NOT NULL,
    notification_id BIGINT NOT NULL,
    read_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, notification_id),
    CONSTRAINT fk_notification_user_read_state_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id),
    CONSTRAINT fk_notification_user_read_state_notification
        FOREIGN KEY (notification_id) REFERENCES notification_inbox (id)
);

CREATE INDEX idx_notification_user_read_state_notification_user
    ON notification_user_read_state (notification_id, user_id);
