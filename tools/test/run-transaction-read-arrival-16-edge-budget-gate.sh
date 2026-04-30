#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-arrival-16-edge-budget-gate.sh [--print-plan]

Environment:
  ARRIVAL16_EDGE_BUDGET_NAME              default transaction-read-arrival-16-edge-budget-<timestamp>
  ARRIVAL16_EDGE_BUDGET_SUMMARY_DIR       required summary dir with arrival-16/vu16/burst summary JSON files
  ARRIVAL16_EDGE_BUDGET_OUTPUT_DIR        default build/reports/k6/<gate>
  ARRIVAL16_EDGE_BUDGET_ARRIVAL_RATE      default 16
  ARRIVAL16_EDGE_BUDGET_BURST_RATES       default 48,64,80,96
  ARRIVAL16_EDGE_BUDGET_DELAYED_RATE      default 0.25
  ARRIVAL16_EDGE_BUDGET_ARRIVAL_429_RATE  default 0.000
  ARRIVAL16_EDGE_BUDGET_VU16_429_RATE     default 0.10
  ARRIVAL16_EDGE_BUDGET_BURST_429_CURVE   default 48:0.35,64:0.45,80:0.60,96:0.70
  ARRIVAL16_EDGE_BUDGET_ACCEPTED_P95_MS   default 350
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

name="${ARRIVAL16_EDGE_BUDGET_NAME:-transaction-read-arrival-16-edge-budget-$(date +%Y-%m-%d-%H%M%S)}"
summary_dir="${ARRIVAL16_EDGE_BUDGET_SUMMARY_DIR:-}"
output_dir="${ARRIVAL16_EDGE_BUDGET_OUTPUT_DIR:-build/reports/k6/${name}}"
arrival_rate="${ARRIVAL16_EDGE_BUDGET_ARRIVAL_RATE:-16}"
burst_rates="${ARRIVAL16_EDGE_BUDGET_BURST_RATES:-48,64,80,96}"
delayed_rate="${ARRIVAL16_EDGE_BUDGET_DELAYED_RATE:-0.25}"
arrival_429_rate="${ARRIVAL16_EDGE_BUDGET_ARRIVAL_429_RATE:-0.000}"
vu16_429_rate="${ARRIVAL16_EDGE_BUDGET_VU16_429_RATE:-0.10}"
burst_429_curve="${ARRIVAL16_EDGE_BUDGET_BURST_429_CURVE:-48:0.35,64:0.45,80:0.60,96:0.70}"
accepted_p95_ms="${ARRIVAL16_EDGE_BUDGET_ACCEPTED_P95_MS:-350}"
summary_tsv="${output_dir}/${name}-edge-budget.tsv"
report_md="${output_dir}/${name}-edge-budget.md"

require_positive_integer() {
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
    require_positive_integer "${name}" "${item}"
  done
}

