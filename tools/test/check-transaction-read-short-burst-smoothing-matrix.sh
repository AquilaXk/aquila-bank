#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-short-burst-smoothing-matrix.sh"

echo "[transaction-read-short-burst-smoothing] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/short-burst.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
policy	hot_limit_mode	archive_limit_mode	hot_burst	archive_burst	burst48_429_rate	accepted_p95_ms	accepted_p99_ms	retry_after_p95_ms	edge_delayed_rate	five_xx_count	backend_429_rate	backend_pending	hikari_warning_count
nodelay	nodelay	nodelay	10	10	0.1833	95	145	180	0	0	0.0002	0	0
delay1	delay=1	delay=1	12	12	0.0900	150	240	190	0.20	0	0.0002	0	0
TSV

echo "[transaction-read-short-burst-smoothing] print plan"
plan="$(
  SHORT_BURST_SMOOTHING_NAME=short-burst-check \
  SHORT_BURST_SMOOTHING_INPUT_TSV="${input_tsv}" \
  SHORT_BURST_SMOOTHING_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=short-burst-check" <<<"${plan}" >/dev/null
grep -F "required_policies=nodelay,delay1" <<<"${plan}" >/dev/null
grep -F "max_burst48_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "max_accepted_p95_ms=200" <<<"${plan}" >/dev/null
grep -F "max_accepted_p99_ms=300" <<<"${plan}" >/dev/null
grep -F "max_retry_after_p95_ms=250" <<<"${plan}" >/dev/null
grep -F "max_delayed_rate=0.25" <<<"${plan}" >/dev/null

echo "[transaction-read-short-burst-smoothing] report"
output="$(
  SHORT_BURST_SMOOTHING_NAME=short-burst-check \
  SHORT_BURST_SMOOTHING_INPUT_TSV="${input_tsv}" \
  SHORT_BURST_SMOOTHING_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/short-burst-check-short-burst-smoothing.tsv"
test "${report_md}" = "${output_dir}/short-burst-check-short-burst-smoothing.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "recommended policy: delay1" "${report_md}" >/dev/null
grep -F "Retry-After contract: fixed 150ms + jitter 100ms" "${report_md}" >/dev/null
grep -F $'policy\tstatus\thot_limit_mode\tarchive_limit_mode\thot_burst\tarchive_burst\tburst48_429_rate\taccepted_p95_ms\taccepted_p99_ms\tretry_after_p95_ms\tedge_delayed_rate\tfive_xx_count\tbackend_429_rate\tbackend_pending\thikari_warning_count' "${summary_tsv}" >/dev/null
grep -F $'nodelay\tfail\tnodelay\tnodelay\t10\t10\t0.1833\t95\t145\t180\t0\t0\t0.0002\t0\t0' "${summary_tsv}" >/dev/null
grep -F $'delay1\tpass\tdelay=1\tdelay=1\t12\t12\t0.0900\t150\t240\t190\t0.20\t0\t0.0002\t0\t0' "${summary_tsv}" >/dev/null

echo "[transaction-read-short-burst-smoothing] fail report"
awk -F '\t' 'BEGIN { OFS = "\t" } NR == 1 { print; next } { $6 = "0.1800"; print }' \
  "${input_tsv}" >"${input_tsv}.fail"
if SHORT_BURST_SMOOTHING_NAME=short-burst-fail \
  SHORT_BURST_SMOOTHING_INPUT_TSV="${input_tsv}.fail" \
  SHORT_BURST_SMOOTHING_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "short burst smoothing matrix unexpectedly passed without a 429-safe candidate" >&2
  exit 1
fi

echo "[transaction-read-short-burst-smoothing] latency fail report"
awk -F '\t' 'BEGIN { OFS = "\t" } NR == 1 { print; next } { $8 = "360"; print }' \
  "${input_tsv}" >"${input_tsv}.p99-fail"
if SHORT_BURST_SMOOTHING_NAME=short-burst-p99-fail \
  SHORT_BURST_SMOOTHING_INPUT_TSV="${input_tsv}.p99-fail" \
  SHORT_BURST_SMOOTHING_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "short burst smoothing matrix unexpectedly passed without a p99-safe candidate" >&2
  exit 1
fi

echo "[transaction-read-short-burst-smoothing] delayed ratio fail report"
awk -F '\t' 'BEGIN { OFS = "\t" } NR == 1 { print; next } { $10 = "0.30"; print }' \
  "${input_tsv}" >"${input_tsv}.delayed-fail"
if SHORT_BURST_SMOOTHING_NAME=short-burst-delayed-fail \
  SHORT_BURST_SMOOTHING_INPUT_TSV="${input_tsv}.delayed-fail" \
  SHORT_BURST_SMOOTHING_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "short burst smoothing matrix unexpectedly passed without a delayed-ratio-safe candidate" >&2
  exit 1
fi

echo "[transaction-read-short-burst-smoothing] invalid input fails"
if SHORT_BURST_SMOOTHING_MAX_BURST48_429_RATE=1.5 \
  SHORT_BURST_SMOOTHING_INPUT_TSV="${input_tsv}" \
    "${runner}" --print-plan >/dev/null 2>&1; then
  echo "invalid burst48 rate unexpectedly succeeded" >&2
  exit 1
fi
