#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-fresh-volume-restore-k6.sh [--print-plan|--dry-run]

Environment:
  FRESH_VOLUME_CONFIRM              must be erase-postgres-volume for actual run
  FRESH_VOLUME_NAME                 default aquila-bank-postgres-data
  FRESH_VOLUME_BUILD_BACKEND        default true
  FRESH_VOLUME_K6_ENABLED           default true
  FRESH_VOLUME_ARTIFACT_PREFLIGHT   default true
  FRESH_VOLUME_DUMP_MISSING_MODE    fail-only|seed-only, default fail-only
  FRESH_VOLUME_K6_AFTER_SEED        run k6 after seed-only fallback, default false
  FRESH_VOLUME_RESTORE_VERIFY_MIN_ROWS default 1000
  FRESH_VOLUME_READINESS_TIMEOUT_SECONDS default 120
  FIXTURE_NAME                      default transaction-100m-fixture
  FIXTURE_PATH                      default build/fixtures/<FIXTURE_NAME>.dump
  FIXTURE_DATASET_ENV_PATH          default <FIXTURE_PATH>.dataset.env
  K6_REPORT_NAME                    default transaction-100m-fresh-volume-<timestamp>

Examples:
  tools/test/run-transaction-100m-fresh-volume-restore-k6.sh --print-plan
  tools/test/run-transaction-100m-fresh-volume-restore-k6.sh --dry-run
  FRESH_VOLUME_CONFIRM=erase-postgres-volume tools/test/run-transaction-100m-fresh-volume-restore-k6.sh
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

require_bool_value() {
  local key="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${key} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_non_negative_integer_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${key} must be zero or a positive integer: ${value}" >&2
    exit 1
  fi
}

fixture_name="${FIXTURE_NAME:-transaction-100m-fixture}"
fixture_dir="${FIXTURE_DIR:-build/fixtures}"
fixture_path="${FIXTURE_PATH:-${fixture_dir}/${fixture_name}.dump}"
dataset_env_path="${FIXTURE_DATASET_ENV_PATH:-${fixture_path}.dataset.env}"
db_gate_artifact_path="${FIXTURE_DATASET_DB_REPORT_PATH:-${fixture_path}.db-gate.env}"
volume_name="${FRESH_VOLUME_NAME:-aquila-bank-postgres-data}"
build_backend="${FRESH_VOLUME_BUILD_BACKEND:-true}"
k6_enabled="${FRESH_VOLUME_K6_ENABLED:-true}"
artifact_preflight="${FRESH_VOLUME_ARTIFACT_PREFLIGHT:-true}"
dump_missing_mode="${FRESH_VOLUME_DUMP_MISSING_MODE:-fail-only}"
k6_after_seed="${FRESH_VOLUME_K6_AFTER_SEED:-false}"
restore_verify_min_rows="${FRESH_VOLUME_RESTORE_VERIFY_MIN_ROWS:-1000}"
readiness_timeout_seconds="${FRESH_VOLUME_READINESS_TIMEOUT_SECONDS:-120}"
k6_report_name="${K6_REPORT_NAME:-transaction-100m-fresh-volume-$(date +%Y-%m-%d-%H%M%S)}"
hot_account_id="${K6_HOT_ACCOUNT_ID:-${SEED_HOT_ACCOUNT_ID:-910000001}}"
cold_account_id="${K6_COLD_ACCOUNT_ID:-${SEED_COLD_ACCOUNT_ID:-910000002}}"
hot_from="${K6_HOT_FROM:-${SEED_HOT_FROM:-2026-04-01T00:00:00Z}}"
hot_to="${K6_HOT_TO:-${SEED_HOT_TO:-2026-04-30T00:00:00Z}}"
cold_from="${K6_COLD_FROM:-${SEED_COLD_FROM:-2026-01-01T00:00:00Z}}"
cold_to="${K6_COLD_TO:-${SEED_COLD_TO:-2026-01-31T00:00:00Z}}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}")

