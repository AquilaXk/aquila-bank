-- auth audit 목록은 created_at DESC, id DESC keyset pagination으로 고정해 offset 비용을 피합니다.
CREATE INDEX idx_auth_status_change_audit_created_id
    ON auth_status_change_audit (created_at DESC, id DESC);

CREATE INDEX idx_auth_status_change_audit_user_created_id
    ON auth_status_change_audit (target_user_id, created_at DESC, id DESC);

CREATE INDEX idx_auth_status_change_audit_account_created_id
    ON auth_status_change_audit (target_account_id, created_at DESC, id DESC)
    WHERE target_account_id IS NOT NULL;

CREATE INDEX idx_auth_status_change_audit_type_created_id
    ON auth_status_change_audit (change_type, created_at DESC, id DESC);

CREATE INDEX idx_auth_status_change_audit_reason_created_id
    ON auth_status_change_audit (reason_code, created_at DESC, id DESC);
