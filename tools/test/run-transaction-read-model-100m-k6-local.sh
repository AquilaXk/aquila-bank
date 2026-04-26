#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-model-100m-k6-local.sh [--print-plan|--seed-only|--k6-only] [--no-deps]

Environment:
  SEED_TOTAL_ROWS      default 100000000
  SEED_BATCH_SIZE      default 250000
  SEED_TRUNCATE        default true for this wrapper
  SEED_INDEX_STRATEGY  default required
  SEED_CONFLICT_MODE   default fail
  K6_REPORT_NAME       default transaction-100m-local-<timestamp>
  K6_VUS               default 8
  K6_DURATION          default 1m

Examples:
  tools/test/run-transaction-read-model-100m-k6-local.sh --print-plan
  tools/test/run-transaction-read-model-100m-k6-local.sh --k6-only --no-deps
  SEED_TOTAL_ROWS=100000000 tools/test/run-transaction-read-model-100m-k6-local.sh
USAGE
}

mode="run"
start_dependencies="true"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --seed-only)
      mode="seed-only"
      ;;
    --k6-only)
      mode="k6-only"
      ;;
    --no-deps)
      start_dependencies="false"
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

compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
db_username="${DB_USERNAME:-postgres}"
db_name="${DB_NAME:-aquila_bank}"
seed_total_rows="${SEED_TOTAL_ROWS:-100000000}"
seed_batch_size="${SEED_BATCH_SIZE:-250000}"
seed_hot_account_id="${SEED_HOT_ACCOUNT_ID:-910000001}"
seed_cold_account_id="${SEED_COLD_ACCOUNT_ID:-910000002}"
seed_hot_from="${SEED_HOT_FROM:-2026-04-01T00:00:00Z}"
seed_hot_to="${SEED_HOT_TO:-2026-04-30T00:00:00Z}"
seed_cold_from="${SEED_COLD_FROM:-2026-01-01T00:00:00Z}"
seed_cold_to="${SEED_COLD_TO:-2026-01-31T00:00:00Z}"
seed_truncate="${SEED_TRUNCATE:-true}"
seed_index_strategy="${SEED_INDEX_STRATEGY:-required}"
seed_conflict_mode="${SEED_CONFLICT_MODE:-fail}"
k6_report_name="${K6_REPORT_NAME:-transaction-100m-local-$(date +%Y-%m-%d-%H%M%S)}"

psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${db_username}" -d "${db_name}")

print_plan() {
  echo "[transaction-100m-local] mode=${mode}"
  if [[ "${start_dependencies}" == "true" ]]; then
    echo "[transaction-100m-local] dependencies=compose-runtime"
  else
    echo "[transaction-100m-local] dependencies=no-deps"
  fi
  echo "[transaction-100m-local] backend env DB_USERNAME=${db_username} DB_NAME=${db_name}"
  echo "[transaction-100m-local] seed_total_rows=${seed_total_rows} seed_batch_size=${seed_batch_size} seed_truncate=${seed_truncate} seed_index_strategy=${seed_index_strategy} seed_conflict_mode=${seed_conflict_mode}"
  echo "[transaction-100m-local] hot account=${seed_hot_account_id} window=${seed_hot_from}..${seed_hot_to}"
  echo "[transaction-100m-local] cold account=${seed_cold_account_id} window=${seed_cold_from}..${seed_cold_to}"
  echo "[transaction-100m-local] flyway preflight=latest local migration"
  echo "[transaction-100m-local] k6 report=${k6_report_name} vus=${K6_VUS:-8} duration=${K6_DURATION:-1m}"
  echo "[transaction-100m-local] observability: Prometheus http://localhost:9090, Grafana http://localhost:3001"
}

validate_backend_env() {
  if [[ -z "${db_username}" ]]; then
    echo "DB_USERNAME must not be empty for backend/postgres preflight" >&2
    exit 1
  fi
  if [[ -z "${db_name}" ]]; then
    echo "DB_NAME must not be empty for backend/postgres preflight" >&2
    exit 1
  fi
}

log_psql_failure() {
  local attempt="$1"
  local output="$2"
  if (( attempt == 1 || attempt % 15 == 0 || attempt == 90 )); then
    echo "[transaction-100m-local] psql schema check failed attempt=${attempt}/90" >&2
    echo "[transaction-100m-local] psql output:" >&2
    echo "${output}" >&2
  fi
}

wait_for_schema() {
  local attempt exists psql_output last_psql_output
  for attempt in $(seq 1 90); do
    if psql_output="$("${psql_base[@]}" --no-align --tuples-only --command "SELECT to_regclass('public.transaction_read_model') IS NOT NULL;" 2>&1)"; then
      last_psql_output="${psql_output}"
      exists="$(tr -d '[:space:]' <<<"${psql_output}")"
      if [[ "${exists}" == "t" ]]; then
        return 0
      fi
    else
      last_psql_output="${psql_output}"
      log_psql_failure "${attempt}" "${psql_output}"
    fi
    sleep 2
  done
  echo "transaction_read_model schema was not created in time" >&2
  if [[ -n "${last_psql_output:-}" ]]; then
    echo "[transaction-100m-local] psql output:" >&2
    echo "${last_psql_output}" >&2
  fi
  exit 1
}