require_bool_value "FRESH_VOLUME_BUILD_BACKEND" "${build_backend}"
require_bool_value "FRESH_VOLUME_K6_ENABLED" "${k6_enabled}"
require_bool_value "FRESH_VOLUME_ARTIFACT_PREFLIGHT" "${artifact_preflight}"
require_bool_value "FRESH_VOLUME_K6_AFTER_SEED" "${k6_after_seed}"
require_non_negative_integer_value "FRESH_VOLUME_RESTORE_VERIFY_MIN_ROWS" "${restore_verify_min_rows}"
require_non_negative_integer_value "FRESH_VOLUME_READINESS_TIMEOUT_SECONDS" "${readiness_timeout_seconds}"
case "${dump_missing_mode}" in
  fail-only|seed-only) ;;
  *) echo "FRESH_VOLUME_DUMP_MISSING_MODE must be fail-only or seed-only: ${dump_missing_mode}" >&2; exit 1 ;;
esac

print_plan() {
  echo "[transaction-100m-fresh-volume] mode=${mode}"
  echo "[transaction-100m-fresh-volume] volume=${volume_name}"
  echo "[transaction-100m-fresh-volume] confirm=erase-postgres-volume required for run"
  echo "[transaction-100m-fresh-volume] fixture=${fixture_name}"
  echo "[transaction-100m-fresh-volume] dump=${fixture_path}"
  echo "[transaction-100m-fresh-volume] dataset_probe=tools/test/run-transaction-100m-fixture-dataset-probe.sh"
  echo "[transaction-100m-fresh-volume] dataset_env=${dataset_env_path}"
  echo "[transaction-100m-fresh-volume] db_gate_artifact=${db_gate_artifact_path}"
  echo "[transaction-100m-fresh-volume] dataset_source=existing-artifact-or-probe"
  echo "[transaction-100m-fresh-volume] build_backend=${build_backend}"
  echo "[transaction-100m-fresh-volume] artifact_preflight=${artifact_preflight}"
  echo "[transaction-100m-fresh-volume] dump_missing_mode=${dump_missing_mode}"
  echo "[transaction-100m-fresh-volume] k6_after_seed=${k6_after_seed}"
  echo "[transaction-100m-fresh-volume] restore_verify_min_rows=${restore_verify_min_rows}"
  echo "[transaction-100m-fresh-volume] readiness_timeout_seconds=${readiness_timeout_seconds}"
  echo "[transaction-100m-fresh-volume] restore_runner=tools/test/run-transaction-100m-fixture-restore.sh"
  echo "[transaction-100m-fresh-volume] k6_runner=tools/test/run-k6-transaction-100m-loadtest.sh --no-up"
  echo "[transaction-100m-fresh-volume] k6_enabled=${k6_enabled}"
  echo "[transaction-100m-fresh-volume] k6 report=${k6_report_name}"
  echo "[transaction-100m-fresh-volume] hot account=${hot_account_id} window=${hot_from}..${hot_to}"
  echo "[transaction-100m-fresh-volume] cold account=${cold_account_id} window=${cold_from}..${cold_to}"
  echo "[transaction-100m-fresh-volume] artifact_gate=tools/test/validate-transaction-100m-fixture-artifact.sh --verify"
  echo "[transaction-100m-fresh-volume] steps=artifact-preflight,stop-runtime,remove-postgres-volume,start-runtime,restore-or-seed,verify,k6"
}

