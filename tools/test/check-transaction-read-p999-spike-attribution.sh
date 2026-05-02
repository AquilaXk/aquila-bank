#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-p999-spike-attribution.sh"

echo "[transaction-read-p999-attribution] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_json="${temp_dir}/transaction-summary.json"
layer_tsv="${temp_dir}/layer.tsv"
output_dir="${temp_dir}/output"

cat >"${summary_json}" <<'JSON'
{
  "metrics": {
    "http_req_duration": {"values": {"p(99.9)": 260.0, "max": 420.0}},
    "http_req_failed": {"values": {"rate": 0}},
    "aquila_transaction_429_rate": {"values": {"rate": 0.095}},
    "aquila_transaction_edge_delayed_rate": {"values": {"rate": 0.85}},
    "aquila_transaction_edge_delayed_count": {"values": {"count": 3200}},
    "aquila_transaction_retry_after_sleep_ms": {"values": {"p(95)": 180, "max": 250}},
    "aquila_transaction_hot_first_ms": {"values": {"p(99.9)": 198.0, "max": 410.0}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(99.9)": 160.0, "max": 300.0}},
    "aquila_transaction_cold_first_ms": {"values": {"p(99.9)": 170.0, "max": 280.0}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(99.9)": 155.0, "max": 260.0}}
  }
}
JSON

cat >"${layer_tsv}" <<'TSV'
layer	metric	value	source	note
authorization	p999_ms	12	prometheus	account access lookup
serialization	p999_ms	44	jfr	response mapping/json
db	query_p999_ms	0.8	explain	index scan
backend	server_request_max_ms	82	prometheus	http server
nginx	upstream_response_max_ms	94	access-log	upstream response time
TSV

echo "[transaction-read-p999-attribution] print plan"
plan="$(
  P999_ATTRIBUTION_NAME=p999-attribution-check \
  P999_ATTRIBUTION_K6_SUMMARY_JSON="${summary_json}" \
  P999_ATTRIBUTION_LAYER_TSV="${layer_tsv}" \
  P999_ATTRIBUTION_OUTPUT_DIR="${output_dir}" \
  P999_ATTRIBUTION_WARN_MS=180 \
  P999_ATTRIBUTION_FAIL_MS=500 \
    "${runner}" --print-plan
)"
grep -F "name=p999-attribution-check" <<<"${plan}" >/dev/null
grep -F "k6_summary_json=${summary_json}" <<<"${plan}" >/dev/null
grep -F "layer_tsv=${layer_tsv}" <<<"${plan}" >/dev/null
grep -F "warn_ms=180" <<<"${plan}" >/dev/null
grep -F "fail_ms=500" <<<"${plan}" >/dev/null
grep -F "layers=nginx,network,authorization,serialization,db,backend" <<<"${plan}" >/dev/null

echo "[transaction-read-p999-attribution] report"
output="$(
  P999_ATTRIBUTION_NAME=p999-attribution-check \
  P999_ATTRIBUTION_K6_SUMMARY_JSON="${summary_json}" \
  P999_ATTRIBUTION_LAYER_TSV="${layer_tsv}" \
  P999_ATTRIBUTION_OUTPUT_DIR="${output_dir}" \
  P999_ATTRIBUTION_WARN_MS=180 \
  P999_ATTRIBUTION_FAIL_MS=500 \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
attribution_tsv="${output_dir}/p999-attribution-check-p999-attribution.tsv"
test "${report_md}" = "${output_dir}/p999-attribution-check-p999-attribution.md"
test -s "${attribution_tsv}"
grep -F $'layer\tmetric\tvalue\tsource\tnote' "${attribution_tsv}" >/dev/null
grep -F $'network\thttp_req_duration_p999_ms\t260.0\tk6:http_req_duration\tclient-visible total path' "${attribution_tsv}" >/dev/null
grep -F $'nginx\tedge_delayed_rate\t0.85\tk6:aquila_transaction_edge_delayed_rate\tdelayed 200 queue dependency' "${attribution_tsv}" >/dev/null
grep -F $'authorization\tp999_ms\t12\tprometheus\taccount access lookup' "${attribution_tsv}" >/dev/null
grep -F $'serialization\tp999_ms\t44\tjfr\tresponse mapping/json' "${attribution_tsv}" >/dev/null
grep -F $'db\tquery_p999_ms\t0.8\texplain\tindex scan' "${attribution_tsv}" >/dev/null
grep -F "tail_status=warn" "${report_md}" >/dev/null
grep -F "max client-visible latency ms: 420.0" "${report_md}" >/dev/null
grep -F "edge delayed rate: 0.85" "${report_md}" >/dev/null
grep -F "Nginx delayed queue, network, authorization, serialization, DB query" "${report_md}" >/dev/null

echo "[transaction-read-p999-attribution] fail report"
jq '.metrics.http_req_duration.values.max = 650' "${summary_json}" >"${summary_json}.fail"
if P999_ATTRIBUTION_NAME=p999-attribution-fail \
  P999_ATTRIBUTION_K6_SUMMARY_JSON="${summary_json}.fail" \
  P999_ATTRIBUTION_LAYER_TSV="${layer_tsv}" \
  P999_ATTRIBUTION_OUTPUT_DIR="${output_dir}" \
  P999_ATTRIBUTION_WARN_MS=180 \
  P999_ATTRIBUTION_FAIL_MS=500 \
    "${runner}" >/dev/null 2>&1; then
  echo "p999 attribution unexpectedly passed over fail threshold" >&2
  exit 1
fi

echo "[transaction-read-p999-attribution] invalid input fails"
if P999_ATTRIBUTION_NAME=missing-json "${runner}" >/dev/null 2>&1; then
  echo "missing summary unexpectedly succeeded" >&2
  exit 1
fi
