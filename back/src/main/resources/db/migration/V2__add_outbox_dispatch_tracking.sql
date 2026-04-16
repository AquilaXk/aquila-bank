ALTER TABLE outbox_event
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_outbox_event_dispatch_queue
    ON outbox_event (publish_status, available_at, updated_at, id);
