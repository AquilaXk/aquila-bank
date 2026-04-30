#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-oci-public-api-arrival-capacity-gate.sh"

echo "[oci-public-arrival-capacity] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_dir="${temp_dir}/summaries"
output_dir="${temp_dir}/output"
mkdir -p "${summary_dir}"

write_summary() {
  local rate="$1"
  local total_429="$2"
  local edge_429="$3"
  local backend_429="$4"
  local p95="$5"
  local count_502="${6:-0}"
  local delayed_rate="${7:-0.05}"
  local count_503="${8:-0}"
  cat >"${summary_dir}/arrival-${rate}-summary.json" <<JSON
{
  "metrics": {
    "http_reqs": {"values": {"count": 1000}},
    "http_req_duration": {"values": {"p(95)": ${p95}}},
    "aquila_transaction_429_rate": {"values": {"rate": ${total_429}}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": ${edge_429}}},
    "aquila_transaction_edge_429_count": {"values": {"count": 30}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": ${backend_429}}},
    "aquila_transaction_backend_429_count": {"values": {"count": 50}},
    "aquila_transaction_unknown_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_unknown_429_count": {"values": {"count": 0}},
    "aquila_transaction_502_rate": {"values": {"rate": 0}},
    "aquila_transaction_502_count": {"values": {"count": ${count_502}}},
    "aquila_transaction_503_rate": {"values": {"rate": 0}},
    "aquila_transaction_503_count": {"values": {"count": ${count_503}}},
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
write_summary 10 0.080 0.040 0.040 330 0 0.12
write_summary 16 0.000 0.000 0.000 325 0 0.18

echo "[oci-public-arrival-capacity] print plan"
plan="$(
  OCI_PUBLIC_ARRIVAL_GATE_NAME=arrival-check \
  OCI_PUBLIC_ARRIVAL_SUMMARY_DIR="${summary_dir}" \
  OCI_PUBLIC_ARRIVAL_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "gate=arrival-check" <<<"${plan}" >/dev/null
grep -F "rates=4,5,6,7,8,10,16" <<<"${plan}" >/dev/null
grep -F "strict_zero_rates=16" <<<"${plan}" >/dev/null
grep -F "fail_rate=0.10" <<<"${plan}" >/dev/null
grep -F "delayed_fail_rate=0.25" <<<"${plan}" >/dev/null
grep -F "accepted_p95_ms=350" <<<"${plan}" >/dev/null
grep -F "summary_dir=${summary_dir}" <<<"${plan}" >/dev/null

echo "[oci-public-arrival-capacity] pass report"
output="$(
  OCI_PUBLIC_ARRIVAL_GATE_NAME=arrival-check \
  OCI_PUBLIC_ARRIVAL_SUMMARY_DIR="${summary_dir}" \
  OCI_PUBLIC_ARRIVAL_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/arrival-check-arrival-capacity.tsv"
test "${report_md}" = "${output_dir}/arrival-check-arrival-capacity.md"
grep -F $'arrival_rate\tstatus\ttotal_429_rate\tedge_429_rate\tbackend_429_rate\tunknown_429_count\ttransaction_502_count\ttransaction_503_count\tedge_delayed_rate\tedge_delayed_count\taccepted_p95_ms\taccepted_200_rate\tsource_report_md\tsummary_json' "${summary_tsv}" >/dev/null
grep -F $'8\tpass\t0.090\t0.040\t0.050\t0\t0\t0\t0.05\t50\t320\t0.92' "${summary_tsv}" >/dev/null
grep -F $'10\tpass\t0.080\t0.040\t0.040\t0\t0\t0\t0.12\t50\t330\t0.92' "${summary_tsv}" >/dev/null
grep -F $'16\tpass\t0.000\t0.000\t0.000\t0\t0\t0\t0.18\t50\t325\t0.92' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "arrival-16rps target: 429 = 0, edge delayed ratio < 0.25, 5xx = 0, accepted p95 < 350ms" "${report_md}" >/dev/null
grep -F "| 10 | pass | 0.080 | 0.040 | 0.040 | 0 | 0 | 0.12 | 330 |" "${report_md}" >/dev/null
grep -F "| 16 | pass | 0.000 | 0.000 | 0.000 | 0 | 0 | 0.18 | 325 |" "${report_md}" >/dev/null

echo "[oci-public-arrival-capacity] fail report"
write_summary 10 0.080 0.040 0.040 330 0 0.12
write_summary 16 0.001 0.001 0.000 325 0 0.18
if OCI_PUBLIC_ARRIVAL_GATE_NAME=arrival-fail \
  OCI_PUBLIC_ARRIVAL_SUMMARY_DIR="${summary_dir}" \
  OCI_PUBLIC_ARRIVAL_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "arrival gate unexpectedly passed 429/p95/502 failure" >&2
  exit 1
fi

echo "[oci-public-arrival-capacity] runner contract"
grep -F "tools/test/run-transaction-read-429-source-gate.sh" "${runner}" >/dev/null
grep -F "K6_SCENARIO_MODE=constant-arrival-rate" "${runner}" >/dev/null
grep -F "K6_RATE=" "${runner}" >/dev/null
grep -F "4,5,6,7,8,10,16" "${runner}" >/dev/null
grep -F "OCI_PUBLIC_ARRIVAL_STRICT_ZERO_RATES" "${runner}" >/dev/null
grep -F "accepted_p95_ms" "${runner}" >/dev/null
grep -F "edge_delayed_rate" "${runner}" >/dev/null

echo "[oci-public-arrival-capacity] invalid input fails"
if OCI_PUBLIC_ARRIVAL_RATES=bad "${runner}" --print-plan >/dev/null 2>&1; then
  echo "invalid arrival rates unexpectedly succeeded" >&2
  exit 1
fi
