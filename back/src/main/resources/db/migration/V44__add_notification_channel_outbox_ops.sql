ALTER TABLE notification_channel_outbox
    DROP CONSTRAINT chk_notification_channel_outbox_status;

ALTER TABLE notification_channel_outbox
    ADD CONSTRAINT chk_notification_channel_outbox_status
        CHECK (delivery_status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'QUARANTINED'));

CREATE INDEX idx_notification_channel_outbox_sent_cleanup
    ON notification_channel_outbox (sent_at, id)
    WHERE delivery_status = 'SENT';

CREATE INDEX idx_notification_channel_outbox_quarantined_cleanup
    ON notification_channel_outbox (updated_at, id)
    WHERE delivery_status = 'QUARANTINED';

COMMENT ON CONSTRAINT chk_notification_channel_outbox_status ON notification_channel_outbox IS
    'QUARANTINED는 max retry 도달 row를 provider worker claim 대상에서 제외하기 위한 격리 상태입니다.';

COMMENT ON INDEX idx_notification_channel_outbox_sent_cleanup IS
    '오래된 SENT channel delivery row를 sent_at 기준 작은 batch로 삭제할 때 사용하는 cleanup index입니다.';

COMMENT ON INDEX idx_notification_channel_outbox_quarantined_cleanup IS
    '오래된 QUARANTINED channel delivery row를 updated_at 기준 작은 batch로 삭제할 때 사용하는 cleanup index입니다.';
