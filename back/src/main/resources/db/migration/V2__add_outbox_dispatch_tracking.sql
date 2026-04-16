-- updated_at lets the poller reclaim events that were stuck in SENDING after a worker crash.
ALTER TABLE outbox_event
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- The dispatch queue index covers both fresh retries and stale in-flight event recovery.
CREATE INDEX idx_outbox_event_dispatch_queue
    ON outbox_event (publish_status, available_at, updated_at, id);