require_rate() {
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

require_curve() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "${name} must not be empty" >&2
    exit 1
  fi
  IFS=',' read -r -a items <<<"${value}"
  local item rate budget
  for item in "${items[@]}"; do
    rate="${item%%:*}"
    budget="${item#*:}"
    if [[ "${rate}" == "${item}" ]]; then
      echo "${name} item must use rate:budget format: ${item}" >&2
      exit 1
    fi
    require_positive_integer "${name}" "${rate}"
    require_rate "${name}" "${budget}"
  done
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

accepted_p95() {
  local summary_json="$1"
  max_numeric_value \
    "$(metric_value "${summary_json}" aquila_transaction_hot_first_ms "p(95)")" \
    "$(metric_value "${summary_json}" aquila_transaction_hot_cursor_ms "p(95)")" \
    "$(metric_value "${summary_json}" aquila_transaction_hot_deep_cursor_ms "p(95)")" \
    "$(metric_value "${summary_json}" aquila_transaction_cold_first_ms "p(95)")" \
    "$(metric_value "${summary_json}" aquila_transaction_cold_cursor_ms "p(95)")" \
    "$(metric_value "${summary_json}" aquila_transaction_cold_deep_cursor_ms "p(95)")"
}

five_xx_count() {
  local summary_json="$1"
  awk \
    -v bad_gateway="$(metric_value "${summary_json}" aquila_transaction_502_count count)" \
    -v unavailable="$(metric_value "${summary_json}" aquila_transaction_503_count count)" \
    'BEGIN { print bad_gateway + unavailable }'
}

burst_budget() {
  local rate="$1"
  IFS=',' read -r -a items <<<"${burst_429_curve}"
  local item
  for item in "${items[@]}"; do
    if [[ "${item%%:*}" == "${rate}" ]]; then
      echo "${item#*:}"
      return
    fi
  done
  echo "missing"
}

print_plan() {
  echo "[transaction-read-arrival-16-edge-budget] name=${name}"
  echo "[transaction-read-arrival-16-edge-budget] summary_dir=${summary_dir:-missing}"
  echo "[transaction-read-arrival-16-edge-budget] output_dir=${output_dir}"
  echo "[transaction-read-arrival-16-edge-budget] arrival_rate=${arrival_rate}"
  echo "[transaction-read-arrival-16-edge-budget] burst_rates=${burst_rates}"
  echo "[transaction-read-arrival-16-edge-budget] delayed_rate=${delayed_rate}"
  echo "[transaction-read-arrival-16-edge-budget] arrival_429_rate=${arrival_429_rate}"
  echo "[transaction-read-arrival-16-edge-budget] vu16_429_rate=${vu16_429_rate}"
  echo "[transaction-read-arrival-16-edge-budget] burst_429_curve=${burst_429_curve}"
  echo "[transaction-read-arrival-16-edge-budget] accepted_p95_ms=${accepted_p95_ms}"
  echo "[transaction-read-arrival-16-edge-budget] summary_tsv=${summary_tsv}"
  echo "[transaction-read-arrival-16-edge-budget] report_md=${report_md}"
}

require_positive_integer "ARRIVAL16_EDGE_BUDGET_ARRIVAL_RATE" "${arrival_rate}"
require_csv_positive_integers "ARRIVAL16_EDGE_BUDGET_BURST_RATES" "${burst_rates}"
require_rate "ARRIVAL16_EDGE_BUDGET_DELAYED_RATE" "${delayed_rate}"
require_rate "ARRIVAL16_EDGE_BUDGET_ARRIVAL_429_RATE" "${arrival_429_rate}"
require_rate "ARRIVAL16_EDGE_BUDGET_VU16_429_RATE" "${vu16_429_rate}"
require_curve "ARRIVAL16_EDGE_BUDGET_BURST_429_CURVE" "${burst_429_curve}"
require_positive_integer "ARRIVAL16_EDGE_BUDGET_ACCEPTED_P95_MS" "${accepted_p95_ms}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  if [[ -z "${summary_dir}" || ! -d "${summary_dir}" ]]; then
    echo "ARRIVAL16_EDGE_BUDGET_SUMMARY_DIR is required" >&2
    exit 1
  fi
  exit 0
fi

if [[ -z "${summary_dir}" || ! -d "${summary_dir}" ]]; then
  echo "ARRIVAL16_EDGE_BUDGET_SUMMARY_DIR is required" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
printf "scenario\tstatus\ttotal_429_rate\tedge_429_rate\tbackend_429_rate\tedge_delayed_rate\tedge_delayed_count\t5xx_count\taccepted_p95_ms\tbudget\n" >"${summary_tsv}"
summary_table=$'| Scenario | Status | Total 429 | Edge 429 | Backend 429 | Edge delayed | 5xx | Accepted p95 ms | Budget |\n| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |'
gate_status="pass"

append_row() {
  local scenario="$1"
  local summary_json="$2"
  local max_429="$3"
  local max_delayed="$4"
  local budget="$5"

  if [[ ! -s "${summary_json}" ]]; then
    echo "summary missing for ${scenario}: ${summary_json}" >&2
    exit 1
  fi

  local total_429 edge_429 backend_429 delayed delayed_count five_xx p95 status
  total_429="$(metric_value "${summary_json}" aquila_transaction_429_rate rate)"
  edge_429="$(metric_value "${summary_json}" aquila_transaction_edge_429_rate rate)"
  backend_429="$(metric_value "${summary_json}" aquila_transaction_backend_429_rate rate)"
  delayed="$(metric_value "${summary_json}" aquila_transaction_edge_delayed_rate rate)"
  delayed_count="$(metric_value "${summary_json}" aquila_transaction_edge_delayed_count count)"
  five_xx="$(five_xx_count "${summary_json}")"
  p95="$(accepted_p95 "${summary_json}")"

  status="pass"
  if number_greater_than "${total_429}" "${max_429}" \
      || number_greater_than "${delayed}" "${max_delayed}" \
      || number_greater_than "${five_xx}" "0" \
      || number_greater_than "${p95}" "${accepted_p95_ms}"; then
    status="fail"
    gate_status="fail"
  fi

  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${scenario}" "${status}" "${total_429}" "${edge_429}" "${backend_429}" \
    "${delayed}" "${delayed_count}" "${five_xx}" "${p95}" "${budget}" >>"${summary_tsv}"
  summary_table="${summary_table}"$'\n'"| ${scenario} | ${status} | ${total_429} | ${edge_429} | ${backend_429} | ${delayed} | ${five_xx} | ${p95} | ${budget} |"
}

append_row \
  "arrival-${arrival_rate}" \
  "${summary_dir%/}/arrival-${arrival_rate}-summary.json" \
  "${arrival_429_rate}" \
  "${delayed_rate}" \
  "429<=${arrival_429_rate} delayed<${delayed_rate} 5xx=0"
append_row \
  "vu16-soak-2m" \
  "${summary_dir%/}/vu16-soak-2m-summary.json" \
  "${vu16_429_rate}" \
  "${delayed_rate}" \
  "429<${vu16_429_rate} delayed<${delayed_rate} 5xx=0"

IFS=',' read -r -a rate_items <<<"${burst_rates}"
for rate in "${rate_items[@]}"; do
  budget="$(burst_budget "${rate}")"
  if [[ "${budget}" == "missing" ]]; then
    echo "burst budget missing for rate ${rate} in ARRIVAL16_EDGE_BUDGET_BURST_429_CURVE" >&2
    exit 1
  fi
  append_row \
    "burst-${rate}" \
    "${summary_dir%/}/burst-${rate}-summary.json" \
    "${budget}" \
    "${delayed_rate}" \
    "429<${budget} 5xx=0"
done

cat >"${report_md}" <<REPORT
# Transaction Read Arrival-16 Edge Budget Gate

## Summary

- gate_status=${gate_status}
- arrival-16 target: 429 = 0, 5xx = 0, edge delayed < ${delayed_rate}
- VU16 soak target: 429 < ${vu16_429_rate}
- accepted p95 target: < ${accepted_p95_ms}ms
- burst reject curve: ${burst_429_curve}

## Result Table

${summary_table}

## Contract Notes

- arrival-16은 정상 capacity 후보라 429를 허용하지 않는다.
- VU16 soak는 steady shared-client 압박을 반영하되 429 budget을 10% 미만으로 제한한다.
- burst-48/64/80/96은 overload 방어 곡선으로 분리해 fail-fast 429가 어느 지점에서 증가하는지 기록한다.

## Artifacts

- summary TSV: ${summary_tsv}
- summary dir: ${summary_dir}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read arrival-16 edge budget failed: ${summary_tsv}" >&2
  exit 1
fi
