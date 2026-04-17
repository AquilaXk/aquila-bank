CREATE INDEX idx_transaction_read_model_account_reference_cursor
    ON transaction_read_model (account_id, transaction_reference, booked_at DESC, id DESC);

COMMENT ON INDEX idx_transaction_read_model_account_cursor IS
    'GET /api/v1/transactions 첫 page/후속 cursor page용. direction/amount range 보조 조건도 account_id + booked_at DESC, id DESC bounded scan 뒤 post-filter';

COMMENT ON INDEX idx_transaction_read_model_account_status_cursor IS
    'GET /api/v1/transactions status filter page용. status 뒤에도 booked_at DESC, id DESC keyset 정렬 유지, direction/amount range 조합은 post-filter';

COMMENT ON INDEX idx_transaction_read_model_account_reference_cursor IS
    'GET /api/v1/transactions transactionReference exact lookup 보조 조건용. account_id + transaction_reference exact match 뒤 booked_at DESC, id DESC 정렬 유지';
