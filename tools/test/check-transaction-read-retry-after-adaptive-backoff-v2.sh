#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-retry-after-adaptive-backoff-v2.sh"

echo "[transaction-read-retry-after-v2] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/retry-after.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
scenario	retry_after_p95_ms	retry_after_max_ms	reject_streak_p95	reject_streak_max	jitter_ms	preemptive_pacing_count	edge_429_rate	five_xx_count
paced-weighted	180	320	2	4	100	120	0.02	0
burst64	240	450	3	4	100	48	0.095	0
TSV

echo "[transaction-read-retry-after-v2] print plan"
plan="$(
  RETRY_AFTER_V2_NAME=retry-v2-check \
  RETRY_AFTER_V2_INPUT_TSV="${input_tsv}" \
  RETRY_AFTER_V2_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=retry-v2-check" <<<"${plan}" >/dev/null
grep -F "max_retry_after_p95_ms=300" <<<"${plan}" >/dev/null
grep -F "max_reject_streak=4" <<<"${plan}" >/dev/null
grep -F "required_jitter_ms=100" <<<"${plan}" >/dev/null

echo "[transaction-read-retry-after-v2] pass report"
output="$(
  RETRY_AFTER_V2_NAME=retry-v2-check \
  RETRY_AFTER_V2_INPUT_TSV="${input_tsv}" \
  RETRY_AFTER_V2_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/retry-v2-check-retry-after-v2.tsv"
test "${report_md}" = "${output_dir}/retry-v2-check-retry-after-v2.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "overload amplification guard" "${report_md}" >/dev/null
grep -F $'burst64\tpass\t240\t450\t3\t4\t100\t48\t0.095\t0' "${summary_tsv}" >/dev/null

echo "[transaction-read-retry-after-v2] fail report"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "burst64" { $2 = 409; $5 = 7; $6 = 0 } { print }' \
  "${input_tsv}" >"${input_tsv}.fail"
if RETRY_AFTER_V2_NAME=retry-v2-fail \
  RETRY_AFTER_V2_INPUT_TSV="${input_tsv}.fail" \
  RETRY_AFTER_V2_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "retry-after v2 unexpectedly passed p95/streak/jitter failure" >&2
  exit 1
fi
