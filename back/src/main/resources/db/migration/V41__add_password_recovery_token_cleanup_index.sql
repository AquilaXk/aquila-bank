-- password recovery token cleanup 은 유효 token과 완료 token 기준을 분리해 작은 batch delete 를 유지합니다.
CREATE INDEX idx_auth_password_recovery_token_pending_cleanup
    ON auth_password_recovery_token (expires_at ASC, id ASC)
    WHERE token_status = 'PENDING';

CREATE INDEX idx_auth_password_recovery_token_completed_cleanup
    ON auth_password_recovery_token (updated_at ASC, id ASC)
    WHERE token_status IN ('USED', 'EXPIRED', 'SUPERSEDED');
