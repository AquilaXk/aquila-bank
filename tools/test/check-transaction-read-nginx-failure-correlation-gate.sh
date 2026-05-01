#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-nginx-failure-correlation-gate.sh"

echo "[transaction-read-failure-correlation] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

access_log="${temp_dir}/access.jsonl"
output_dir="${temp_dir}/output"

cat >"${access_log}" <<'JSONL'
{"request":"GET /api/v1/transactions?accountId=1 HTTP/1.1","status":200,"request_time":0.091,"upstream_status":"200","upstream_response_time":"0.090","k6_run_id":"weighted-vu16"}
{"request":"GET /api/v1/transactions?accountId=1 HTTP/1.1","status":502,"request_time":0.001,"upstream_status":"502","upstream_response_time":"0.001","k6_run_id":"weighted-vu16"}
{"request":"GET /api/v1/transactions/archive?accountId=2 HTTP/1.1","status":499,"request_time":30.100,"upstream_status":"","upstream_response_time":"","k6_run_id":"burst-64"}
JSONL

echo "[transaction-read-failure-correlation] print plan"
plan="$(
  FAILURE_CORRELATION_NAME=failure-check \
  FAILURE_CORRELATION_ACCESS_LOG="${access_log}" \
  FAILURE_CORRELATION_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=failure-check" <<<"${plan}" >/dev/null
grep -F "access_log=${access_log}" <<<"${plan}" >/dev/null
grep -F "target_499_count=0" <<<"${plan}" >/dev/null
grep -F "target_502_count=0" <<<"${plan}" >/dev/null

echo "[transaction-read-failure-correlation] fail report"
if FAILURE_CORRELATION_NAME=failure-check \
  FAILURE_CORRELATION_ACCESS_LOG="${access_log}" \
  FAILURE_CORRELATION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "failure correlation unexpectedly passed 499/502 fixture" >&2
  exit 1
fi

FAILURE_CORRELATION_NAME=failure-check \
FAILURE_CORRELATION_ACCESS_LOG="${access_log}" \
FAILURE_CORRELATION_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
summary_tsv="${output_dir}/failure-check-failure-correlation.tsv"
grep -F $'run\tstatus\tnginx_499_count\tupstream_502_count\tk6_timeout_count\tnginx_keepalive_close_count\tupstream_close_count\trequest_time_0_2ms_502_count\tcause' "${summary_tsv}" >/dev/null
grep -F $'weighted-vu16\tfail\t0\t1\t0\t1\t0\t1\tnginx-keepalive-close' "${summary_tsv}" >/dev/null
grep -F $'burst-64\tfail\t1\t0\t1\t0\t0\t0\tk6-timeout' "${summary_tsv}" >/dev/null

echo "[transaction-read-failure-correlation] pass report"
cat >"${access_log}" <<'JSONL'
{"request":"GET /api/v1/transactions?accountId=1 HTTP/1.1","status":200,"request_time":0.091,"upstream_status":"200","upstream_response_time":"0.090","k6_run_id":"weighted-vu16"}
JSONL
output="$(
  FAILURE_CORRELATION_NAME=failure-pass \
  FAILURE_CORRELATION_ACCESS_LOG="${access_log}" \
  FAILURE_CORRELATION_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
test "${report_md}" = "${output_dir}/failure-pass-failure-correlation.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
