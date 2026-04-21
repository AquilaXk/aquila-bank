-- daily transfer limit은 source 계좌의 BOOKED DEBIT 당일 합계만 조회합니다.
CREATE INDEX idx_ledger_entry_transfer_limit_daily
    ON ledger_entry (account_id, currency_code, booked_at)
    WHERE direction = 'DEBIT' AND entry_status = 'BOOKED';
