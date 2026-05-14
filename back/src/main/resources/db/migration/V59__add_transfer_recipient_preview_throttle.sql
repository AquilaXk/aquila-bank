CREATE TABLE transfer_recipient_preview_throttle (
    user_id BIGINT PRIMARY KEY,
    window_start_epoch_second BIGINT NOT NULL CHECK (window_start_epoch_second > 0),
    attempts INTEGER NOT NULL CHECK (attempts > 0),
    updated_at TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE transfer_recipient_preview_throttle IS
    'JWT 사용자 수취인 preview 탐색 제한 window. 계좌번호 sequence 탐색을 다중 backend instance 기준으로 제한한다.';
