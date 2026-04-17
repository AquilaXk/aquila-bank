COMMENT ON INDEX idx_transaction_read_model_account_cursor IS
    'GET /api/v1/transactions 첫 page/후속 cursor page용. account_id filter 뒤 booked_at DESC, id DESC keyset 정렬 유지';

COMMENT ON INDEX idx_transaction_read_model_account_status_cursor IS
    'GET /api/v1/transactions status filter page용. status filter 뒤에도 booked_at DESC, id DESC keyset 정렬 유지';
