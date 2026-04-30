#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-arrival-16-edge-budget-gate.sh"

echo "[transaction-read-arrival-16-edge-budget] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_dir="${temp_dir}/summaries"
output_dir="${temp_dir}/output"
mkdir -p "${summary_dir}"

write_summary() {
  local name="$1"
  local total_429="$2"
  local edge_429="$3"
  local backend_429="$4"
  local delayed_rate="$5"
  local delayed_count="$6"
  local p95="$7"
  cat >"${summary_dir}/${name}-summary.json" <<JSON
{
  "metrics": {
    "aquila_transaction_429_rate": {"values": {"rate": ${total_429}}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": ${edge_429}}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": ${backend_429}}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_edge_delayed_rate": {"values": {"rate": ${delayed_rate}}},
    "aquila_transaction_edge_delayed_count": {"values": {"count": ${delayed_count}}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": ${p95}}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": 180}},
    "aquila_transaction_hot_deep_cursor_ms": {"values": {"p(95)": 190}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": 185}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": 190}},
    "aquila_transaction_cold_deep_cursor_ms": {"values": {"p(95)": 195}}
  }
}
JSON
}

write_summary arrival-16 0.000 0.000 0.000 0.18 220 260
write_summary vu16-soak-2m 0.080 0.070 0.010 0.10 340 275
write_summary burst-48 0.280 0.260 0.020 0.05 120 190
write_summary burst-64 0.380 0.350 0.030 0.04 115 185
write_summary burst-80 0.520 0.490 0.030 0.03 100 180
write_summary burst-96 0.620 0.580 0.040 0.02 80 175

echo "[transaction-read-arrival-16-edge-budget] print plan"
plan="$(
  ARRIVAL16_EDGE_BUDGET_NAME=arrival16-check \
  ARRIVAL16_EDGE_BUDGET_SUMMARY_DIR="${summary_dir}" \
  ARRIVAL16_EDGE_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=arrival16-check" <<<"${plan}" >/dev/null
grep -F "summary_dir=${summary_dir}" <<<"${plan}" >/dev/null
grep -F "arrival_rate=16" <<<"${plan}" >/dev/null
grep -F "vu16_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "burst_rates=48,64,80,96" <<<"${plan}" >/dev/null

echo "[transaction-read-arrival-16-edge-budget] report"
output="$(
  ARRIVAL16_EDGE_BUDGET_NAME=arrival16-check \
  ARRIVAL16_EDGE_BUDGET_SUMMARY_DIR="${summary_dir}" \
  ARRIVAL16_EDGE_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/arrival16-check-edge-budget.tsv"
test "${report_md}" = "${output_dir}/arrival16-check-edge-budget.md"
grep -F $'scenario\tstatus\ttotal_429_rate\tedge_429_rate\tbackend_429_rate\tedge_delayed_rate\tedge_delayed_count\t5xx_count\taccepted_p95_ms\tbudget' "${summary_tsv}" >/dev/null
grep -F $'arrival-16\tpass\t0.000\t0.000\t0.000\t0.18\t220\t0\t260\t429<=0.000 delayed<0.25 5xx=0' "${summary_tsv}" >/dev/null
grep -F $'vu16-soak-2m\tpass\t0.080\t0.070\t0.010\t0.10\t340\t0\t275\t429<0.10 delayed<0.25 5xx=0' "${summary_tsv}" >/dev/null
grep -F $'burst-48\tpass\t0.280\t0.260\t0.020\t0.05\t120\t0\t195\t429<0.35 5xx=0' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "arrival-16 target: 429 = 0, 5xx = 0, edge delayed < 0.25" "${report_md}" >/dev/null
grep -F "VU16 soak target: 429 < 0.10" "${report_md}" >/dev/null
grep -F "burst reject curve" "${report_md}" >/dev/null

echo "[transaction-read-arrival-16-edge-budget] fail report"
write_summary arrival-16 0.000 0.000 0.000 0.31 220 260
if ARRIVAL16_EDGE_BUDGET_NAME=arrival16-fail \
  ARRIVAL16_EDGE_BUDGET_SUMMARY_DIR="${summary_dir}" \
  ARRIVAL16_EDGE_BUDGET_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "arrival-16 gate unexpectedly passed delayed ratio regression" >&2
  exit 1
fi

echo "[transaction-read-arrival-16-edge-budget] invalid input fails"
if ARRIVAL16_EDGE_BUDGET_SUMMARY_DIR="${summary_dir}" ARRIVAL16_EDGE_BUDGET_VU16_429_RATE=1.5 "${runner}" --print-plan >/dev/null 2>&1; then
  echo "invalid VU16 429 rate unexpectedly succeeded" >&2
  exit 1
fi
