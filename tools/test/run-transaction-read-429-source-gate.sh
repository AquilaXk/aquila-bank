#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-429-source-gate.sh [--print-plan]

Environment:
  SOURCE_429_GATE_NAME     default transaction-read-429-source-<timestamp>
  SOURCE_429_SUMMARY_JSON  required k6 summary JSON
  SOURCE_429_OUTPUT_DIR    default build/reports/k6/<gate>
  SOURCE_429_RUN_ID        default SOURCE_429_GATE_NAME
  SOURCE_429_FAIL_RATE     default 0.10
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

gate_name="${SOURCE_429_GATE_NAME:-transaction-read-429-source-$(date +%Y-%m-%d-%H%M%S)}"
summary_json="${SOURCE_429_SUMMARY_JSON:-}"
output_dir="${SOURCE_429_OUTPUT_DIR:-build/reports/k6/${gate_name}}"
run_id="${SOURCE_429_RUN_ID:-${gate_name}}"
fail_rate="${SOURCE_429_FAIL_RATE:-0.10}"
summary_tsv="${output_dir}/${gate_name}-429-source.tsv"
report_md="${output_dir}/${gate_name}-429-source.md"

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

number_greater_than() {
  awk -v value="$1" -v threshold="$2" 'BEGIN { exit !(value > threshold) }'
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

metric_value() {
  local metric="$1"
  local field="$2"
  jq -r --arg metric "${metric}" --arg field "${field}" \
    '.metrics[$metric].values[$field] // "0"' "${summary_json}"
}

require_rate_value "SOURCE_429_FAIL_RATE" "${fail_rate}"

print_plan() {
  echo "[transaction-read-429-source] gate=${gate_name}"
  echo "[transaction-read-429-source] summary_json=${summary_json:-missing}"
  echo "[transaction-read-429-source] run_id=${run_id}"
  echo "[transaction-read-429-source] output_dir=${output_dir}"
  echo "[transaction-read-429-source] fail_rate=${fail_rate}"
  echo "[transaction-read-429-source] summary_tsv=${summary_tsv}"
  echo "[transaction-read-429-source] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ -z "${summary_json}" || ! -s "${summary_json}" ]]; then
  echo "SOURCE_429_SUMMARY_JSON is required: ${summary_json:-missing}" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"

http_reqs="$(metric_value http_reqs count)"
total_429_rate="$(metric_value aquila_transaction_429_rate rate)"
edge_429_rate="$(metric_value aquila_transaction_edge_429_rate rate)"
edge_429_count="$(metric_value aquila_transaction_edge_429_count count)"
backend_429_rate="$(metric_value aquila_transaction_backend_429_rate rate)"
backend_429_count="$(metric_value aquila_transaction_backend_429_count count)"
unknown_429_rate="$(metric_value aquila_transaction_unknown_429_rate rate)"
unknown_429_count="$(metric_value aquila_transaction_unknown_429_count count)"
transaction_502_rate="$(metric_value aquila_transaction_502_rate rate)"
transaction_502_count="$(metric_value aquila_transaction_502_count count)"
accepted_200_rate="$(metric_value aquila_transaction_accepted_200_rate rate)"
accepted_200_count="$(metric_value aquila_transaction_accepted_200_count count)"

total_429_status="$(status_for_rate "${total_429_rate}")"
edge_429_status="$(status_for_rate "${edge_429_rate}")"
backend_429_status="$(status_for_rate "${backend_429_rate}")"
unknown_429_status="$(status_for_zero "${unknown_429_count}")"
transaction_502_status="$(status_for_zero "${transaction_502_count}")"

gate_status="pass"
for item in "${total_429_status}" "${edge_429_status}" "${backend_429_status}" "${unknown_429_status}" "${transaction_502_status}"; do
  if [[ "${item}" == "fail" ]]; then
    gate_status="fail"
  fi
done

{
  printf "source\tstatus\trate\tcount\tfail_threshold\n"
  printf "total_429\t%s\t%s\t%s\t%s\n" "${total_429_status}" "${total_429_rate}" "n/a" "${fail_rate}"
  printf "edge\t%s\t%s\t%s\t%s\n" "${edge_429_status}" "${edge_429_rate}" "${edge_429_count}" "${fail_rate}"
  printf "backend\t%s\t%s\t%s\t%s\n" "${backend_429_status}" "${backend_429_rate}" "${backend_429_count}" "${fail_rate}"
  printf "unknown\t%s\t%s\t%s\t0\n" "${unknown_429_status}" "${unknown_429_rate}" "${unknown_429_count}"
  printf "502\t%s\t%s\t%s\t0\n" "${transaction_502_status}" "${transaction_502_rate}" "${transaction_502_count}"
  printf "accepted_200\tobserve\t%s\t%s\tn/a\n" "${accepted_200_rate}" "${accepted_200_count}"
  printf "http_reqs\tobserve\tn/a\t%s\tn/a\n" "${http_reqs}"
} >"${summary_tsv}"

cat >"${report_md}" <<REPORT
# Transaction Read 429 Source Gate

## Summary

- gate: ${gate_name}
- gate_status=${gate_status}
- run_id=${run_id}
- fail_rate=${fail_rate}
- http_reqs=${http_reqs}

## Source Split

| Source | Status | Rate | Count |
| --- | --- | ---: | ---: |
| total 429 | ${total_429_status} | ${total_429_rate} | n/a |
| edge 429 | ${edge_429_status} | ${edge_429_rate} | ${edge_429_count} |
| backend admission 429 | ${backend_429_status} | ${backend_429_rate} | ${backend_429_count} |
| unknown 429 | ${unknown_429_status} | ${unknown_429_rate} | ${unknown_429_count} |
| 502 | ${transaction_502_status} | ${transaction_502_rate} | ${transaction_502_count} |
| accepted 200 | observe | ${accepted_200_rate} | ${accepted_200_count} |

## Artifacts

- summary TSV: ${summary_tsv}
- summary JSON: ${summary_json}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read 429 source gate failed: ${summary_tsv}" >&2
  exit 1
fi
