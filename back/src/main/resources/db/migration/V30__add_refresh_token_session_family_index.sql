-- session family revoke recursive CTE가 parent lookup을 index 경로로 타도록 보조합니다.
CREATE INDEX idx_auth_refresh_token_session_replaced_by
    ON auth_refresh_token_session (replaced_by_session_id)
    WHERE replaced_by_session_id IS NOT NULL;
