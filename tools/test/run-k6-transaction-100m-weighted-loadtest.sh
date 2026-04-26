#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-k6-transaction-100m-weighted-loadtest.sh [--print-plan|--no-up] [--no-deps]

Required runtime environment:
  K6_HOT_ACCOUNT_ID K6_HOT_FROM K6_HOT_TO
  K6_COLD_ACCOUNT_ID K6_COLD_FROM K6_COLD_TO

Optional environment:
  K6_REPORT_NAME default transaction-100m-weighted-<timestamp>
  K6_VUS default 8
  K6_DURATION default 1m
  K6_LIMIT default 50
  K6_WEIGHT_HOT_FIRST default 45
  K6_WEIGHT_HOT_CURSOR default 25
  K6_WEIGHT_COLD_FIRST default 15
  K6_WEIGHT_COLD_CURSOR default 10
  K6_WEIGHT_DETAIL default 0, synthetic 100m fixture usually has no detail rows
USAGE
}

mode="run"
run_dependencies="true"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan) mode="print-plan" ;;
    --no-up) mode="no-up" ;;
    --no-deps) run_dependencies="false" ;;
    -h|--help) usage; exit 0 ;;
    *) usage; exit 1 ;;
  esac
  shift
done

K6_REPORT_NAME="${K6_REPORT_NAME:-transaction-100m-weighted-$(date +%Y-%m-%d-%H%M%S)}"
K6_VUS="${K6_VUS:-8}"
K6_DURATION="${K6_DURATION:-1m}"
K6_LIMIT="${K6_LIMIT:-50}"
K6_WEIGHT_HOT_FIRST="${K6_WEIGHT_HOT_FIRST:-45}"
K6_WEIGHT_HOT_CURSOR="${K6_WEIGHT_HOT_CURSOR:-25}"
K6_WEIGHT_COLD_FIRST="${K6_WEIGHT_COLD_FIRST:-15}"
K6_WEIGHT_COLD_CURSOR="${K6_WEIGHT_COLD_CURSOR:-10}"
K6_WEIGHT_DETAIL="${K6_WEIGHT_DETAIL:-0}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
script_path="/scripts/transaction-read-100m-weighted.js"

require_positive_integer() {
  local name="$1"
  local value="${!name:-}"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer" >&2
    exit 1
  fi
}

require_non_negative_integer() {
  local name="$1"
  local value="${!name:-}"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${name} must be zero or a positive integer" >&2
    exit 1
  fi
}

require_env() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "${value}" ]]; then
    echo "${name} is required" >&2
    exit 1
  fi
}

require_positive_integer K6_VUS
require_positive_integer K6_LIMIT
require_non_negative_integer K6_WEIGHT_HOT_FIRST
require_non_negative_integer K6_WEIGHT_HOT_CURSOR
require_non_negative_integer K6_WEIGHT_COLD_FIRST
require_non_negative_integer K6_WEIGHT_COLD_CURSOR
require_non_negative_integer K6_WEIGHT_DETAIL

echo "[k6-transaction-100m-weighted] weighted report name: ${K6_REPORT_NAME}"
echo "[k6-transaction-100m-weighted] vus=${K6_VUS} duration=${K6_DURATION} limit=${K6_LIMIT}"
echo "[k6-transaction-100m-weighted] weights hot_first=${K6_WEIGHT_HOT_FIRST} hot_cursor=${K6_WEIGHT_HOT_CURSOR} cold_first=${K6_WEIGHT_COLD_FIRST} cold_cursor=${K6_WEIGHT_COLD_CURSOR} detail=${K6_WEIGHT_DETAIL}"
echo "[k6-transaction-100m-weighted] script=${script_path}"
if [[ "${run_dependencies}" == "true" ]]; then
  echo "[k6-transaction-100m-weighted] dependencies=compose-default"
else
  echo "[k6-transaction-100m-weighted] dependencies=no-deps"
fi

if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

require_env K6_HOT_ACCOUNT_ID
require_env K6_HOT_FROM
require_env K6_HOT_TO
require_env K6_COLD_ACCOUNT_ID
require_env K6_COLD_FROM
require_env K6_COLD_TO

if [[ "${mode}" != "no-up" ]]; then
  tools/test/with-resource-lock.sh back-gradle-loadtest-bootjar ./back/gradlew -p back bootJar
  docker compose "${compose_files[@]}" --profile loadtest up -d \
    postgres aquila-bank-backend prometheus grafana alertmanager postgres-exporter
fi

run_args=(--rm)
if [[ "${run_dependencies}" != "true" ]]; then
  run_args+=(--no-deps)
fi
docker compose "${compose_files[@]}" --profile loadtest run "${run_args[@]}" \
  -e K6_REPORT_NAME="${K6_REPORT_NAME}" \
  -e K6_HOT_ACCOUNT_ID="${K6_HOT_ACCOUNT_ID}" \
  -e K6_HOT_FROM="${K6_HOT_FROM}" \
  -e K6_HOT_TO="${K6_HOT_TO}" \
  -e K6_COLD_ACCOUNT_ID="${K6_COLD_ACCOUNT_ID}" \
  -e K6_COLD_FROM="${K6_COLD_FROM}" \
  -e K6_COLD_TO="${K6_COLD_TO}" \
  -e K6_AUTH_TOKEN="${K6_AUTH_TOKEN:-}" \
  -e K6_VUS="${K6_VUS}" \
  -e K6_DURATION="${K6_DURATION}" \
  -e K6_LIMIT="${K6_LIMIT}" \
  -e K6_WEIGHT_HOT_FIRST="${K6_WEIGHT_HOT_FIRST}" \
  -e K6_WEIGHT_HOT_CURSOR="${K6_WEIGHT_HOT_CURSOR}" \
  -e K6_WEIGHT_COLD_FIRST="${K6_WEIGHT_COLD_FIRST}" \
  -e K6_WEIGHT_COLD_CURSOR="${K6_WEIGHT_COLD_CURSOR}" \
  -e K6_WEIGHT_DETAIL="${K6_WEIGHT_DETAIL}" \
  k6-transaction-read-100m \
  run --out experimental-prometheus-rw "${script_path}"
