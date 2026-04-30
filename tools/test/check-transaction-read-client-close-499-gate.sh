#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-client-close-499-gate.sh"

echo "[transaction-read-499] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

status_tsv="${temp_dir}/client-close.tsv"
output_dir="${temp_dir}/output"

cat >"${status_tsv}" <<'TSV'
run	nginx_499_count	k6_timeout_count	nginx_keepalive_close_count	upstream_close_count
arrival-16	0	0	0	0
burst-48	0	0	0	0
TSV

echo "[transaction-read-499] print plan"
plan="$(
  CLIENT_CLOSE_499_NAME=client-close-check \
  CLIENT_CLOSE_499_INPUT_TSV="${status_tsv}" \
  CLIENT_CLOSE_499_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=client-close-check" <<<"${plan}" >/dev/null
grep -F "input_tsv=${status_tsv}" <<<"${plan}" >/dev/null
grep -F "target_499_count=0" <<<"${plan}" >/dev/null

echo "[transaction-read-499] report"
output="$(
  CLIENT_CLOSE_499_NAME=client-close-check \
  CLIENT_CLOSE_499_INPUT_TSV="${status_tsv}" \
  CLIENT_CLOSE_499_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/client-close-check-499.tsv"
test "${report_md}" = "${output_dir}/client-close-check-499.md"
grep -F $'run\tstatus\tnginx_499_count\tcause\tk6_timeout_count\tnginx_keepalive_close_count\tupstream_close_count' "${summary_tsv}" >/dev/null
grep -F $'arrival-16\tpass\t0\tnone\t0\t0\t0' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "499 target: 0" "${report_md}" >/dev/null

echo "[transaction-read-499] fail report"
cat >"${status_tsv}" <<'TSV'
run	nginx_499_count	k6_timeout_count	nginx_keepalive_close_count	upstream_close_count
arrival-16	1	1	0	0
TSV
if CLIENT_CLOSE_499_NAME=client-close-fail \
  CLIENT_CLOSE_499_INPUT_TSV="${status_tsv}" \
  CLIENT_CLOSE_499_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "499 gate unexpectedly passed client timeout regression" >&2
  exit 1
fi

CLIENT_CLOSE_499_NAME=client-close-fail \
CLIENT_CLOSE_499_INPUT_TSV="${status_tsv}" \
CLIENT_CLOSE_499_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F $'arrival-16\tfail\t1\tk6-timeout\t1\t0\t0' "${output_dir}/client-close-fail-499.tsv" >/dev/null
