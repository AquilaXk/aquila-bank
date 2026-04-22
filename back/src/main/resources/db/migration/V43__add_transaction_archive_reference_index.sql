CREATE INDEX idx_transaction_read_model_archive_account_reference_cursor
    ON transaction_read_model_archive (account_id, transaction_reference, booked_at DESC, id DESC);

COMMENT ON INDEX idx_transaction_read_model_archive_account_reference_cursor IS
    'archive transactionReference exact lookup용. account_id + transaction_reference 뒤 booked_at DESC, id DESC 정렬 유지';
