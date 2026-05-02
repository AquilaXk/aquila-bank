#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-rejection-runtime-retune.sh"

echo "[transaction-read-rejection-runtime-retune] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/rejection-retune.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
scenario	policy	total_429_rate	edge_429_rate	accepted_hot_p95_ms	accepted_cold_p95_ms	retry_after_p95_ms	reject_streak_max	five_xx_count	backend_429_count	unknown_429_count	degradation_curve_ref	gate_threshold_ref	operation_policy_ref
burst64	smoothing	0.090	0.088	142	130	420	3	0	0	0	n/a	oci/policy/burst64-gate.md	oci/policy/runtime-budget.md
burst80	fail-fast	0.140	0.140	138	128	430	3	0	0	0	oci/curve/burst80.tsv	oci/policy/burst80-gate.md	oci/policy/runtime-budget.md
burst96	fail-fast	0.190	0.190	135	124	440	3	0	0	0	oci/curve/burst96.tsv	oci/policy/burst96-gate.md	oci/policy/runtime-budget.md
vu16-unpaced	fail-fast	0.160	0.160	145	135	430	3	0	0	0	oci/curve/vu16.tsv	oci/policy/vu16-gate.md	oci/policy/runtime-budget.md
retry-after	smoothing	0.090	0.090	140	130	430	3	0	0	0	n/a	oci/policy/retry-after-gate.md	oci/policy/runtime-budget.md
TSV

echo "[transaction-read-rejection-runtime-retune] print plan"
plan="$(
  REJECTION_RETUNE_NAME=retune-check \
  REJECTION_RETUNE_INPUT_TSV="${input_tsv}" \
  REJECTION_RETUNE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=retune-check" <<<"${plan}" >/dev/null
grep -F "required_scenarios=burst64,burst80,burst96,vu16-unpaced,retry-after" <<<"${plan}" >/dev/null
grep -F "max_burst64_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "max_accepted_p95_ms=150" <<<"${plan}" >/dev/null
grep -F "max_retry_p95_ms=450" <<<"${plan}" >/dev/null
grep -F "max_reject_streak=3" <<<"${plan}" >/dev/null

echo "[transaction-read-rejection-runtime-retune] pass report"
output="$(
  REJECTION_RETUNE_NAME=retune-check \
  REJECTION_RETUNE_INPUT_TSV="${input_tsv}" \
  REJECTION_RETUNE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/retune-check-rejection-runtime-retune.tsv"
test "${report_md}" = "${output_dir}/retune-check-rejection-runtime-retune.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "burst64 total/edge 429 budget: <= 0.10" "${report_md}" >/dev/null
grep -F "Retry-After p95 budget: <= 450ms" "${report_md}" >/dev/null
grep -F $'burst64\tpass\tok\tsmoothing\t0.090\t0.088\t142\t130\t420\t3' "${summary_tsv}" >/dev/null
grep -F "oci/curve/burst80.tsv" "${summary_tsv}" >/dev/null
grep -F "oci/policy/runtime-budget.md" "${summary_tsv}" >/dev/null

echo "[transaction-read-rejection-runtime-retune] burst64 budget fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "burst64" { $3 = 0.13553; $4 = 0.12798; $5 = 151 } { print }' \
  "${input_tsv}" >"${input_tsv}.burst64-fail"
if REJECTION_RETUNE_NAME=retune-burst64-fail \
  REJECTION_RETUNE_INPUT_TSV="${input_tsv}.burst64-fail" \
  REJECTION_RETUNE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "rejection retune unexpectedly passed burst64 budget violation" >&2
  exit 1
fi
REJECTION_RETUNE_NAME=retune-burst64-fail \
REJECTION_RETUNE_INPUT_TSV="${input_tsv}.burst64-fail" \
REJECTION_RETUNE_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "burst64-total429>0.10" "${output_dir}/retune-burst64-fail-rejection-runtime-retune.tsv" >/dev/null
grep -F "burst64-edge429>0.10" "${output_dir}/retune-burst64-fail-rejection-runtime-retune.tsv" >/dev/null
grep -F "hot-p95>150" "${output_dir}/retune-burst64-fail-rejection-runtime-retune.tsv" >/dev/null

