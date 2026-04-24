#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-k6-transaction-100m-loadtest.sh [--print-plan|--no-up]

Required environment:
  K6_HOT_ACCOUNT_ID
  K6_HOT_FROM
  K6_HOT_TO
  K6_COLD_ACCOUNT_ID
  K6_COLD_FROM
  K6_COLD_TO

Optional environment:
  K6_REPORT_NAME       default transaction-100m-<timestamp>
  K6_VUS               default 8
  K6_DURATION          default 1m
  K6_LIMIT             default 50
  K6_AUTH_TOKEN        bearer token, optional when bootstrap header auth is enabled
  K6_ARCHIVE_RESULTS   copy markdown summary to docs/performance-results, default true

Examples:
  K6_HOT_ACCOUNT_ID=101 K6_HOT_FROM=2026-04-01T00:00:00Z K6_HOT_TO=2026-04-30T00:00:00Z \
  K6_COLD_ACCOUNT_ID=202 K6_COLD_FROM=2026-01-01T00:00:00Z K6_COLD_TO=2026-01-31T00:00:00Z \
    tools/test/run-k6-transaction-100m-loadtest.sh
USAGE
}

require_env() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "${value}" ]]; then
    echo "${name} is required" >&2
    exit 1
  fi
}

require_positive_integer() {
  local name="$1"
  local value="${!name:-}"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer" >&2
    exit 1
  fi
}

mode="run"
if [[ "${1:-}" == "--print-plan" ]]; then
  mode="print-plan"
  shift
elif [[ "${1:-}" == "--no-up" ]]; then
  mode="no-up"
  shift
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "$#" -ne 0 ]]; then
  usage
  exit 1
fi

K6_VUS="${K6_VUS:-8}"
K6_LIMIT="${K6_LIMIT:-50}"
K6_ARCHIVE_RESULTS="${K6_ARCHIVE_RESULTS:-true}"
K6_REPORT_NAME="${K6_REPORT_NAME:-transaction-100m-$(date +%Y-%m-%d-%H%M%S)}"
export K6_VUS K6_LIMIT K6_ARCHIVE_RESULTS K6_REPORT_NAME

require_positive_integer K6_VUS
require_positive_integer K6_LIMIT

compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
report_dir="build/reports/k6"
summary_md="${report_dir}/${K6_REPORT_NAME}-summary.md"
summary_json="${report_dir}/${K6_REPORT_NAME}-summary.json"

print_plan() {
  echo "[k6-transaction-100m] compose files: ${compose_files[*]}"
  echo "[k6-transaction-100m] backend: aquila-bank-backend:8080 with t3.micro budget"
  echo "[k6-transaction-100m] observability: prometheus:9090 grafana:3000 alertmanager:9093 postgres-exporter:9187"
  echo "[k6-transaction-100m] k6 report name: ${K6_REPORT_NAME}"
  echo "[k6-transaction-100m] k6 vus=${K6_VUS} duration=${K6_DURATION:-1m} limit=${K6_LIMIT}"
  echo "[k6-transaction-100m] required dataset: prepared 100m transaction read model hot/cold accounts"
  echo "[k6-transaction-100m] archive results: ${K6_ARCHIVE_RESULTS}"
}

print_plan

if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

require_env K6_HOT_ACCOUNT_ID
require_env K6_HOT_FROM
require_env K6_HOT_TO
require_env K6_COLD_ACCOUNT_ID
require_env K6_COLD_FROM
require_env K6_COLD_TO

mkdir -p "${report_dir}"

if [[ "${mode}" != "no-up" ]]; then
  echo "[k6-transaction-100m] building backend bootJar"
  tools/test/with-resource-lock.sh back-gradle-loadtest-bootjar ./back/gradlew -p back bootJar

  echo "[k6-transaction-100m] starting loadtest services"
  docker compose "${compose_files[@]}" --profile loadtest up -d \
    postgres aquila-bank-backend prometheus grafana alertmanager postgres-exporter
fi

set +e
docker compose "${compose_files[@]}" --profile loadtest run --rm \
  -e K6_REPORT_NAME="${K6_REPORT_NAME}" \
  -e K6_HOT_ACCOUNT_ID="${K6_HOT_ACCOUNT_ID}" \
  -e K6_HOT_FROM="${K6_HOT_FROM}" \
  -e K6_HOT_TO="${K6_HOT_TO}" \
  -e K6_COLD_ACCOUNT_ID="${K6_COLD_ACCOUNT_ID}" \
  -e K6_COLD_FROM="${K6_COLD_FROM}" \
  -e K6_COLD_TO="${K6_COLD_TO}" \
  -e K6_AUTH_TOKEN="${K6_AUTH_TOKEN:-}" \
  -e K6_VUS="${K6_VUS}" \
  -e K6_DURATION="${K6_DURATION:-1m}" \
  -e K6_LIMIT="${K6_LIMIT}" \
  k6-transaction-read-100m
status=$?
set -e

if [[ "${K6_ARCHIVE_RESULTS}" == "true" && -f "${summary_md}" ]]; then
  tools/test/archive-k6-transaction-100m-result.sh "${summary_md}" "${summary_json}"
elif [[ "${K6_ARCHIVE_RESULTS}" == "true" ]]; then
  echo "k6 summary markdown was not produced: ${summary_md}" >&2
fi

exit "${status}"
