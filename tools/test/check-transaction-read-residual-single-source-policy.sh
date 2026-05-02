#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-residual-single-source-policy.sh"

echo "[transaction-read-residual-single-source-policy] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/single-source-policy.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
run	source_mode	client_contract	edge_429_rate	backend_429_count	five_xx_count	nginx_499_count	retry_after_p95_ms	reject_streak_max
vu16-fixed	single-source-unpaced	diagnostic	0.1756	0	0	0	420	7
vu16-weighted	single-source-unpaced	diagnostic	0.1613	0	0	0	409	7
weighted-paced	single-source-paced	operating	0.0000	0	0	0	80	1
real-ip-multisource	multi-source	operating	0.0600	0	0	0	120	3
TSV

echo "[transaction-read-residual-single-source-policy] print plan"
plan="$(
  RESIDUAL_SINGLE_SOURCE_NAME=policy-check \
  RESIDUAL_SINGLE_SOURCE_INPUT_TSV="${input_tsv}" \
  RESIDUAL_SINGLE_SOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=policy-check" <<<"${plan}" >/dev/null
grep -F "operating_contract=paced-or-multi-source" <<<"${plan}" >/dev/null
grep -F "max_operating_edge_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "max_diagnostic_edge_429_rate=0.20" <<<"${plan}" >/dev/null

echo "[transaction-read-residual-single-source-policy] pass report"
output="$(
  RESIDUAL_SINGLE_SOURCE_NAME=policy-check \
  RESIDUAL_SINGLE_SOURCE_INPUT_TSV="${input_tsv}" \
  RESIDUAL_SINGLE_SOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/policy-check-residual-single-source-policy.tsv"
test "${report_md}" = "${output_dir}/policy-check-residual-single-source-policy.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "operating contract: paced-or-multi-source" "${report_md}" >/dev/null
grep -F "single-source unpaced decision: diagnostic-only" "${report_md}" >/dev/null
grep -F $'vu16-fixed\tpass\tdiagnostic-only\tsingle-source-unpaced\tdiagnostic\t0.1756\t0.20\t0\t0\t0\t420\t7' "${summary_tsv}" >/dev/null
grep -F $'weighted-paced\tpass\toperating\tsingle-source-paced\toperating\t0.0000\t0.10\t0\t0\t0\t80\t1' "${summary_tsv}" >/dev/null

echo "[transaction-read-residual-single-source-policy] operating fail report"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "weighted-paced" { $4 = 0.12 } { print }' \
  "${input_tsv}" >"${input_tsv}.operating-fail"
if RESIDUAL_SINGLE_SOURCE_NAME=policy-operating-fail \
  RESIDUAL_SINGLE_SOURCE_INPUT_TSV="${input_tsv}.operating-fail" \
  RESIDUAL_SINGLE_SOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "residual single-source policy unexpectedly passed operating 429 failure" >&2
  exit 1
fi

echo "[transaction-read-residual-single-source-policy] diagnostic ceiling fail report"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "vu16-fixed" { $4 = 0.25 } { print }' \
  "${input_tsv}" >"${input_tsv}.diagnostic-fail"
if RESIDUAL_SINGLE_SOURCE_NAME=policy-diagnostic-fail \
  RESIDUAL_SINGLE_SOURCE_INPUT_TSV="${input_tsv}.diagnostic-fail" \
  RESIDUAL_SINGLE_SOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "residual single-source policy unexpectedly passed diagnostic ceiling failure" >&2
  exit 1
fi
