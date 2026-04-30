#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-edge-delay-contract-gate.sh [--print-plan]

Environment:
  EDGE_DELAY_CONTRACT_NAME             default transaction-read-edge-delay-contract-<timestamp>
  EDGE_DELAY_CONTRACT_RATES            default 80,96
  EDGE_DELAY_CONTRACT_SUMMARY_DIR      required summary dir with burst-<rate>-summary.json
  EDGE_DELAY_CONTRACT_NGINX_STATUS_TSV optional TSV: run,status,limit_req_status,count
  EDGE_DELAY_CONTRACT_SOURCE_MODE      multi-source|synthetic-source-key, default multi-source
  EDGE_DELAY_CONTRACT_OUTPUT_DIR       default build/reports/k6/<gate>
  EDGE_DELAY_CONTRACT_429_FAIL_RATE    default 0.10
  EDGE_DELAY_CONTRACT_DELAYED_FAIL_RATE default 0.25
  EDGE_DELAY_CONTRACT_RETRY_P95_MS     default 250
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

name="${EDGE_DELAY_CONTRACT_NAME:-transaction-read-edge-delay-contract-$(date +%Y-%m-%d-%H%M%S)}"
rates="${EDGE_DELAY_CONTRACT_RATES:-80,96}"
summary_dir="${EDGE_DELAY_CONTRACT_SUMMARY_DIR:-}"
nginx_status_tsv="${EDGE_DELAY_CONTRACT_NGINX_STATUS_TSV:-}"
source_mode="${EDGE_DELAY_CONTRACT_SOURCE_MODE:-multi-source}"
output_dir="${EDGE_DELAY_CONTRACT_OUTPUT_DIR:-build/reports/k6/${name}}"
fail_rate="${EDGE_DELAY_CONTRACT_429_FAIL_RATE:-0.10}"
delayed_fail_rate="${EDGE_DELAY_CONTRACT_DELAYED_FAIL_RATE:-0.25}"
retry_after_p95_ms="${EDGE_DELAY_CONTRACT_RETRY_P95_MS:-250}"
summary_tsv="${output_dir}/${name}-delay-contract.tsv"
report_md="${output_dir}/${name}-delay-contract.md"

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

require_source_mode() {
  case "$1" in
    multi-source|synthetic-source-key)
      ;;
    *)
      echo "EDGE_DELAY_CONTRACT_SOURCE_MODE must be multi-source or synthetic-source-key: $1" >&2
      exit 1
      ;;
  esac
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

nginx_count() {
  local run="$1"
  local status="$2"
  if [[ -z "${nginx_status_tsv}" || ! -s "${nginx_status_tsv}" ]]; then
    echo "0"
    return
  fi
  awk -F '\t' -v run="${run}" -v status="${status}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "run") run_col = i
        if ($i == "status") status_col = i
        if ($i == "count") count_col = i
      }
      next
    }
    $run_col == run && $status_col == status {
      total += $count_col
    }
    END { print total + 0 }
  ' "${nginx_status_tsv}"
}

print_plan() {
  echo "[transaction-read-edge-delay-contract] name=${name}"
  echo "[transaction-read-edge-delay-contract] rates=${rates}"
  echo "[transaction-read-edge-delay-contract] summary_dir=${summary_dir:-missing}"
  echo "[transaction-read-edge-delay-contract] nginx_status=${nginx_status_tsv:-missing}"
  echo "[transaction-read-edge-delay-contract] source_mode=${source_mode}"
  echo "[transaction-read-edge-delay-contract] fail_rate=${fail_rate}"
  echo "[transaction-read-edge-delay-contract] delayed_fail_rate=${delayed_fail_rate}"
  echo "[transaction-read-edge-delay-contract] retry_after_p95_ms=${retry_after_p95_ms}"
  echo "[transaction-read-edge-delay-contract] summary_tsv=${summary_tsv}"
  echo "[transaction-read-edge-delay-contract] report_md=${report_md}"
}

