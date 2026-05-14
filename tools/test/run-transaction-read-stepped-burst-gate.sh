#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-stepped-burst-gate.sh [--print-plan]

Environment:
  STEPPED_BURST_GATE_NAME     default transaction-read-stepped-burst-<timestamp>
  STEPPED_BURST_RATES         default 32,48,64,80,96
  STEPPED_BURST_DURATION      default 20s
  STEPPED_BURST_WARN_RATE     default 0.08
  STEPPED_BURST_FAIL_RATE     default 0.10
  STEPPED_BURST_RUN_K6        true|false, default false
  STEPPED_BURST_DATASET_PREFLIGHT true|false, default true when STEPPED_BURST_RUN_K6=true
  STEPPED_BURST_DATASET_PREFLIGHT_RUNNER default tools/test/run-transaction-100m-fixture-dataset-probe.sh
  STEPPED_BURST_SUMMARY_DIR   required when STEPPED_BURST_RUN_K6=false
  STEPPED_BURST_OUTPUT_DIR    default build/reports/k6/<gate>

Summary input when STEPPED_BURST_RUN_K6=false:
  ${STEPPED_BURST_SUMMARY_DIR}/rate-32-summary.json
  ${STEPPED_BURST_SUMMARY_DIR}/rate-48-summary.json
  ${STEPPED_BURST_SUMMARY_DIR}/rate-64-summary.json
  ${STEPPED_BURST_SUMMARY_DIR}/rate-80-summary.json
  ${STEPPED_BURST_SUMMARY_DIR}/rate-96-summary.json
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
rates="${STEPPED_BURST_RATES:-32,48,64,80,96}"
burst_duration="${STEPPED_BURST_DURATION:-20s}"
warn_rate="${STEPPED_BURST_WARN_RATE:-0.08}"
fail_rate="${STEPPED_BURST_FAIL_RATE:-0.10}"
run_k6="${STEPPED_BURST_RUN_K6:-false}"
dataset_preflight="${STEPPED_BURST_DATASET_PREFLIGHT:-true}"
dataset_preflight_runner="${STEPPED_BURST_DATASET_PREFLIGHT_RUNNER:-tools/test/run-transaction-100m-fixture-dataset-probe.sh}"
summary_dir="${STEPPED_BURST_SUMMARY_DIR:-}"
output_dir="${STEPPED_BURST_OUTPUT_DIR:-build/reports/k6/${gate_name}}"
summary_tsv="${output_dir}/${gate_name}-stepped-burst.tsv"
report_md="${output_dir}/${gate_name}-stepped-burst.md"
preflight_failure_report="${output_dir}/${gate_name}-stepped-burst-preflight-failure.env"
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
require_bool_value "STEPPED_BURST_DATASET_PREFLIGHT" "${dataset_preflight}"
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
  echo "[transaction-read-stepped-burst] dataset_preflight=${dataset_preflight}"
  echo "[transaction-read-stepped-burst] dataset_preflight_runner=${dataset_preflight_runner}"
  echo "[transaction-read-stepped-burst] summary_dir=${summary_dir:-missing}"
  echo "[transaction-read-stepped-burst] output_dir=${output_dir}"
  echo "[transaction-read-stepped-burst] k6_command=K6_SCENARIO_MODE=burst K6_BURST_RATE=<rate> ${single_gate}"
  echo "[transaction-read-stepped-burst] k6_headroom=K6_PRE_ALLOCATED_VUS=<rate> K6_MAX_VUS=<rate*2> K6_MAX_RETRY_AFTER_SLEEP_SECONDS=1"
  echo "[transaction-read-stepped-burst] summary_tsv=${summary_tsv}"
  echo "[transaction-read-stepped-burst] report_md=${report_md}"
  echo "[transaction-read-stepped-burst] preflight_failure_report=${preflight_failure_report}"
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

