#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-burst-429-budget-gate.sh [--print-plan]

Environment:
  BURST_429_GATE_NAME       default transaction-read-burst-429-<timestamp>
  BURST_429_SUMMARY_JSON    required unless BURST_429_RUN_K6=true
  BURST_429_OUTPUT_DIR      default build/reports/k6/<gate>
  BURST_429_BURST_RATE      default 256
  BURST_429_BURST_DURATION  default 20s
  BURST_429_PRE_ALLOCATED_VUS default BURST_429_BURST_RATE
  BURST_429_MAX_VUS         default BURST_429_BURST_RATE*2
  BURST_429_MAX_RETRY_AFTER_SLEEP_SECONDS default 1
  BURST_429_WARN_RATE       default 0.001
  BURST_429_FAIL_RATE       default 0.005
  BURST_429_EXPECTED_RATE   default 0
  BURST_429_RUN_K6          true|false, default false
  BURST_429_K6_DURATION     default 30s

Required when BURST_429_RUN_K6=true:
  K6_HOT_ACCOUNT_ID K6_HOT_FROM K6_HOT_TO K6_COLD_ACCOUNT_ID K6_COLD_FROM K6_COLD_TO
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
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

gate_name="${BURST_429_GATE_NAME:-transaction-read-burst-429-$(date +%Y-%m-%d-%H%M%S)}"
summary_json="${BURST_429_SUMMARY_JSON:-}"
output_dir="${BURST_429_OUTPUT_DIR:-build/reports/k6/${gate_name}}"
burst_rate="${BURST_429_BURST_RATE:-256}"
burst_duration="${BURST_429_BURST_DURATION:-20s}"
pre_allocated_vus="${BURST_429_PRE_ALLOCATED_VUS:-}"
max_vus="${BURST_429_MAX_VUS:-}"
max_retry_after_sleep_seconds="${BURST_429_MAX_RETRY_AFTER_SLEEP_SECONDS:-1}"
warn_rate="${BURST_429_WARN_RATE:-0.001}"
fail_rate="${BURST_429_FAIL_RATE:-0.005}"
expected_rate="${BURST_429_EXPECTED_RATE:-0}"
run_k6="${BURST_429_RUN_K6:-false}"
k6_duration="${BURST_429_K6_DURATION:-30s}"
result_tsv="${output_dir}/${gate_name}-burst-429-budget.tsv"
report_md="${output_dir}/${gate_name}-burst-429-budget.md"
k6_report_name="${gate_name}-k6"
generated_summary_json="build/reports/k6/${k6_report_name}-summary.json"
k6_runner="tools/test/run-k6-transaction-100m-loadtest.sh"

require_bool_value() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_rate_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value >= 0 && value <= 1) }' || {
    echo "${name} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  }
}

require_non_negative_number_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be zero or greater: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value >= 0) }' || {
    echo "${name} must be zero or greater: ${value}" >&2
    exit 1
  }
}

require_positive_integer_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_duration_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*(s|m|h)$ ]]; then
    echo "${name} must use a positive duration such as 20s, 1m, or 1h: ${value}" >&2
    exit 1
  fi
}

number_greater_than() {
  awk -v value="$1" -v threshold="$2" 'BEGIN { exit !(value > threshold) }'
}

require_bool_value "BURST_429_RUN_K6" "${run_k6}"
require_positive_integer_value "BURST_429_BURST_RATE" "${burst_rate}"
pre_allocated_vus="${pre_allocated_vus:-${burst_rate}}"
max_vus="${max_vus:-$((burst_rate * 2))}"
require_positive_integer_value "BURST_429_PRE_ALLOCATED_VUS" "${pre_allocated_vus}"
require_positive_integer_value "BURST_429_MAX_VUS" "${max_vus}"
require_non_negative_number_value "BURST_429_MAX_RETRY_AFTER_SLEEP_SECONDS" "${max_retry_after_sleep_seconds}"
require_duration_value "BURST_429_BURST_DURATION" "${burst_duration}"
require_duration_value "BURST_429_K6_DURATION" "${k6_duration}"
require_rate_value "BURST_429_WARN_RATE" "${warn_rate}"
require_rate_value "BURST_429_FAIL_RATE" "${fail_rate}"
require_rate_value "BURST_429_EXPECTED_RATE" "${expected_rate}"
if number_greater_than "${warn_rate}" "${fail_rate}"; then
  echo "BURST_429_WARN_RATE must be less than or equal to BURST_429_FAIL_RATE" >&2
  exit 1
fi

