-- QUARANTINED는 자동 dispatch 대상에서 제외되는 poison event 격리 상태입니다.
ALTER TABLE outbox_event
    DROP CONSTRAINT IF EXISTS outbox_event_publish_status_check;

ALTER TABLE outbox_event
    ADD CONSTRAINT outbox_event_publish_status_check
        CHECK (publish_status IN ('PENDING', 'SENDING', 'FAILED', 'PUBLISHED', 'QUARANTINED'));

-- quarantine row는 일반 retry queue와 분리된 운영 확인 대상이므로 낮은 write 비용의 partial index만 둡니다.
CREATE INDEX idx_outbox_event_quarantine_lookup
    ON outbox_event (updated_at DESC, id DESC)
    WHERE publish_status = 'QUARANTINED';