write_preflight_failure_report() {
  local reason="$1"
  local status="$2"
  mkdir -p "${output_dir}"
  {
    echo "STEPPED_BURST_PREFLIGHT_STATUS=failed"
    echo "STEPPED_BURST_GATE_NAME=${gate_name}"
    echo "STEPPED_BURST_PREFLIGHT_FAILURE_REASON=${reason}"
    echo "STEPPED_BURST_PREFLIGHT_EXIT_STATUS=${status}"
    echo "STEPPED_BURST_DATASET_PREFLIGHT=${dataset_preflight}"
    echo "STEPPED_BURST_DATASET_PREFLIGHT_RUNNER=${dataset_preflight_runner}"
    echo "STEPPED_BURST_SUMMARY_TSV=${summary_tsv}"
    echo "STEPPED_BURST_REPORT_MD=${report_md}"
  } >"${preflight_failure_report}"
  echo "[transaction-read-stepped-burst] preflight failure report written=${preflight_failure_report}" >&2
}

run_dataset_preflight() {
  local status
  if [[ "${run_k6}" != "true" || "${dataset_preflight}" != "true" ]]; then
    echo "[transaction-read-stepped-burst] dataset preflight skipped"
    return 0
  fi
  if [[ ! -x "${dataset_preflight_runner}" ]]; then
    echo "stepped burst dataset preflight runner missing or not executable: ${dataset_preflight_runner}" >&2
    exit 1
  fi

  echo "[transaction-read-stepped-burst] dataset preflight: ${dataset_preflight_runner}"
  set +e
  "${dataset_preflight_runner}"
  status=$?
  set -e
  if [[ "${status}" -ne 0 ]]; then
    write_preflight_failure_report "dataset-preflight-failed" "${status}"
    exit "${status}"
  fi
}

run_dataset_preflight

mkdir -p "${output_dir}"
printf "rate\tstatus\ttransaction_429_rate\ttransaction_503_rate\ttransaction_503_count\tdropped_iterations\tinterrupted_iterations\tgenerator_headroom_status\treport_md\tsummary_json\n" >"${summary_tsv}"

gate_status="pass"
first_fail_rate="none"
max_non_fail_rate="none"
summary_table=$'| rate | status | transaction 429 rate | transaction 503 rate | transaction 503 count | dropped iterations | interrupted iterations | generator headroom | report |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- |'
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
  dropped_iterations="$(metric_value "${summary_json}" dropped_iterations count)"
  interrupted_iterations="$(metric_value "${summary_json}" interrupted_iterations count)"
  generator_headroom_status="pass"
  if number_greater_than "${dropped_iterations}" "0" || number_greater_than "${interrupted_iterations}" "0"; then
    generator_headroom_status="fail"
  fi
  rate_status="pass"
  if [[ "${single_status}" -ne 0 ]] || number_greater_than "${transaction_429_rate}" "${fail_rate}" \
      || number_greater_than "${transaction_503_rate}" "0" \
      || number_greater_than "${transaction_503_count}" "0" \
      || [[ "${generator_headroom_status}" == "fail" ]]; then
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
    if [[ "${first_fail_rate}" == "none" ]]; then
      max_non_fail_rate="${rate}"
    fi
  else
    if [[ "${first_fail_rate}" == "none" ]]; then
      max_non_fail_rate="${rate}"
    fi
  fi

  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${rate}" "${rate_status}" "${transaction_429_rate}" "${transaction_503_rate}" \
    "${transaction_503_count}" "${dropped_iterations}" "${interrupted_iterations}" \
    "${generator_headroom_status}" "${single_report}" "${summary_json}" >>"${summary_tsv}"
  summary_table="${summary_table}"$'\n'"| ${rate} | ${rate_status} | ${transaction_429_rate} | ${transaction_503_rate} | ${transaction_503_count} | ${dropped_iterations} | ${interrupted_iterations} | ${generator_headroom_status} | ${single_report} |"
done

cat >"${report_md}" <<REPORT
# Transaction Read Stepped Burst Gate

## Summary

- gate: ${gate_name}
- gate_status=${gate_status}
- rates: ${rates}
- focus: 32/48/64/80/96 it/s boundary
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

- 32/48/64/80/96 단계는 2026-04-30 OCI A1 burst 재현 기준의 fail point를 낮은 구간부터 고정합니다.
- 429는 admission 보호 신호로 따로 budget 관리하고, 503은 hard fail로 처리합니다.
- dropped/interrupted iterations는 generator headroom 실패로 분리합니다.
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "stepped burst gate failed: ${summary_tsv}" >&2
  exit 1
fi
