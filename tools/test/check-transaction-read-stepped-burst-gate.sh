#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-stepped-burst-gate.sh"

echo "[transaction-read-stepped-burst] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_dir="${temp_dir}/summaries"
output_dir="${temp_dir}/output"
mkdir -p "${summary_dir}"

write_summary() {
  local rate="$1"
  local transaction_429_rate="$2"
  local transaction_503_rate="${3:-0}"
  local transaction_503_count="${4:-0}"
  cat >"${summary_dir}/rate-${rate}-summary.json" <<JSON
{
  "metrics": {
    "http_req_failed": {"values": {"rate": 0}},
    "http_reqs": {"values": {"count": 4096}},
    "dropped_iterations": {"values": {"count": 0}},
    "interrupted_iterations": {"values": {"count": 0}},
    "aquila_transaction_429_rate": {"values": {"rate": ${transaction_429_rate}}},
    "aquila_transaction_503_rate": {"values": {"rate": ${transaction_503_rate}}},
    "aquila_transaction_503_count": {"values": {"count": ${transaction_503_count}}}
  }
}
JSON
}

write_summary 96 0.0836
write_summary 112 0.095
write_summary 128 0.099

echo "[transaction-read-stepped-burst] print plan"
plan="$(
  STEPPED_BURST_GATE_NAME=stepped-check \
  STEPPED_BURST_RATES=96,112,128 \
  STEPPED_BURST_SUMMARY_DIR="${summary_dir}" \
  STEPPED_BURST_OUTPUT_DIR="${output_dir}" \
  STEPPED_BURST_FAIL_RATE=0.10 \
  STEPPED_BURST_WARN_RATE=0.08 \
    "${runner}" --print-plan
)"
grep -F "gate=stepped-check" <<<"${plan}" >/dev/null
grep -F "rates=96,112,128" <<<"${plan}" >/dev/null
grep -F "summary_dir=${summary_dir}" <<<"${plan}" >/dev/null
grep -F "output_dir=${output_dir}" <<<"${plan}" >/dev/null
grep -F "run_k6=false" <<<"${plan}" >/dev/null
grep -F "fail_rate=0.10" <<<"${plan}" >/dev/null
grep -F "warn_rate=0.08" <<<"${plan}" >/dev/null

echo "[transaction-read-stepped-burst] aggregate pass"
output="$(
  STEPPED_BURST_GATE_NAME=stepped-check \
  STEPPED_BURST_RATES=96,112,128 \
  STEPPED_BURST_SUMMARY_DIR="${summary_dir}" \
  STEPPED_BURST_OUTPUT_DIR="${output_dir}" \
  STEPPED_BURST_FAIL_RATE=0.10 \
  STEPPED_BURST_WARN_RATE=0.08 \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/stepped-check-stepped-burst.tsv"
test "${report_md}" = "${output_dir}/stepped-check-stepped-burst.md"
grep -F $'rate\tstatus\ttransaction_429_rate\ttransaction_503_rate\ttransaction_503_count\treport_md\tsummary_json' "${summary_tsv}" >/dev/null
grep -F $'96\twarn\t0.0836\t0\t0' "${summary_tsv}" >/dev/null
grep -F $'112\twarn\t0.095\t0\t0' "${summary_tsv}" >/dev/null
grep -F $'128\twarn\t0.099\t0\t0' "${summary_tsv}" >/dev/null
grep -F "gate_status=warn" "${report_md}" >/dev/null
grep -F "96/112/128" "${report_md}" >/dev/null

echo "[transaction-read-stepped-burst] aggregate fail"
write_summary 128 0.125
if STEPPED_BURST_GATE_NAME=stepped-fail-check \
  STEPPED_BURST_RATES=96,112,128 \
  STEPPED_BURST_SUMMARY_DIR="${summary_dir}" \
  STEPPED_BURST_OUTPUT_DIR="${output_dir}" \
  STEPPED_BURST_FAIL_RATE=0.10 \
  STEPPED_BURST_WARN_RATE=0.08 \
    "${runner}" >/dev/null 2>&1; then
  echo "stepped burst fail threshold unexpectedly passed" >&2
  exit 1
fi

echo "[transaction-read-stepped-burst] runner contract"
grep -F "run-transaction-read-burst-429-budget-gate.sh" "${runner}" >/dev/null
grep -F "STEPPED_BURST_RATES" "${runner}" >/dev/null
grep -F "rate-96-summary.json" "${runner}" >/dev/null
grep -F "K6_SCENARIO_MODE=burst" "${runner}" >/dev/null

echo "[transaction-read-stepped-burst] invalid input fails"
if STEPPED_BURST_RATES=bad "${runner}" --print-plan >/dev/null 2>&1; then
  echo "invalid stepped burst rate unexpectedly succeeded" >&2
  exit 1
fi
if STEPPED_BURST_WARN_RATE=0.12 STEPPED_BURST_FAIL_RATE=0.10 "${runner}" --print-plan >/dev/null 2>&1; then
  echo "warn greater than fail unexpectedly succeeded" >&2
  exit 1
fi
