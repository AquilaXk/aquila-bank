#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/seed-transaction-read-model-100m.sh [--print-plan]

Environment:
  SEED_TOTAL_ROWS                 total rows, default 100000000
  SEED_HOT_ROWS                   hot table rows, default half of total
  SEED_BATCH_SIZE                 insert batch size, default 1000000
  SEED_HOT_ACCOUNT_ID             default 910000001
  SEED_COLD_ACCOUNT_ID            default 910000002
  SEED_HOT_FROM                   default 2026-04-01T00:00:00Z
  SEED_HOT_TO                     default 2026-04-30T00:00:00Z
  SEED_COLD_FROM                  default 2026-01-01T00:00:00Z
  SEED_COLD_TO                    default 2026-01-31T00:00:00Z
  SEED_TRUNCATE                   truncate read model tables first, default false
  SEED_REBUILD_SECONDARY_INDEXES  drop/recreate read indexes around seed, default true
  SEED_DISABLE_FK_TRIGGERS        disable read model FK triggers around seed, default true

Examples:
  tools/test/seed-transaction-read-model-100m.sh --print-plan
  SEED_TOTAL_ROWS=100000000 SEED_TRUNCATE=true tools/test/seed-transaction-read-model-100m.sh
USAGE
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer" >&2
    exit 1
  fi
}

require_boolean() {
  local name="$1"
  local value="$2"
  case "${value}" in
    true | false) ;;
    *)
      echo "${name} must be true or false" >&2
      exit 1
      ;;
  esac
}

mode="run"
if [[ "${1:-}" == "--print-plan" ]]; then
  mode="print-plan"
  shift
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "$#" -ne 0 ]]; then
  usage
  exit 1
fi

total_rows="${SEED_TOTAL_ROWS:-100000000}"
hot_rows="${SEED_HOT_ROWS:-$((total_rows / 2))}"
cold_rows=$((total_rows - hot_rows))
batch_size="${SEED_BATCH_SIZE:-1000000}"
hot_account_id="${SEED_HOT_ACCOUNT_ID:-910000001}"
cold_account_id="${SEED_COLD_ACCOUNT_ID:-910000002}"
hot_from="${SEED_HOT_FROM:-2026-04-01T00:00:00Z}"
hot_to="${SEED_HOT_TO:-2026-04-30T00:00:00Z}"
cold_from="${SEED_COLD_FROM:-2026-01-01T00:00:00Z}"
cold_to="${SEED_COLD_TO:-2026-01-31T00:00:00Z}"
truncate_tables="${SEED_TRUNCATE:-false}"
rebuild_indexes="${SEED_REBUILD_SECONDARY_INDEXES:-true}"
disable_fk_triggers="${SEED_DISABLE_FK_TRIGGERS:-true}"

require_positive_integer SEED_TOTAL_ROWS "${total_rows}"
require_positive_integer SEED_HOT_ROWS "${hot_rows}"
require_positive_integer SEED_BATCH_SIZE "${batch_size}"
require_positive_integer SEED_HOT_ACCOUNT_ID "${hot_account_id}"
require_positive_integer SEED_COLD_ACCOUNT_ID "${cold_account_id}"
require_boolean SEED_TRUNCATE "${truncate_tables}"
require_boolean SEED_REBUILD_SECONDARY_INDEXES "${rebuild_indexes}"
require_boolean SEED_DISABLE_FK_TRIGGERS "${disable_fk_triggers}"

if ((hot_rows >= total_rows)); then
  echo "SEED_HOT_ROWS must be lower than SEED_TOTAL_ROWS" >&2
  exit 1
fi

compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}")

