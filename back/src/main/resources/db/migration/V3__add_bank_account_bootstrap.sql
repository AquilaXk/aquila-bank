CREATE SEQUENCE bank_account_number_seq
    START WITH 1
    INCREMENT BY 1
    CACHE 20;

-- 계좌 원본 row를 두고 snapshot/ledger가 같은 account_id를 공유하게 만듭니다.
CREATE TABLE bank_account (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    account_status VARCHAR(16) NOT NULL
        CHECK (account_status IN ('ACTIVE', 'LOCKED', 'CLOSED')),
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bank_account_number UNIQUE (account_number)
);

CREATE INDEX idx_bank_account_status_created_at
    ON bank_account (account_status, created_at DESC, id DESC);

ALTER TABLE account_balance_snapshot
    ADD CONSTRAINT fk_account_balance_snapshot_account
        FOREIGN KEY (account_id) REFERENCES bank_account (id);

ALTER TABLE ledger_entry
    ADD CONSTRAINT fk_ledger_entry_account
        FOREIGN KEY (account_id) REFERENCES bank_account (id);

ALTER TABLE transaction_read_model
    ADD CONSTRAINT fk_transaction_read_model_account
        FOREIGN KEY (account_id) REFERENCES bank_account (id);
