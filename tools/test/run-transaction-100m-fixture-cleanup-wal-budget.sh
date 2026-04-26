#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-fixture-cleanup-wal-budget.sh [--print-plan|--dry-run]

Environment:
  CLEANUP_CONFIRM        must be delete-100m-fixture for actual run
  CLEANUP_HOT_ACCOUNT_ID default 910000001
  CLEANUP_COLD_ACCOUNT_ID default 910000002
  CLEANUP_CHUNK_SIZE     default 50000
  CLEANUP_MAX_CHUNKS     default 4000
  CLEANUP_WAL_MAX_BYTES  default 1073741824

Examples:
  tools/test/run-transaction-100m-fixture-cleanup-wal-budget.sh --print-plan
  tools/test/run-transaction-100m-fixture-cleanup-wal-budget.sh --dry-run
  CLEANUP_CONFIRM=delete-100m-fixture tools/test/run-transaction-100m-fixture-cleanup-wal-budget.sh
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --dry-run)
      mode="dry-run"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

require_positive_integer_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${key} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

hot_account_id="${CLEANUP_HOT_ACCOUNT_ID:-910000001}"
cold_account_id="${CLEANUP_COLD_ACCOUNT_ID:-910000002}"
chunk_size="${CLEANUP_CHUNK_SIZE:-50000}"
max_chunks="${CLEANUP_MAX_CHUNKS:-4000}"
wal_max_bytes="${CLEANUP_WAL_MAX_BYTES:-1073741824}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}")
wal_start_lsn=""

require_positive_integer_value "CLEANUP_HOT_ACCOUNT_ID" "${hot_account_id}"
require_positive_integer_value "CLEANUP_COLD_ACCOUNT_ID" "${cold_account_id}"
require_positive_integer_value "CLEANUP_CHUNK_SIZE" "${chunk_size}"
require_positive_integer_value "CLEANUP_MAX_CHUNKS" "${max_chunks}"
require_positive_integer_value "CLEANUP_WAL_MAX_BYTES" "${wal_max_bytes}"
if [[ "${hot_account_id}" == "${cold_account_id}" ]]; then
  echo "CLEANUP_HOT_ACCOUNT_ID and CLEANUP_COLD_ACCOUNT_ID must differ" >&2
  exit 1
fi

print_plan() {
  echo "[transaction-100m-cleanup-wal] mode=${mode}"
  echo "[transaction-100m-cleanup-wal] confirm=delete-100m-fixture required for run"
  echo "[transaction-100m-cleanup-wal] accounts=${hot_account_id},${cold_account_id}"
  echo "[transaction-100m-cleanup-wal] chunk_size=${chunk_size}"
  echo "[transaction-100m-cleanup-wal] max_chunks=${max_chunks}"
  echo "[transaction-100m-cleanup-wal] wal_max_bytes=${wal_max_bytes}"
  echo "[transaction-100m-cleanup-wal] tables=transaction_read_model,transaction_read_model_archive"
  echo "[transaction-100m-cleanup-wal] recovery_preflight=tools/test/run-transaction-100m-fixture-restore.sh verify"
}

print_dry_run() {
  echo "FIXTURE_MODE=verify FIXTURE_VERIFY_MIN_ROWS=0 tools/test/run-transaction-100m-fixture-restore.sh"
  echo "WITH candidates AS (SELECT ctid FROM public.transaction_read_model WHERE account_id IN (${hot_account_id}, ${cold_account_id}) LIMIT ${chunk_size}), deleted AS (DELETE FROM public.transaction_read_model item USING candidates WHERE item.ctid = candidates.ctid RETURNING 1) SELECT count(*) FROM deleted;"
  echo "WITH candidates AS (SELECT ctid FROM public.transaction_read_model_archive WHERE account_id IN (${hot_account_id}, ${cold_account_id}) LIMIT ${chunk_size}), deleted AS (DELETE FROM public.transaction_read_model_archive item USING candidates WHERE item.ctid = candidates.ctid RETURNING 1) SELECT count(*) FROM deleted;"
  echo "SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), '<start_lsn>'::pg_lsn)::bigint;"
}

