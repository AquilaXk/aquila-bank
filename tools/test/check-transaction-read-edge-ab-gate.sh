#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-edge-ab-gate.sh"

echo "[transaction-read-edge-ab] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

direct_json="${temp_dir}/direct-summary.json"
edge_json="${temp_dir}/edge-summary.json"
nginx_tsv="${temp_dir}/nginx.tsv"
output_dir="${temp_dir}/output"

write_summary() {
  local file="$1"
  local total_429="$2"
  local edge_429="$3"
  local p95="$4"
  local delayed_rate="$5"
  cat >"${file}" <<JSON
{
  "metrics": {
    "aquila_transaction_429_rate": {"values": {"rate": ${total_429}}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": ${edge_429}}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": 0.01}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_edge_delayed_rate": {"values": {"rate": ${delayed_rate}}},
    "aquila_transaction_edge_delayed_count": {"values": {"count": 12}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": ${p95}}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": 170}},
    "aquila_transaction_hot_deep_cursor_ms": {"values": {"p(95)": 180}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": 190}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": 200}},
    "aquila_transaction_cold_deep_cursor_ms": {"values": {"p(95)": 210}}
  }
}
JSON
}

write_summary "${direct_json}" 0.010 0.000 240 0.00
write_summary "${edge_json}" 0.060 0.040 315 0.12

cat >"${nginx_tsv}" <<'TSV'
path	status	limit_req_status	request_time_ms	upstream_response_time_ms
/api/v1/transactions	200	PASSED	120	100
/api/v1/transactions/archive	200	DELAYED	315	105
TSV

echo "[transaction-read-edge-ab] print plan"
plan="$(
  EDGE_AB_GATE_NAME=edge-ab-check \
  EDGE_AB_RUN_ID=edge-ab-run-001 \
  EDGE_AB_DIRECT_SUMMARY_JSON="${direct_json}" \
  EDGE_AB_EDGE_SUMMARY_JSON="${edge_json}" \
  EDGE_AB_NGINX_TIMING_TSV="${nginx_tsv}" \
  EDGE_AB_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=edge-ab-check" <<<"${plan}" >/dev/null
grep -F "run_id=edge-ab-run-001" <<<"${plan}" >/dev/null
grep -F "direct_summary=${direct_json}" <<<"${plan}" >/dev/null
grep -F "edge_summary=${edge_json}" <<<"${plan}" >/dev/null
grep -F "nginx_timing=${nginx_tsv}" <<<"${plan}" >/dev/null

echo "[transaction-read-edge-ab] report"
output="$(
  EDGE_AB_GATE_NAME=edge-ab-check \
  EDGE_AB_RUN_ID=edge-ab-run-001 \
  EDGE_AB_DIRECT_SUMMARY_JSON="${direct_json}" \
  EDGE_AB_EDGE_SUMMARY_JSON="${edge_json}" \
  EDGE_AB_NGINX_TIMING_TSV="${nginx_tsv}" \
  EDGE_AB_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/edge-ab-check-edge-ab.tsv"
test "${report_md}" = "${output_dir}/edge-ab-check-edge-ab.md"
grep -F $'path\taccepted_p95_ms\ttotal_429_rate\tedge_429_rate\tbackend_429_rate\tdelayed_rate\tdelayed_count\t5xx_count' "${summary_tsv}" >/dev/null
grep -F $'direct\t240\t0.010\t0.000\t0.01\t0.00\t12\t0' "${summary_tsv}" >/dev/null
grep -F $'edge\t315\t0.060\t0.040\t0.01\t0.12\t12\t0' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "run id: edge-ab-run-001" "${report_md}" >/dev/null
grep -F "| accepted p95 delta | 75ms |" "${report_md}" >/dev/null
grep -F "| nginx queue delay max sample | 210ms |" "${report_md}" >/dev/null
grep -F "backend controller/service timer: aquila_transaction_read_http_stage_seconds" "${report_md}" >/dev/null
grep -F "repository timer: aquila_transaction_query_latency_seconds" "${report_md}" >/dev/null
grep -F "serialization proxy timer: response_mapping stage" "${report_md}" >/dev/null

echo "[transaction-read-edge-ab] fail report"
write_summary "${edge_json}" 0.060 0.040 390 0.12
if EDGE_AB_GATE_NAME=edge-ab-fail \
  EDGE_AB_RUN_ID=edge-ab-run-001 \
  EDGE_AB_DIRECT_SUMMARY_JSON="${direct_json}" \
  EDGE_AB_EDGE_SUMMARY_JSON="${edge_json}" \
  EDGE_AB_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "edge A/B gate unexpectedly passed p95 regression" >&2
  exit 1
fi

echo "[transaction-read-edge-ab] invalid input fails"
if EDGE_AB_DIRECT_SUMMARY_JSON="${direct_json}" "${runner}" --print-plan >/dev/null 2>&1; then
  echo "missing edge summary unexpectedly succeeded" >&2
  exit 1
fi
