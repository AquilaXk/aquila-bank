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
traffic_shape	endpoint	arrival_rate	total_requests	backend_fairness_429_count	backend_429_count	edge_429_count	unknown_429_count	five_xx_count	cold_account_p95_ms	accepted_p95_ms	rejection_reason
arrival16	active	16	2000	1	1	0	0	0	210	105	fairness-limiter-active
arrival16	archive	16	2000	1	1	0	0	0	610	118	fairness-limiter-archive
burst64	active	64	5000	20	20	150	0	0	220	112	fairness-limiter-active
burst64	archive	64	5000	20	20	150	0	0	620	119	fairness-limiter-archive
TSV

echo "[transaction-read-fairness-budget] print plan"
plan="$(
  FAIRNESS_BUDGET_NAME=fairness-check \
  FAIRNESS_BUDGET_INPUT_TSV="${input_tsv}" \
  FAIRNESS_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "arrival_rate=16" <<<"${plan}" >/dev/null
grep -F "required_endpoints=active,archive" <<<"${plan}" >/dev/null
grep -F "max_fairness_429_rate=0.001" <<<"${plan}" >/dev/null
grep -F "max_burst64_backend_429_rate=0.005" <<<"${plan}" >/dev/null
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
grep -F "arrival16 backend fairness budget: < 0.001" "${report_md}" >/dev/null
grep -F "burst64 backend 429 budget: <= 0.005" "${report_md}" >/dev/null
grep -F "required endpoints: active,archive" "${report_md}" >/dev/null
grep -F "rejection reason must include endpoint" "${report_md}" >/dev/null
grep -F "cold account latency budget: <= 750ms" "${report_md}" >/dev/null
grep -F $'traffic_shape\tendpoint\tarrival_rate\tstatus\tfairness_429_rate\tbackend_429_rate\tedge_429_rate\tbackend_429_count\tunknown_429_count\tfive_xx_count\tcold_account_p95_ms\taccepted_p95_ms\trejection_reason' "${summary_tsv}" >/dev/null
grep -F $'arrival16\tactive\t16\tpass\t0.000500\t0.000500\t0.000000\t1\t0\t0\t210\t105\tfairness-limiter-active' "${summary_tsv}" >/dev/null
grep -F $'arrival16\tarchive\t16\tpass\t0.000500\t0.000500\t0.000000\t1\t0\t0\t610\t118\tfairness-limiter-archive' "${summary_tsv}" >/dev/null
grep -F $'burst64\tactive\t64\tpass\t0.004000\t0.004000\t0.030000\t20\t0\t0\t220\t112\tfairness-limiter-active' "${summary_tsv}" >/dev/null

echo "[transaction-read-fairness-budget] fairness over budget fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "arrival16" && $2 == "archive" { $5 = "3"; $6 = "3" } { print }' \
  "${input_tsv}" >"${input_tsv}.fairness-fail"
if FAIRNESS_BUDGET_NAME=fairness-over \
  FAIRNESS_BUDGET_INPUT_TSV="${input_tsv}.fairness-fail" \
  FAIRNESS_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "fairness budget unexpectedly passed arrival16 fairness 429 over 0.1%" >&2
  exit 1
fi

echo "[transaction-read-fairness-budget] burst64 backend budget fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "burst64" && $2 == "archive" { $5 = "30"; $6 = "30" } { print }' \
  "${input_tsv}" >"${input_tsv}.burst64-fail"
if FAIRNESS_BUDGET_NAME=fairness-burst64 \
  FAIRNESS_BUDGET_INPUT_TSV="${input_tsv}.burst64-fail" \
  FAIRNESS_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "fairness budget unexpectedly passed burst64 backend 429 over 0.5%" >&2
  exit 1
fi

echo "[transaction-read-fairness-budget] cold latency regression fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "arrival16" && $2 == "archive" { $10 = "900" } { print }' \
  "${input_tsv}" >"${input_tsv}.latency-fail"
if FAIRNESS_BUDGET_NAME=fairness-latency \
  FAIRNESS_BUDGET_INPUT_TSV="${input_tsv}.latency-fail" \
  FAIRNESS_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "fairness budget unexpectedly passed cold account p95 regression" >&2
  exit 1
fi

echo "[transaction-read-fairness-budget] mixed rejection reason fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "arrival16" && $2 == "archive" { $12 = "fairness-limiter-active" } { print }' \
  "${input_tsv}" >"${input_tsv}.reason-fail"
if FAIRNESS_BUDGET_NAME=fairness-reason \
  FAIRNESS_BUDGET_INPUT_TSV="${input_tsv}.reason-fail" \
  FAIRNESS_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "fairness budget unexpectedly passed mixed active/archive rejection reason" >&2
  exit 1
fi
