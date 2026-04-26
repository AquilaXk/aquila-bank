#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-staging-rds-gp3-smoke.sh [--print-plan|--dry-run]

Required environment:
  STAGING_RDS_BASE_URL
  STAGING_RDS_HOT_ACCOUNT_ID
  STAGING_RDS_HOT_FROM
  STAGING_RDS_HOT_TO
  STAGING_RDS_COLD_ACCOUNT_ID
  STAGING_RDS_COLD_FROM
  STAGING_RDS_COLD_TO

Required for actual run:
  STAGING_RDS_CONFIRM=read-only-staging-rds

Optional environment:
  STAGING_RDS_REQUIRE_HTTPS     default true
  STAGING_RDS_ALLOW_LOCAL_URL   default false
  STAGING_RDS_AUTH_TOKEN        optional bearer token, not printed
  STAGING_RDS_REPORT_NAME       default transaction-100m-staging-rds-gp3-<timestamp>
  STAGING_RDS_VUS               default 4
  STAGING_RDS_DURATION          default 2m
  STAGING_RDS_LIMIT             default 50
  STAGING_RDS_ARCHIVE_RESULTS   default true

Examples:
  tools/test/run-transaction-100m-staging-rds-gp3-smoke.sh --print-plan
  STAGING_RDS_CONFIRM=read-only-staging-rds tools/test/run-transaction-100m-staging-rds-gp3-smoke.sh
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

require_env() {
  local key="$1"
  local value="${!key:-}"
  if [[ -z "${value}" ]]; then
    echo "${key} is required" >&2
    exit 1
  fi
}

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

require_env STAGING_RDS_BASE_URL
require_env STAGING_RDS_HOT_ACCOUNT_ID
require_env STAGING_RDS_HOT_FROM
require_env STAGING_RDS_HOT_TO
require_env STAGING_RDS_COLD_ACCOUNT_ID
require_env STAGING_RDS_COLD_FROM
require_env STAGING_RDS_COLD_TO

base_url="${STAGING_RDS_BASE_URL%/}"
hot_account_id="${STAGING_RDS_HOT_ACCOUNT_ID}"
hot_from="${STAGING_RDS_HOT_FROM}"
hot_to="${STAGING_RDS_HOT_TO}"
cold_account_id="${STAGING_RDS_COLD_ACCOUNT_ID}"
cold_from="${STAGING_RDS_COLD_FROM}"
cold_to="${STAGING_RDS_COLD_TO}"
require_https="${STAGING_RDS_REQUIRE_HTTPS:-true}"
allow_local_url="${STAGING_RDS_ALLOW_LOCAL_URL:-false}"
auth_token="${STAGING_RDS_AUTH_TOKEN:-}"
report_name="${STAGING_RDS_REPORT_NAME:-transaction-100m-staging-rds-gp3-$(date +%Y-%m-%d-%H%M%S)}"
vus="${STAGING_RDS_VUS:-4}"
duration="${STAGING_RDS_DURATION:-2m}"
limit="${STAGING_RDS_LIMIT:-50}"
archive_results="${STAGING_RDS_ARCHIVE_RESULTS:-true}"
report_dir="build/reports/k6"
summary_md="${report_dir}/${report_name}-summary.md"
summary_json="${report_dir}/${report_name}-summary.json"

require_bool_value "STAGING_RDS_REQUIRE_HTTPS" "${require_https}"
require_bool_value "STAGING_RDS_ALLOW_LOCAL_URL" "${allow_local_url}"
require_bool_value "STAGING_RDS_ARCHIVE_RESULTS" "${archive_results}"
require_positive_integer_value "STAGING_RDS_HOT_ACCOUNT_ID" "${hot_account_id}"
require_positive_integer_value "STAGING_RDS_COLD_ACCOUNT_ID" "${cold_account_id}"
require_positive_integer_value "STAGING_RDS_VUS" "${vus}"
require_positive_integer_value "STAGING_RDS_LIMIT" "${limit}"

