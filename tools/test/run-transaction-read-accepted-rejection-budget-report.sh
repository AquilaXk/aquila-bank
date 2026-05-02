#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-accepted-rejection-budget-report.sh [--print-plan]

Environment:
  ACCEPTED_REJECTION_NAME             default transaction-read-accepted-rejection-<timestamp>
  ACCEPTED_REJECTION_INPUT_TSV        required accepted latency/rejection TSV
  ACCEPTED_REJECTION_OUTPUT_DIR       default build/reports/k6/<name>
  ACCEPTED_REJECTION_P95_MS           default 350
  ACCEPTED_REJECTION_P99_MS           default 750
  ACCEPTED_REJECTION_P999_MS          default 1200
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

name="${ACCEPTED_REJECTION_NAME:-transaction-read-accepted-rejection-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${ACCEPTED_REJECTION_INPUT_TSV:-}"
output_dir="${ACCEPTED_REJECTION_OUTPUT_DIR:-build/reports/k6/${name}}"
accepted_p95_ms="${ACCEPTED_REJECTION_P95_MS:-350}"
accepted_p99_ms="${ACCEPTED_REJECTION_P99_MS:-750}"
accepted_p999_ms="${ACCEPTED_REJECTION_P999_MS:-1200}"
summary_tsv="${output_dir}/${name}-accepted-rejection-budget.tsv"
report_md="${output_dir}/${name}-accepted-rejection-budget.md"
meta_file="${output_dir}/${name}-accepted-rejection-budget.meta"

require_positive_number() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*([.][0-9]+)?$ ]]; then
    echo "${key} must be positive: ${value}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[transaction-read-accepted-rejection] name=${name}"
  echo "[transaction-read-accepted-rejection] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-accepted-rejection] output_dir=${output_dir}"
  echo "[transaction-read-accepted-rejection] accepted_p95_ms=${accepted_p95_ms}"
  echo "[transaction-read-accepted-rejection] accepted_p99_ms=${accepted_p99_ms}"
  echo "[transaction-read-accepted-rejection] accepted_p999_ms=${accepted_p999_ms}"
  echo "[transaction-read-accepted-rejection] rejection_budget_mode=source-split"
  echo "[transaction-read-accepted-rejection] summary_tsv=${summary_tsv}"
  echo "[transaction-read-accepted-rejection] report_md=${report_md}"
}

require_positive_number "ACCEPTED_REJECTION_P95_MS" "${accepted_p95_ms}"
require_positive_number "ACCEPTED_REJECTION_P99_MS" "${accepted_p99_ms}"
require_positive_number "ACCEPTED_REJECTION_P999_MS" "${accepted_p999_ms}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
  echo "ACCEPTED_REJECTION_INPUT_TSV is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"

awk -F '\t' \
  -v p95_budget="${accepted_p95_ms}" \
  -v p99_budget="${accepted_p99_ms}" \
  -v p999_budget="${accepted_p999_ms}" '
function value(name, fallback) {
  if (!(name in col) || $(col[name]) == "") return fallback
  return $(col[name])
}
BEGIN {
  print "run\tstatus\taccepted_p95_ms\taccepted_p99_ms\taccepted_p999_ms\tedge_429_rate\tbackend_429_rate\tunknown_429_count\tfive_xx_count"
}
NR == 1 {
  for (i = 1; i <= NF; i++) col[$i] = i
  next
}
{
  run = value("run", "unknown")
  p95 = value("accepted_p95_ms", "999999") + 0
  p99 = value("accepted_p99_ms", "999999") + 0
  p999 = value("accepted_p999_ms", "999999") + 0
  edge = value("edge_429_rate", "0")
  backend = value("backend_429_rate", "0")
  unknown = value("unknown_429_count", "1") + 0
  five_xx = value("five_xx_count", "1") + 0
  status = "pass"
  if (p95 > p95_budget || p99 > p99_budget || p999 > p999_budget || unknown > 0 || five_xx > 0) status = "fail"
  if (status == "fail") fail_count++
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    run, status, p95, p99, p999, edge, backend, unknown, five_xx
}
END {
  print "fail_count=" (fail_count + 0) > "/dev/stderr"
}
' "${input_tsv}" >"${summary_tsv}" 2>"${meta_file}"

fail_count="$(awk -F '=' '/^fail_count=/ { print $2 }' "${meta_file}")"
gate_status="pass"
if [[ "${fail_count}" != "0" ]]; then
  gate_status="fail"
fi

budget_table="$(awk -F '\t' '
  BEGIN {
    print "| Run | Status | Accepted p95 ms | Accepted p99 ms | Accepted p99.9 ms | Edge 429 | Backend 429 | Unknown 429 | 5xx |"
    print "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Accepted Latency / Rejection Budget

## Summary

- gate_status=${gate_status}
- accepted p95 budget: <= ${accepted_p95_ms}ms
- accepted p99 budget: <= ${accepted_p99_ms}ms
- accepted p99.9 budget: <= ${accepted_p999_ms}ms
- accepted latency SLO and rejection budget are separated.
- edge rejection is not a hard failure when accepted latency and hard-zero budgets pass.
- hard-zero: unknown 429, 5xx

## Budget Table

${budget_table}

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read accepted/rejection budget failed: ${summary_tsv}" >&2
  exit 1
fi