latest_local_flyway_version() {
  local version
  version="$(
    find back/src/main/resources/db/migration -maxdepth 1 -type f -name 'V*__*.sql' \
      | sed -E 's#^.*/V([0-9]+)__.*$#\1#' \
      | sort -n \
      | tail -1
  )"
  if [[ -z "${version}" ]]; then
    echo "no local Flyway migrations found" >&2
    exit 1
  fi
  echo "${version}"
}

assert_flyway_latest() {
  local required_version applied_version
  required_version="$(latest_local_flyway_version)"
  applied_version="$(
    "${psql_base[@]}" --no-align --tuples-only --command "
      SELECT COALESCE(MAX(version::integer), 0)
      FROM flyway_schema_history
      WHERE success
        AND version ~ '^[0-9]+$';
    "
  )"
  if ! [[ "${applied_version}" =~ ^[0-9]+$ ]]; then
    echo "Flyway latest version check returned invalid value: ${applied_version}" >&2
    exit 1
  fi
  echo "[transaction-100m-local] flyway latest applied=${applied_version} required=${required_version}"
  if ((applied_version < required_version)); then
    echo "Flyway schema is stale. Recreate aquila-bank-backend and retry before seed." >&2
    exit 1
  fi
}

start_runtime() {
  echo "[transaction-100m-local] building backend bootJar"
  tools/test/with-resource-lock.sh back-gradle-loadtest-bootjar ./back/gradlew -p back bootJar

  echo "[transaction-100m-local] starting loadtest runtime"
  docker compose "${compose_files[@]}" --profile loadtest up -d \
    postgres prometheus grafana alertmanager postgres-exporter

  # stale backend container는 새 bootJar/Flyway를 놓칠 수 있어 backend만 강제 재생성합니다.
  docker compose "${compose_files[@]}" --profile loadtest up -d --force-recreate aquila-bank-backend

  echo "[transaction-100m-local] waiting for Flyway schema"
  wait_for_schema
  assert_flyway_latest
}

run_seed() {
  SEED_TOTAL_ROWS="${seed_total_rows}" \
  SEED_BATCH_SIZE="${seed_batch_size}" \
  SEED_HOT_ACCOUNT_ID="${seed_hot_account_id}" \
  SEED_COLD_ACCOUNT_ID="${seed_cold_account_id}" \
  SEED_HOT_FROM="${seed_hot_from}" \
  SEED_HOT_TO="${seed_hot_to}" \
  SEED_COLD_FROM="${seed_cold_from}" \
  SEED_COLD_TO="${seed_cold_to}" \
  SEED_TRUNCATE="${seed_truncate}" \
  SEED_INDEX_STRATEGY="${seed_index_strategy}" \
  SEED_CONFLICT_MODE="${seed_conflict_mode}" \
    tools/test/seed-transaction-read-model-100m.sh
}

assert_k6_preflight() {
  K6_HOT_ACCOUNT_ID="${seed_hot_account_id}" \
  K6_HOT_FROM="${seed_hot_from}" \
  K6_HOT_TO="${seed_hot_to}" \
  K6_COLD_ACCOUNT_ID="${seed_cold_account_id}" \
  K6_COLD_FROM="${seed_cold_from}" \
  K6_COLD_TO="${seed_cold_to}" \
  K6_REPORT_NAME="${k6_report_name}" \
  K6_VUS="${K6_VUS:-8}" \
  K6_DURATION="${K6_DURATION:-1m}" \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null
}

run_k6() {
  K6_REPORT_NAME="${k6_report_name}" \
  K6_HOT_ACCOUNT_ID="${seed_hot_account_id}" \
  K6_HOT_FROM="${seed_hot_from}" \
  K6_HOT_TO="${seed_hot_to}" \
  K6_COLD_ACCOUNT_ID="${seed_cold_account_id}" \
  K6_COLD_FROM="${seed_cold_from}" \
  K6_COLD_TO="${seed_cold_to}" \
    tools/test/run-k6-transaction-100m-loadtest.sh --no-up
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

validate_backend_env

if [[ "${mode}" != "k6-only" && "${start_dependencies}" == "true" ]]; then
  start_runtime
elif [[ "${mode}" != "k6-only" ]]; then
  echo "[transaction-100m-local] dependency start skipped by --no-deps"
fi

if [[ "${mode}" != "k6-only" ]]; then
  run_seed
fi

if [[ "${mode}" != "seed-only" ]]; then
  if [[ "${mode}" == "k6-only" ]]; then
    wait_for_schema
    assert_flyway_latest
  fi
  assert_k6_preflight
  run_k6
fi
