#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-runtime-policy-budget.sh"

echo "[transaction-read-runtime-policy] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/runtime-policy.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
profile	burst_rate	total_429_rate	edge_429_rate	backend_429_rate	unknown_429_count	five_xx_count	accepted_p95_ms	delayed_rate	retry_after_p95_ms	reject_streak_max	policy_candidate
burst48	48	0.0367	0.0367	0	0	0	280	0.05	190	2	burst64-smoothing-v1
burst64	64	0.0920	0.0880	0.0040	0	0	320	0.08	230	3	burst64-smoothing-v1
burst96	96	0.4200	0.4100	0.0100	0	0	340	0.02	260	3	burst64-smoothing-v1
vu16	16	0.1500	0.1400	0.0100	0	0	330	0.03	250	3	client-pacing-required
paced-weighted-vu16	16	0.00014	0	0.00014	0	0	118	0.70	120	1	client-pacing-default
TSV

echo "[transaction-read-runtime-policy] print plan"
plan="$(
  RUNTIME_POLICY_NAME=runtime-policy-check \
  RUNTIME_POLICY_INPUT_TSV="${input_tsv}" \
  RUNTIME_POLICY_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "required_profiles=burst48,burst64,burst96,vu16,paced-weighted-vu16" <<<"${plan}" >/dev/null
grep -F "burst64_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "paced_vu16_429_rate=0.005" <<<"${plan}" >/dev/null
grep -F "reject_streak_max=3" <<<"${plan}" >/dev/null
grep -F "accepted_p95_ms=350" <<<"${plan}" >/dev/null

echo "[transaction-read-runtime-policy] pass report"
output="$(
  RUNTIME_POLICY_NAME=runtime-policy-check \
  RUNTIME_POLICY_INPUT_TSV="${input_tsv}" \
  RUNTIME_POLICY_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/runtime-policy-check-runtime-policy.tsv"
test "${report_md}" = "${output_dir}/runtime-policy-check-runtime-policy.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "burst64 <= 10% 429" "${report_md}" >/dev/null
grep -F "paced weighted VU16 <= 0.005 429" "${report_md}" >/dev/null
grep -F "unpaced VU16 operating decision: client-pacing-required" "${report_md}" >/dev/null
grep -F "Retry-After reject streak <= 3" "${report_md}" >/dev/null
grep -F $'profile\tstatus\tdecision\ttotal_429_rate\tedge_429_rate\tbackend_429_rate\tunknown_429_count\tfive_xx_count\taccepted_p95_ms\tdelayed_rate\tretry_after_p95_ms\treject_streak_max\tpolicy_candidate' "${summary_tsv}" >/dev/null
grep -F $'burst64\tpass\tburst64-smoothing\t0.0920\t0.0880\t0.0040\t0\t0\t320\t0.08\t230\t3\tburst64-smoothing-v1' "${summary_tsv}" >/dev/null
grep -F $'vu16\tpass\tclient-pacing-required\t0.1500\t0.1400\t0.0100\t0\t0\t330\t0.03\t250\t3\tclient-pacing-required' "${summary_tsv}" >/dev/null
grep -F $'paced-weighted-vu16\tpass\tclient-pacing-default\t0.00014\t0\t0.00014\t0\t0\t118\t0.70\t120\t1\tclient-pacing-default' "${summary_tsv}" >/dev/null

echo "[transaction-read-runtime-policy] burst64 over budget fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "burst64" { $3 = "0.1407" } { print }' \
  "${input_tsv}" >"${input_tsv}.burst64-fail"
if RUNTIME_POLICY_NAME=runtime-policy-burst64 \
  RUNTIME_POLICY_INPUT_TSV="${input_tsv}.burst64-fail" \
  RUNTIME_POLICY_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "runtime policy unexpectedly passed burst64 429 over budget" >&2
  exit 1
fi

echo "[transaction-read-runtime-policy] reject streak fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "burst96" { $11 = "5" } { print }' \
  "${input_tsv}" >"${input_tsv}.streak-fail"
if RUNTIME_POLICY_NAME=runtime-policy-streak \
  RUNTIME_POLICY_INPUT_TSV="${input_tsv}.streak-fail" \
  RUNTIME_POLICY_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "runtime policy unexpectedly passed reject streak over budget" >&2
  exit 1
fi

echo "[transaction-read-runtime-policy] paced VU16 over budget fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "paced-weighted-vu16" { $3 = "0.020" } { print }' \
  "${input_tsv}" >"${input_tsv}.paced-fail"
if RUNTIME_POLICY_NAME=runtime-policy-paced \
  RUNTIME_POLICY_INPUT_TSV="${input_tsv}.paced-fail" \
  RUNTIME_POLICY_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "runtime policy unexpectedly passed paced weighted VU16 over budget" >&2
  exit 1
fi