print_plan() {
  echo "[transaction-100m-seed] total_rows=${total_rows}"
  echo "[transaction-100m-seed] hot_rows=${hot_rows} archive_rows=${cold_rows} batch_size=${batch_size}"
  echo "[transaction-100m-seed] hot account=${hot_account_id} window=${hot_from}..${hot_to}"
  echo "[transaction-100m-seed] cold account=${cold_account_id} window=${cold_from}..${cold_to}"
  echo "[transaction-100m-seed] truncate=${truncate_tables} rebuild-secondary-indexes=${rebuild_indexes} disable-fk-triggers=${disable_fk_triggers}"
  echo "[transaction-100m-seed] caveat: local read-path seed only; ledger source-of-truth rows are not generated."
}

psql_exec() {
  "${psql_base[@]}" "$@"
}

psql_sql() {
  psql_exec --command "$1"
}

ensure_schema() {
  local exists
  exists="$(psql_exec --no-align --tuples-only --command "SELECT to_regclass('public.transaction_read_model') IS NOT NULL;")"
  if [[ "${exists}" != "t" ]]; then
    echo "transaction_read_model schema is missing. Start aquila-bank-backend once so Flyway applies migrations." >&2
    exit 1
  fi
}

drop_secondary_indexes() {
  psql_sql "
    DROP INDEX IF EXISTS idx_transaction_read_model_account_cursor;
    DROP INDEX IF EXISTS idx_transaction_read_model_account_status_cursor;
    DROP INDEX IF EXISTS idx_transaction_read_model_account_reference_cursor;
    DROP INDEX IF EXISTS idx_transaction_read_model_cleanup_cursor;
    DROP INDEX IF EXISTS idx_transaction_read_model_archive_account_cursor;
    DROP INDEX IF EXISTS idx_transaction_read_model_archive_account_status_cursor;
    DROP INDEX IF EXISTS idx_transaction_read_model_archive_account_reference_cursor;
  "
}

create_secondary_indexes() {
  psql_sql "
    CREATE INDEX IF NOT EXISTS idx_transaction_read_model_account_cursor
      ON transaction_read_model (account_id, booked_at DESC, id DESC);
    CREATE INDEX IF NOT EXISTS idx_transaction_read_model_account_status_cursor
      ON transaction_read_model (account_id, transaction_status, booked_at DESC, id DESC);
    CREATE INDEX IF NOT EXISTS idx_transaction_read_model_account_reference_cursor
      ON transaction_read_model (account_id, transaction_reference, booked_at DESC, id DESC);
    CREATE INDEX IF NOT EXISTS idx_transaction_read_model_cleanup_cursor
      ON transaction_read_model USING BRIN (booked_at)
      WITH (pages_per_range = 32);
    CREATE INDEX IF NOT EXISTS idx_transaction_read_model_archive_account_cursor
      ON transaction_read_model_archive (account_id, booked_at DESC, id DESC);
    CREATE INDEX IF NOT EXISTS idx_transaction_read_model_archive_account_status_cursor
      ON transaction_read_model_archive (account_id, transaction_status, booked_at DESC, id DESC);
    CREATE INDEX IF NOT EXISTS idx_transaction_read_model_archive_account_reference_cursor
      ON transaction_read_model_archive (account_id, transaction_reference, booked_at DESC, id DESC);
  "
}

set_trigger_state() {
  local state="$1"
  psql_sql "
    ALTER TABLE transaction_read_model ${state} TRIGGER ALL;
    ALTER TABLE transaction_read_model_archive ${state} TRIGGER ALL;
  "
}

