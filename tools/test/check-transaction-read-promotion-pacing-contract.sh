#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-promotion-pacing-contract.sh"

echo "[transaction-read-promotion-pacing] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/promotion-pacing.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
profile	gate_role	promotion_target_rate	burst_rate	preemptive_pacing	total_429_rate	edge_429_rate	backend_429_rate	backend_429_count	five_xx_count	nginx_499_count	accepted_p95_ms	pacing_sleep_p95_ms	summary_ref	nginx_aggregate_ref	pacing_summary_ref
burst64	promotion-target	64	64	false	0.081851	0.081851	0.000000	0	0	0	3.223	0	oci://runs/target64/burst64-summary.json	oci://runs/target64/burst64-nginx.tsv	n/a
burst80	overload-observation	64	80	false	0.158699	0.158699	0.000000	0	0	0	3.670	472.806	oci://runs/target80/burst80-summary.json	oci://runs/target80/burst80-nginx.tsv	n/a
vu16-unpaced	saturation-observation	64	0	false	0.899085	0.899075	0.000010	1	0	0	25.491	0	oci://runs/vu16-unpaced/summary.json	oci://runs/vu16-unpaced/nginx.tsv	oci://runs/vu16-unpaced/pacing.md
vu16-paced	paced-saturation-contract	64	0	true	0.000000	0.000000	0.000000	0	0	0	7.240	250.000	oci://runs/vu16-paced/summary.json	oci://runs/vu16-paced/nginx.tsv	oci://runs/vu16-paced/pacing.md
TSV

echo "[transaction-read-promotion-pacing] print plan"
plan="$(
  TRANSACTION_READ_PROMOTION_PACING_NAME=promotion-pacing-check \
  TRANSACTION_READ_PROMOTION_PACING_INPUT_TSV="${input_tsv}" \
  TRANSACTION_READ_PROMOTION_PACING_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=promotion-pacing-check" <<<"${plan}" >/dev/null
grep -F "promotion_target_rate=64" <<<"${plan}" >/dev/null
grep -F "max_promotion_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "max_backend_429_rate=0.005" <<<"${plan}" >/dev/null
grep -F "max_paced_vu16_429_rate=0.01" <<<"${plan}" >/dev/null
grep -F "summary_tsv=${output_dir}/promotion-pacing-check-promotion-pacing.tsv" <<<"${plan}" >/dev/null

echo "[transaction-read-promotion-pacing] pass report"
output="$(
  TRANSACTION_READ_PROMOTION_PACING_NAME=promotion-pacing-check \
  TRANSACTION_READ_PROMOTION_PACING_INPUT_TSV="${input_tsv}" \
  TRANSACTION_READ_PROMOTION_PACING_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/promotion-pacing-check-promotion-pacing.tsv"
test "${report_md}" = "${output_dir}/promotion-pacing-check-promotion-pacing.md"
grep -F $'profile\tgate_role\tstatus\treason\tpromotion_target_rate\tburst_rate\tpreemptive_pacing\ttotal_429_rate\tedge_429_rate\tbackend_429_rate\tbackend_429_count\tfive_xx_count\tnginx_499_count\taccepted_p95_ms\tpacing_sleep_p95_ms\tsummary_ref\tnginx_aggregate_ref\tpacing_summary_ref' "${summary_tsv}" >/dev/null
grep -F $'burst64\tpromotion-target\tpass\tok\t64\t64\tfalse\t0.081851\t0.081851\t0.000000\t0\t0\t0\t3.223\t0\toci://runs/target64/burst64-summary.json\toci://runs/target64/burst64-nginx.tsv\tn/a' "${summary_tsv}" >/dev/null
grep -F $'burst80\toverload-observation\tobserve\tok\t64\t80\tfalse\t0.158699\t0.158699\t0.000000\t0\t0\t0\t3.670\t472.806\toci://runs/target80/burst80-summary.json\toci://runs/target80/burst80-nginx.tsv\tn/a' "${summary_tsv}" >/dev/null
grep -F $'vu16-unpaced\tsaturation-observation\tobserve\tok\t64\t0\tfalse\t0.899085\t0.899075\t0.000010\t1\t0\t0\t25.491\t0\toci://runs/vu16-unpaced/summary.json\toci://runs/vu16-unpaced/nginx.tsv\toci://runs/vu16-unpaced/pacing.md' "${summary_tsv}" >/dev/null
grep -F $'vu16-paced\tpaced-saturation-contract\tpass\tok\t64\t0\ttrue\t0.000000\t0.000000\t0.000000\t0\t0\t0\t7.240\t250.000\toci://runs/vu16-paced/summary.json\toci://runs/vu16-paced/nginx.tsv\toci://runs/vu16-paced/pacing.md' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "promotion target: burst64" "${report_md}" >/dev/null
grep -F "VU16 unpaced: observation-only saturation probe" "${report_md}" >/dev/null
grep -F "VU16 paced: safe saturation contract" "${report_md}" >/dev/null

