#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-oci-public-api-arrival-capacity-gate.sh [--print-plan]

Environment:
  OCI_PUBLIC_ARRIVAL_GATE_NAME      default oci-public-arrival-capacity-<timestamp>
  OCI_PUBLIC_ARRIVAL_RATES          default 4,5,6,7,8,10
  OCI_PUBLIC_ARRIVAL_DURATION       default 1m
  OCI_PUBLIC_ARRIVAL_FAIL_RATE      default 0.10
  OCI_PUBLIC_ARRIVAL_DELAYED_FAIL_RATE default 0.25
  OCI_PUBLIC_ARRIVAL_ACCEPTED_P95_MS default 350
  OCI_PUBLIC_ARRIVAL_RUN_K6         true|false, default false
  OCI_PUBLIC_ARRIVAL_SUMMARY_DIR    required when RUN_K6=false
  OCI_PUBLIC_ARRIVAL_OUTPUT_DIR     default build/reports/k6/<gate>
  OCI_PUBLIC_ARRIVAL_MAX_RETRY_AFTER_SLEEP_SECONDS default 1
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

gate_name="${OCI_PUBLIC_ARRIVAL_GATE_NAME:-oci-public-arrival-capacity-$(date +%Y-%m-%d-%H%M%S)}"
rates="${OCI_PUBLIC_ARRIVAL_RATES:-4,5,6,7,8,10}"
duration="${OCI_PUBLIC_ARRIVAL_DURATION:-1m}"
fail_rate="${OCI_PUBLIC_ARRIVAL_FAIL_RATE:-0.10}"
delayed_fail_rate="${OCI_PUBLIC_ARRIVAL_DELAYED_FAIL_RATE:-0.25}"
accepted_p95_ms="${OCI_PUBLIC_ARRIVAL_ACCEPTED_P95_MS:-350}"
run_k6="${OCI_PUBLIC_ARRIVAL_RUN_K6:-false}"
summary_dir="${OCI_PUBLIC_ARRIVAL_SUMMARY_DIR:-}"
output_dir="${OCI_PUBLIC_ARRIVAL_OUTPUT_DIR:-build/reports/k6/${gate_name}}"
max_retry_after_sleep_seconds="${OCI_PUBLIC_ARRIVAL_MAX_RETRY_AFTER_SLEEP_SECONDS:-1}"
summary_tsv="${output_dir}/${gate_name}-arrival-capacity.tsv"
report_md="${output_dir}/${gate_name}-arrival-capacity.md"
source_gate="tools/test/run-transaction-read-429-source-gate.sh"
k6_runner="tools/test/run-k6-transaction-100m-loadtest.sh"

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

require_duration_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*(s|m|h)$ ]]; then
    echo "${name} must use a positive duration such as 1m: ${value}" >&2
    exit 1
  fi
}

require_bool_value() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_non_negative_number_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be zero or greater: ${value}" >&2
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

status_for_p95() {
  local value="$1"
  if number_greater_than "${value}" "${accepted_p95_ms}"; then
    echo "fail"
  else
    echo "pass"
  fi
}

require_csv_positive_integers "OCI_PUBLIC_ARRIVAL_RATES" "${rates}"
require_duration_value "OCI_PUBLIC_ARRIVAL_DURATION" "${duration}"
require_rate_value "OCI_PUBLIC_ARRIVAL_FAIL_RATE" "${fail_rate}"
require_rate_value "OCI_PUBLIC_ARRIVAL_DELAYED_FAIL_RATE" "${delayed_fail_rate}"
require_positive_integer_value "OCI_PUBLIC_ARRIVAL_ACCEPTED_P95_MS" "${accepted_p95_ms}"
require_bool_value "OCI_PUBLIC_ARRIVAL_RUN_K6" "${run_k6}"
require_non_negative_number_value "OCI_PUBLIC_ARRIVAL_MAX_RETRY_AFTER_SLEEP_SECONDS" "${max_retry_after_sleep_seconds}"

