-- hot transaction_read_model 은 최근 조회용으로 작게 유지하고, 오래된 projection 은 archive table 로 이동합니다.
CREATE TABLE transaction_read_model_archive (
    id BIGINT PRIMARY KEY,
    ledger_entry_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    transaction_reference VARCHAR(64) NOT NULL,
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    transaction_status VARCHAR(16) NOT NULL
        CHECK (transaction_status IN ('PENDING', 'BOOKED', 'PARTIALLY_REVERSED', 'REVERSED')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    balance_after_minor BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    summary VARCHAR(120),
    counterparty_masked_name VARCHAR(80),
    booked_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_transaction_read_model_archive_ledger_entry UNIQUE (ledger_entry_id),
    CONSTRAINT fk_transaction_read_model_archive_ledger_entry
        FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entry (id)
);

CREATE INDEX idx_transaction_read_model_cleanup_cursor
    ON transaction_read_model USING BRIN (booked_at)
    WITH (pages_per_range = 32);

CREATE INDEX idx_transaction_read_model_archive_account_cursor
    ON transaction_read_model_archive (account_id, booked_at DESC, id DESC);

CREATE INDEX idx_transaction_read_model_archive_account_status_cursor
    ON transaction_read_model_archive (account_id, transaction_status, booked_at DESC, id DESC);

COMMENT ON TABLE transaction_read_model_archive IS
    'transaction_read_model hot table retention 대상 projection 보관. ledger_entry 원장은 삭제하지 않음';

COMMENT ON INDEX idx_transaction_read_model_cleanup_cursor IS
    'transaction read model retention batch용. account-scoped 조회 btree와 경쟁하지 않는 cutoff BRIN 보조 index';

COMMENT ON INDEX idx_transaction_read_model_archive_account_cursor IS
    'archive projection account-scoped timeline 재조회/복구용. hot read model과 같은 keyset 정렬 유지';

COMMENT ON INDEX idx_transaction_read_model_archive_account_status_cursor IS
    'archive projection status filter 재조회/복구용. account_id + status 뒤 keyset 정렬 유지';
