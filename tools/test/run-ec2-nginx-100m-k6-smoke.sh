#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-ec2-nginx-100m-k6-smoke.sh [--print-plan|--dry-run]

Environment:
  EC2_LOCAL_DB_CAPACITY_ENV_FILE optional env file
  EC2_NGINX_BASE_URL required through env doctor
  EC2_LOADTEST_AUTH_TOKEN required when auth is required
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan) mode="print-plan" ;;
    --dry-run) mode="dry-run" ;;
    -h|--help) usage; exit 0 ;;
    *) usage; exit 1 ;;
  esac
  shift
done

if [[ -n "${EC2_LOCAL_DB_CAPACITY_ENV_FILE:-}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${EC2_LOCAL_DB_CAPACITY_ENV_FILE}"
  set +a
fi

run_id="${EC2_CAPACITY_RUN_ID:-ec2-local-db-100m-$(date +%Y-%m-%d-%H%M%S)}"
base_url="${EC2_NGINX_BASE_URL:-}"
report_name="${EC2_NGINX_K6_REPORT_NAME:-${run_id}-nginx}"

print_plan() {
  EC2_LOCAL_DB_TUNNEL_CHECK="${EC2_LOCAL_DB_TUNNEL_CHECK:-false}" \
    tools/test/run-ec2-local-db-capacity-env-doctor.sh --print-plan
  echo "[ec2-nginx-k6] mode=${mode}"
  echo "[ec2-nginx-k6] run_id=${run_id}"
  echo "[ec2-nginx-k6] base_url=${base_url}"
  echo "[ec2-nginx-k6] report_name=${report_name}"
  echo "[ec2-nginx-k6] scenario=${EC2_K6_SCENARIO_MODE:-constant-vus}"
  echo "[ec2-nginx-k6] vus=${EC2_K6_VUS:-8} duration=${EC2_K6_DURATION:-1m}"
}

print_command() {
  echo "K6_BASE_URL=${base_url} K6_REPORT_NAME=${report_name} K6_RUN_ID=${run_id} K6_RUN_PURPOSE=smoke tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_command
  exit 0
fi

tools/test/run-ec2-local-db-capacity-env-doctor.sh --dry-run >/dev/null

K6_BASE_URL="${base_url}" \
K6_BACKEND_READINESS_BASE_URL="${base_url}" \
K6_REPORT_NAME="${report_name}" \
K6_RUN_ID="${run_id}" \
K6_RUN_PURPOSE=smoke \
K6_GENERATOR_MODE=local \
K6_OBSERVABILITY_MODE="${EC2_K6_OBSERVABILITY_MODE:-summary-only}" \
K6_PREFLIGHT=false \
K6_POSTGRES_HEALTH_GATE=false \
K6_POSTGRES_RECOVERY_GATE=false \
K6_POSTGRES_EXPORTER_STABLE_GATE=false \
K6_EXPLAIN_SNAPSHOT=false \
K6_AUTH_TOKEN="${EC2_LOADTEST_AUTH_TOKEN:-}" \
K6_HOT_ACCOUNT_ID="${EC2_K6_HOT_ACCOUNT_ID:-}" \
K6_HOT_FROM="${EC2_K6_HOT_FROM:-}" \
K6_HOT_TO="${EC2_K6_HOT_TO:-}" \
K6_COLD_ACCOUNT_ID="${EC2_K6_COLD_ACCOUNT_ID:-}" \
K6_COLD_FROM="${EC2_K6_COLD_FROM:-}" \
K6_COLD_TO="${EC2_K6_COLD_TO:-}" \
K6_VUS="${EC2_K6_VUS:-8}" \
K6_DURATION="${EC2_K6_DURATION:-1m}" \
K6_SCENARIO_MODE="${EC2_K6_SCENARIO_MODE:-constant-vus}" \
  tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps
