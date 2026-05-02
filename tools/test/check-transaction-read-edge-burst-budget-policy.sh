#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-edge-burst-budget-policy.sh"

echo "[transaction-read-edge-burst-policy] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/edge-burst-policy.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
burst_rate	edge_429_rate	backend_429_count	unknown_429_count	five_xx_count	nginx_499_count	accepted_p95_ms	retry_after_p95_ms	delayed_rate	policy_candidate
256	0.45	0	0	0	0	180	220	0.05	rate72-burst256-nodelay
512	0.70	0	0	0	0	195	240	0.03	rate72-burst512-nodelay
TSV

echo "[transaction-read-edge-burst-policy] print plan"
plan="$(
  EDGE_BURST_POLICY_NAME=edge-burst-policy-check \
  EDGE_BURST_POLICY_INPUT_TSV="${input_tsv}" \
  EDGE_BURST_POLICY_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=edge-burst-policy-check" <<<"${plan}" >/dev/null
grep -F "required_bursts=256,512" <<<"${plan}" >/dev/null
grep -F "burst256_allowed_429_rate=0.20..0.75" <<<"${plan}" >/dev/null
grep -F "burst512_allowed_429_rate=0.50..0.90" <<<"${plan}" >/dev/null
grep -F "classification=defensive-reject|under-protected|excessive-loss" <<<"${plan}" >/dev/null

echo "[transaction-read-edge-burst-policy] pass report"
output="$(
  EDGE_BURST_POLICY_NAME=edge-burst-policy-check \
  EDGE_BURST_POLICY_INPUT_TSV="${input_tsv}" \
  EDGE_BURST_POLICY_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/edge-burst-policy-check-edge-burst-budget-policy.tsv"
test "${report_md}" = "${output_dir}/edge-burst-policy-check-edge-burst-budget-policy.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "burst-256 allowed 429 budget: 0.20..0.75" "${report_md}" >/dev/null
grep -F "burst-512 allowed 429 budget: 0.50..0.90" "${report_md}" >/dev/null
grep -F "거절이 정상 방어인지, 과도한 손실인지" "${report_md}" >/dev/null
grep -F $'burst_rate\tstatus\tclassification\tedge_429_rate\tbackend_429_count\tunknown_429_count\tfive_xx_count\tnginx_499_count\taccepted_p95_ms\tretry_after_p95_ms\tdelayed_rate\tpolicy_candidate' "${summary_tsv}" >/dev/null
grep -F $'256\tpass\tdefensive-reject\t0.45\t0\t0\t0\t0\t180\t220\t0.05\trate72-burst256-nodelay' "${summary_tsv}" >/dev/null
grep -F $'512\tpass\tdefensive-reject\t0.70\t0\t0\t0\t0\t195\t240\t0.03\trate72-burst512-nodelay' "${summary_tsv}" >/dev/null

echo "[transaction-read-edge-burst-policy] excessive loss fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "512" { $2 = "0.95" } { print }' \
  "${input_tsv}" >"${input_tsv}.loss"
if EDGE_BURST_POLICY_NAME=edge-burst-loss \
  EDGE_BURST_POLICY_INPUT_TSV="${input_tsv}.loss" \
  EDGE_BURST_POLICY_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "edge burst policy unexpectedly passed excessive loss" >&2
  exit 1
fi

echo "[transaction-read-edge-burst-policy] unknown 429 fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "256" { $4 = "1" } { print }' \
  "${input_tsv}" >"${input_tsv}.unknown"
if EDGE_BURST_POLICY_NAME=edge-burst-unknown \
  EDGE_BURST_POLICY_INPUT_TSV="${input_tsv}.unknown" \
  EDGE_BURST_POLICY_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "edge burst policy unexpectedly passed unknown 429" >&2
  exit 1
fi

echo "[transaction-read-edge-burst-policy] missing burst fails"
awk -F '\t' '$1 != "512"' "${input_tsv}" >"${input_tsv}.missing"
if EDGE_BURST_POLICY_NAME=edge-burst-missing \
  EDGE_BURST_POLICY_INPUT_TSV="${input_tsv}.missing" \
  EDGE_BURST_POLICY_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "edge burst policy unexpectedly passed missing burst 512" >&2
  exit 1
fi
