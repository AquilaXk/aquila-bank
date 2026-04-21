ALTER TABLE transaction_read_model
    DROP CONSTRAINT transaction_read_model_transaction_status_check;

ALTER TABLE transaction_read_model
    ALTER COLUMN transaction_status TYPE VARCHAR(24);

ALTER TABLE transaction_read_model
    ADD CONSTRAINT transaction_read_model_transaction_status_check
        CHECK (transaction_status IN ('PENDING', 'BOOKED', 'PARTIALLY_REVERSED', 'REVERSED'));

ALTER TABLE transfer_reversal
    DROP CONSTRAINT uq_transfer_reversal_original_reference;

CREATE INDEX idx_transfer_reversal_original_created_at
    ON transfer_reversal (original_transaction_reference, created_at DESC, id DESC);