insert_hot_batch() {
  local start="$1"
  local finish="$2"
  psql_sql "
    INSERT INTO transaction_read_model OVERRIDING SYSTEM VALUE (
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
    SELECT
      n,
      n,
      ${hot_account_id},
      'TRX-HOT-' || n,
      CASE WHEN n % 2 = 0 THEN 'DEBIT' ELSE 'CREDIT' END,
      'BOOKED',
      (n % 100000) + 1,
      1000000000 + n,
      'KRW',
      '100m hot seed',
      'seed',
      '${hot_to}'::timestamptz - (((n - 1) % 2500000) * interval '1 second'),
      now()
    FROM generate_series(${start}, ${finish}) AS series(n)
    ON CONFLICT (id) DO NOTHING;
  "
}

insert_archive_batch() {
  local start="$1"
  local finish="$2"
  local id_offset="$hot_rows"
  local ledger_offset="1000000000000"
  psql_sql "
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
    SELECT
      ${id_offset} + n,
      ${ledger_offset} + n,
      ${cold_account_id},
      'TRX-COLD-' || n,
      CASE WHEN n % 2 = 0 THEN 'DEBIT' ELSE 'CREDIT' END,
      'BOOKED',
      (n % 100000) + 1,
      2000000000 + n,
      'KRW',
      '100m archive seed',
      'seed',
      '${cold_to}'::timestamptz - (((n - 1) % 2500000) * interval '1 second'),
      now(),
      now()
    FROM generate_series(${start}, ${finish}) AS series(n)
    ON CONFLICT (id) DO NOTHING;
  "
}

insert_batches() {
  local table_name="$1"
  local rows="$2"
  local start finish

  start=1
  while ((start <= rows)); do
    finish=$((start + batch_size - 1))
    if ((finish > rows)); then
      finish="$rows"
    fi
    echo "[transaction-100m-seed] inserting ${table_name} rows ${start}..${finish}"
    if [[ "${table_name}" == "hot" ]]; then
      insert_hot_batch "$start" "$finish"
    else
      insert_archive_batch "$start" "$finish"
    fi
    start=$((finish + 1))
  done
}

verify_seed() {
  psql_sql "
    SELECT setval(pg_get_serial_sequence('transaction_read_model', 'id'), COALESCE((SELECT MAX(id) FROM transaction_read_model), 1), true);
    ANALYZE transaction_read_model;
    ANALYZE transaction_read_model_archive;
  "
  psql_exec --command "
    SELECT
      'transaction_read_model_estimate' AS metric,
      reltuples::bigint AS value
    FROM pg_class
    WHERE oid = 'transaction_read_model'::regclass
    UNION ALL
    SELECT
      'transaction_read_model_archive_estimate' AS metric,
      reltuples::bigint AS value
    FROM pg_class
    WHERE oid = 'transaction_read_model_archive'::regclass
    UNION ALL
    SELECT 'hot_account_id', ${hot_account_id}
    UNION ALL
    SELECT 'cold_account_id', ${cold_account_id};
  "
}

main() {
  print_plan
  if [[ "${mode}" == "print-plan" ]]; then
    exit 0
  fi

  docker compose "${compose_files[@]}" --profile loadtest up -d postgres
  ensure_schema

  if [[ "${truncate_tables}" == "true" ]]; then
    echo "[transaction-100m-seed] truncating transaction read model tables"
    psql_sql "TRUNCATE transaction_read_model, transaction_read_model_archive RESTART IDENTITY;"
  fi

  if [[ "${rebuild_indexes}" == "true" ]]; then
    echo "[transaction-100m-seed] dropping secondary read indexes"
    drop_secondary_indexes
  fi

  if [[ "${disable_fk_triggers}" == "true" ]]; then
    echo "[transaction-100m-seed] disabling read model FK triggers"
    set_trigger_state DISABLE
  fi

  trap 'if [[ "${disable_fk_triggers}" == "true" ]]; then set_trigger_state ENABLE || true; fi' EXIT

  insert_batches hot "${hot_rows}"
  insert_batches archive "${cold_rows}"

  if [[ "${disable_fk_triggers}" == "true" ]]; then
    echo "[transaction-100m-seed] enabling read model FK triggers"
    set_trigger_state ENABLE
  fi
  trap - EXIT

  if [[ "${rebuild_indexes}" == "true" ]]; then
    echo "[transaction-100m-seed] recreating secondary read indexes"
    create_secondary_indexes
  fi

  verify_seed
}

main "$@"