require_csv_positive_integers "EDGE_DELAY_CONTRACT_RATES" "${rates}"
require_rate "EDGE_DELAY_CONTRACT_429_FAIL_RATE" "${fail_rate}"
require_rate "EDGE_DELAY_CONTRACT_DELAYED_FAIL_RATE" "${delayed_fail_rate}"
require_positive_integer "EDGE_DELAY_CONTRACT_RETRY_P95_MS" "${retry_after_p95_ms}"
require_source_mode "${source_mode}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  if [[ -z "${summary_dir}" || ! -d "${summary_dir}" ]]; then
    echo "EDGE_DELAY_CONTRACT_SUMMARY_DIR is required" >&2
    exit 1
  fi
  exit 0
fi

if [[ -z "${summary_dir}" || ! -d "${summary_dir}" ]]; then
  echo "EDGE_DELAY_CONTRACT_SUMMARY_DIR is required" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
printf "burst_rate\tstatus\ttotal_429_rate\tretry_after_p95_ms\tedge_delayed_rate\tnginx_499_count\t5xx_count\n" >"${summary_tsv}"

gate_status="pass"
IFS=',' read -r -a rate_items <<<"${rates}"
for rate in "${rate_items[@]}"; do
  summary_json="${summary_dir%/}/burst-${rate}-summary.json"
  if [[ ! -s "${summary_json}" ]]; then
    echo "burst summary missing for rate ${rate}: ${summary_json}" >&2
    exit 1
  fi
  run="burst-${rate}"
  total_429_rate="$(metric_value "${summary_json}" aquila_transaction_429_rate rate)"
  retry_p95="$(metric_value "${summary_json}" aquila_transaction_retry_after_sleep_ms "p(95)")"
  edge_delayed_rate="$(metric_value "${summary_json}" aquila_transaction_edge_delayed_rate rate)"
  nginx_499_count="$(nginx_count "${run}" "499")"
  five_xx_count="$(
    awk \
      -v bad_gateway="$(metric_value "${summary_json}" aquila_transaction_502_count count)" \
      -v unavailable="$(metric_value "${summary_json}" aquila_transaction_503_count count)" \
      'BEGIN { print bad_gateway + unavailable }'
  )"
  status="pass"
  if number_greater_than "${total_429_rate}" "${fail_rate}" \
      || number_greater_than "${retry_p95}" "${retry_after_p95_ms}" \
      || number_greater_than "${edge_delayed_rate}" "${delayed_fail_rate}" \
      || number_greater_than "${nginx_499_count}" "0" \
      || number_greater_than "${five_xx_count}" "0"; then
    status="fail"
    gate_status="fail"
  fi
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${rate}" "${status}" "${total_429_rate}" "${retry_p95}" "${edge_delayed_rate}" \
    "${nginx_499_count}" "${five_xx_count}" >>"${summary_tsv}"
done

cat >"${report_md}" <<REPORT
# Transaction Read Edge Delay Contract Gate

## Summary

- gate_status=${gate_status}
- rates: ${rates}
- hot/archive edge budget: split
- per-IP source mode: ${source_mode}
- Retry-After contract: 150ms + jitter 100ms
- burst 80/96 499/5xx target: 0
- 429 target: < ${fail_rate}
- delayed ratio target: < ${delayed_fail_rate}
- retry-after p95 target: <= ${retry_after_p95_ms}ms

## Contract Notes

- Nginx limiter uses \`\$binary_remote_addr\`; single-source local k6 is intentionally conservative.
- Use \`EDGE_DELAY_CONTRACT_SOURCE_MODE=multi-source\` for OCI multi-source evidence, or \`synthetic-source-key\` when a lab can vary source-key safely.
- hot/archive split keeps active and archive queues from spending the same delay budget.

## Artifacts

- summary TSV: ${summary_tsv}
- nginx status TSV: ${nginx_status_tsv:-missing}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read edge delay contract failed: ${report_md}" >&2
  exit 1
fi
