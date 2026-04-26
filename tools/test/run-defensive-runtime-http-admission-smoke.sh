#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-defensive-runtime-http-admission-smoke.sh [--print-plan|--dry-run]

Environment:
  ADMISSION_NAME             default defensive-http-admission-<timestamp>
  ADMISSION_BASE_URL         default http://localhost:8080
  ADMISSION_REQUESTS         default 16
  ADMISSION_CONCURRENCY      default 8
  ADMISSION_EXPECT_429       default true
  ADMISSION_MAX_FAILED_RATE  default 0
  ADMISSION_ACCOUNT_ID       default 910000001
  ADMISSION_FROM             default 2026-04-01T00:00:00Z
  ADMISSION_TO               default 2026-04-30T00:00:00Z
  ADMISSION_LIMIT            default 50
  ADMISSION_AUTH_TOKEN       optional bearer token, not printed

Examples:
  tools/test/run-defensive-runtime-http-admission-smoke.sh --print-plan
  tools/test/run-defensive-runtime-http-admission-smoke.sh --dry-run
  ADMISSION_BASE_URL=http://localhost:8080 tools/test/run-defensive-runtime-http-admission-smoke.sh
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

require_rate_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${key} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value >= 0 && value <= 1) }' \
    || {
      echo "${key} must be a rate between 0 and 1: ${value}" >&2
      exit 1
    }
}

number_greater_than() {
  local value="$1"
  local threshold="$2"
  awk -v value="${value}" -v threshold="${threshold}" 'BEGIN { exit !(value > threshold) }'
}

name="${ADMISSION_NAME:-defensive-http-admission-$(date +%Y-%m-%d-%H%M%S)}"
base_url="${ADMISSION_BASE_URL:-http://localhost:8080}"
base_url="${base_url%/}"
requests="${ADMISSION_REQUESTS:-16}"
concurrency="${ADMISSION_CONCURRENCY:-8}"
expect_429="${ADMISSION_EXPECT_429:-true}"
max_failed_rate="${ADMISSION_MAX_FAILED_RATE:-0}"
account_id="${ADMISSION_ACCOUNT_ID:-910000001}"
from="${ADMISSION_FROM:-2026-04-01T00:00:00Z}"
to="${ADMISSION_TO:-2026-04-30T00:00:00Z}"
limit="${ADMISSION_LIMIT:-50}"
auth_token="${ADMISSION_AUTH_TOKEN:-}"
report_dir="build/reports/admission/${name}"
raw_tsv="${report_dir}/http-admission-raw.tsv"
summary_tsv="${report_dir}/http-admission-summary.tsv"
request_dir="${report_dir}/requests"

require_positive_integer_value "ADMISSION_REQUESTS" "${requests}"
require_positive_integer_value "ADMISSION_CONCURRENCY" "${concurrency}"
require_positive_integer_value "ADMISSION_ACCOUNT_ID" "${account_id}"
require_positive_integer_value "ADMISSION_LIMIT" "${limit}"
require_bool_value "ADMISSION_EXPECT_429" "${expect_429}"
require_rate_value "ADMISSION_MAX_FAILED_RATE" "${max_failed_rate}"

print_plan() {
  echo "[defensive-http-admission] name=${name}"
  echo "[defensive-http-admission] base_url=${base_url}"
  echo "[defensive-http-admission] requests=${requests}"
  echo "[defensive-http-admission] concurrency=${concurrency}"
  echo "[defensive-http-admission] expect_429=${expect_429}"
  echo "[defensive-http-admission] max_failed_rate=${max_failed_rate}"
  echo "[defensive-http-admission] account=${account_id} window=${from}..${to} limit=${limit}"
  echo "[defensive-http-admission] summary=${summary_tsv}"
}

print_dry_run() {
  echo "curl --get ${base_url}/api/v1/transactions -H 'X-Account-Id: ${account_id}' -H 'X-Subject: admission-smoke' -H 'Authorization: Bearer ***' --data-urlencode accountId=${account_id} --data-urlencode from=${from} --data-urlencode to=${to} --data-urlencode limit=${limit}"
}

