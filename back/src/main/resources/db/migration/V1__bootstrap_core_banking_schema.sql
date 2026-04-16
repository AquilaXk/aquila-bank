CREATE TABLE ledger_entry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL,
    transaction_reference VARCHAR(64) NOT NULL,
    entry_reference VARCHAR(64) NOT NULL,
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    entry_status VARCHAR(16) NOT NULL CHECK (entry_status IN ('PENDING', 'BOOKED', 'REVERSED')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    booked_at TIMESTAMPTZ NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(120),
    trace_id VARCHAR(64),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ledger_entry_reference UNIQUE (entry_reference)
);

CREATE INDEX idx_ledger_entry_account_cursor
    ON ledger_entry (account_id, booked_at DESC, id DESC);

CREATE INDEX idx_ledger_entry_transaction_reference
    ON ledger_entry (transaction_reference);

CREATE INDEX idx_ledger_entry_status_booked_at
    ON ledger_entry (entry_status, booked_at DESC, id DESC);

CREATE TABLE transaction_read_model (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ledger_entry_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    transaction_reference VARCHAR(64) NOT NULL,
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    transaction_status VARCHAR(16) NOT NULL CHECK (transaction_status IN ('PENDING', 'BOOKED', 'REVERSED')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    balance_after_minor BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    summary VARCHAR(120),
    counterparty_masked_name VARCHAR(80),
    booked_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_transaction_read_model_ledger_entry UNIQUE (ledger_entry_id),
    CONSTRAINT fk_transaction_read_model_ledger_entry
        FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entry (id)
);

CREATE INDEX idx_transaction_read_model_account_cursor
    ON transaction_read_model (account_id, booked_at DESC, id DESC);

CREATE INDEX idx_transaction_read_model_account_status_cursor
    ON transaction_read_model (account_id, transaction_status, booked_at DESC, id DESC);

CREATE TABLE account_balance_snapshot (
    account_id BIGINT PRIMARY KEY,
    last_applied_ledger_entry_id BIGINT NOT NULL DEFAULT 0,
    available_balance_minor BIGINT NOT NULL DEFAULT 0,
    pending_balance_minor BIGINT NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE command_idempotency (
    idempotency_key VARCHAR(80) PRIMARY KEY,
    request_fingerprint VARCHAR(128) NOT NULL,
    processing_status VARCHAR(16) NOT NULL CHECK (processing_status IN ('STARTED', 'COMPLETED', 'FAILED')),
    response_code INTEGER,
    response_payload JSONB,
    locked_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_command_idempotency_status_lock
    ON command_idempotency (processing_status, locked_until);

CREATE TABLE outbox_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    event_key VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    publish_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (publish_status IN ('PENDING', 'SENDING', 'FAILED', 'PUBLISHED')),
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    last_error VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_outbox_event_key UNIQUE (event_key)
);

CREATE INDEX idx_outbox_event_publish_queue
    ON outbox_event (publish_status, available_at, id);
