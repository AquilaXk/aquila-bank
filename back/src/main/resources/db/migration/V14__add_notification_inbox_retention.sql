-- notification inbox retention cleanup 는 created_at 기준 작은 batch 로만 지워 t3.micro 환경의 lock/vacuum 충격을 낮춥니다.
CREATE INDEX idx_notification_inbox_cleanup_cursor
    ON notification_inbox (created_at ASC, id ASC);

ALTER TABLE notification_user_read_state
    DROP CONSTRAINT fk_notification_user_read_state_notification;

ALTER TABLE notification_user_read_state
    ADD CONSTRAINT fk_notification_user_read_state_notification
        FOREIGN KEY (notification_id) REFERENCES notification_inbox (id) ON DELETE CASCADE;
