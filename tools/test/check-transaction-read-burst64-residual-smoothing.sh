#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-burst64-residual-smoothing.sh"

echo "[transaction-read-burst64-residual-smoothing] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/burst64.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
policy	burst48_429_rate	burst64_429_rate	burst80_429_rate	burst96_429_rate	accepted_p95_ms	retry_after_p95_ms	reject_streak_max	delayed_rate	five_xx_count	backend_429_count
residual-v2	0.080	0.095	0.220	0.330	145	220	4	0.04	0	0
TSV

echo "[transaction-read-burst64-residual-smoothing] print plan"
plan="$(
  BURST64_SMOOTHING_NAME=burst64-check \
  BURST64_SMOOTHING_INPUT_TSV="${input_tsv}" \
  BURST64_SMOOTHING_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=burst64-check" <<<"${plan}" >/dev/null
grep -F "max_burst64_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "min_burst80_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "max_reject_streak=4" <<<"${plan}" >/dev/null

echo "[transaction-read-burst64-residual-smoothing] pass report"
output="$(
  BURST64_SMOOTHING_NAME=burst64-check \
  BURST64_SMOOTHING_INPUT_TSV="${input_tsv}" \
  BURST64_SMOOTHING_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/burst64-check-burst64-residual-smoothing.tsv"
test "${report_md}" = "${output_dir}/burst64-check-burst64-residual-smoothing.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "burst 64 total 429 budget: <= 0.10" "${report_md}" >/dev/null
grep -F $'residual-v2\tpass\t0.080\t0.095\t0.220\t0.330\t145\t220\t4\t0.04\t0\t0' "${summary_tsv}" >/dev/null

echo "[transaction-read-burst64-residual-smoothing] fail report"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $3 = 0.125; $8 = 7 } { print }' \
  "${input_tsv}" >"${input_tsv}.fail"
if BURST64_SMOOTHING_NAME=burst64-fail \
  BURST64_SMOOTHING_INPUT_TSV="${input_tsv}.fail" \
  BURST64_SMOOTHING_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "burst64 smoothing unexpectedly passed residual 429/streak failure" >&2
  exit 1
fi