print_plan() {
  echo "[transaction-read-burst-429] gate=${gate_name}"
  echo "[transaction-read-burst-429] summary_json=${summary_json:-missing}"
  echo "[transaction-read-burst-429] output_dir=${output_dir}"
  echo "[transaction-read-burst-429] burst_rate=${burst_rate}"
  echo "[transaction-read-burst-429] burst_duration=${burst_duration}"
  echo "[transaction-read-burst-429] expected_rate=${expected_rate}"
  echo "[transaction-read-burst-429] warn_rate=${warn_rate}"
  echo "[transaction-read-burst-429] fail_rate=${fail_rate}"
  echo "[transaction-read-burst-429] pre_allocated_vus=${pre_allocated_vus}"
  echo "[transaction-read-burst-429] max_vus=${max_vus}"
  echo "[transaction-read-burst-429] max_retry_after_sleep_seconds=${max_retry_after_sleep_seconds}"
  echo "[transaction-read-burst-429] run_k6=${run_k6}"
  echo "[transaction-read-burst-429] k6_command=K6_SCENARIO_MODE=burst K6_BURST_RATE=${burst_rate} K6_PRE_ALLOCATED_VUS=${pre_allocated_vus} K6_MAX_VUS=${max_vus} K6_MAX_RETRY_AFTER_SLEEP_SECONDS=${max_retry_after_sleep_seconds} ${k6_runner}"
  echo "[transaction-read-burst-429] result_tsv=${result_tsv}"
  echo "[transaction-read-burst-429] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ "${run_k6}" == "true" ]]; then
  K6_REPORT_NAME="${k6_report_name}" \
  K6_SCENARIO_MODE=burst \
  K6_BURST_RATE="${burst_rate}" \
  K6_BURST_DURATION="${burst_duration}" \
  K6_PRE_ALLOCATED_VUS="${pre_allocated_vus}" \
  K6_MAX_VUS="${max_vus}" \
  K6_DURATION="${k6_duration}" \
  K6_OVERLOAD_MODE=true \
  K6_BURST_429_RATE_THRESHOLD="${fail_rate}" \
  K6_MAX_RETRY_AFTER_SLEEP_SECONDS="${max_retry_after_sleep_seconds}" \
  K6_ARCHIVE_RESULTS=false \
    "${k6_runner}"
  summary_json="${generated_summary_json}"
fi

if [[ -z "${summary_json}" || ! -s "${summary_json}" ]]; then
  echo "BURST_429_SUMMARY_JSON is required: ${summary_json:-missing}" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

metric_value() {
  local metric="$1"
  local field="$2"
  jq -r --arg metric "${metric}" --arg field "${field}" \
    '.metrics[$metric].values[$field] // "0"' "${summary_json}"
}

status_for_rate() {
  local value="$1"
  if number_greater_than "${value}" "${fail_rate}"; then
    echo "fail"
  elif number_greater_than "${value}" "${warn_rate}"; then
    echo "warn"
  else
    echo "pass"
  fi
}

status_for_zero() {
  local value="$1"
  if number_greater_than "${value}" "0"; then
    echo "fail"
  else
    echo "pass"
  fi
}

mkdir -p "${output_dir}"

transaction_429_rate="$(metric_value aquila_transaction_429_rate rate)"
transaction_503_rate="$(metric_value aquila_transaction_503_rate rate)"
transaction_503_count="$(metric_value aquila_transaction_503_count count)"
dropped_iterations="$(metric_value dropped_iterations count)"
interrupted_iterations="$(metric_value interrupted_iterations count)"
http_failed_rate="$(metric_value http_req_failed rate)"
http_reqs="$(metric_value http_reqs count)"
transaction_429_status="$(status_for_rate "${transaction_429_rate}")"
dropped_status="$(status_for_zero "${dropped_iterations}")"
interrupted_status="$(status_for_zero "${interrupted_iterations}")"
generator_headroom_status="pass"
if [[ "${dropped_status}" == "fail" || "${interrupted_status}" == "fail" ]]; then
  generator_headroom_status="fail"
fi
status="pass"
case "${transaction_429_status}:${dropped_status}:${interrupted_status}" in
  *fail*) status="fail" ;;
  *warn*) status="warn" ;;
esac
if number_greater_than "${transaction_503_rate}" "0" || number_greater_than "${transaction_503_count}" "0"; then
  status="fail"
fi

{
  printf "metric\tvalue\twarn_threshold\tfail_threshold\tstatus\n"
  printf "transaction_429_rate\t%s\t%s\t%s\t%s\n" "${transaction_429_rate}" "${warn_rate}" "${fail_rate}" "${transaction_429_status}"
  printf "transaction_503_rate\t%s\t0\t0\t%s\n" "${transaction_503_rate}" "$(status_for_zero "${transaction_503_rate}")"
  printf "transaction_503_count\t%s\t0\t0\t%s\n" "${transaction_503_count}" "$(status_for_zero "${transaction_503_count}")"
  printf "dropped_iterations\t%s\t0\t0\t%s\n" "${dropped_iterations}" "${dropped_status}"
  printf "interrupted_iterations\t%s\t0\t0\t%s\n" "${interrupted_iterations}" "${interrupted_status}"
  printf "generator_headroom_status\t%s\tn/a\tn/a\t%s\n" "${generator_headroom_status}" "${generator_headroom_status}"
  printf "http_failed_rate\t%s\tn/a\tn/a\tobserve\n" "${http_failed_rate}"
  printf "http_reqs\t%s\tn/a\tn/a\tobserve\n" "${http_reqs}"
} >"${result_tsv}"

cat >"${report_md}" <<REPORT
# Transaction Read Burst 429 Budget

## Summary

- gate: ${gate_name}
- gate_status=${status}
- burst rate: ${burst_rate}/s
- burst duration: ${burst_duration}
- preAllocated VUs: ${pre_allocated_vus}
- max VUs: ${max_vus}
- max Retry-After sleep seconds: ${max_retry_after_sleep_seconds}
- generator headroom status: ${generator_headroom_status}
- expected 429 rate: ${expected_rate}
- warning threshold: ${warn_rate}
- fail threshold: ${fail_rate}
- transaction 429 rate: ${transaction_429_rate}
- transaction 503 rate: ${transaction_503_rate}
- dropped iterations: ${dropped_iterations}
- interrupted iterations: ${interrupted_iterations}

## Artifacts

- result TSV: ${result_tsv}
- summary JSON: ${summary_json}

## Notes

- warning은 generator/server 변동성을 고려한 회귀 관측 신호입니다.
- fail threshold, 503, dropped/interrupted iteration은 non-zero exit로 처리합니다.
REPORT

echo "${report_md}"

if [[ "${status}" == "fail" ]]; then
  echo "burst 429 budget failed: rate=${transaction_429_rate} fail=${fail_rate}" >&2
  exit 1
fi