print_dry_run() {
  if [[ "${artifact_preflight}" == "true" ]]; then
    echo "FIXTURE_NAME=${fixture_name} FIXTURE_PATH=${fixture_path} FIXTURE_ARTIFACT_MIN_ROWS=${restore_verify_min_rows} tools/test/validate-transaction-100m-fixture-artifact.sh --verify"
  fi
  echo "docker compose ${compose_files[*]} --profile loadtest stop aquila-bank-backend postgres prometheus grafana alertmanager postgres-exporter"
  echo "docker compose ${compose_files[*]} --profile loadtest rm -f -s postgres"
  echo "docker volume rm ${volume_name}"
  echo "docker compose ${compose_files[*]} --profile loadtest up -d postgres aquila-bank-backend prometheus grafana alertmanager postgres-exporter"
  echo "FIXTURE_MODE=restore FIXTURE_RESTORE_TRUNCATE=true FIXTURE_NAME=${fixture_name} FIXTURE_PATH=${fixture_path} tools/test/run-transaction-100m-fixture-restore.sh"
  echo "FIXTURE_MODE=verify FIXTURE_VERIFY_MIN_ROWS=${restore_verify_min_rows} FIXTURE_NAME=${fixture_name} FIXTURE_PATH=${fixture_path} tools/test/run-transaction-100m-fixture-restore.sh"
  echo "db gate artifact: ${db_gate_artifact_path}"
  echo "source existing dataset env when db gate artifact passed"
  echo "FIXTURE_NAME=${fixture_name} FIXTURE_PATH=${fixture_path} FIXTURE_DATASET_ENV_PATH=${dataset_env_path} tools/test/run-transaction-100m-fixture-dataset-probe.sh"
  if [[ "${dump_missing_mode}" == "seed-only" ]]; then
    echo "if fixture dump is absent: tools/test/prepare-transaction-read-model-100m-fixture.sh"
  fi
  echo "K6_REPORT_NAME=${k6_report_name} K6_HOT_ACCOUNT_ID=${hot_account_id} K6_COLD_ACCOUNT_ID=${cold_account_id} tools/test/run-k6-transaction-100m-loadtest.sh --no-up"
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
  local deadline=$((SECONDS + readiness_timeout_seconds))
  local exists
  while ((SECONDS < deadline)); do
    if exists="$("${psql_base[@]}" --no-align --tuples-only --command "SELECT to_regclass('public.transaction_read_model') IS NOT NULL;" 2>/dev/null)"; then
      if [[ "$(tr -d '[:space:]' <<<"${exists}")" == "t" ]]; then
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
  echo "[transaction-100m-fresh-volume] flyway latest applied=${applied_version} required=${required_version}"
  if ((applied_version < required_version)); then
    echo "Flyway schema is stale after fresh volume recreate." >&2
    exit 1
  fi
}

reset_postgres_volume() {
  echo "[transaction-100m-fresh-volume] stopping runtime before volume reset"
  docker compose "${compose_files[@]}" --profile loadtest stop \
    aquila-bank-backend postgres prometheus grafana alertmanager postgres-exporter >/dev/null 2>&1 || true
  docker compose "${compose_files[@]}" --profile loadtest rm -f -s postgres >/dev/null 2>&1 || true

  if docker volume inspect "${volume_name}" >/dev/null 2>&1; then
    # fresh restore는 손상 volume 재사용을 피하기 위해 named volume을 명시 삭제합니다.
    docker volume rm "${volume_name}"
  else
    echo "[transaction-100m-fresh-volume] volume already absent: ${volume_name}"
  fi
}

artifact_ready="true"
seed_fallback_used="false"

preflight_fixture_artifact() {
  artifact_ready="true"
  if [[ "${artifact_preflight}" != "true" ]]; then
    echo "[transaction-100m-fresh-volume] artifact preflight skipped"
    return 0
  fi

  if FIXTURE_NAME="${fixture_name}" \
    FIXTURE_PATH="${fixture_path}" \
    FIXTURE_ARTIFACT_MIN_ROWS="${restore_verify_min_rows}" \
      tools/test/validate-transaction-100m-fixture-artifact.sh --verify; then
    return 0
  fi

  if [[ ! -s "${fixture_path}" && "${dump_missing_mode}" == "seed-only" ]]; then
    artifact_ready="false"
    echo "[transaction-100m-fresh-volume] fixture dump missing; seed-only fallback selected"
    return 0
  fi

  echo "fixture artifact preflight failed before volume reset" >&2
  exit 1
}

start_runtime() {
  if [[ "${build_backend}" == "true" ]]; then
    echo "[transaction-100m-fresh-volume] building backend bootJar"
    tools/test/with-resource-lock.sh back-gradle-fresh-volume-bootjar ./back/gradlew -p back bootJar
  fi

  echo "[transaction-100m-fresh-volume] starting runtime for restore"
  docker compose "${compose_files[@]}" --profile loadtest up -d \
    postgres aquila-bank-backend prometheus grafana alertmanager postgres-exporter
  wait_for_schema
  assert_flyway_latest
}

