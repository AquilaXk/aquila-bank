-- DLQ redrive는 운영 복구 행위라 source/target offset과 outcome을 영구 audit으로 남깁니다.
CREATE TABLE notification_dlq_redrive_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor VARCHAR(120) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    source_topic VARCHAR(160) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    event_key VARCHAR(160),
    target_topic VARCHAR(160),
    target_partition INTEGER,
    target_offset BIGINT,
    outcome VARCHAR(16) NOT NULL,
    error_message TEXT,
    redriven_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notification_dlq_redrive_audit_source_partition
        CHECK (source_partition >= 0),
    CONSTRAINT chk_notification_dlq_redrive_audit_source_offset
        CHECK (source_offset >= 0),
    CONSTRAINT chk_notification_dlq_redrive_audit_target_partition
        CHECK (target_partition IS NULL OR target_partition >= 0),
    CONSTRAINT chk_notification_dlq_redrive_audit_target_offset
        CHECK (target_offset IS NULL OR target_offset >= 0),
    CONSTRAINT chk_notification_dlq_redrive_audit_outcome
        CHECK (outcome IN ('SUCCESS', 'NOT_FOUND', 'FAILED')),
    CONSTRAINT chk_notification_dlq_redrive_audit_success_target
        CHECK (
            outcome <> 'SUCCESS'
            OR (
                target_topic IS NOT NULL
                AND target_partition IS NOT NULL
                AND target_offset IS NOT NULL
            )
        )
);

CREATE INDEX idx_notification_dlq_redrive_audit_source
    ON notification_dlq_redrive_audit (
        source_topic,
        source_partition,
        source_offset,
        id
    );

CREATE INDEX idx_notification_dlq_redrive_audit_created
    ON notification_dlq_redrive_audit (created_at DESC, id DESC);

COMMENT ON TABLE notification_dlq_redrive_audit IS
    'notification DLQ redrive 운영 이력. 같은 source offset 반복 redrive도 outcome별로 누적합니다.';

COMMENT ON INDEX idx_notification_dlq_redrive_audit_source IS
    'DLQ preview 좌표 기준으로 redrive audit history를 확인하는 lookup index입니다.';