echo "[transaction-read-promotion-pacing] target80 promotion fails"
target80_input="${temp_dir}/target80.tsv"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $3 = "80" } $1 == "burst80" { $2 = "promotion-target" } { print }' \
  "${input_tsv}" >"${target80_input}"
if TRANSACTION_READ_PROMOTION_PACING_NAME=promotion-pacing-target80 \
  TRANSACTION_READ_PROMOTION_PACING_INPUT_TSV="${target80_input}" \
  TRANSACTION_READ_PROMOTION_PACING_OUTPUT_DIR="${temp_dir}/target80-output" \
  TRANSACTION_READ_PROMOTION_TARGET_RATE=80 \
    "${runner}" >"${temp_dir}/target80.log" 2>&1; then
  echo "target80 promotion unexpectedly passed" >&2
  exit 1
fi
grep -F "burst80 reason=promotion-total429>0.10,promotion-edge429>0.10" "${temp_dir}/target80.log" >/dev/null

echo "[transaction-read-promotion-pacing] paced VU16 regression fails"
paced_fail_input="${temp_dir}/paced-fail.tsv"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "vu16-paced" { $6 = "0.020000"; $7 = "0.020000" } { print }' \
  "${input_tsv}" >"${paced_fail_input}"
if TRANSACTION_READ_PROMOTION_PACING_NAME=promotion-pacing-paced-fail \
  TRANSACTION_READ_PROMOTION_PACING_INPUT_TSV="${paced_fail_input}" \
  TRANSACTION_READ_PROMOTION_PACING_OUTPUT_DIR="${temp_dir}/paced-fail-output" \
    "${runner}" >"${temp_dir}/paced-fail.log" 2>&1; then
  echo "paced VU16 429 regression unexpectedly passed" >&2
  exit 1
fi
grep -F "vu16-paced reason=paced-vu16-total429>0.01,paced-vu16-edge429>0.01" "${temp_dir}/paced-fail.log" >/dev/null

echo "[transaction-read-promotion-pacing] unpaced strict role fails"
strict_unpaced_input="${temp_dir}/strict-unpaced.tsv"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "vu16-unpaced" { $2 = "promotion-target" } { print }' \
  "${input_tsv}" >"${strict_unpaced_input}"
if TRANSACTION_READ_PROMOTION_PACING_NAME=promotion-pacing-strict-unpaced \
  TRANSACTION_READ_PROMOTION_PACING_INPUT_TSV="${strict_unpaced_input}" \
  TRANSACTION_READ_PROMOTION_PACING_OUTPUT_DIR="${temp_dir}/strict-unpaced-output" \
    "${runner}" >"${temp_dir}/strict-unpaced.log" 2>&1; then
  echo "unpaced VU16 strict role unexpectedly passed" >&2
  exit 1
fi
grep -F "vu16-unpaced reason=vu16-unpaced-must-be-observation" "${temp_dir}/strict-unpaced.log" >/dev/null
