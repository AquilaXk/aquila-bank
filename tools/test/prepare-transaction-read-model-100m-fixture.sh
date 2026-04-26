#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/prepare-transaction-read-model-100m-fixture.sh [--print-plan]

Fixture environment:
  FIXTURE_POSTGRES_CPUS         default 2
  FIXTURE_POSTGRES_MEMORY       default 2g
  FIXTURE_POSTGRES_MEMORY_SWAP  default same as FIXTURE_POSTGRES_MEMORY
  FIXTURE_POSTGRES_PIDS_LIMIT   default 256

Seed environment:
  SEED_TOTAL_ROWS      default 100000000
  SEED_BATCH_SIZE      default 250000
  SEED_TRUNCATE        default true for this wrapper
  SEED_INDEX_STRATEGY  default required
  SEED_CONFLICT_MODE   default fail

Examples:
  tools/test/prepare-transaction-read-model-100m-fixture.sh --print-plan
  FIXTURE_POSTGRES_MEMORY=4g SEED_TOTAL_ROWS=100000000 tools/test/prepare-transaction-read-model-100m-fixture.sh

After success, run the read phase with:
  tools/test/run-transaction-read-model-100m-k6-local.sh --k6-only
USAGE
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

compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
fixture_postgres_cpus="${FIXTURE_POSTGRES_CPUS:-2}"
fixture_postgres_memory="${FIXTURE_POSTGRES_MEMORY:-2g}"
fixture_postgres_memory_swap="${FIXTURE_POSTGRES_MEMORY_SWAP:-${fixture_postgres_memory}}"
fixture_postgres_pids_limit="${FIXTURE_POSTGRES_PIDS_LIMIT:-256}"
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

psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}")

print_plan() {
  echo "[transaction-100m-fixture] mode=${mode}"
  echo "[transaction-100m-fixture] fixture phase only"
  echo "[transaction-100m-fixture] seed_total_rows=${seed_total_rows} seed_batch_size=${seed_batch_size} seed_truncate=${seed_truncate} seed_index_strategy=${seed_index_strategy} seed_conflict_mode=${seed_conflict_mode}"
  echo "[transaction-100m-fixture] hot account=${seed_hot_account_id} window=${seed_hot_from}..${seed_hot_to}"
  echo "[transaction-100m-fixture] cold account=${seed_cold_account_id} window=${seed_cold_from}..${seed_cold_to}"
  echo "[transaction-100m-fixture] postgres budget cpus=${fixture_postgres_cpus} memory=${fixture_postgres_memory} memory_swap=${fixture_postgres_memory_swap} pids=${fixture_postgres_pids_limit}"
  echo "[transaction-100m-fixture] observability=off services=postgres,aquila-bank-backend"
  echo "[transaction-100m-fixture] flyway preflight=latest local migration"
  echo "[transaction-100m-fixture] read phase: tools/test/run-transaction-read-model-100m-k6-local.sh --k6-only"
}

compose_with_fixture_budget() {
  T3MICRO_POSTGRES_CPUS="${fixture_postgres_cpus}" \
  T3MICRO_POSTGRES_MEMORY="${fixture_postgres_memory}" \
  T3MICRO_POSTGRES_MEMORY_SWAP="${fixture_postgres_memory_swap}" \
  T3MICRO_POSTGRES_PIDS_LIMIT="${fixture_postgres_pids_limit}" \
    docker compose "${compose_files[@]}" "$@"
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
  echo "[transaction-100m-fixture] flyway latest applied=${applied_version} required=${required_version}"
  if ((applied_version < required_version)); then
    echo "Flyway schema is stale. Recreate aquila-bank-backend and retry before seed." >&2
    exit 1
  fi
}

stop_observability() {
  # fixture 생성 중에는 exporter/Prometheus 부하를 제거해 seed resource만 측정합니다.
  compose_with_fixture_budget --profile loadtest stop \
    prometheus grafana alertmanager postgres-exporter >/dev/null 2>&1 || true
}

start_runtime() {
  echo "[transaction-100m-fixture] building backend bootJar"
  tools/test/with-resource-lock.sh back-gradle-fixture-bootjar ./back/gradlew -p back bootJar

  echo "[transaction-100m-fixture] stopping observability services"
  stop_observability

  echo "[transaction-100m-fixture] starting fixture runtime"
  compose_with_fixture_budget --profile loadtest up -d postgres
  compose_with_fixture_budget --profile loadtest up -d --force-recreate aquila-bank-backend

  echo "[transaction-100m-fixture] waiting for Flyway schema"
  wait_for_schema
  assert_flyway_latest
}

run_seed() {
  T3MICRO_POSTGRES_CPUS="${fixture_postgres_cpus}" \
  T3MICRO_POSTGRES_MEMORY="${fixture_postgres_memory}" \
  T3MICRO_POSTGRES_MEMORY_SWAP="${fixture_postgres_memory_swap}" \
  T3MICRO_POSTGRES_PIDS_LIMIT="${fixture_postgres_pids_limit}" \
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

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

start_runtime
run_seed

echo "[transaction-100m-fixture] fixture seed complete"
echo "[transaction-100m-fixture] read phase: tools/test/run-transaction-read-model-100m-k6-local.sh --k6-only"
