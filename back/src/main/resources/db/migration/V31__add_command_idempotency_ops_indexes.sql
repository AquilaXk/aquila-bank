-- command idempotency cleanup/recovery 는 작은 batch cursor 로만 처리해 lock/vacuum 충격을 낮춥니다.
CREATE INDEX idx_command_idempotency_retention_cleanup
    ON command_idempotency (updated_at, idempotency_key)
    WHERE processing_status IN ('COMPLETED', 'FAILED');

CREATE INDEX idx_command_idempotency_started_recovery
    ON command_idempotency (locked_until, idempotency_key)
    WHERE processing_status = 'STARTED';
