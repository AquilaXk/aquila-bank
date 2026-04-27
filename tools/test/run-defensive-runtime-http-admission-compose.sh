#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-defensive-runtime-http-admission-compose.sh [--print-plan|--dry-run]

Environment:
  ADMISSION_COMPOSE_BUILD_BACKEND default true
  ADMISSION_COMPOSE_SEED_ROWS     default 1000
  ADMISSION_COMPOSE_SKIP_SEED     default false
  ADMISSION_OUTBOX_PREFLIGHT      run local outbox backlog gate before smoke, default false
  ADMISSION_COMPOSE_READINESS_TIMEOUT_SECONDS default 120
  ADMISSION_BASE_URL              default http://localhost:${LOADTEST_BACKEND_PORT:-18080}
  ADMISSION_NAME                  passed to run-defensive-runtime-http-admission-smoke.sh

Examples:
  tools/test/run-defensive-runtime-http-admission-compose.sh --print-plan
  tools/test/run-defensive-runtime-http-admission-compose.sh --dry-run
  ADMISSION_NAME=local-admission tools/test/run-defensive-runtime-http-admission-compose.sh
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

require_positive_integer_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${key} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

build_backend="${ADMISSION_COMPOSE_BUILD_BACKEND:-true}"
seed_rows="${ADMISSION_COMPOSE_SEED_ROWS:-1000}"
skip_seed="${ADMISSION_COMPOSE_SKIP_SEED:-false}"
outbox_preflight="${ADMISSION_OUTBOX_PREFLIGHT:-false}"
readiness_timeout_seconds="${ADMISSION_COMPOSE_READINESS_TIMEOUT_SECONDS:-120}"
base_url="${ADMISSION_BASE_URL:-http://localhost:${LOADTEST_BACKEND_PORT:-18080}}"
base_url="${base_url%/}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)

require_bool_value "ADMISSION_COMPOSE_BUILD_BACKEND" "${build_backend}"
require_bool_value "ADMISSION_COMPOSE_SKIP_SEED" "${skip_seed}"
require_bool_value "ADMISSION_OUTBOX_PREFLIGHT" "${outbox_preflight}"
require_positive_integer_value "ADMISSION_COMPOSE_SEED_ROWS" "${seed_rows}"
require_positive_integer_value "ADMISSION_COMPOSE_READINESS_TIMEOUT_SECONDS" "${readiness_timeout_seconds}"

print_plan() {
  echo "[defensive-http-admission-compose] mode=${mode}"
  echo "[defensive-http-admission-compose] build_backend=${build_backend}"
  echo "[defensive-http-admission-compose] seed_rows=${seed_rows}"
  echo "[defensive-http-admission-compose] skip_seed=${skip_seed}"
  echo "[defensive-http-admission-compose] outbox_preflight=${outbox_preflight}"
  echo "[defensive-http-admission-compose] readiness_timeout_seconds=${readiness_timeout_seconds}"
  echo "[defensive-http-admission-compose] services=postgres,aquila-bank-backend"
  echo "[defensive-http-admission-compose] smoke=tools/test/run-defensive-runtime-http-admission-smoke.sh"
  echo "[defensive-http-admission-compose] base_url=${base_url}"
}

print_dry_run() {
  if [[ "${build_backend}" == "true" ]]; then
    echo "tools/test/with-resource-lock.sh back-gradle-admission-compose-bootjar ./back/gradlew -p back bootJar"
  fi
  echo "docker compose ${compose_files[*]} --profile loadtest up -d postgres aquila-bank-backend"
  if [[ "${skip_seed}" != "true" ]]; then
    echo "SEED_TOTAL_ROWS=${seed_rows} SEED_TRUNCATE=true tools/test/seed-transaction-read-model-100m.sh"
  fi
  if [[ "${outbox_preflight}" == "true" ]]; then
    echo "OUTBOX_LOCAL_BUILD_BACKEND=false OUTBOX_BACKLOG_BASE_URL=${base_url} tools/test/run-outbox-provider-backlog-local-gate.sh"
  fi
  echo "ADMISSION_BASE_URL=${base_url} tools/test/run-defensive-runtime-http-admission-smoke.sh"
}

wait_for_backend() {
  local deadline=$((SECONDS + readiness_timeout_seconds))
  while ((SECONDS < deadline)); do
    if curl -fsS "${base_url}/actuator/health/readiness" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "backend readiness did not become healthy: ${base_url}" >&2
  exit 1
}

start_runtime() {
  if [[ "${build_backend}" == "true" ]]; then
    tools/test/with-resource-lock.sh back-gradle-admission-compose-bootjar ./back/gradlew -p back bootJar
  fi
  docker compose "${compose_files[@]}" --profile loadtest up -d postgres aquila-bank-backend
  wait_for_backend
}

seed_fixture() {
  if [[ "${skip_seed}" == "true" ]]; then
    echo "[defensive-http-admission-compose] seed skipped"
    return 0
  fi
  SEED_TOTAL_ROWS="${seed_rows}" \
  SEED_TRUNCATE=true \
  SEED_INDEX_STRATEGY=required \
  SEED_CONFLICT_MODE=fail \
    tools/test/seed-transaction-read-model-100m.sh
}

run_outbox_preflight() {
  if [[ "${outbox_preflight}" != "true" ]]; then
    echo "[defensive-http-admission-compose] outbox preflight skipped"
    return 0
  fi
  OUTBOX_LOCAL_BUILD_BACKEND=false \
  OUTBOX_BACKLOG_BASE_URL="${base_url}" \
    tools/test/run-outbox-provider-backlog-local-gate.sh
}

run_smoke() {
  ADMISSION_BASE_URL="${base_url}" tools/test/run-defensive-runtime-http-admission-smoke.sh
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_dry_run
  exit 0
fi

start_runtime
seed_fixture
run_outbox_preflight
run_smoke
