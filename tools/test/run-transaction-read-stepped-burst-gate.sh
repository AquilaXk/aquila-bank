#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-stepped-burst-gate.sh [--print-plan]

Environment:
  STEPPED_BURST_GATE_NAME     default transaction-read-stepped-burst-<timestamp>
  STEPPED_BURST_RATES         default 64,80,96,112,128
  STEPPED_BURST_DURATION      default 20s
  STEPPED_BURST_WARN_RATE     default 0.08
  STEPPED_BURST_FAIL_RATE     default 0.10
  STEPPED_BURST_RUN_K6        true|false, default false
  STEPPED_BURST_SUMMARY_DIR   required when STEPPED_BURST_RUN_K6=false
  STEPPED_BURST_OUTPUT_DIR    default build/reports/k6/<gate>

Summary input when STEPPED_BURST_RUN_K6=false:
  ${STEPPED_BURST_SUMMARY_DIR}/rate-64-summary.json
  ${STEPPED_BURST_SUMMARY_DIR}/rate-80-summary.json
  ${STEPPED_BURST_SUMMARY_DIR}/rate-96-summary.json
  ${STEPPED_BURST_SUMMARY_DIR}/rate-112-summary.json
  ${STEPPED_BURST_SUMMARY_DIR}/rate-128-summary.json
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

gate_name="${STEPPED_BURST_GATE_NAME:-transaction-read-stepped-burst-$(date +%Y-%m-%d-%H%M%S)}"
rates="${STEPPED_BURST_RATES:-64,80,96,112,128}"
burst_duration="${STEPPED_BURST_DURATION:-20s}"
warn_rate="${STEPPED_BURST_WARN_RATE:-0.08}"
fail_rate="${STEPPED_BURST_FAIL_RATE:-0.10}"
run_k6="${STEPPED_BURST_RUN_K6:-false}"
summary_dir="${STEPPED_BURST_SUMMARY_DIR:-}"
output_dir="${STEPPED_BURST_OUTPUT_DIR:-build/reports/k6/${gate_name}}"
summary_tsv="${output_dir}/${gate_name}-stepped-burst.tsv"
report_md="${output_dir}/${gate_name}-stepped-burst.md"
single_gate="tools/test/run-transaction-read-burst-429-budget-gate.sh"

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
    if ! [[ "${item}" =~ ^[1-9][0-9]*$ ]]; then
      echo "${name} must contain positive integers: ${value}" >&2
      exit 1
    fi
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

require_bool_value() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

number_greater_than() {
  awk -v value="$1" -v threshold="$2" 'BEGIN { exit !(value > threshold) }'
}

metric_value() {
  local summary_json="$1"
  local metric="$2"
  local field="$3"
  jq -r --arg metric "${metric}" --arg field "${field}" \
    '.metrics[$metric].values[$field] // "0"' "${summary_json}"
}

require_csv_positive_integers "STEPPED_BURST_RATES" "${rates}"
require_rate_value "STEPPED_BURST_WARN_RATE" "${warn_rate}"
require_rate_value "STEPPED_BURST_FAIL_RATE" "${fail_rate}"
require_bool_value "STEPPED_BURST_RUN_K6" "${run_k6}"
if number_greater_than "${warn_rate}" "${fail_rate}"; then
  echo "STEPPED_BURST_WARN_RATE must be less than or equal to STEPPED_BURST_FAIL_RATE" >&2
  exit 1
fi
if ! [[ "${burst_duration}" =~ ^[1-9][0-9]*(s|m|h)$ ]]; then
  echo "STEPPED_BURST_DURATION must use a positive duration such as 20s, 1m, or 1h" >&2
  exit 1
fi

