CREATE INDEX idx_notification_channel_outbox_due_claim
    ON notification_channel_outbox (available_at, id)
    WHERE delivery_status IN ('PENDING', 'FAILED');

COMMENT ON INDEX idx_notification_channel_outbox_due_claim IS
    'provider worker claim이 PENDING/FAILED 전체 queue를 available_at,id 순서로 작은 batch 조회할 때 Sort 없이 사용하는 partial index입니다.';
