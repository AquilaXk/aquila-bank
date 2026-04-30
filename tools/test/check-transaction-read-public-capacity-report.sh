#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-public-capacity-report.sh"

echo "[transaction-read-public-report] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_dir="${temp_dir}/summaries"
output_dir="${temp_dir}/docs"
resource_snapshot="${temp_dir}/resource.tsv"
mkdir -p "${summary_dir}"

write_summary() {
  local rate="$1"
  local total_429="$2"
  local edge_429="$3"
  local backend_429="$4"
  local p95="$5"
  local delayed_rate="${6:-0.05}"
  cat >"${summary_dir}/arrival-${rate}-summary.json" <<JSON
{
  "metrics": {
    "http_reqs": {"values": {"count": 1000}},
    "aquila_transaction_429_rate": {"values": {"rate": ${total_429}}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": ${edge_429}}},
    "aquila_transaction_edge_429_count": {"values": {"count": 30}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": ${backend_429}}},
    "aquila_transaction_backend_429_count": {"values": {"count": 50}},
    "aquila_transaction_unknown_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_unknown_429_count": {"values": {"count": 0}},
    "aquila_transaction_502_rate": {"values": {"rate": 0}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_rate": {"values": {"rate": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_edge_delayed_rate": {"values": {"rate": ${delayed_rate}}},
    "aquila_transaction_edge_delayed_count": {"values": {"count": 50}},
    "aquila_transaction_accepted_200_rate": {"values": {"rate": 0.92}},
    "aquila_transaction_accepted_200_count": {"values": {"count": 920}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": ${p95}}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": 180}},
    "aquila_transaction_hot_deep_cursor_ms": {"values": {"p(95)": 210}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": 220}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": 240}},
    "aquila_transaction_cold_deep_cursor_ms": {"values": {"p(95)": 260}}
  }
}
JSON
}

write_summary 4 0.000 0.000 0.000 180
write_summary 5 0.020 0.010 0.010 210
write_summary 6 0.040 0.020 0.020 240
write_summary 7 0.070 0.030 0.040 280
write_summary 8 0.090 0.040 0.050 320
write_summary 10 0.080 0.040 0.040 330 0.12

cat >"${resource_snapshot}" <<'TSV'
component	cpu_percent	memory_mib	note
backend	64.2	640	oci-a1
postgres	42.1	2048	data-200gb
nginx	3.2	64	edge
TSV

echo "[transaction-read-public-report] print plan"
plan="$(
  PUBLIC_CAPACITY_REPORT_NAME=public-report-check \
  PUBLIC_CAPACITY_REPORT_OUTPUT_DIR="${output_dir}" \
  PUBLIC_CAPACITY_REPORT_ARRIVAL_SUMMARY_DIR="${summary_dir}" \
  PUBLIC_CAPACITY_REPORT_RESOURCE_SNAPSHOT_TSV="${resource_snapshot}" \
    "${runner}" --print-plan
)"
grep -F "name=public-report-check" <<<"${plan}" >/dev/null
grep -F "output=${output_dir}/public-report-check.md" <<<"${plan}" >/dev/null
grep -F "arrival_summary_dir=${summary_dir}" <<<"${plan}" >/dev/null
grep -F "resource_snapshot=${resource_snapshot}" <<<"${plan}" >/dev/null

echo "[transaction-read-public-report] report"
output="$(
  PUBLIC_CAPACITY_REPORT_NAME=public-report-check \
  PUBLIC_CAPACITY_REPORT_OUTPUT_DIR="${output_dir}" \
  PUBLIC_CAPACITY_REPORT_ARRIVAL_SUMMARY_DIR="${summary_dir}" \
  PUBLIC_CAPACITY_REPORT_RESOURCE_SNAPSHOT_TSV="${resource_snapshot}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
test "${report_md}" = "${output_dir}/public-report-check.md"
grep -F "# public-report-check" "${report_md}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "| arrival capacity | pass |" "${report_md}" >/dev/null
grep -F "| budget matrix | pass |" "${report_md}" >/dev/null
grep -F "| 10 | pass | 0.080 | 0.040 | 0.040 | 0 | 0 | 0 | 0.12 | 330 | 0.92 |" "${report_md}" >/dev/null
grep -F "| backend | 64.2 | 640 | oci-a1 |" "${report_md}" >/dev/null
grep -F "## Next Bottleneck Candidates" "${report_md}" >/dev/null
grep -F "arrival-10rps sample is inside 429/delay/p95/5xx budget" "${report_md}" >/dev/null

echo "[transaction-read-public-report] runner contract"
grep -F "run-oci-public-api-arrival-capacity-gate.sh" "${runner}" >/dev/null
grep -F "run-oci-a1-edge-backend-budget-matrix.sh" "${runner}" >/dev/null
grep -F "docs/performance-results" "${runner}" >/dev/null
grep -F "resource snapshot" "${runner}" >/dev/null
