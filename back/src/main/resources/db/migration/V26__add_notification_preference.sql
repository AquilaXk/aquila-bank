CREATE TABLE notification_preference (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_preference_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_preference_user_category_channel
        UNIQUE (user_id, category, channel)
);

CREATE INDEX idx_notification_preference_user_category_channel
    ON notification_preference (user_id, category, channel);

COMMENT ON INDEX idx_notification_preference_user_category_channel IS
    'user preference exact lookup/upsert 경로용. user_id 범위 안에서 category/channel 조합을 빠르게 찾습니다.';
