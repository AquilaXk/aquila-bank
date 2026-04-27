#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-k6-explain-snapshot.sh [--print-plan|--dry-run|--print-sql]

Required environment:
  K6_HOT_ACCOUNT_ID
  K6_HOT_FROM
  K6_HOT_TO
  K6_COLD_ACCOUNT_ID
  K6_COLD_FROM
  K6_COLD_TO

Optional environment:
  K6_REPORT_NAME         default transaction-100m
  K6_EXPLAIN_PHASE       default manual
  K6_EXPLAIN_DIR         default build/reports/k6/<K6_REPORT_NAME>-explain
  K6_LIMIT               default 50
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
    --print-sql)
      mode="print-sql"
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

require_env() {
  local key="$1"
  local value="${!key:-}"
  if [[ -z "${value}" ]]; then
    echo "${key} is required" >&2
    exit 1
  fi
}

K6_REPORT_NAME="${K6_REPORT_NAME:-transaction-100m}"
K6_EXPLAIN_PHASE="${K6_EXPLAIN_PHASE:-manual}"
K6_LIMIT="${K6_LIMIT:-50}"
K6_EXPLAIN_DIR="${K6_EXPLAIN_DIR:-build/reports/k6/${K6_REPORT_NAME}-explain}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}")

if ! [[ "${K6_LIMIT}" =~ ^[1-9][0-9]*$ ]]; then
  echo "K6_LIMIT must be a positive integer" >&2
  exit 1
fi

hot_first_path="${K6_EXPLAIN_DIR}/${K6_EXPLAIN_PHASE}-hot-first.txt"
hot_cursor_path="${K6_EXPLAIN_DIR}/${K6_EXPLAIN_PHASE}-hot-cursor.txt"
cold_first_path="${K6_EXPLAIN_DIR}/${K6_EXPLAIN_PHASE}-cold-first.txt"
cold_cursor_path="${K6_EXPLAIN_DIR}/${K6_EXPLAIN_PHASE}-cold-cursor.txt"

print_plan() {
  echo "[transaction-100m-k6-explain] mode=${mode}"
  echo "[transaction-100m-k6-explain] report=${K6_REPORT_NAME}"
  echo "[transaction-100m-k6-explain] phase=${K6_EXPLAIN_PHASE}"
  echo "[transaction-100m-k6-explain] dir=${K6_EXPLAIN_DIR}"
  echo "[transaction-100m-k6-explain] hot-first=${hot_first_path}"
  echo "[transaction-100m-k6-explain] hot-cursor=${hot_cursor_path}"
  echo "[transaction-100m-k6-explain] cold-first=${cold_first_path}"
  echo "[transaction-100m-k6-explain] cold-cursor=${cold_cursor_path}"
}

query_sql() {
  local table="$1"
  local account_id="$2"
  local from="$3"
  local to="$4"
  local cursor="$5"
  local cursor_predicate=""
  if [[ "${cursor}" == "true" ]]; then
    cursor_predicate="
  AND (booked_at, id) < (
    SELECT booked_at, id
    FROM public.${table}
    WHERE account_id = '${account_id}'
      AND booked_at >= '${from}'::timestamptz
      AND booked_at < '${to}'::timestamptz
    ORDER BY booked_at DESC, id DESC
    OFFSET $((K6_LIMIT - 1))
    LIMIT 1
  )"
  fi
  cat <<SQL
EXPLAIN (FORMAT TEXT)
SELECT id, account_id, transaction_reference, booked_at
FROM public.${table}
WHERE account_id = '${account_id}'
  AND booked_at >= '${from}'::timestamptz
  AND booked_at < '${to}'::timestamptz${cursor_predicate}
ORDER BY booked_at DESC, id DESC
LIMIT ${K6_LIMIT};
SQL
}

print_sql() {
  query_sql "transaction_read_model" "${K6_HOT_ACCOUNT_ID}" "${K6_HOT_FROM}" "${K6_HOT_TO}" false
  query_sql "transaction_read_model" "${K6_HOT_ACCOUNT_ID}" "${K6_HOT_FROM}" "${K6_HOT_TO}" true
  query_sql "transaction_read_model_archive" "${K6_COLD_ACCOUNT_ID}" "${K6_COLD_FROM}" "${K6_COLD_TO}" false
  query_sql "transaction_read_model_archive" "${K6_COLD_ACCOUNT_ID}" "${K6_COLD_FROM}" "${K6_COLD_TO}" true
}

write_explain() {
  local table="$1"
  local account_id="$2"
  local from="$3"
  local to="$4"
  local cursor="$5"
  local output="$6"
  query_sql "${table}" "${account_id}" "${from}" "${to}" "${cursor}" \
    | "${psql_base[@]}" --output "${output}"
}

require_inputs() {
  require_env K6_HOT_ACCOUNT_ID
  require_env K6_HOT_FROM
  require_env K6_HOT_TO
  require_env K6_COLD_ACCOUNT_ID
  require_env K6_COLD_FROM
  require_env K6_COLD_TO
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

require_inputs
if [[ "${mode}" == "dry-run" ]]; then
  echo "mkdir -p ${K6_EXPLAIN_DIR}"
  echo "write hot-first ${hot_first_path}"
  echo "write hot-cursor ${hot_cursor_path}"
  echo "write cold-first ${cold_first_path}"
  echo "write cold-cursor ${cold_cursor_path}"
  exit 0
fi
if [[ "${mode}" == "print-sql" ]]; then
  print_sql
  exit 0
fi

mkdir -p "${K6_EXPLAIN_DIR}"
write_explain "transaction_read_model" "${K6_HOT_ACCOUNT_ID}" "${K6_HOT_FROM}" "${K6_HOT_TO}" false "${hot_first_path}"
write_explain "transaction_read_model" "${K6_HOT_ACCOUNT_ID}" "${K6_HOT_FROM}" "${K6_HOT_TO}" true "${hot_cursor_path}"
write_explain "transaction_read_model_archive" "${K6_COLD_ACCOUNT_ID}" "${K6_COLD_FROM}" "${K6_COLD_TO}" false "${cold_first_path}"
write_explain "transaction_read_model_archive" "${K6_COLD_ACCOUNT_ID}" "${K6_COLD_FROM}" "${K6_COLD_TO}" true "${cold_cursor_path}"
