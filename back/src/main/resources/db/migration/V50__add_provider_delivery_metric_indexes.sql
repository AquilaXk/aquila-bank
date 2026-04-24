CREATE INDEX idx_notification_channel_outbox_metric_status
    ON notification_channel_outbox (delivery_status)
    WHERE delivery_status IN ('SENT', 'SKIPPED', 'FAILED', 'QUARANTINED');

CREATE INDEX idx_notification_channel_outbox_metric_skip_reason
    ON notification_channel_outbox (skip_reason)
    WHERE delivery_status = 'SKIPPED'
      AND skip_reason IS NOT NULL;

CREATE INDEX idx_auth_password_recovery_delivery_metric_status
    ON auth_password_recovery_delivery_outbox (delivery_status)
    WHERE delivery_status IN ('SENT', 'SKIPPED', 'FAILED', 'QUARANTINED');

CREATE INDEX idx_auth_password_recovery_delivery_metric_skip_reason
    ON auth_password_recovery_delivery_outbox (skip_reason)
    WHERE delivery_status = 'SKIPPED'
      AND skip_reason IS NOT NULL;

COMMENT ON INDEX idx_notification_channel_outbox_metric_status IS
    'Prometheus provider delivery status count가 대량 channel delivery row에서 table full scan 없이 상태별 partial index만 읽게 합니다.';

COMMENT ON INDEX idx_notification_channel_outbox_metric_skip_reason IS
    'Prometheus provider delivery skip reason count가 SKIPPED channel delivery row만 집계하도록 범위를 제한합니다.';

COMMENT ON INDEX idx_auth_password_recovery_delivery_metric_status IS
    'Prometheus password recovery delivery status count가 대량 delivery row에서 table full scan 없이 상태별 partial index만 읽게 합니다.';

COMMENT ON INDEX idx_auth_password_recovery_delivery_metric_skip_reason IS
    'Prometheus password recovery delivery skip reason count가 SKIPPED row만 집계하도록 범위를 제한합니다.';