print_plan() {
  echo "[transaction-read-stepped-burst] gate=${gate_name}"
  echo "[transaction-read-stepped-burst] rates=${rates}"
  echo "[transaction-read-stepped-burst] burst_duration=${burst_duration}"
  echo "[transaction-read-stepped-burst] warn_rate=${warn_rate}"
  echo "[transaction-read-stepped-burst] fail_rate=${fail_rate}"
  echo "[transaction-read-stepped-burst] run_k6=${run_k6}"
  echo "[transaction-read-stepped-burst] summary_dir=${summary_dir:-missing}"
  echo "[transaction-read-stepped-burst] output_dir=${output_dir}"
  echo "[transaction-read-stepped-burst] k6_command=K6_SCENARIO_MODE=burst K6_BURST_RATE=<rate> ${single_gate}"
  echo "[transaction-read-stepped-burst] summary_tsv=${summary_tsv}"
  echo "[transaction-read-stepped-burst] report_md=${report_md}"
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
    echo "STEPPED_BURST_SUMMARY_DIR is required when STEPPED_BURST_RUN_K6=false" >&2
    exit 1
  fi
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
printf "rate\tstatus\ttransaction_429_rate\ttransaction_503_rate\ttransaction_503_count\treport_md\tsummary_json\n" >"${summary_tsv}"

gate_status="pass"
first_fail_rate="none"
max_non_fail_rate="none"
summary_table=$'| rate | status | transaction 429 rate | transaction 503 rate | transaction 503 count | report |\n| --- | --- | --- | --- | --- | --- |'
IFS=',' read -r -a rate_items <<<"${rates}"
for rate in "${rate_items[@]}"; do
  single_name="${gate_name}-rate-${rate}"
  single_output_dir="${output_dir}/rate-${rate}"
  summary_json="${summary_dir%/}/rate-${rate}-summary.json"
  if [[ "${run_k6}" == "true" ]]; then
    summary_json="build/reports/k6/${single_name}-k6-summary.json"
  elif [[ ! -s "${summary_json}" ]]; then
    echo "stepped burst summary missing for rate ${rate}: ${summary_json}" >&2
    exit 1
  fi

  set +e
  BURST_429_GATE_NAME="${single_name}" \
  BURST_429_SUMMARY_JSON="${summary_json}" \
  BURST_429_OUTPUT_DIR="${single_output_dir}" \
  BURST_429_BURST_RATE="${rate}" \
  BURST_429_BURST_DURATION="${burst_duration}" \
  BURST_429_WARN_RATE="${warn_rate}" \
  BURST_429_FAIL_RATE="${fail_rate}" \
  BURST_429_RUN_K6="${run_k6}" \
    "${single_gate}" >/dev/null
  single_status=$?
  set -e

  single_report="${single_output_dir}/${single_name}-burst-429-budget.md"
  transaction_429_rate="$(metric_value "${summary_json}" aquila_transaction_429_rate rate)"
  transaction_503_rate="$(metric_value "${summary_json}" aquila_transaction_503_rate rate)"
  transaction_503_count="$(metric_value "${summary_json}" aquila_transaction_503_count count)"
  rate_status="pass"
  if [[ "${single_status}" -ne 0 ]] || number_greater_than "${transaction_429_rate}" "${fail_rate}" \
      || number_greater_than "${transaction_503_rate}" "0" \
      || number_greater_than "${transaction_503_count}" "0"; then
    rate_status="fail"
    gate_status="fail"
    if [[ "${first_fail_rate}" == "none" ]]; then
      first_fail_rate="${rate}"
    fi
  elif number_greater_than "${transaction_429_rate}" "${warn_rate}"; then
    rate_status="warn"
    if [[ "${gate_status}" == "pass" ]]; then
      gate_status="warn"
    fi
    max_non_fail_rate="${rate}"
  else
    max_non_fail_rate="${rate}"
  fi

  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${rate}" "${rate_status}" "${transaction_429_rate}" "${transaction_503_rate}" \
    "${transaction_503_count}" "${single_report}" "${summary_json}" >>"${summary_tsv}"
  summary_table="${summary_table}"$'\n'"| ${rate} | ${rate_status} | ${transaction_429_rate} | ${transaction_503_rate} | ${transaction_503_count} | ${single_report} |"
done

cat >"${report_md}" <<REPORT
# Transaction Read Stepped Burst Gate

## Summary

- gate: ${gate_name}
- gate_status=${gate_status}
- rates: ${rates}
- focus: 64/80/96/112/128 it/s boundary
- burst duration: ${burst_duration}
- warning threshold: ${warn_rate}
- fail threshold: ${fail_rate}
- first_fail_rate=${first_fail_rate}
- max_non_fail_rate=${max_non_fail_rate}

## Result Table

${summary_table}

## Artifacts

- summary TSV: ${summary_tsv}
- output dir: ${output_dir}

## Notes

- 64/80/96/112/128 단계는 2026-04-29 OCI A1 burst 경계인 96~128 it/s의 전후 구간까지 고정합니다.
- 429는 admission 보호 신호로 따로 budget 관리하고, 503은 hard fail로 처리합니다.
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "stepped burst gate failed: ${summary_tsv}" >&2
  exit 1
fi
