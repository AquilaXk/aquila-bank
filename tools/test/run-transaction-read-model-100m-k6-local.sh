#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-model-100m-k6-local.sh [--print-plan|--seed-only|--k6-only]

Environment:
  SEED_TOTAL_ROWS      default 100000000
  SEED_TRUNCATE        default true for this wrapper
  SEED_INDEX_STRATEGY  default required
  K6_REPORT_NAME       default transaction-100m-local-<timestamp>
  K6_VUS               default 8
  K6_DURATION          default 1m

Examples:
  tools/test/run-transaction-read-model-100m-k6-local.sh --print-plan
  SEED_TOTAL_ROWS=100000000 tools/test/run-transaction-read-model-100m-k6-local.sh
USAGE
}

mode="run"
if [[ "${1:-}" == "--print-plan" ]]; then
  mode="print-plan"
  shift
elif [[ "${1:-}" == "--seed-only" ]]; then
  mode="seed-only"
  shift
elif [[ "${1:-}" == "--k6-only" ]]; then
  mode="k6-only"
  shift
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "$#" -ne 0 ]]; then
  usage
  exit 1
fi

compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
seed_total_rows="${SEED_TOTAL_ROWS:-100000000}"
seed_hot_account_id="${SEED_HOT_ACCOUNT_ID:-910000001}"
seed_cold_account_id="${SEED_COLD_ACCOUNT_ID:-910000002}"
seed_hot_from="${SEED_HOT_FROM:-2026-04-01T00:00:00Z}"
seed_hot_to="${SEED_HOT_TO:-2026-04-30T00:00:00Z}"
seed_cold_from="${SEED_COLD_FROM:-2026-01-01T00:00:00Z}"
seed_cold_to="${SEED_COLD_TO:-2026-01-31T00:00:00Z}"
seed_truncate="${SEED_TRUNCATE:-true}"
seed_index_strategy="${SEED_INDEX_STRATEGY:-required}"
k6_report_name="${K6_REPORT_NAME:-transaction-100m-local-$(date +%Y-%m-%d-%H%M%S)}"

psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}")

print_plan() {
  echo "[transaction-100m-local] mode=${mode}"
  echo "[transaction-100m-local] seed_total_rows=${seed_total_rows} seed_truncate=${seed_truncate} seed_index_strategy=${seed_index_strategy}"
  echo "[transaction-100m-local] hot account=${seed_hot_account_id} window=${seed_hot_from}..${seed_hot_to}"
  echo "[transaction-100m-local] cold account=${seed_cold_account_id} window=${seed_cold_from}..${seed_cold_to}"
  echo "[transaction-100m-local] k6 report=${k6_report_name} vus=${K6_VUS:-8} duration=${K6_DURATION:-1m}"
  echo "[transaction-100m-local] observability: Prometheus http://localhost:9090, Grafana http://localhost:3001"
}

wait_for_schema() {
  local attempt exists
  for attempt in $(seq 1 90); do
    if exists="$("${psql_base[@]}" --no-align --tuples-only --command "SELECT to_regclass('public.transaction_read_model') IS NOT NULL;" 2>/dev/null)"; then
      if [[ "${exists}" == "t" ]]; then
        return 0
      fi
    fi
    sleep 2
  done
  echo "transaction_read_model schema was not created in time" >&2
  exit 1
}

start_runtime() {
  echo "[transaction-100m-local] building backend bootJar"
  tools/test/with-resource-lock.sh back-gradle-loadtest-bootjar ./back/gradlew -p back bootJar

  echo "[transaction-100m-local] starting loadtest runtime"
  docker compose "${compose_files[@]}" --profile loadtest up -d \
    postgres aquila-bank-backend prometheus grafana alertmanager postgres-exporter

  echo "[transaction-100m-local] waiting for Flyway schema"
  wait_for_schema
}

run_seed() {
  SEED_TOTAL_ROWS="${seed_total_rows}" \
  SEED_HOT_ACCOUNT_ID="${seed_hot_account_id}" \
  SEED_COLD_ACCOUNT_ID="${seed_cold_account_id}" \
  SEED_HOT_FROM="${seed_hot_from}" \
  SEED_HOT_TO="${seed_hot_to}" \
  SEED_COLD_FROM="${seed_cold_from}" \
  SEED_COLD_TO="${seed_cold_to}" \
  SEED_TRUNCATE="${seed_truncate}" \
  SEED_INDEX_STRATEGY="${seed_index_strategy}" \
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

if [[ "${mode}" != "k6-only" ]]; then
  start_runtime
  run_seed
fi

if [[ "${mode}" != "seed-only" ]]; then
  if [[ "${mode}" == "k6-only" ]]; then
    wait_for_schema
  fi
  assert_k6_preflight
  run_k6
fi
