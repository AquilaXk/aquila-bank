#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-48-64-capacity-gate.sh [--print-plan]

Environment:
  CAPACITY_48_64_GATE_NAME                  default transaction-read-48-64-capacity-<timestamp>
  CAPACITY_48_64_LOWER_ANCHOR_RATE         default 32
  CAPACITY_48_64_RATES                     default 32,48,64,80,96
  CAPACITY_48_64_DURATION                  default 20s
  CAPACITY_48_64_WARN_RATE                 default 0.08
  CAPACITY_48_64_FAIL_RATE                 default 0.10
  CAPACITY_48_64_RUN_K6                    true|false, default false
  CAPACITY_48_64_SUMMARY_DIR               required when CAPACITY_48_64_RUN_K6=false
  CAPACITY_48_64_OUTPUT_DIR                default build/reports/k6/<gate>
  CAPACITY_48_64_MAX_RETRY_AFTER_SLEEP_SECONDS default 1

Summary input when CAPACITY_48_64_RUN_K6=false:
  ${CAPACITY_48_64_SUMMARY_DIR}/rate-52-summary.json
  ${CAPACITY_48_64_SUMMARY_DIR}/rate-56-summary.json
  ${CAPACITY_48_64_SUMMARY_DIR}/rate-60-summary.json
  ${CAPACITY_48_64_SUMMARY_DIR}/rate-64-summary.json
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

gate_name="${CAPACITY_48_64_GATE_NAME:-transaction-read-burst-reject-curve-$(date +%Y-%m-%d-%H%M%S)}"
lower_anchor_rate="${CAPACITY_48_64_LOWER_ANCHOR_RATE:-32}"
rates="${CAPACITY_48_64_RATES:-32,48,64,80,96}"
duration="${CAPACITY_48_64_DURATION:-20s}"
warn_rate="${CAPACITY_48_64_WARN_RATE:-0.08}"
fail_rate="${CAPACITY_48_64_FAIL_RATE:-0.10}"
run_k6="${CAPACITY_48_64_RUN_K6:-false}"
summary_dir="${CAPACITY_48_64_SUMMARY_DIR:-}"
output_dir="${CAPACITY_48_64_OUTPUT_DIR:-build/reports/k6/${gate_name}}"
max_retry_after_sleep_seconds="${CAPACITY_48_64_MAX_RETRY_AFTER_SLEEP_SECONDS:-1}"
summary_tsv="${output_dir}/${gate_name}-burst-reject-curve.tsv"
report_md="${output_dir}/${gate_name}-burst-reject-curve.md"
single_gate="tools/test/run-transaction-read-burst-429-budget-gate.sh"

require_positive_integer_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_csv_positive_integers() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "${name} must not be empty" >&2
    exit 1
  fi
  IFS=',' read -r -a items <<<"${value}"
  local item
  for item in "${items[@]}"; do
    require_positive_integer_value "${name}" "${item}"
  done
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

require_bool_value() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
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

is_numeric_value() {
  [[ "$1" =~ ^[0-9]+([.][0-9]+)?$ ]]
}

max_numeric_value() {
  local result="0"
  local value
  for value in "$@"; do
    if is_numeric_value "${value}" && number_greater_than "${value}" "${result}"; then
      result="${value}"
    fi
  done
  echo "${result}"
}

