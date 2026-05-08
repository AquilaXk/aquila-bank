-- daily-limit SUM은 amount_minor까지 index leaf에서 읽어 heap fetch 비용을 낮춥니다.
SET LOCAL statement_timeout = '30min';
SET LOCAL lock_timeout = '5s';
SET LOCAL idle_in_transaction_session_timeout = 0;

CREATE INDEX IF NOT EXISTS idx_ledger_entry_transfer_limit_daily_covering
    ON ledger_entry (account_id, currency_code, booked_at)
    INCLUDE (amount_minor)
    WHERE direction = 'DEBIT' AND entry_status = 'BOOKED';
