-- updated_at로 worker crash 후 SENDING 상태에 멈춘 event를 poller가 다시 회수할 수 있습니다.
ALTER TABLE outbox_event
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- dispatch queue index는 일반 retry와 stale in-flight event 회수를 함께 커버합니다.
CREATE INDEX idx_outbox_event_dispatch_queue
    ON outbox_event (publish_status, available_at, updated_at, id);
