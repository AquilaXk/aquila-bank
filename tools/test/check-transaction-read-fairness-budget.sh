#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-fairness-budget.sh"

echo "[transaction-read-fairness-budget] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/fairness.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
arrival_rate	total_requests	backend_fairness_429_count	backend_429_count	edge_429_count	unknown_429_count	five_xx_count	cold_account_p95_ms	accepted_p95_ms
8	1000	5	5	0	0	0	420	260
16	2000	12	12	0	0	0	610	330
TSV

echo "[transaction-read-fairness-budget] print plan"
plan="$(
  FAIRNESS_BUDGET_NAME=fairness-check \
  FAIRNESS_BUDGET_INPUT_TSV="${input_tsv}" \
  FAIRNESS_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "arrival_rate=16" <<<"${plan}" >/dev/null
grep -F "max_fairness_429_rate=0.01" <<<"${plan}" >/dev/null
grep -F "max_edge_429_rate=0" <<<"${plan}" >/dev/null
grep -F "cold_account_p95_ms=750" <<<"${plan}" >/dev/null

echo "[transaction-read-fairness-budget] pass report"
output="$(
  FAIRNESS_BUDGET_NAME=fairness-check \
  FAIRNESS_BUDGET_INPUT_TSV="${input_tsv}" \
  FAIRNESS_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/fairness-check-fairness-budget.tsv"
test "${report_md}" = "${output_dir}/fairness-check-fairness-budget.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "arrival16 backend fairness budget: < 0.01" "${report_md}" >/dev/null
grep -F "cold account latency budget: <= 750ms" "${report_md}" >/dev/null
grep -F $'arrival_rate\tstatus\tfairness_429_rate\tedge_429_rate\tbackend_429_count\tunknown_429_count\tfive_xx_count\tcold_account_p95_ms\taccepted_p95_ms' "${summary_tsv}" >/dev/null
grep -F $'16\tpass\t0.006000\t0.000000\t12\t0\t0\t610\t330' "${summary_tsv}" >/dev/null

echo "[transaction-read-fairness-budget] fairness over budget fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "16" { $3 = "25"; $4 = "25" } { print }' \
  "${input_tsv}" >"${input_tsv}.fairness-fail"
if FAIRNESS_BUDGET_NAME=fairness-over \
  FAIRNESS_BUDGET_INPUT_TSV="${input_tsv}.fairness-fail" \
  FAIRNESS_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "fairness budget unexpectedly passed fairness 429 over 1%" >&2
  exit 1
fi

echo "[transaction-read-fairness-budget] cold latency regression fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "16" { $8 = "900" } { print }' \
  "${input_tsv}" >"${input_tsv}.latency-fail"
if FAIRNESS_BUDGET_NAME=fairness-latency \
  FAIRNESS_BUDGET_INPUT_TSV="${input_tsv}.latency-fail" \
  FAIRNESS_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "fairness budget unexpectedly passed cold account p95 regression" >&2
  exit 1
fi
