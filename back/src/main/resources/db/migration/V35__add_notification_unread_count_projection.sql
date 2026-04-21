-- unread count API는 대규모 inbox에서 COUNT/JOIN 반복 대신 scope별 counter row만 조회합니다.
CREATE TABLE notification_unread_count_projection (
    scope_type VARCHAR(16) NOT NULL,
    scope_id BIGINT NOT NULL,
    unread_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (scope_type, scope_id),
    CONSTRAINT chk_notification_unread_projection_scope
        CHECK (scope_type IN ('USER', 'ACCOUNT')),
    CONSTRAINT chk_notification_unread_projection_count
        CHECK (unread_count >= 0)
);

COMMENT ON TABLE notification_unread_count_projection IS
    'notification unread count 단건 조회용 projection. USER/ACCOUNT scope별 counter만 유지합니다.';
