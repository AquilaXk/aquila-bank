-- 거래 한도 집계, SSE 알림 재전송, 타임라인 조회의 heap random I/O를 줄이기 위한 커버링/부분 인덱스 추가
-- 주의: idx_ledger_entry_transfer_limit_daily_covering 및 기존 baseline 인덱스는 하위 호환성을 위해 유지합니다.
SET LOCAL statement_timeout = '30min';
SET LOCAL lock_timeout = '5s';
SET LOCAL idle_in_transaction_session_timeout = 0;

CREATE INDEX IF NOT EXISTS idx_ledger_entry_transfer_limit_daily_tx_covering
    ON ledger_entry (account_id, currency_code, booked_at)
    INCLUDE (amount_minor, transaction_reference)
    WHERE direction = 'DEBIT' AND entry_status = 'BOOKED';

CREATE INDEX IF NOT EXISTS idx_notification_inbox_account_replay_active
    ON notification_inbox (account_id, id)
    INCLUDE (event_type, title, message, created_at, read_at)
    WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_read_model_account_cursor_covering
    ON transaction_read_model (account_id, booked_at DESC, id DESC)
    INCLUDE (
        transaction_reference,
        direction,
        transaction_status,
        amount_minor,
        balance_after_minor,
        currency_code,
        summary,
        counterparty_masked_name
    );

CREATE INDEX IF NOT EXISTS idx_transaction_read_model_archive_account_cursor_covering
    ON transaction_read_model_archive (account_id, booked_at DESC, id DESC)
    INCLUDE (
        transaction_reference,
        direction,
        transaction_status,
        amount_minor,
        balance_after_minor,
        currency_code,
        summary,
        counterparty_masked_name
    );

COMMENT ON INDEX idx_ledger_entry_transfer_limit_daily_tx_covering IS
    'daily-limit SUM과 reversal 반조인을 heap fetch 없이 index-only scan으로 처리하기 위한 커버링 인덱스';

COMMENT ON INDEX idx_notification_inbox_account_replay_active IS
    '알림 SSE Last-Event-ID 재전송(account_id + archived_at IS NULL + id > :lastEventId ORDER BY id ASC)용 부분 커버링 인덱스';

COMMENT ON INDEX idx_transaction_read_model_account_cursor_covering IS
    '거래 타임라인 keyset 조회 시 heap 랜덤 조회를 줄이기 위한 커버링 파티션 인덱스';

COMMENT ON INDEX idx_transaction_read_model_archive_account_cursor_covering IS
    '아카이브 거래 타임라인 keyset 조회 시 heap 랜덤 조회를 줄이기 위한 커버링 파티션 인덱스';
