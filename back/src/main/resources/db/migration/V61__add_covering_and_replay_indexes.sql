-- 거래 한도 집계 반조인과 SSE 알림 재전송의 heap random I/O를 줄이기 위한 무중단 부분 커버링 인덱스 추가
-- 주의: idx_ledger_entry_transfer_limit_daily_covering 및 기존 baseline 인덱스는 하위 호환성을 위해 유지합니다.
SELECT
    set_config('aquila.prev_statement_timeout', current_setting('statement_timeout'), false),
    set_config('aquila.prev_lock_timeout', current_setting('lock_timeout'), false),
    set_config('statement_timeout', '120s', false),
    set_config('lock_timeout', '30s', false);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_entry_transfer_limit_daily_tx_covering
    ON ledger_entry (account_id, currency_code, booked_at)
    INCLUDE (amount_minor, transaction_reference)
    WHERE direction = 'DEBIT' AND entry_status = 'BOOKED';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_inbox_account_replay_active
    ON notification_inbox (account_id, id)
    INCLUDE (event_type, title, message, created_at, read_at)
    WHERE archived_at IS NULL;

SELECT
    set_config('statement_timeout', current_setting('aquila.prev_statement_timeout'), false),
    set_config('lock_timeout', current_setting('aquila.prev_lock_timeout'), false);