run_request() {
  local request_id="$1"
  local header_path="${request_dir}/${request_id}.headers"
  local result_path="${request_dir}/${request_id}.tsv"
  local error_path="${request_dir}/${request_id}.err"
  local curl_output status duration retry_after
  local curl_args

  curl_args=(
    -sS
    -o /dev/null
    -D "${header_path}"
    -w "%{http_code}\t%{time_total}"
    --get "${base_url}/api/v1/transactions"
    -H "X-Account-Id: ${account_id}"
    -H "X-Subject: admission-smoke"
    --data-urlencode "accountId=${account_id}"
    --data-urlencode "from=${from}"
    --data-urlencode "to=${to}"
    --data-urlencode "limit=${limit}"
  )
  if [[ -n "${auth_token}" ]]; then
    curl_args+=(-H "Authorization: Bearer ${auth_token}")
  fi

  if curl_output="$(curl "${curl_args[@]}" 2>"${error_path}")"; then
    status="${curl_output%%$'\t'*}"
    duration="${curl_output#*$'\t'}"
  else
    status="000"
    duration="0"
  fi
  retry_after="$(awk 'BEGIN {IGNORECASE=1} /^Retry-After:/ {gsub("\r", "", $2); print $2}' "${header_path}" 2>/dev/null | tail -1)"
  printf "%s\t%s\t%s\t%s\n" "${request_id}" "${status}" "${duration}" "${retry_after}" >"${result_path}"
  return 0
}

run_requests() {
  mkdir -p "${request_dir}"
  local active=0
  local request_id
  for request_id in $(seq 1 "${requests}"); do
    run_request "${request_id}" &
    active=$((active + 1))
    if ((active >= concurrency)); then
      wait
      active=0
    fi
  done
  wait
}

write_raw() {
  printf "request_id\tstatus\tduration_seconds\tretry_after\n" >"${raw_tsv}"
  local request_id
  for request_id in $(seq 1 "${requests}"); do
    cat "${request_dir}/${request_id}.tsv" >>"${raw_tsv}"
  done
}

write_summary() {
  local success_count rejected_count failed_count retry_after_count failed_rate
  success_count="$(awk -F '\t' 'NR > 1 && $2 ~ /^2/ {count++} END {print count + 0}' "${raw_tsv}")"
  rejected_count="$(awk -F '\t' 'NR > 1 && $2 == "429" {count++} END {print count + 0}' "${raw_tsv}")"
  failed_count="$(awk -F '\t' 'NR > 1 && $2 !~ /^2/ && $2 != "429" {count++} END {print count + 0}' "${raw_tsv}")"
  retry_after_count="$(awk -F '\t' 'NR > 1 && $4 != "" {count++} END {print count + 0}' "${raw_tsv}")"
  failed_rate="$(awk -v failed="${failed_count}" -v total="${requests}" 'BEGIN {printf "%.6f", failed / total}')"

  {
    printf "requests\tsuccess_count\trejected_count\tfailed_count\tfailed_rate\tretry_after_count\traw_path\n"
    printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
      "${requests}" "${success_count}" "${rejected_count}" "${failed_count}" "${failed_rate}" "${retry_after_count}" "${raw_tsv}"
  } >"${summary_tsv}"

  echo "[defensive-http-admission] summary=${summary_tsv}"
  if [[ "${expect_429}" == "true" && "${rejected_count}" -eq 0 ]]; then
    echo "expected at least one HTTP 429 admission rejection, but rejected_count=0" >&2
    exit 1
  fi
  if number_greater_than "${failed_rate}" "${max_failed_rate}"; then
    echo "failed_rate exceeded threshold: failed_rate=${failed_rate} max=${max_failed_rate}" >&2
    exit 1
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

mkdir -p "${report_dir}"
run_requests
write_raw
write_summary
