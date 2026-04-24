ALTER TABLE auth_password_recovery_delivery_outbox
    ADD COLUMN delivery_channel VARCHAR(16),
    ADD COLUMN provider_destination VARCHAR(255);

UPDATE auth_password_recovery_delivery_outbox
SET delivery_channel = 'EMAIL',
    provider_destination = login_id,
    updated_at = CURRENT_TIMESTAMP
WHERE delivery_channel IS NULL
  AND login_id ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$';

UPDATE auth_password_recovery_delivery_outbox
SET delivery_channel = 'SMS',
    provider_destination = login_id,
    updated_at = CURRENT_TIMESTAMP
WHERE delivery_channel IS NULL
  AND login_id ~ '^\+[1-9][0-9]{7,14}$';

UPDATE auth_password_recovery_delivery_outbox
SET delivery_status = 'SENT',
    last_error = 'legacy password recovery delivery skipped: provider destination is unavailable',
    updated_at = CURRENT_TIMESTAMP
WHERE delivery_channel IS NULL
  AND delivery_status IN ('PENDING', 'SENDING', 'FAILED');

ALTER TABLE auth_password_recovery_delivery_outbox
    ADD CONSTRAINT chk_auth_password_recovery_delivery_channel
        CHECK (delivery_channel IS NULL OR delivery_channel IN ('EMAIL', 'SMS')),
    ADD CONSTRAINT chk_auth_password_recovery_provider_destination
        CHECK (provider_destination IS NULL OR BTRIM(provider_destination) <> '');

COMMENT ON COLUMN auth_password_recovery_delivery_outbox.delivery_channel IS
    'request 시점에 확정한 password recovery provider channel snapshot입니다.';

COMMENT ON COLUMN auth_password_recovery_delivery_outbox.provider_destination IS
    'retry 중 verified contact 변경 영향을 받지 않도록 request 시점에 확정한 provider destination입니다.';
