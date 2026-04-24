ALTER TABLE notification_channel_outbox
    ADD COLUMN skip_reason VARCHAR(64);

ALTER TABLE notification_channel_outbox
    DROP CONSTRAINT chk_notification_channel_outbox_status;

ALTER TABLE notification_channel_outbox
    ADD CONSTRAINT chk_notification_channel_outbox_status
        CHECK (delivery_status IN ('PENDING', 'SENDING', 'SENT', 'SKIPPED', 'FAILED', 'QUARANTINED')),
    ADD CONSTRAINT chk_notification_channel_outbox_skip_reason
        CHECK (skip_reason IS NULL OR BTRIM(skip_reason) <> ''),
    ADD CONSTRAINT chk_notification_channel_outbox_skip_state
        CHECK ((delivery_status = 'SKIPPED') = (skip_reason IS NOT NULL));

CREATE INDEX idx_notification_channel_outbox_skipped_cleanup
    ON notification_channel_outbox (updated_at, id)
    WHERE delivery_status = 'SKIPPED';

COMMENT ON COLUMN notification_channel_outbox.skip_reason IS
    'provider 미호출 완료 사유입니다. SKIPPED 상태에서만 값이 있으며 SENT와 운영상 분리합니다.';

COMMENT ON CONSTRAINT chk_notification_channel_outbox_skip_state ON notification_channel_outbox IS
    'SKIPPED row만 skip_reason을 가져 실제 발송 성공과 fail-safe skip을 분리합니다.';

COMMENT ON INDEX idx_notification_channel_outbox_skipped_cleanup IS
    '오래된 SKIPPED channel delivery row를 updated_at 기준 작은 batch로 삭제할 때 사용하는 cleanup index입니다.';

ALTER TABLE auth_password_recovery_delivery_outbox
    ADD COLUMN skip_reason VARCHAR(64);

ALTER TABLE auth_password_recovery_delivery_outbox
    DROP CONSTRAINT chk_auth_password_recovery_delivery_outbox_status;

ALTER TABLE auth_password_recovery_delivery_outbox
    ADD CONSTRAINT chk_auth_password_recovery_delivery_outbox_status
        CHECK (delivery_status IN ('PENDING', 'SENDING', 'SENT', 'SKIPPED', 'FAILED', 'QUARANTINED')),
    ADD CONSTRAINT chk_auth_password_recovery_delivery_skip_reason
        CHECK (skip_reason IS NULL OR BTRIM(skip_reason) <> ''),
    ADD CONSTRAINT chk_auth_password_recovery_delivery_skip_state
        CHECK ((delivery_status = 'SKIPPED') = (skip_reason IS NOT NULL));

CREATE INDEX idx_auth_password_recovery_delivery_skipped_cleanup
    ON auth_password_recovery_delivery_outbox (updated_at, id)
    WHERE delivery_status = 'SKIPPED';

COMMENT ON COLUMN auth_password_recovery_delivery_outbox.skip_reason IS
    'password recovery provider 미호출 완료 사유입니다. SKIPPED 상태에서만 값이 있으며 SENT와 운영상 분리합니다.';

COMMENT ON CONSTRAINT chk_auth_password_recovery_delivery_skip_state ON auth_password_recovery_delivery_outbox IS
    'SKIPPED row만 skip_reason을 가져 실제 발송 성공과 fail-safe skip을 분리합니다.';

COMMENT ON INDEX idx_auth_password_recovery_delivery_skipped_cleanup IS
    '오래된 SKIPPED password recovery delivery row를 updated_at 기준 작은 batch로 삭제할 때 사용하는 cleanup index입니다.';