if [[ "${hot_account_id}" == "${cold_account_id}" ]]; then
  echo "STAGING_RDS_HOT_ACCOUNT_ID and STAGING_RDS_COLD_ACCOUNT_ID must differ" >&2
  exit 1
fi
if [[ "${require_https}" == "true" && ! "${base_url}" =~ ^https:// ]]; then
  echo "STAGING_RDS_BASE_URL must use https unless STAGING_RDS_REQUIRE_HTTPS=false" >&2
  exit 1
fi
if [[ "${allow_local_url}" != "true" && "${base_url}" =~ ^https?://(localhost|127\.|0\.0\.0\.0|\[::1\]|::1) ]]; then
  echo "local URL is not allowed for staging RDS smoke unless STAGING_RDS_ALLOW_LOCAL_URL=true" >&2
  exit 1
fi

print_plan() {
  echo "[transaction-100m-staging-rds] base_url=${base_url}"
  echo "[transaction-100m-staging-rds] confirm=read-only-staging-rds required for run"
  echo "[transaction-100m-staging-rds] observability=summary-only"
  echo "[transaction-100m-staging-rds] generator=docker run grafana/k6:0.54.0"
  echo "[transaction-100m-staging-rds] report=${report_name}"
  echo "[transaction-100m-staging-rds] vus=${vus} duration=${duration} limit=${limit}"
  echo "[transaction-100m-staging-rds] hot account=${hot_account_id} window=${hot_from}..${hot_to}"
  echo "[transaction-100m-staging-rds] cold account=${cold_account_id} window=${cold_from}..${cold_to}"
  echo "[transaction-100m-staging-rds] archive_results=${archive_results}"
}

print_dry_run() {
  echo "docker run --rm -e BASE_URL=${base_url} -e K6_OBSERVABILITY_MODE=summary-only -e K6_REPORT_NAME=${report_name} -v $(pwd)/ops/k6:/scripts:ro -v $(pwd)/build/reports/k6:/reports grafana/k6:0.54.0 run /scripts/transaction-read-100m.js"
  echo "Authorization token is passed only through env when STAGING_RDS_AUTH_TOKEN is set"
}

run_k6() {
  mkdir -p "${report_dir}"
  docker run --rm \
    -e BASE_URL="${base_url}" \
    -e K6_OBSERVABILITY_MODE=summary-only \
    -e K6_REPORT_NAME="${report_name}" \
    -e K6_HOT_ACCOUNT_ID="${hot_account_id}" \
    -e K6_HOT_FROM="${hot_from}" \
    -e K6_HOT_TO="${hot_to}" \
    -e K6_COLD_ACCOUNT_ID="${cold_account_id}" \
    -e K6_COLD_FROM="${cold_from}" \
    -e K6_COLD_TO="${cold_to}" \
    -e K6_AUTH_TOKEN="${auth_token}" \
    -e K6_VUS="${vus}" \
    -e K6_DURATION="${duration}" \
    -e K6_LIMIT="${limit}" \
    -v "$(pwd)/ops/k6:/scripts:ro" \
    -v "$(pwd)/build/reports/k6:/reports" \
    grafana/k6:0.54.0 \
    run \
    /scripts/transaction-read-100m.js
}

archive_result() {
  if [[ "${archive_results}" != "true" ]]; then
    return 0
  fi
  if [[ -f "${summary_md}" ]]; then
    PERFORMANCE_RESULT_NAME="${report_name}" \
      tools/test/archive-k6-transaction-100m-result.sh "${summary_md}" "${summary_json}"
  else
    echo "k6 summary markdown was not produced: ${summary_md}" >&2
  fi
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_dry_run
  exit 0
fi
if [[ "${STAGING_RDS_CONFIRM:-}" != "read-only-staging-rds" ]]; then
  echo "STAGING_RDS_CONFIRM=read-only-staging-rds is required for staging RDS smoke" >&2
  exit 1
fi

set +e
run_k6
status=$?
set -e
archive_result
exit "${status}"
