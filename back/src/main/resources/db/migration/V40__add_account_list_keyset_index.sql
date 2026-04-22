-- JWT 사용자 계좌 목록은 user/status 범위 안에서 account_id keyset 으로 다음 page를 읽습니다.
CREATE INDEX idx_user_account_membership_user_status_account_cursor
    ON user_account_membership (user_id, membership_status, account_id);

COMMENT ON INDEX idx_user_account_membership_user_status_account_cursor
    IS 'GET /api/v1/accounts JWT user keyset pagination: user_id + ACTIVE status + account_id cursor';
