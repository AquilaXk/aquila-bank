#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-burst-429-budget-gate.sh"

echo "[transaction-read-burst-429] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_json="${temp_dir}/burst-summary.json"
fail_json="${temp_dir}/burst-fail-summary.json"
output_dir="${temp_dir}/burst-output"

cat >"${summary_json}" <<'JSON'
{
  "metrics": {
    "http_req_failed": {"values": {"rate": 0}},
    "http_reqs": {"values": {"count": 4096}},
    "dropped_iterations": {"values": {"count": 0}},
    "interrupted_iterations": {"values": {"count": 0}},
    "aquila_transaction_429_rate": {"values": {"rate": 0.00275}},
    "aquila_transaction_503_rate": {"values": {"rate": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}}
  }
}
JSON

cat >"${fail_json}" <<'JSON'
{
  "metrics": {
    "http_req_failed": {"values": {"rate": 0}},
    "http_reqs": {"values": {"count": 4096}},
    "dropped_iterations": {"values": {"count": 0}},
    "interrupted_iterations": {"values": {"count": 0}},
    "aquila_transaction_429_rate": {"values": {"rate": 0.006}},
    "aquila_transaction_503_rate": {"values": {"rate": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}}
  }
}
JSON

echo "[transaction-read-burst-429] print plan"
plan="$(
  BURST_429_GATE_NAME=burst-429-check \
  BURST_429_SUMMARY_JSON="${summary_json}" \
  BURST_429_OUTPUT_DIR="${output_dir}" \
  BURST_429_WARN_RATE=0.001 \
  BURST_429_FAIL_RATE=0.005 \
  BURST_429_BURST_RATE=256 \
    "${runner}" --print-plan
)"
grep -F "gate=burst-429-check" <<<"${plan}" >/dev/null
grep -F "summary_json=${summary_json}" <<<"${plan}" >/dev/null
grep -F "burst_rate=256" <<<"${plan}" >/dev/null
grep -F "expected_rate=0" <<<"${plan}" >/dev/null
grep -F "warn_rate=0.001" <<<"${plan}" >/dev/null
grep -F "fail_rate=0.005" <<<"${plan}" >/dev/null
grep -F "result_tsv=${output_dir}/burst-429-check-burst-429-budget.tsv" <<<"${plan}" >/dev/null
grep -F "report_md=${output_dir}/burst-429-check-burst-429-budget.md" <<<"${plan}" >/dev/null

echo "[transaction-read-burst-429] warning report"
output="$(
  BURST_429_GATE_NAME=burst-429-check \
  BURST_429_SUMMARY_JSON="${summary_json}" \
  BURST_429_OUTPUT_DIR="${output_dir}" \
  BURST_429_WARN_RATE=0.001 \
  BURST_429_FAIL_RATE=0.005 \
  BURST_429_BURST_RATE=256 \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
result_tsv="${output_dir}/burst-429-check-burst-429-budget.tsv"
test "${report_md}" = "${output_dir}/burst-429-check-burst-429-budget.md"
grep -F $'metric\tvalue\twarn_threshold\tfail_threshold\tstatus' "${result_tsv}" >/dev/null
grep -F $'transaction_429_rate\t0.00275\t0.001\t0.005\twarn' "${result_tsv}" >/dev/null
grep -F $'dropped_iterations\t0\t0\t0\tpass' "${result_tsv}" >/dev/null
grep -F "gate_status=warn" "${report_md}" >/dev/null
grep -F "burst rate: 256/s" "${report_md}" >/dev/null
grep -F "transaction 429 rate: 0.00275" "${report_md}" >/dev/null

echo "[transaction-read-burst-429] fail threshold"
if BURST_429_GATE_NAME=burst-429-fail-check \
  BURST_429_SUMMARY_JSON="${fail_json}" \
  BURST_429_OUTPUT_DIR="${output_dir}" \
  BURST_429_WARN_RATE=0.001 \
  BURST_429_FAIL_RATE=0.005 \
    "${runner}" >/dev/null 2>&1; then
  echo "429 fail budget unexpectedly passed" >&2
  exit 1
fi

echo "[transaction-read-burst-429] runner contract"
grep -F "aquila_transaction_429_rate" "${runner}" >/dev/null
grep -F "dropped_iterations" "${runner}" >/dev/null
grep -F "interrupted_iterations" "${runner}" >/dev/null
grep -F "BURST_429_WARN_RATE" "${runner}" >/dev/null
grep -F "BURST_429_FAIL_RATE" "${runner}" >/dev/null
grep -F "K6_SCENARIO_MODE=burst" "${runner}" >/dev/null
grep -F "K6_BURST_RATE" "${runner}" >/dev/null

echo "[transaction-read-burst-429] invalid input fails"
if BURST_429_GATE_NAME=bad-threshold \
  BURST_429_SUMMARY_JSON="${summary_json}" \
  BURST_429_WARN_RATE=0.01 \
  BURST_429_FAIL_RATE=0.005 \
    "${runner}" --print-plan >/dev/null 2>&1; then
  echo "warn threshold greater than fail threshold unexpectedly succeeded" >&2
  exit 1
fi
if BURST_429_GATE_NAME=missing-json "${runner}" >/dev/null 2>&1; then
  echo "missing burst summary unexpectedly succeeded" >&2
  exit 1
fi
