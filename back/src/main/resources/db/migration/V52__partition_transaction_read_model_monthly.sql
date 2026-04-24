-- 1억 row 조회 경로에서 단일 hot/archive table btree build가 OOM을 만들지 않도록
-- booked_at 월 단위 partition parent로 전환합니다.

ALTER TABLE transaction_read_model RENAME TO transaction_read_model_legacy;
ALTER TABLE transaction_read_model_archive RENAME TO transaction_read_model_archive_legacy;

DROP INDEX IF EXISTS idx_transaction_read_model_account_cursor;
DROP INDEX IF EXISTS idx_transaction_read_model_account_status_cursor;
DROP INDEX IF EXISTS idx_transaction_read_model_account_reference_cursor;
DROP INDEX IF EXISTS idx_transaction_read_model_cleanup_cursor;
DROP INDEX IF EXISTS idx_transaction_read_model_archive_account_cursor;
DROP INDEX IF EXISTS idx_transaction_read_model_archive_account_status_cursor;
DROP INDEX IF EXISTS idx_transaction_read_model_archive_account_reference_cursor;

CREATE SEQUENCE transaction_read_model_monthly_id_seq;

CREATE TABLE transaction_read_model (
    id BIGINT NOT NULL DEFAULT nextval('transaction_read_model_monthly_id_seq'),
    ledger_entry_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    transaction_reference VARCHAR(64) NOT NULL,
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    transaction_status VARCHAR(24) NOT NULL
        CHECK (transaction_status IN ('PENDING', 'BOOKED', 'PARTIALLY_REVERSED', 'REVERSED')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    balance_after_minor BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    summary VARCHAR(120),
    counterparty_masked_name VARCHAR(80),
    booked_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_transaction_read_model_monthly PRIMARY KEY (id, booked_at),
    CONSTRAINT uq_transaction_read_model_ledger_monthly UNIQUE (ledger_entry_id, booked_at),
    CONSTRAINT fk_transaction_read_model_ledger_entry_monthly
        FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entry (id)
) PARTITION BY RANGE (booked_at);

ALTER SEQUENCE transaction_read_model_monthly_id_seq OWNED BY transaction_read_model.id;

CREATE TABLE transaction_read_model_archive (
    id BIGINT NOT NULL,
    ledger_entry_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    transaction_reference VARCHAR(64) NOT NULL,
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    transaction_status VARCHAR(24) NOT NULL
        CHECK (transaction_status IN ('PENDING', 'BOOKED', 'PARTIALLY_REVERSED', 'REVERSED')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    balance_after_minor BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    summary VARCHAR(120),
    counterparty_masked_name VARCHAR(80),
    booked_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_transaction_read_model_archive_monthly PRIMARY KEY (id, booked_at),
    CONSTRAINT uq_transaction_read_model_archive_ledger_monthly UNIQUE (ledger_entry_id, booked_at),
    CONSTRAINT fk_transaction_read_model_archive_ledger_entry_monthly
        FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entry (id)
) PARTITION BY RANGE (booked_at);

DO $$
DECLARE
    current_month_start TIMESTAMPTZ;
    data_min TIMESTAMPTZ;
    data_max TIMESTAMPTZ;
    partition_start TIMESTAMPTZ;
    partition_end TIMESTAMPTZ;
    month_cursor TIMESTAMPTZ;
    partition_name TEXT;
BEGIN
    current_month_start := date_trunc('month', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') AT TIME ZONE 'UTC';

    SELECT MIN(booked_at), MAX(booked_at)
      INTO data_min, data_max
      FROM transaction_read_model_legacy;

    partition_start := current_month_start - INTERVAL '24 months';
    partition_end := current_month_start + INTERVAL '3 months';

    IF data_min IS NOT NULL THEN
        partition_start := LEAST(
            partition_start,
            date_trunc('month', data_min AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'
        );
        partition_end := GREATEST(
            partition_end,
            (date_trunc('month', data_max AT TIME ZONE 'UTC') AT TIME ZONE 'UTC') + INTERVAL '1 month'
        );
    END IF;

    month_cursor := partition_start;
    WHILE month_cursor < partition_end LOOP
        partition_name := 'transaction_read_model_y' || to_char(month_cursor AT TIME ZONE 'UTC', 'YYYYMM');
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF transaction_read_model FOR VALUES FROM (%L) TO (%L)',
            partition_name,
            month_cursor,
            month_cursor + INTERVAL '1 month'
        );
        month_cursor := month_cursor + INTERVAL '1 month';
    END LOOP;
END $$;

DO $$
DECLARE
    current_month_start TIMESTAMPTZ;
    data_min TIMESTAMPTZ;
    data_max TIMESTAMPTZ;
    partition_start TIMESTAMPTZ;
    partition_end TIMESTAMPTZ;
    month_cursor TIMESTAMPTZ;
    partition_name TEXT;
BEGIN
    current_month_start := date_trunc('month', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') AT TIME ZONE 'UTC';

    SELECT MIN(booked_at), MAX(booked_at)
      INTO data_min, data_max
      FROM transaction_read_model_archive_legacy;

    partition_start := current_month_start - INTERVAL '24 months';
    partition_end := current_month_start + INTERVAL '3 months';

    IF data_min IS NOT NULL THEN
        partition_start := LEAST(
            partition_start,
            date_trunc('month', data_min AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'
        );
        partition_end := GREATEST(
            partition_end,
            (date_trunc('month', data_max AT TIME ZONE 'UTC') AT TIME ZONE 'UTC') + INTERVAL '1 month'
        );
    END IF;

    month_cursor := partition_start;
    WHILE month_cursor < partition_end LOOP
        partition_name := 'transaction_read_model_archive_y' || to_char(month_cursor AT TIME ZONE 'UTC', 'YYYYMM');
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF transaction_read_model_archive FOR VALUES FROM (%L) TO (%L)',
            partition_name,
            month_cursor,
            month_cursor + INTERVAL '1 month'
        );
        month_cursor := month_cursor + INTERVAL '1 month';
    END LOOP;
END $$;

CREATE TABLE transaction_read_model_default PARTITION OF transaction_read_model DEFAULT;
CREATE TABLE transaction_read_model_archive_default PARTITION OF transaction_read_model_archive DEFAULT;

CREATE INDEX idx_transaction_read_model_account_cursor
    ON transaction_read_model (account_id, booked_at DESC, id DESC);

CREATE INDEX idx_transaction_read_model_account_status_cursor
    ON transaction_read_model (account_id, transaction_status, booked_at DESC, id DESC);

CREATE INDEX idx_transaction_read_model_account_reference_cursor
    ON transaction_read_model (account_id, transaction_reference, booked_at DESC, id DESC);

CREATE INDEX idx_transaction_read_model_cleanup_cursor
    ON transaction_read_model USING BRIN (booked_at)
    WITH (pages_per_range = 32);

CREATE INDEX idx_transaction_read_model_archive_account_cursor
    ON transaction_read_model_archive (account_id, booked_at DESC, id DESC);

CREATE INDEX idx_transaction_read_model_archive_account_status_cursor
    ON transaction_read_model_archive (account_id, transaction_status, booked_at DESC, id DESC);

CREATE INDEX idx_transaction_read_model_archive_account_reference_cursor
    ON transaction_read_model_archive (account_id, transaction_reference, booked_at DESC, id DESC);

INSERT INTO transaction_read_model (
    id,
    ledger_entry_id,
    account_id,
    transaction_reference,
    direction,
    transaction_status,
    amount_minor,
    balance_after_minor,
    currency_code,
    summary,
    counterparty_masked_name,
    booked_at,
    created_at
)
SELECT id,
       ledger_entry_id,
       account_id,
       transaction_reference,
       direction,
       transaction_status,
       amount_minor,
       balance_after_minor,
       currency_code,
       summary,
       counterparty_masked_name,
       booked_at,
       created_at
FROM transaction_read_model_legacy;

INSERT INTO transaction_read_model_archive (
    id,
    ledger_entry_id,
    account_id,
    transaction_reference,
    direction,
    transaction_status,
    amount_minor,
    balance_after_minor,
    currency_code,
    summary,
    counterparty_masked_name,
    booked_at,
    created_at,
    archived_at
)
SELECT id,
       ledger_entry_id,
       account_id,
       transaction_reference,
       direction,
       transaction_status,
       amount_minor,
       balance_after_minor,
       currency_code,
       summary,
       counterparty_masked_name,
       booked_at,
       created_at,
       archived_at
FROM transaction_read_model_archive_legacy;

SELECT setval(
    'transaction_read_model_monthly_id_seq',
    GREATEST(COALESCE((SELECT MAX(id) FROM transaction_read_model), 1), 1),
    true
);

COMMENT ON TABLE transaction_read_model IS
    'booked_at monthly partition parent. hot transaction projection을 월별 chunk로 유지해 대형 btree 사후 build를 피함';

COMMENT ON TABLE transaction_read_model_archive IS
    'booked_at monthly partition parent. archive projection을 월별 chunk로 유지해 cold 조회와 retention을 분리함';

COMMENT ON INDEX idx_transaction_read_model_account_cursor IS
    'GET /api/v1/transactions 첫 page/후속 cursor page용. partition별 account_id + booked_at DESC, id DESC bounded scan 유지';

COMMENT ON INDEX idx_transaction_read_model_account_status_cursor IS
    'GET /api/v1/transactions status filter page용. 월별 partition 안에서 status 뒤 keyset 정렬 유지';

COMMENT ON INDEX idx_transaction_read_model_account_reference_cursor IS
    'GET /api/v1/transactions transactionReference exact lookup 보조 조건용. account scope 안에서 partition index 사용';

COMMENT ON INDEX idx_transaction_read_model_cleanup_cursor IS
    'retention cutoff batch용 BRIN. 월별 partition prune과 함께 오래된 projection 후보를 좁힘';

COMMENT ON INDEX idx_transaction_read_model_archive_account_cursor IS
    'archive projection account-scoped timeline 재조회/복구용. hot read model과 같은 keyset 정렬 유지';

COMMENT ON INDEX idx_transaction_read_model_archive_account_status_cursor IS
    'archive projection status filter 재조회/복구용. account_id + status 뒤 keyset 정렬 유지';

COMMENT ON INDEX idx_transaction_read_model_archive_account_reference_cursor IS
    'archive transactionReference exact lookup용. account scope 안에서 partition index 사용';

DROP TABLE transaction_read_model_legacy;
DROP TABLE transaction_read_model_archive_legacy;