echo "[transaction-read-rejection-runtime-retune] retry-after fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "retry-after" { $7 = 517; $8 = 6 } { print }' \
  "${input_tsv}" >"${input_tsv}.retry-fail"
if REJECTION_RETUNE_NAME=retune-retry-fail \
  REJECTION_RETUNE_INPUT_TSV="${input_tsv}.retry-fail" \
  REJECTION_RETUNE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "rejection retune unexpectedly passed Retry-After violation" >&2
  exit 1
fi
REJECTION_RETUNE_NAME=retune-retry-fail \
REJECTION_RETUNE_INPUT_TSV="${input_tsv}.retry-fail" \
REJECTION_RETUNE_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "retry-p95>450" "${output_dir}/retune-retry-fail-rejection-runtime-retune.tsv" >/dev/null
grep -F "streak-max>3" "${output_dir}/retune-retry-fail-rejection-runtime-retune.tsv" >/dev/null

echo "[transaction-read-rejection-runtime-retune] hard-zero fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "vu16-unpaced" { $9 = 1; $10 = 1; $11 = 1 } { print }' \
  "${input_tsv}" >"${input_tsv}.hard-zero-fail"
if REJECTION_RETUNE_NAME=retune-hard-zero-fail \
  REJECTION_RETUNE_INPUT_TSV="${input_tsv}.hard-zero-fail" \
  REJECTION_RETUNE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "rejection retune unexpectedly passed hard-zero violation" >&2
  exit 1
fi
REJECTION_RETUNE_NAME=retune-hard-zero-fail \
REJECTION_RETUNE_INPUT_TSV="${input_tsv}.hard-zero-fail" \
REJECTION_RETUNE_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "5xx>0" "${output_dir}/retune-hard-zero-fail-rejection-runtime-retune.tsv" >/dev/null
grep -F "backend429>0" "${output_dir}/retune-hard-zero-fail-rejection-runtime-retune.tsv" >/dev/null
grep -F "unknown429>0" "${output_dir}/retune-hard-zero-fail-rejection-runtime-retune.tsv" >/dev/null

echo "[transaction-read-rejection-runtime-retune] missing policy refs fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "burst96" { $12 = "n/a"; $13 = "n/a"; $14 = "https://internal.example/policy?token=secret" } { print }' \
  "${input_tsv}" >"${input_tsv}.ref-fail"
if REJECTION_RETUNE_NAME=retune-ref-fail \
  REJECTION_RETUNE_INPUT_TSV="${input_tsv}.ref-fail" \
  REJECTION_RETUNE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "rejection retune unexpectedly passed missing/unsafe policy refs" >&2
  exit 1
fi
REJECTION_RETUNE_NAME=retune-ref-fail \
REJECTION_RETUNE_INPUT_TSV="${input_tsv}.ref-fail" \
REJECTION_RETUNE_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "degradation_curve_ref-missing" "${output_dir}/retune-ref-fail-rejection-runtime-retune.tsv" >/dev/null
grep -F "gate_threshold_ref-missing" "${output_dir}/retune-ref-fail-rejection-runtime-retune.tsv" >/dev/null
grep -F "operation_policy_ref-unsafe" "${output_dir}/retune-ref-fail-rejection-runtime-retune.tsv" >/dev/null

echo "[transaction-read-rejection-runtime-retune] missing scenario fail"
awk -F '\t' '$1 != "vu16-unpaced"' "${input_tsv}" >"${input_tsv}.missing"
if REJECTION_RETUNE_NAME=retune-missing \
  REJECTION_RETUNE_INPUT_TSV="${input_tsv}.missing" \
  REJECTION_RETUNE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "rejection retune unexpectedly passed missing vu16-unpaced scenario" >&2
  exit 1
fi
