ALTER TABLE auth_status_change_audit
    ADD COLUMN reason_code VARCHAR(32);

UPDATE auth_status_change_audit
SET reason_code = 'LEGACY_FREE_TEXT'
WHERE reason_code IS NULL;

ALTER TABLE auth_status_change_audit
    ALTER COLUMN reason_code SET NOT NULL;

ALTER TABLE auth_status_change_audit
    ADD CONSTRAINT chk_auth_status_change_audit_reason_code
        CHECK (reason_code IN (
            'FRAUD_REVIEW',
            'USER_REQUEST',
            'OPS_MANUAL',
            'ACCOUNT_CLOSURE',
            'LEGACY_FREE_TEXT'
        ));