print_plan() {
  echo "[oci-public-arrival-capacity] gate=${gate_name}"
  echo "[oci-public-arrival-capacity] rates=${rates}"
  echo "[oci-public-arrival-capacity] duration=${duration}"
  echo "[oci-public-arrival-capacity] fail_rate=${fail_rate}"
  echo "[oci-public-arrival-capacity] delayed_fail_rate=${delayed_fail_rate}"
  echo "[oci-public-arrival-capacity] accepted_p95_ms=${accepted_p95_ms}"
  echo "[oci-public-arrival-capacity] run_k6=${run_k6}"
  echo "[oci-public-arrival-capacity] summary_dir=${summary_dir:-missing}"
  echo "[oci-public-arrival-capacity] output_dir=${output_dir}"
  echo "[oci-public-arrival-capacity] k6_command=K6_SCENARIO_MODE=constant-arrival-rate K6_RATE=<rate> ${k6_runner}"
  echo "[oci-public-arrival-capacity] source_gate=${source_gate}"
  echo "[oci-public-arrival-capacity] summary_tsv=${summary_tsv}"
  echo "[oci-public-arrival-capacity] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ ! -x "${source_gate}" ]]; then
  echo "source gate missing or not executable: ${source_gate}" >&2
  exit 1
fi
if [[ "${run_k6}" != "true" ]]; then
  if [[ -z "${summary_dir}" || ! -d "${summary_dir}" ]]; then
    echo "OCI_PUBLIC_ARRIVAL_SUMMARY_DIR is required when OCI_PUBLIC_ARRIVAL_RUN_K6=false" >&2
    exit 1
  fi
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
printf "arrival_rate\tstatus\ttotal_429_rate\tedge_429_rate\tbackend_429_rate\tunknown_429_count\ttransaction_502_count\ttransaction_503_count\tedge_delayed_rate\tedge_delayed_count\taccepted_p95_ms\taccepted_200_rate\tsource_report_md\tsummary_json\n" >"${summary_tsv}"

gate_status="pass"
summary_table=$'| Rate | Status | Total 429 | Edge 429 | Backend 429 | 502 | 503 | Edge delayed | Accepted p95 ms |\n| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |'
IFS=',' read -r -a rate_items <<<"${rates}"
for rate in "${rate_items[@]}"; do
  single_name="${gate_name}-arrival-${rate}"
  single_output_dir="${output_dir}/arrival-${rate}"
  summary_json="${summary_dir%/}/arrival-${rate}-summary.json"
  if [[ "${run_k6}" == "true" ]]; then
    summary_json="build/reports/k6/${single_name}-k6-summary.json"
    K6_REPORT_NAME="${single_name}-k6" \
    K6_RUN_ID="${single_name}" \
    K6_SCENARIO_MODE=constant-arrival-rate \
    K6_RATE="${rate}" \
    K6_DURATION="${duration}" \
    K6_PRE_ALLOCATED_VUS="$((rate * 2))" \
    K6_MAX_VUS="$((rate * 4))" \
    K6_OVERLOAD_MODE=true \
    K6_OVERLOAD_429_RATE_THRESHOLD="${fail_rate}" \
    K6_MAX_RETRY_AFTER_SLEEP_SECONDS="${max_retry_after_sleep_seconds}" \
    K6_RUN_PURPOSE=capacity \
    K6_ARCHIVE_RESULTS=false \
      "${k6_runner}"
  elif [[ ! -s "${summary_json}" ]]; then
    echo "arrival summary missing for rate ${rate}: ${summary_json}" >&2
    exit 1
  fi

  set +e
  SOURCE_429_GATE_NAME="${single_name}" \
  SOURCE_429_SUMMARY_JSON="${summary_json}" \
  SOURCE_429_OUTPUT_DIR="${single_output_dir}" \
  SOURCE_429_RUN_ID="${single_name}" \
  SOURCE_429_FAIL_RATE="${fail_rate}" \
    "${source_gate}" >/dev/null
  source_status=$?
  set -e

  total_429_rate="$(metric_value "${summary_json}" aquila_transaction_429_rate rate)"
  edge_429_rate="$(metric_value "${summary_json}" aquila_transaction_edge_429_rate rate)"
  backend_429_rate="$(metric_value "${summary_json}" aquila_transaction_backend_429_rate rate)"
  unknown_429_count="$(metric_value "${summary_json}" aquila_transaction_unknown_429_count count)"
  transaction_502_count="$(metric_value "${summary_json}" aquila_transaction_502_count count)"
  transaction_503_count="$(metric_value "${summary_json}" aquila_transaction_503_count count)"
  edge_delayed_rate="$(metric_value "${summary_json}" aquila_transaction_edge_delayed_rate rate)"
  edge_delayed_count="$(metric_value "${summary_json}" aquila_transaction_edge_delayed_count count)"
  accepted_200_rate="$(metric_value "${summary_json}" aquila_transaction_accepted_200_rate rate)"
  hot_first_p95="$(metric_value "${summary_json}" aquila_transaction_hot_first_ms "p(95)")"
  hot_cursor_p95="$(metric_value "${summary_json}" aquila_transaction_hot_cursor_ms "p(95)")"
  hot_deep_p95="$(metric_value "${summary_json}" aquila_transaction_hot_deep_cursor_ms "p(95)")"
  cold_first_p95="$(metric_value "${summary_json}" aquila_transaction_cold_first_ms "p(95)")"
  cold_cursor_p95="$(metric_value "${summary_json}" aquila_transaction_cold_cursor_ms "p(95)")"
  cold_deep_p95="$(metric_value "${summary_json}" aquila_transaction_cold_deep_cursor_ms "p(95)")"
  accepted_p95="$(max_numeric_value "${hot_first_p95}" "${hot_cursor_p95}" "${hot_deep_p95}" "${cold_first_p95}" "${cold_cursor_p95}" "${cold_deep_p95}")"

  rate_status="$(status_for_rate "${total_429_rate}")"
  if [[ "${source_status}" -ne 0 ]] \
      || [[ "$(status_for_zero "${unknown_429_count}")" == "fail" ]] \
      || [[ "$(status_for_zero "${transaction_502_count}")" == "fail" ]] \
      || [[ "$(status_for_zero "${transaction_503_count}")" == "fail" ]] \
      || [[ "$(status_for_p95 "${accepted_p95}")" == "fail" ]]; then
    rate_status="fail"
  fi
  if [[ "${rate_status}" == "fail" ]]; then
    gate_status="fail"
  fi

  source_report="${single_output_dir}/${single_name}-429-source.md"
  if number_greater_than "${edge_delayed_rate}" "${delayed_fail_rate}"; then
    rate_status="fail"
    gate_status="fail"
  fi

  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${rate}" "${rate_status}" "${total_429_rate}" "${edge_429_rate}" "${backend_429_rate}" \
    "${unknown_429_count}" "${transaction_502_count}" "${transaction_503_count}" \
    "${edge_delayed_rate}" "${edge_delayed_count}" "${accepted_p95}" "${accepted_200_rate}" \
    "${source_report}" "${summary_json}" >>"${summary_tsv}"
  summary_table="${summary_table}"$'\n'"| ${rate} | ${rate_status} | ${total_429_rate} | ${edge_429_rate} | ${backend_429_rate} | ${transaction_502_count} | ${transaction_503_count} | ${edge_delayed_rate} | ${accepted_p95} |"
done

cat >"${report_md}" <<REPORT
# OCI Public API Arrival Capacity Gate

## Summary

- gate: ${gate_name}
- gate_status=${gate_status}
- rates: ${rates}
- duration: ${duration}
- arrival-10rps target: 429 < ${fail_rate}, edge delayed ratio < ${delayed_fail_rate}, 5xx = 0, accepted p95 < ${accepted_p95_ms}ms

## Result Table

${summary_table}

## Artifacts

- summary TSV: ${summary_tsv}
- output dir: ${output_dir}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "OCI public arrival capacity gate failed: ${summary_tsv}" >&2
  exit 1
fi
