#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-429-source-gate.sh"

echo "[transaction-read-429-source] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_json="${temp_dir}/summary.json"
fail_json="${temp_dir}/fail-summary.json"
unknown_fail_json="${temp_dir}/unknown-fail-summary.json"
saturation_json="${temp_dir}/saturation-summary.json"
nginx_aggregate_tsv="${temp_dir}/nginx-aggregate.tsv"
output_dir="${temp_dir}/output"

cat >"${summary_json}" <<'JSON'
{
  "metrics": {
    "http_reqs": {"values": {"count": 1000}},
    "aquila_transaction_429_rate": {"values": {"rate": 0.08}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": 0.03}},
    "aquila_transaction_edge_429_count": {"values": {"count": 30}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": 0.05}},
    "aquila_transaction_backend_429_count": {"values": {"count": 50}},
    "aquila_transaction_unknown_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_unknown_429_count": {"values": {"count": 0}},
    "aquila_transaction_502_rate": {"values": {"rate": 0}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_rate": {"values": {"rate": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_accepted_200_rate": {"values": {"rate": 0.92}},
    "aquila_transaction_accepted_200_count": {"values": {"count": 920}}
  }
}
JSON

cat >"${unknown_fail_json}" <<'JSON'
{
  "metrics": {
    "http_reqs": {"values": {"count": 1000}},
    "aquila_transaction_429_rate": {"values": {"rate": 0.08}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": 0.03}},
    "aquila_transaction_edge_429_count": {"values": {"count": 30}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": 0.04}},
    "aquila_transaction_backend_429_count": {"values": {"count": 40}},
    "aquila_transaction_unknown_429_rate": {"values": {"rate": 0.01}},
    "aquila_transaction_unknown_429_count": {"values": {"count": 10}},
    "aquila_transaction_502_rate": {"values": {"rate": 0}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_rate": {"values": {"rate": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_accepted_200_rate": {"values": {"rate": 0.92}},
    "aquila_transaction_accepted_200_count": {"values": {"count": 920}}
  }
}
JSON

cat >"${fail_json}" <<'JSON'
{
  "metrics": {
    "http_reqs": {"values": {"count": 1000}},
    "aquila_transaction_429_rate": {"values": {"rate": 0.12}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": 0.11}},
    "aquila_transaction_edge_429_count": {"values": {"count": 110}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": 0.01}},
    "aquila_transaction_backend_429_count": {"values": {"count": 10}},
    "aquila_transaction_unknown_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_unknown_429_count": {"values": {"count": 0}},
    "aquila_transaction_502_rate": {"values": {"rate": 0.001}},
    "aquila_transaction_502_count": {"values": {"count": 1}},
    "aquila_transaction_503_rate": {"values": {"rate": 0.001}},
    "aquila_transaction_503_count": {"values": {"count": 1}},
    "aquila_transaction_accepted_200_rate": {"values": {"rate": 0.879}},
    "aquila_transaction_accepted_200_count": {"values": {"count": 879}}
  }
}
JSON

cat >"${saturation_json}" <<'JSON'
{
  "metrics": {
    "http_reqs": {"values": {"count": 1000}},
    "aquila_transaction_429_rate": {"values": {"rate": 0.12}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": 0.11}},
    "aquila_transaction_edge_429_count": {"values": {"count": 110}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": 0.01}},
    "aquila_transaction_backend_429_count": {"values": {"count": 10}},
    "aquila_transaction_unknown_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_unknown_429_count": {"values": {"count": 0}},
    "aquila_transaction_502_rate": {"values": {"rate": 0}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_rate": {"values": {"rate": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_accepted_200_rate": {"values": {"rate": 0.879}},
    "aquila_transaction_accepted_200_count": {"values": {"count": 879}}
  }
}
JSON

cat >"${nginx_aggregate_tsv}" <<'TSV'
k6_run_id	status	limit_req_status	upstream_status	reject_source	reject_reason	upstream_reject_source	upstream_reject_reason	count	delayed_count	rejected_count	request_p95_ms	upstream_p95_ms
vu16	200	PASSED	200	none	none	none	none	990	0	0	10.000	10.000
vu16	429	PASSED	429	backend	backend-admission	backend-admission	backend-admission	1	0	1	40.000	40.000
vu16	429	PASSED	429	backend	fairness-limiter	fairness-limiter	fairness-limiter	4	0	4	45.000	45.000
vu16	429	REJECTED	none	nginx-edge	edge-rate-limit	none	edge-rate-limit	5	0	5	0.000	0.000
TSV

echo "[transaction-read-429-source] print plan"
plan="$(
  SOURCE_429_GATE_NAME=source-check \
  SOURCE_429_SUMMARY_JSON="${summary_json}" \
  SOURCE_429_OUTPUT_DIR="${output_dir}" \
  SOURCE_429_RUN_ID=public-arrival-8 \
  SOURCE_429_TOTAL_FAIL_RATE=0.10 \
  SOURCE_429_EDGE_FAIL_RATE=0.10 \
  SOURCE_429_BACKEND_FAIL_RATE=0.10 \
    "${runner}" --print-plan
)"
grep -F "gate=source-check" <<<"${plan}" >/dev/null
grep -F "summary_json=${summary_json}" <<<"${plan}" >/dev/null
grep -F "run_id=public-arrival-8" <<<"${plan}" >/dev/null
grep -F "output_dir=${output_dir}" <<<"${plan}" >/dev/null
grep -F "total_fail_rate=0.10" <<<"${plan}" >/dev/null
grep -F "edge_fail_rate=0.10" <<<"${plan}" >/dev/null
grep -F "backend_fail_rate=0.10" <<<"${plan}" >/dev/null

echo "[transaction-read-429-source] pass report"
output="$(
  SOURCE_429_GATE_NAME=source-check \
  SOURCE_429_SUMMARY_JSON="${summary_json}" \
  SOURCE_429_OUTPUT_DIR="${output_dir}" \
  SOURCE_429_RUN_ID=public-arrival-8 \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/source-check-429-source.tsv"
test "${report_md}" = "${output_dir}/source-check-429-source.md"
grep -F $'source\tstatus\trate\tcount\tfail_threshold' "${summary_tsv}" >/dev/null
grep -F $'edge\tpass\t0.03\t30\t0.10' "${summary_tsv}" >/dev/null
grep -F $'backend\tpass\t0.05\t50\t0.10' "${summary_tsv}" >/dev/null
grep -F $'unknown\tpass\t0\t0\t0' "${summary_tsv}" >/dev/null
grep -F $'502\tpass\t0\t0\t0' "${summary_tsv}" >/dev/null
grep -F $'503\tpass\t0\t0\t0' "${summary_tsv}" >/dev/null
grep -F $'accepted_200\tobserve\t0.92\t920\tn/a' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "run_id=public-arrival-8" "${report_md}" >/dev/null
grep -F "| edge 429 | pass | 0.03 | 30 |" "${report_md}" >/dev/null
grep -F "| backend admission 429 | pass | 0.05 | 50 |" "${report_md}" >/dev/null
grep -F "| unknown 429 | pass | 0 | 0 |" "${report_md}" >/dev/null
grep -F "| 503 | pass | 0 | 0 |" "${report_md}" >/dev/null
grep -F "| accepted 200 | observe | 0.92 | 920 |" "${report_md}" >/dev/null
grep -F "unknown 429 hard-zero" "${report_md}" >/dev/null

echo "[transaction-read-429-source] saturation aggregate source budget"
saturation_output="$(
  SOURCE_429_GATE_NAME=source-vu16 \
  SOURCE_429_SUMMARY_JSON="${saturation_json}" \
  SOURCE_429_OUTPUT_DIR="${output_dir}" \
  SOURCE_429_RUN_ID=vu16 \
  SOURCE_429_GATE_MODE=saturation-observation \
  SOURCE_429_TOTAL_FAIL_RATE=1 \
  SOURCE_429_EDGE_FAIL_RATE=1 \
  SOURCE_429_BACKEND_FAIL_RATE=0.02 \
  SOURCE_429_NGINX_AGGREGATE_TSV="${nginx_aggregate_tsv}" \
    "${runner}"
)"
saturation_report_md="$(tail -1 <<<"${saturation_output}")"
saturation_summary_tsv="${output_dir}/source-vu16-429-source.tsv"
test "${saturation_report_md}" = "${output_dir}/source-vu16-429-source.md"
grep -F $'total_429\tpass\t0.12\tn/a\t1' "${saturation_summary_tsv}" >/dev/null
grep -F $'edge\tpass\t0.11\t110\t1' "${saturation_summary_tsv}" >/dev/null
grep -F $'backend\tpass\t0.01\t10\t0.02' "${saturation_summary_tsv}" >/dev/null
grep -F $'backend-admission\tpass\t0.001000\t1\t0.02' "${saturation_summary_tsv}" >/dev/null
grep -F $'fairness-limiter\tpass\t0.004000\t4\t0.02' "${saturation_summary_tsv}" >/dev/null
grep -F $'nginx_499\tpass\t0.000000\t0\t0' "${saturation_summary_tsv}" >/dev/null
grep -F $'nginx_5xx\tpass\t0.000000\t0\t0' "${saturation_summary_tsv}" >/dev/null
grep -F "gate_mode=saturation-observation" "${saturation_report_md}" >/dev/null
grep -F "nginx aggregate rows=1000" "${saturation_report_md}" >/dev/null
grep -F "| backend-admission | pass | 0.001000 | 1 |" "${saturation_report_md}" >/dev/null
grep -F "| fairness-limiter | pass | 0.004000 | 4 |" "${saturation_report_md}" >/dev/null

echo "[transaction-read-429-source] fail report"
if SOURCE_429_GATE_NAME=source-fail \
  SOURCE_429_SUMMARY_JSON="${fail_json}" \
  SOURCE_429_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "source gate unexpectedly passed edge 429/502 failure" >&2
  exit 1
fi

echo "[transaction-read-429-source] unknown 429 hard-zero"
if SOURCE_429_GATE_NAME=source-unknown-fail \
  SOURCE_429_SUMMARY_JSON="${unknown_fail_json}" \
  SOURCE_429_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "source gate unexpectedly passed unknown 429" >&2
  exit 1
fi

echo "[transaction-read-429-source] runner contract"
grep -F "aquila_transaction_edge_429_rate" "${runner}" >/dev/null
grep -F "aquila_transaction_backend_429_rate" "${runner}" >/dev/null
grep -F "aquila_transaction_unknown_429_rate" "${runner}" >/dev/null
grep -F "aquila_transaction_502_count" "${runner}" >/dev/null
grep -F "aquila_transaction_503_count" "${runner}" >/dev/null
grep -F "aquila_transaction_accepted_200_count" "${runner}" >/dev/null
grep -F "unknown 429 hard-zero" "${runner}" >/dev/null
grep -F "SOURCE_429_NGINX_AGGREGATE_TSV" "${runner}" >/dev/null
grep -F "backend-admission" "${runner}" >/dev/null
grep -F "fairness-limiter" "${runner}" >/dev/null
grep -F 'source === "backend"' ops/k6/transaction-read-100m.js >/dev/null
grep -F 'reason === "saturation-guard"' ops/k6/transaction-read-100m.js >/dev/null
grep -F 'const constantVusGateRole = __ENV.K6_CONSTANT_VUS_GATE_ROLE || "not-selected";' ops/k6/transaction-read-100m.js >/dev/null
grep -F 'const saturationObservationMode =' ops/k6/transaction-read-100m.js >/dev/null
grep -F 'const rejectionObservationMode = overloadMode || saturationObservationMode;' ops/k6/transaction-read-100m.js >/dev/null
grep -F 'if (is429 && rejectionObservationMode)' ops/k6/transaction-read-100m.js >/dev/null
grep -F 'K6_CONSTANT_VUS_GATE_ROLE="${K6_CONSTANT_VUS_GATE_ROLE:-not-selected}"' tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F 'K6_CONSTANT_VUS_GATE_ROLE=${K6_CONSTANT_VUS_GATE_ROLE}' tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F '-e K6_CONSTANT_VUS_GATE_ROLE="${K6_CONSTANT_VUS_GATE_ROLE}"' tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null

echo "[transaction-read-429-source] invalid input fails"
if SOURCE_429_FAIL_RATE=2 "${runner}" --print-plan >/dev/null 2>&1; then
  echo "invalid SOURCE_429_FAIL_RATE unexpectedly succeeded" >&2
  exit 1
fi
if SOURCE_429_BACKEND_FAIL_RATE=2 "${runner}" --print-plan >/dev/null 2>&1; then
  echo "invalid SOURCE_429_BACKEND_FAIL_RATE unexpectedly succeeded" >&2
  exit 1
fi
