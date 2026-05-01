#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-weighted-10m-soak-gate.sh"

echo "[transaction-read-weighted-10m] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_json="${temp_dir}/weighted-summary.json"
access_log="${temp_dir}/access.jsonl"
hikari_log="${temp_dir}/hikari.log"
resource_snapshot="${temp_dir}/resource.tsv"
output_dir="${temp_dir}/output"

cat >"${summary_json}" <<'JSON'
{
  "metrics": {
    "aquila_transaction_429_rate": {"values": {"rate": 0.080}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": 0.070}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": 0.0002}},
    "aquila_transaction_backend_429_count": {"values": {"count": 0}},
    "aquila_transaction_unknown_429_count": {"values": {"count": 0}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_edge_delayed_rate": {"values": {"rate": 0}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": 120}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": 110}},
    "aquila_transaction_hot_deep_cursor_ms": {"values": {"p(95)": 105}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": 125}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": 115}},
    "aquila_transaction_cold_deep_cursor_ms": {"values": {"p(95)": 108}},
    "aquila_transaction_retry_after_adaptive_multiplier": {"values": {"p(95)": 3, "max": 5}},
    "aquila_transaction_retry_after_reject_streak": {"values": {"p(95)": 3, "max": 5}},
    "aquila_transaction_preemptive_pacing_count": {"values": {"count": 120}},
    "aquila_transaction_preemptive_pacing_sleep_ms": {"values": {"p(95)": 80, "max": 140}}
  }
}
JSON

cat >"${access_log}" <<'JSONL'
{"request":"GET /api/v1/transactions?accountId=1 HTTP/1.1","status":200,"request_time":0.091,"upstream_status":"200","upstream_response_time":"0.090","k6_run_id":"weighted-10m"}
JSONL

: >"${hikari_log}"
cat >"${resource_snapshot}" <<'TSV'
component	cpu_percent	memory_mib	note
backend	58.0	720	oci-a1
postgres	41.0	2300	data-200gb
nginx	3.0	70	edge
TSV

echo "[transaction-read-weighted-10m] print plan"
plan="$(
  WEIGHTED_SOAK_10M_NAME=weighted-check \
  WEIGHTED_SOAK_10M_SUMMARY_JSON="${summary_json}" \
  WEIGHTED_SOAK_10M_ACCESS_LOG="${access_log}" \
  WEIGHTED_SOAK_10M_HIKARI_LOG="${hikari_log}" \
  WEIGHTED_SOAK_10M_RESOURCE_SNAPSHOT_TSV="${resource_snapshot}" \
  WEIGHTED_SOAK_10M_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=weighted-check" <<<"${plan}" >/dev/null
grep -F "duration=10m" <<<"${plan}" >/dev/null
grep -F "max_edge_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "max_backend_429_rate=0.0005" <<<"${plan}" >/dev/null
grep -F "max_backend_429_count=0" <<<"${plan}" >/dev/null
grep -F "live_soak_required=true" <<<"${plan}" >/dev/null

echo "[transaction-read-weighted-10m] pass report"
output="$(
  WEIGHTED_SOAK_10M_NAME=weighted-check \
  WEIGHTED_SOAK_10M_SUMMARY_JSON="${summary_json}" \
  WEIGHTED_SOAK_10M_ACCESS_LOG="${access_log}" \
  WEIGHTED_SOAK_10M_HIKARI_LOG="${hikari_log}" \
  WEIGHTED_SOAK_10M_RESOURCE_SNAPSHOT_TSV="${resource_snapshot}" \
  WEIGHTED_SOAK_10M_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
test "${report_md}" = "${output_dir}/weighted-check-weighted-10m-soak.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "| edge 429 rate | 0.070 |" "${report_md}" >/dev/null
grep -F "| backend 429 rate | 0.0002 |" "${report_md}" >/dev/null
grep -F "| backend 429 count | 0 |" "${report_md}" >/dev/null
grep -F "| 499 count | 0 |" "${report_md}" >/dev/null
grep -F "| 5xx count | 0 |" "${report_md}" >/dev/null
grep -F "| Hikari validation warnings | 0 |" "${report_md}" >/dev/null
grep -F "| retry-after adaptive multiplier p95 | 3 |" "${report_md}" >/dev/null
grep -F "| preemptive pacing count | 120 |" "${report_md}" >/dev/null
grep -F "| preemptive pacing sleep p95 ms | 80 |" "${report_md}" >/dev/null
grep -F "OCI 1억 row live run 기준" "${report_md}" >/dev/null

echo "[transaction-read-weighted-10m] fail report"
jq '.metrics.aquila_transaction_edge_429_rate.values.rate = 0.31 | .metrics.aquila_transaction_backend_429_count.values.count = 1 | .metrics.aquila_transaction_502_count.values.count = 1' \
  "${summary_json}" >"${summary_json}.fail"
if WEIGHTED_SOAK_10M_NAME=weighted-fail \
  WEIGHTED_SOAK_10M_SUMMARY_JSON="${summary_json}.fail" \
  WEIGHTED_SOAK_10M_ACCESS_LOG="${access_log}" \
  WEIGHTED_SOAK_10M_HIKARI_LOG="${hikari_log}" \
  WEIGHTED_SOAK_10M_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "weighted 10m gate unexpectedly passed edge 429/502 failure" >&2
  exit 1
fi

echo "[transaction-read-weighted-10m] Hikari fail report"
echo "WARN Failed to validate connection org.postgresql.jdbc.PgConnection@1" >"${hikari_log}.fail"
if WEIGHTED_SOAK_10M_NAME=weighted-hikari-fail \
  WEIGHTED_SOAK_10M_SUMMARY_JSON="${summary_json}" \
  WEIGHTED_SOAK_10M_ACCESS_LOG="${access_log}" \
  WEIGHTED_SOAK_10M_HIKARI_LOG="${hikari_log}.fail" \
  WEIGHTED_SOAK_10M_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "weighted 10m gate unexpectedly passed Hikari validation warning" >&2
  exit 1
fi