restore_fixture() {
  FIXTURE_MODE=restore \
  FIXTURE_RESTORE_TRUNCATE=true \
  FIXTURE_ARTIFACT_VERIFY=true \
  FIXTURE_NAME="${fixture_name}" \
  FIXTURE_PATH="${fixture_path}" \
    tools/test/run-transaction-100m-fixture-restore.sh
}

verify_fixture() {
  FIXTURE_MODE=verify \
  FIXTURE_VERIFY_MIN_ROWS="${restore_verify_min_rows}" \
  FIXTURE_NAME="${fixture_name}" \
  FIXTURE_PATH="${fixture_path}" \
    tools/test/run-transaction-100m-fixture-restore.sh
}

dataset_artifacts_ready() {
  [[ -s "${dataset_env_path}" && -s "${db_gate_artifact_path}" ]] || return 1
  grep -F "FIXTURE_DATASET_DB_GATE_STATUS=passed" "${db_gate_artifact_path}" >/dev/null \
    || grep -F "FIXTURE_DATASET_DB_GATE=passed" "${db_gate_artifact_path}" >/dev/null
}

load_dataset_env_or_probe() {
  if dataset_artifacts_ready; then
    echo "[transaction-100m-fresh-volume] using existing dataset env and db gate artifact"
  else
    FIXTURE_NAME="${fixture_name}" \
    FIXTURE_PATH="${fixture_path}" \
    FIXTURE_DATASET_ENV_PATH="${dataset_env_path}" \
    FIXTURE_DATASET_DB_REPORT_PATH="${db_gate_artifact_path}" \
      tools/test/run-transaction-100m-fixture-dataset-probe.sh
  fi
  set -a
  # restore 이후 k6 계정/window는 준비된 artifact에서 받아 DB 검증 쿼리 반복을 피합니다.
  source "${dataset_env_path}"
  set +a
  hot_account_id="${K6_HOT_ACCOUNT_ID:-${hot_account_id}}"
  hot_from="${K6_HOT_FROM:-${hot_from}}"
  hot_to="${K6_HOT_TO:-${hot_to}}"
  cold_account_id="${K6_COLD_ACCOUNT_ID:-${cold_account_id}}"
  cold_from="${K6_COLD_FROM:-${cold_from}}"
  cold_to="${K6_COLD_TO:-${cold_to}}"
}

seed_fixture() {
  seed_fallback_used="true"
  tools/test/prepare-transaction-read-model-100m-fixture.sh
}

run_k6() {
  if [[ "${k6_enabled}" != "true" ]]; then
    echo "[transaction-100m-fresh-volume] k6 skipped"
    return 0
  fi
  if [[ "${seed_fallback_used}" == "true" && "${k6_after_seed}" != "true" ]]; then
    echo "[transaction-100m-fresh-volume] k6 skipped after seed-only fallback"
    return 0
  fi

  K6_REPORT_NAME="${k6_report_name}" \
  K6_HOT_ACCOUNT_ID="${hot_account_id}" \
  K6_HOT_FROM="${hot_from}" \
  K6_HOT_TO="${hot_to}" \
  K6_COLD_ACCOUNT_ID="${cold_account_id}" \
  K6_COLD_FROM="${cold_from}" \
  K6_COLD_TO="${cold_to}" \
    tools/test/run-k6-transaction-100m-loadtest.sh --no-up
}

print_plan

if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_dry_run
  exit 0
fi
if [[ "${FRESH_VOLUME_CONFIRM:-}" != "erase-postgres-volume" ]]; then
  echo "FRESH_VOLUME_CONFIRM=erase-postgres-volume is required for fresh volume restore" >&2
  exit 1
fi

preflight_fixture_artifact
reset_postgres_volume
if [[ "${artifact_ready}" == "true" ]]; then
  start_runtime
  restore_fixture
  verify_fixture
  load_dataset_env_or_probe
else
  seed_fixture
fi
run_k6
