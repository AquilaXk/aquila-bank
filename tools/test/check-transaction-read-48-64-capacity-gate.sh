#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-48-64-capacity-gate.sh"

echo "[transaction-read-48-64-capacity] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_dir="${temp_dir}/summaries"
output_dir="${temp_dir}/output"
mkdir -p "${summary_dir}"

write_summary() {
  local rate="$1"
  local transaction_429_rate="$2"
  local hot_first_p95="$3"
  local hot_cursor_p95="$4"
  local cold_first_p95="$5"
  local cold_cursor_p95="$6"
  local transaction_503_rate="${7:-0}"
  local transaction_503_count="${8:-0}"
  local dropped_iterations="${9:-0}"
  local interrupted_iterations="${10:-0}"
  local retry_after_p95="${11:-0}"
  cat >"${summary_dir}/rate-${rate}-summary.json" <<JSON
{
  "metrics": {
    "http_req_failed": {"values": {"rate": 0}},
    "http_req_duration": {"values": {"p(95)": ${hot_cursor_p95}}},
    "http_reqs": {"values": {"count": 4096}},
    "dropped_iterations": {"values": {"count": ${dropped_iterations}}},
    "interrupted_iterations": {"values": {"count": ${interrupted_iterations}}},
    "aquila_transaction_429_rate": {"values": {"rate": ${transaction_429_rate}}},
    "aquila_transaction_503_rate": {"values": {"rate": ${transaction_503_rate}}},
    "aquila_transaction_503_count": {"values": {"count": ${transaction_503_count}}},
    "aquila_transaction_retry_after_sleep_ms": {"values": {"p(95)": ${retry_after_p95}}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": ${hot_first_p95}}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": ${hot_cursor_p95}}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": ${cold_first_p95}}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": ${cold_cursor_p95}}}
  }
}
JSON
}

write_summary 32 0.000 62 78 84 96 0 0 0 0 0
write_summary 48 0.030 72 88 95 120 0 0 0 0 80
write_summary 64 0.095 88 96 92 99 0 0 0 0 180
write_summary 80 0.180 91 130 138 180 0 0 0 0 220
write_summary 96 0.260 110 190 210 260 0 0 0 0 240

echo "[transaction-read-48-64-capacity] print plan"
plan="$(
  CAPACITY_48_64_GATE_NAME=capacity-check \
  CAPACITY_48_64_SUMMARY_DIR="${summary_dir}" \
  CAPACITY_48_64_OUTPUT_DIR="${output_dir}" \
  CAPACITY_48_64_WARN_RATE=0.08 \
  CAPACITY_48_64_FAIL_RATE=0.10 \
    "${runner}" --print-plan
)"
grep -F "gate=capacity-check" <<<"${plan}" >/dev/null
grep -F "lower_anchor_rate=32" <<<"${plan}" >/dev/null
grep -F "rates=32,48,64,80,96" <<<"${plan}" >/dev/null
grep -F "summary_dir=${summary_dir}" <<<"${plan}" >/dev/null
grep -F "output_dir=${output_dir}" <<<"${plan}" >/dev/null
grep -F "run_k6=false" <<<"${plan}" >/dev/null
grep -F "warn_rate=0.08" <<<"${plan}" >/dev/null
grep -F "fail_rate=0.10" <<<"${plan}" >/dev/null
grep -F "report_md=${output_dir}/capacity-check-burst-reject-curve.md" <<<"${plan}" >/dev/null

echo "[transaction-read-48-64-capacity] aggregate boundary"
output="$(
  CAPACITY_48_64_GATE_NAME=capacity-check \
  CAPACITY_48_64_SUMMARY_DIR="${summary_dir}" \
  CAPACITY_48_64_OUTPUT_DIR="${output_dir}" \
  CAPACITY_48_64_WARN_RATE=0.08 \
  CAPACITY_48_64_FAIL_RATE=0.10 \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/capacity-check-burst-reject-curve.tsv"
test "${report_md}" = "${output_dir}/capacity-check-burst-reject-curve.md"
grep -F $'rate\tstatus\ttransaction_429_rate\ttransaction_503_rate\ttransaction_503_count\thttp_req_duration_p95_ms\tfirst_p95_ms\tdeep_p95_ms\tretry_after_p95_ms\tdropped_iterations\tinterrupted_iterations\tgenerator_headroom_status\treport_md\tsummary_json' "${summary_tsv}" >/dev/null
grep -F $'32\tpass\t0.000\t0\t0\t78\t84\t96\t0\t0\t0\tpass' "${summary_tsv}" >/dev/null
grep -F $'48\tpass\t0.030\t0\t0\t88\t95\t120\t80\t0\t0\tpass' "${summary_tsv}" >/dev/null
grep -F $'64\twarn\t0.095\t0\t0\t96\t92\t99\t180\t0\t0\tpass' "${summary_tsv}" >/dev/null
grep -F $'80\toverload\t0.180\t0\t0\t130\t138\t180\t220\t0\t0\tpass' "${summary_tsv}" >/dev/null
grep -F $'96\toverload\t0.260\t0\t0\t190\t210\t260\t240\t0\t0\tpass' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "lower_anchor_rate=32" "${report_md}" >/dev/null
grep -F "stable_pass_rate=48" "${report_md}" >/dev/null
grep -F "max_non_fail_rate=64" "${report_md}" >/dev/null
grep -F "first_overload_rate=80" "${report_md}" >/dev/null
grep -F "| 64 | warn | 0.095 | 0 | 0 | 96 | 92 | 99 | 180 | pass |" "${report_md}" >/dev/null
grep -F "| 96 | overload | 0.260 | 0 | 0 | 190 | 210 | 260 | 240 | pass |" "${report_md}" >/dev/null

echo "[transaction-read-48-64-capacity] generator headroom fail"
write_summary 64 0.020 80 90 100 120 0 0 1 0
if CAPACITY_48_64_GATE_NAME=capacity-headroom-fail \
  CAPACITY_48_64_SUMMARY_DIR="${summary_dir}" \
  CAPACITY_48_64_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "generator headroom failure unexpectedly passed" >&2
  exit 1
fi

echo "[transaction-read-48-64-capacity] runner contract"
grep -F "run-transaction-read-burst-429-budget-gate.sh" "${runner}" >/dev/null
grep -F "CAPACITY_48_64_RATES" "${runner}" >/dev/null
grep -F "32,48,64,80,96" "${runner}" >/dev/null
grep -F "lower_anchor_rate" "${runner}" >/dev/null
grep -F "stable_pass_rate" "${runner}" >/dev/null
grep -F "max_non_fail_rate" "${runner}" >/dev/null
grep -F "first_overload_rate" "${runner}" >/dev/null
grep -F "http_req_duration" "${runner}" >/dev/null
grep -F "first_p95_ms" "${runner}" >/dev/null
grep -F "deep_p95_ms" "${runner}" >/dev/null
grep -F "retry_after_p95_ms" "${runner}" >/dev/null
grep -F "generator_headroom_status" "${runner}" >/dev/null

echo "[transaction-read-48-64-capacity] invalid input fails"
if CAPACITY_48_64_RATES=bad "${runner}" --print-plan >/dev/null 2>&1; then
  echo "invalid capacity rate unexpectedly succeeded" >&2
  exit 1
fi
if CAPACITY_48_64_WARN_RATE=0.12 CAPACITY_48_64_FAIL_RATE=0.10 "${runner}" --print-plan >/dev/null 2>&1; then
  echo "warn greater than fail unexpectedly succeeded" >&2
  exit 1
fi