run_recovery_preflight() {
  FIXTURE_MODE=verify \
  FIXTURE_VERIFY_MIN_ROWS=0 \
    tools/test/run-transaction-100m-fixture-restore.sh >/dev/null
}

capture_wal_start() {
  wal_start_lsn="$("${psql_base[@]}" --no-align --tuples-only --command "SELECT pg_current_wal_lsn();")"
  wal_start_lsn="$(tr -d '[:space:]' <<<"${wal_start_lsn}")"
  if [[ -z "${wal_start_lsn}" ]]; then
    echo "failed to capture PostgreSQL WAL start LSN" >&2
    exit 1
  fi
  echo "[transaction-100m-cleanup-wal] wal_start_lsn=${wal_start_lsn}"
}

current_wal_bytes() {
  local bytes
  bytes="$(
    "${psql_base[@]}" --no-align --tuples-only --command "
      SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), '${wal_start_lsn}'::pg_lsn)::bigint;
    "
  )"
  tr -d '[:space:]' <<<"${bytes}"
}

check_wal_budget() {
  local table="$1"
  local chunk="$2"
  local wal_bytes
  wal_bytes="$(current_wal_bytes)"
  echo "[transaction-100m-cleanup-wal] table=${table} chunk=${chunk} wal_bytes=${wal_bytes}/${wal_max_bytes}"
  if ! [[ "${wal_bytes}" =~ ^[0-9]+$ ]]; then
    echo "WAL budget check returned invalid value: ${wal_bytes}" >&2
    exit 1
  fi
  if ((wal_bytes > wal_max_bytes)); then
    echo "WAL budget exceeded table=${table} chunk=${chunk} wal_bytes=${wal_bytes} max=${wal_max_bytes}" >&2
    exit 1
  fi
}

delete_chunk() {
  local table="$1"
  local deleted
  deleted="$(
    "${psql_base[@]}" --no-align --tuples-only --command "
      WITH candidates AS (
        SELECT ctid
        FROM public.${table}
        WHERE account_id IN (${hot_account_id}, ${cold_account_id})
        LIMIT ${chunk_size}
      ),
      deleted AS (
        DELETE FROM public.${table} item
        USING candidates
        WHERE item.ctid = candidates.ctid
        RETURNING 1
      )
      SELECT count(*) FROM deleted;
    "
  )"
  tr -d '[:space:]' <<<"${deleted}"
}

cleanup_table() {
  local table="$1"
  local chunk deleted
  for chunk in $(seq 1 "${max_chunks}"); do
    # 큰 DELETE 한 번 대신 ctid chunk로 나눠 recovery loop를 만든 WAL spike를 피합니다.
    deleted="$(delete_chunk "${table}")"
    if ! [[ "${deleted}" =~ ^[0-9]+$ ]]; then
      echo "cleanup delete returned invalid count table=${table}: ${deleted}" >&2
      exit 1
    fi
    echo "[transaction-100m-cleanup-wal] table=${table} chunk=${chunk} deleted=${deleted}"
    check_wal_budget "${table}" "${chunk}"
    if [[ "${deleted}" -eq 0 ]]; then
      return 0
    fi
  done

  echo "cleanup stopped after CLEANUP_MAX_CHUNKS=${max_chunks}; table may still contain fixture rows: ${table}" >&2
  exit 1
}

print_plan

if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_dry_run
  exit 0
fi
if [[ "${CLEANUP_CONFIRM:-}" != "delete-100m-fixture" ]]; then
  echo "CLEANUP_CONFIRM=delete-100m-fixture is required for 100m fixture cleanup" >&2
  exit 1
fi

run_recovery_preflight
capture_wal_start
cleanup_table "transaction_read_model"
cleanup_table "transaction_read_model_archive"