metric_value() {
  local summary_json="$1"
  local metric="$2"
  local field="$3"
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

require_positive_integer_value "CAPACITY_48_64_LOWER_ANCHOR_RATE" "${lower_anchor_rate}"
require_csv_positive_integers "CAPACITY_48_64_RATES" "${rates}"
require_duration_value "CAPACITY_48_64_DURATION" "${duration}"
require_rate_value "CAPACITY_48_64_WARN_RATE" "${warn_rate}"
require_rate_value "CAPACITY_48_64_FAIL_RATE" "${fail_rate}"
require_bool_value "CAPACITY_48_64_RUN_K6" "${run_k6}"
require_non_negative_number_value "CAPACITY_48_64_MAX_RETRY_AFTER_SLEEP_SECONDS" "${max_retry_after_sleep_seconds}"
if number_greater_than "${warn_rate}" "${fail_rate}"; then
  echo "CAPACITY_48_64_WARN_RATE must be less than or equal to CAPACITY_48_64_FAIL_RATE" >&2
  exit 1
fi

print_plan() {
  echo "[transaction-read-48-64-capacity] gate=${gate_name}"
  echo "[transaction-read-48-64-capacity] lower_anchor_rate=${lower_anchor_rate}"
  echo "[transaction-read-48-64-capacity] rates=${rates}"
  echo "[transaction-read-48-64-capacity] duration=${duration}"
  echo "[transaction-read-48-64-capacity] warn_rate=${warn_rate}"
  echo "[transaction-read-48-64-capacity] fail_rate=${fail_rate}"
  echo "[transaction-read-48-64-capacity] run_k6=${run_k6}"
  echo "[transaction-read-48-64-capacity] summary_dir=${summary_dir:-missing}"
  echo "[transaction-read-48-64-capacity] output_dir=${output_dir}"
  echo "[transaction-read-48-64-capacity] k6_command=K6_SCENARIO_MODE=burst K6_BURST_RATE=<rate> ${single_gate}"
  echo "[transaction-read-48-64-capacity] k6_headroom=K6_PRE_ALLOCATED_VUS=<rate> K6_MAX_VUS=<rate*2> K6_MAX_RETRY_AFTER_SLEEP_SECONDS=${max_retry_after_sleep_seconds}"
  echo "[transaction-read-48-64-capacity] summary_tsv=${summary_tsv}"
  echo "[transaction-read-48-64-capacity] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ ! -x "${single_gate}" ]]; then
  echo "single burst gate missing or not executable: ${single_gate}" >&2
  exit 1
fi
if [[ "${run_k6}" != "true" ]]; then
  if [[ -z "${summary_dir}" || ! -d "${summary_dir}" ]]; then
    echo "CAPACITY_48_64_SUMMARY_DIR is required when CAPACITY_48_64_RUN_K6=false" >&2
    exit 1
  fi
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
printf "rate\tstatus\ttransaction_429_rate\ttransaction_503_rate\ttransaction_503_count\thttp_req_duration_p95_ms\tfirst_p95_ms\tdeep_p95_ms\tretry_after_p95_ms\tdropped_iterations\tinterrupted_iterations\tgenerator_headroom_status\treport_md\tsummary_json\n" >"${summary_tsv}"

gate_status="pass"
stable_pass_rate="${lower_anchor_rate}"
max_non_fail_rate="${lower_anchor_rate}"
first_overload_rate="none"
summary_table=$'| rate | status | 429 rate | 503 rate | 503 count | http p95 ms | first p95 ms | deep p95 ms | retry-after p95 ms | generator headroom |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |'
IFS=',' read -r -a rate_items <<<"${rates}"
for rate in "${rate_items[@]}"; do
  single_name="${gate_name}-rate-${rate}"
  single_output_dir="${output_dir}/rate-${rate}"
  summary_json="${summary_dir%/}/rate-${rate}-summary.json"
  if [[ "${run_k6}" == "true" ]]; then
    summary_json="build/reports/k6/${single_name}-k6-summary.json"
  elif [[ ! -s "${summary_json}" ]]; then
    echo "capacity summary missing for rate ${rate}: ${summary_json}" >&2
    exit 1
  fi

  set +e
  BURST_429_GATE_NAME="${single_name}" \
  BURST_429_SUMMARY_JSON="${summary_json}" \
  BURST_429_OUTPUT_DIR="${single_output_dir}" \
  BURST_429_BURST_RATE="${rate}" \
  BURST_429_BURST_DURATION="${duration}" \
  BURST_429_WARN_RATE="${warn_rate}" \
  BURST_429_FAIL_RATE="${fail_rate}" \
  BURST_429_RUN_K6="${run_k6}" \
  BURST_429_MAX_RETRY_AFTER_SLEEP_SECONDS="${max_retry_after_sleep_seconds}" \
    "${single_gate}" >/dev/null
  single_status=$?
  set -e

  if [[ "${run_k6}" == "true" ]]; then
    summary_json="build/reports/k6/${single_name}-k6-summary.json"
  fi

  transaction_429_rate="$(metric_value "${summary_json}" aquila_transaction_429_rate rate)"
  transaction_503_rate="$(metric_value "${summary_json}" aquila_transaction_503_rate rate)"
  transaction_503_count="$(metric_value "${summary_json}" aquila_transaction_503_count count)"
  http_req_duration_p95="$(metric_value "${summary_json}" http_req_duration "p(95)")"
  retry_after_p95="$(metric_value "${summary_json}" aquila_transaction_retry_after_sleep_ms "p(95)")"
  hot_first_p95="$(metric_value "${summary_json}" aquila_transaction_hot_first_ms "p(95)")"
  hot_cursor_p95="$(metric_value "${summary_json}" aquila_transaction_hot_cursor_ms "p(95)")"
  cold_first_p95="$(metric_value "${summary_json}" aquila_transaction_cold_first_ms "p(95)")"
  cold_cursor_p95="$(metric_value "${summary_json}" aquila_transaction_cold_cursor_ms "p(95)")"
  first_p95="$(max_numeric_value "${hot_first_p95}" "${cold_first_p95}")"
  deep_p95="$(max_numeric_value "${hot_cursor_p95}" "${cold_cursor_p95}")"
  dropped_iterations="$(metric_value "${summary_json}" dropped_iterations count)"
  interrupted_iterations="$(metric_value "${summary_json}" interrupted_iterations count)"
  generator_headroom_status="pass"
  if [[ "$(status_for_zero "${dropped_iterations}")" == "fail" || "$(status_for_zero "${interrupted_iterations}")" == "fail" ]]; then
    generator_headroom_status="fail"
  fi

  rate_status="$(status_for_rate "${transaction_429_rate}")"
  if (( rate > 64 )) && number_greater_than "${transaction_429_rate}" "${fail_rate}"; then
    rate_status="overload"
  fi
  if [[ "${generator_headroom_status}" == "fail" ]] \
      || number_greater_than "${transaction_503_rate}" "0" \
      || number_greater_than "${transaction_503_count}" "0"; then
    rate_status="fail"
  fi
  if (( rate <= 64 )) && [[ "${single_status}" -ne 0 ]]; then
    rate_status="fail"
  fi

  case "${rate_status}" in
    fail)
      gate_status="fail"
      ;;
    overload)
      if [[ "${first_overload_rate}" == "none" ]]; then
        first_overload_rate="${rate}"
      fi
      ;;
    warn)
      max_non_fail_rate="${rate}"
      ;;
    pass)
      stable_pass_rate="${rate}"
      max_non_fail_rate="${rate}"
      ;;
  esac

  single_report="${single_output_dir}/${single_name}-burst-429-budget.md"
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${rate}" "${rate_status}" "${transaction_429_rate}" "${transaction_503_rate}" \
    "${transaction_503_count}" "${http_req_duration_p95}" "${first_p95}" "${deep_p95}" \
    "${retry_after_p95}" "${dropped_iterations}" "${interrupted_iterations}" "${generator_headroom_status}" \
    "${single_report}" "${summary_json}" >>"${summary_tsv}"
  summary_table="${summary_table}"$'\n'"| ${rate} | ${rate_status} | ${transaction_429_rate} | ${transaction_503_rate} | ${transaction_503_count} | ${http_req_duration_p95} | ${first_p95} | ${deep_p95} | ${retry_after_p95} | ${generator_headroom_status} |"
done

cat >"${report_md}" <<REPORT
# Transaction Read Burst Reject Curve Gate

## Summary

- gate: ${gate_name}
- gate_status=${gate_status}
- lower_anchor_rate=${lower_anchor_rate}
- rates: ${rates}
- duration: ${duration}
- warning threshold: ${warn_rate}
- fail threshold: ${fail_rate}
- stable_pass_rate=${stable_pass_rate}
- max_non_fail_rate=${max_non_fail_rate}
- first_overload_rate=${first_overload_rate}

## Result Table

${summary_table}

## Artifacts

- summary TSV: ${summary_tsv}
- output dir: ${output_dir}

## Notes

- burst 32/48/64/80/96은 같은 evidence pack에 묶어 edge reject curve를 비교합니다.
- 64 이하에서 429 budget을 넘으면 hard fail이고, 80 이상은 overload curve로 기록하되 503/headroom 실패는 hard fail입니다.
- p95와 retry-after p95는 accepted latency와 client backoff 비용을 함께 해석할 수 있도록 report에 포함합니다.
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "48-64 capacity gate failed: ${summary_tsv}" >&2
  exit 1
fi
