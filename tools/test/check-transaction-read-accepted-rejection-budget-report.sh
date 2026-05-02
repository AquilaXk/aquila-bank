#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-accepted-rejection-budget-report.sh"

echo "[transaction-read-accepted-rejection] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/accepted-rejection.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
run	accepted_p95_ms	accepted_p99_ms	accepted_p999_ms	edge_429_rate	backend_429_rate	unknown_429_count	five_xx_count
arrival16	330	620	980	0.14	0.01	0	0
burst64	320	680	1050	0.088	0.004	0	0
burst96	340	700	1100	0.410	0.010	0	0
TSV

echo "[transaction-read-accepted-rejection] print plan"
plan="$(
  ACCEPTED_REJECTION_NAME=accepted-rejection-check \
  ACCEPTED_REJECTION_INPUT_TSV="${input_tsv}" \
  ACCEPTED_REJECTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "accepted_p95_ms=350" <<<"${plan}" >/dev/null
grep -F "accepted_p99_ms=750" <<<"${plan}" >/dev/null
grep -F "accepted_p999_ms=1200" <<<"${plan}" >/dev/null
grep -F "rejection_budget_mode=source-split" <<<"${plan}" >/dev/null

echo "[transaction-read-accepted-rejection] pass report"
output="$(
  ACCEPTED_REJECTION_NAME=accepted-rejection-check \
  ACCEPTED_REJECTION_INPUT_TSV="${input_tsv}" \
  ACCEPTED_REJECTION_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/accepted-rejection-check-accepted-rejection-budget.tsv"
test "${report_md}" = "${output_dir}/accepted-rejection-check-accepted-rejection-budget.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "accepted latency SLO and rejection budget are separated" "${report_md}" >/dev/null
grep -F "edge rejection is not a hard failure when accepted latency and hard-zero budgets pass" "${report_md}" >/dev/null
grep -F $'run\tstatus\taccepted_p95_ms\taccepted_p99_ms\taccepted_p999_ms\tedge_429_rate\tbackend_429_rate\tunknown_429_count\tfive_xx_count' "${summary_tsv}" >/dev/null
grep -F $'burst96\tpass\t340\t700\t1100\t0.410\t0.010\t0\t0' "${summary_tsv}" >/dev/null

echo "[transaction-read-accepted-rejection] accepted p99.9 regression fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "burst96" { $4 = "1400" } { print }' \
  "${input_tsv}" >"${input_tsv}.p999-fail"
if ACCEPTED_REJECTION_NAME=accepted-p999 \
  ACCEPTED_REJECTION_INPUT_TSV="${input_tsv}.p999-fail" \
  ACCEPTED_REJECTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "accepted/rejection report unexpectedly passed p99.9 regression" >&2
  exit 1
fi

echo "[transaction-read-accepted-rejection] unknown 429 fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "arrival16" { $7 = "1" } { print }' \
  "${input_tsv}" >"${input_tsv}.unknown-fail"
if ACCEPTED_REJECTION_NAME=accepted-unknown \
  ACCEPTED_REJECTION_INPUT_TSV="${input_tsv}.unknown-fail" \
  ACCEPTED_REJECTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "accepted/rejection report unexpectedly passed unknown 429" >&2
  exit 1
fi
