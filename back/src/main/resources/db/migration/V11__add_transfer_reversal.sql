CREATE TABLE transfer_reversal (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_transaction_reference VARCHAR(64) NOT NULL,
    reversal_transaction_reference VARCHAR(64) NOT NULL,
    source_account_id BIGINT NOT NULL,
    target_account_id BIGINT NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    reversal_reason VARCHAR(16) NOT NULL
        CHECK (reversal_reason IN ('CANCEL', 'CORRECTION')),
    summary VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_transfer_reversal_original_reference
        UNIQUE (original_transaction_reference),
    CONSTRAINT uq_transfer_reversal_reversal_reference
        UNIQUE (reversal_transaction_reference)
);

CREATE INDEX idx_transfer_reversal_source_created_at
    ON transfer_reversal (source_account_id, created_at DESC, id DESC);
