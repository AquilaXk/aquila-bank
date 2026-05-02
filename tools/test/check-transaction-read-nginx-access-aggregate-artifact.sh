#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-nginx-access-aggregate-artifact.sh"

echo "[transaction-read-nginx-access-aggregate] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

access_log="${temp_dir}/access.jsonl"
output_dir="${temp_dir}/output"

cat >"${access_log}" <<'JSONL'
{"request":"GET /api/v1/transactions?accountId=1 HTTP/1.1","status":200,"request_time":0.091,"upstream_status":"200","upstream_response_time":"0.080","limit_req_status":"PASSED","reject_source":"","reject_reason":"","upstream_reject_source":"","upstream_reject_reason":"","k6_run_id":"run-a"}
{"request":"GET /api/v1/transactions?accountId=1 HTTP/1.1","status":200,"request_time":0.180,"upstream_status":"200","upstream_response_time":"0.082","limit_req_status":"DELAYED","reject_source":"","reject_reason":"","upstream_reject_source":"","upstream_reject_reason":"","k6_run_id":"run-a"}
{"request":"GET /api/v1/transactions?accountId=1 HTTP/1.1","status":429,"request_time":0.001,"upstream_status":"","upstream_response_time":"","limit_req_status":"REJECTED","reject_source":"nginx-edge","reject_reason":"edge-rate-limit","upstream_reject_source":"","upstream_reject_reason":"edge-rate-limit","k6_run_id":"run-a"}
{"request":"GET /api/v1/transactions/archive?accountId=2 HTTP/1.1","status":499,"request_time":30.100,"upstream_status":"","upstream_response_time":"","limit_req_status":"DELAYED","reject_source":"","reject_reason":"","upstream_reject_source":"","upstream_reject_reason":"","k6_run_id":"run-b"}
{"request":"GET /api/v1/accounts HTTP/1.1","status":200,"request_time":0.020,"upstream_status":"200","upstream_response_time":"0.019","limit_req_status":"PASSED","k6_run_id":"run-a"}
JSONL

echo "[transaction-read-nginx-access-aggregate] print plan"
plan="$(
  NGINX_ACCESS_AGGREGATE_NAME=nginx-agg-check \
  NGINX_ACCESS_AGGREGATE_LOG="${access_log}" \
  NGINX_ACCESS_AGGREGATE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=nginx-agg-check" <<<"${plan}" >/dev/null
grep -F "aggregate_key=k6_run_id,status,limit_req_status,upstream_status,reject_source,upstream_reject_source,upstream_reject_reason" <<<"${plan}" >/dev/null

echo "[transaction-read-nginx-access-aggregate] report"
output="$(
  NGINX_ACCESS_AGGREGATE_NAME=nginx-agg-check \
  NGINX_ACCESS_AGGREGATE_LOG="${access_log}" \
  NGINX_ACCESS_AGGREGATE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/nginx-agg-check-nginx-access-aggregate.tsv"
test "${report_md}" = "${output_dir}/nginx-agg-check-nginx-access-aggregate.md"
grep -F "aggregate key: k6_run_id/status/limit_req_status/upstream_status/reject_source/upstream_reject_source/upstream_reject_reason" "${report_md}" >/dev/null
grep -F "transaction_read_rows=4" "${report_md}" >/dev/null
grep -F "delayed ratio: 0.500000" "${report_md}" >/dev/null
grep -F $'k6_run_id\tstatus\tlimit_req_status\tupstream_status\treject_source\treject_reason\tupstream_reject_source\tupstream_reject_reason\tcount\tdelayed_count\trejected_count\trequest_p95_ms\tupstream_p95_ms' "${summary_tsv}" >/dev/null
grep -F $'run-a\t200\tDELAYED\t200\tnone\tnone\tnone\tnone\t1\t1\t0\t180.000\t82.000' "${summary_tsv}" >/dev/null
grep -F $'run-a\t429\tREJECTED\tnone\tnginx-edge\tedge-rate-limit\tnone\tedge-rate-limit\t1\t0\t1\t1.000\t0.000' "${summary_tsv}" >/dev/null
grep -F $'run-b\t499\tDELAYED\tnone\tnone\tnone\tnone\tnone\t1\t1\t0\t30100.000\t0.000' "${summary_tsv}" >/dev/null

echo "[transaction-read-nginx-access-aggregate] invalid json fails"
printf '{bad-json\n' >"${access_log}.bad"
if NGINX_ACCESS_AGGREGATE_NAME=nginx-agg-bad \
  NGINX_ACCESS_AGGREGATE_LOG="${access_log}.bad" \
  NGINX_ACCESS_AGGREGATE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "Nginx aggregate unexpectedly passed invalid JSONL" >&2
  exit 1
fi
