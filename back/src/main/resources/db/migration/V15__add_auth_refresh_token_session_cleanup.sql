-- auth refresh token cleanup 은 ACTIVE 만료 경로와 비활성 세션 보존기간 경로를 분리해 partial index 로 작은 batch delete 를 유지합니다.
CREATE INDEX idx_auth_refresh_token_session_active_cleanup
    ON auth_refresh_token_session (expires_at ASC, id ASC)
    WHERE session_status = 'ACTIVE';

CREATE INDEX idx_auth_refresh_token_session_inactive_cleanup
    ON auth_refresh_token_session (updated_at ASC, id ASC)
    WHERE session_status IN ('ROTATED', 'REVOKED');
