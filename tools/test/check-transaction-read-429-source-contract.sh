#!/usr/bin/env bash
set -euo pipefail

k6_script="ops/k6/transaction-read-100m.js"
nginx_template="ops/nginx/nginx.conf"
deploy_script="ops/deploy/oci/bluegreen-deploy.sh"
aggregate_runner="tools/test/run-transaction-read-nginx-access-aggregate-artifact.sh"

echo "[transaction-read-429-source-contract] k6 source header fallback"
grep -F 'X-Aquila-429-Source' "${k6_script}" >/dev/null
grep -F 'detailedSource === "saturation-guard"' "${k6_script}" >/dev/null
grep -F 'detailedSource === "fairness-limiter"' "${k6_script}" >/dev/null
grep -F 'coarseSource === "backend"' "${k6_script}" >/dev/null
grep -F 'coarseSource === "nginx-edge"' "${k6_script}" >/dev/null

echo "[transaction-read-429-source-contract] nginx upstream source log fields"
grep -F '"upstream_reject_source":"$sent_http_x_aquila_429_source"' "${nginx_template}" >/dev/null
grep -F '"upstream_reject_reason":"$sent_http_x_aquila_reject_reason"' "${nginx_template}" >/dev/null
grep -F '"upstream_reject_source":"\$sent_http_x_aquila_429_source"' "${deploy_script}" >/dev/null
grep -F '"upstream_reject_reason":"\$sent_http_x_aquila_reject_reason"' "${deploy_script}" >/dev/null

echo "[transaction-read-429-source-contract] aggregate artifact source split"
bash -n "${aggregate_runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

access_log="${temp_dir}/access.jsonl"
output_dir="${temp_dir}/output"

cat >"${access_log}" <<'JSONL'
{"request":"GET /api/v1/transactions?accountId=1 HTTP/1.1","status":429,"request_time":0.009,"upstream_status":"429","upstream_response_time":"0.008","limit_req_status":"PASSED","reject_source":"backend","reject_reason":"fairness-limiter","upstream_reject_source":"fairness-limiter","upstream_reject_reason":"fairness-limiter","k6_run_id":"run-source"}
{"request":"GET /api/v1/transactions?accountId=2 HTTP/1.1","status":429,"request_time":0.001,"upstream_status":"","upstream_response_time":"","limit_req_status":"REJECTED","reject_source":"nginx-edge","reject_reason":"edge-rate-limit","upstream_reject_source":"","upstream_reject_reason":"edge-rate-limit","k6_run_id":"run-source"}
{"request":"GET /api/v1/transactions?accountId=3 HTTP/1.1","status":200,"request_time":0.088,"upstream_status":"200","upstream_response_time":"0.070","limit_req_status":"PASSED","reject_source":"","reject_reason":"","upstream_reject_source":"","upstream_reject_reason":"","k6_run_id":"run-source"}
JSONL

output="$(
  NGINX_ACCESS_AGGREGATE_NAME=source-contract \
  NGINX_ACCESS_AGGREGATE_LOG="${access_log}" \
  NGINX_ACCESS_AGGREGATE_OUTPUT_DIR="${output_dir}" \
    "${aggregate_runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/source-contract-nginx-access-aggregate.tsv"

test "${report_md}" = "${output_dir}/source-contract-nginx-access-aggregate.md"
grep -F "aggregate key: k6_run_id/status/limit_req_status/upstream_status/reject_source/upstream_reject_source/upstream_reject_reason" "${report_md}" >/dev/null
grep -F $'k6_run_id\tstatus\tlimit_req_status\tupstream_status\treject_source\treject_reason\tupstream_reject_source\tupstream_reject_reason\tcount\tdelayed_count\trejected_count\trequest_p95_ms\tupstream_p95_ms' "${summary_tsv}" >/dev/null
grep -F $'run-source\t429\tPASSED\t429\tbackend\tfairness-limiter\tfairness-limiter\tfairness-limiter\t1\t0\t1\t9.000\t8.000' "${summary_tsv}" >/dev/null
grep -F $'run-source\t429\tREJECTED\tnone\tnginx-edge\tedge-rate-limit\tnone\tedge-rate-limit\t1\t0\t1\t1.000\t0.000' "${summary_tsv}" >/dev/null
