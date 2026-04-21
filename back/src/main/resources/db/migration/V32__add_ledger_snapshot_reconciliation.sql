CREATE TABLE ledger_snapshot_reconciliation_drift (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    snapshot_available_balance_minor BIGINT NOT NULL,
    expected_available_balance_minor BIGINT NOT NULL,
    snapshot_pending_balance_minor BIGINT NOT NULL,
    expected_pending_balance_minor BIGINT NOT NULL,
    snapshot_last_applied_ledger_entry_id BIGINT NOT NULL,
    expected_last_applied_ledger_entry_id BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    drift_status VARCHAR(16) NOT NULL
        CHECK (drift_status IN ('OPEN', 'RESOLVED', 'RECOVERED')),
    resolved_at TIMESTAMPTZ,
    recovered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_snapshot_reconciliation_drift_account
        FOREIGN KEY (account_id) REFERENCES bank_account (id) ON DELETE CASCADE
);

-- 계좌별 open drift는 하나만 유지해 scheduler가 같은 계좌를 반복 적재하지 않게 합니다.
CREATE UNIQUE INDEX uq_ledger_snapshot_reconciliation_open_drift
    ON ledger_snapshot_reconciliation_drift (account_id)
    WHERE drift_status = 'OPEN';

-- 운영 API는 open drift를 account_id keyset cursor로 작게 훑습니다.
CREATE INDEX idx_ledger_snapshot_reconciliation_open_cursor
    ON ledger_snapshot_reconciliation_drift (account_id, id)
    WHERE drift_status = 'OPEN';

CREATE TABLE ledger_snapshot_recovery_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    drift_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    before_available_balance_minor BIGINT NOT NULL,
    after_available_balance_minor BIGINT NOT NULL,
    before_pending_balance_minor BIGINT NOT NULL,
    after_pending_balance_minor BIGINT NOT NULL,
    before_last_applied_ledger_entry_id BIGINT NOT NULL,
    after_last_applied_ledger_entry_id BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    recovered_by VARCHAR(120) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    recovery_reason VARCHAR(200) NOT NULL,
    recovered_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_snapshot_recovery_audit_drift
        FOREIGN KEY (drift_id) REFERENCES ledger_snapshot_reconciliation_drift (id),
    CONSTRAINT fk_ledger_snapshot_recovery_audit_account
        FOREIGN KEY (account_id) REFERENCES bank_account (id) ON DELETE CASCADE
);

CREATE INDEX idx_ledger_snapshot_recovery_audit_account_created
    ON ledger_snapshot_recovery_audit (account_id, created_at DESC, id DESC);
