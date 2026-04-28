#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-prometheus-remote-write-latency-correlation.sh"

echo "[prometheus-remote-write-correlation] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

baseline_json="${temp_dir}/baseline-summary.json"
prometheus_json="${temp_dir}/prometheus-summary.json"
remote_write_tsv="${temp_dir}/remote-write-snapshot.tsv"
output_dir="${temp_dir}/correlation-output"

cat >"${baseline_json}" <<'JSON'
{
  "metrics": {
    "http_req_failed": {"values": {"rate": 0}},
    "http_reqs": {"values": {"count": 1000}},
    "aquila_transaction_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": 2.0, "p(99)": 6.0, "p(99.9)": 20.0, "max": 40.0}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": 2.1, "p(99)": 6.2, "p(99.9)": 18.0, "max": 35.0}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": 2.3, "p(99)": 6.4, "p(99.9)": 21.0, "max": 39.0}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": 2.2, "p(99)": 6.3, "p(99.9)": 19.0, "max": 34.0}}
  }
}
JSON

cat >"${prometheus_json}" <<'JSON'
{
  "metrics": {
    "http_req_failed": {"values": {"rate": 0}},
    "http_reqs": {"values": {"count": 1000}},
    "aquila_transaction_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": 2.4, "p(99)": 7.1, "p(99.9)": 25.0, "max": 60.0}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": 2.3, "p(99)": 6.9, "p(99.9)": 22.0, "max": 44.0}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": 2.5, "p(99)": 7.0, "p(99.9)": 24.0, "max": 48.0}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": 2.4, "p(99)": 7.2, "p(99.9)": 26.0, "max": 52.0}}
  }
}
JSON

cat >"${remote_write_tsv}" <<'TSV'
metric	value
prometheus.remote_write.samples_total	12345
prometheus.remote_write.failed_samples_total	0
prometheus.remote_write.highest_latency_seconds	0.025
TSV

echo "[prometheus-remote-write-correlation] print plan"
plan="$(
  REMOTE_WRITE_CORRELATION_NAME=remote-write-check \
  REMOTE_WRITE_BASELINE_SUMMARY_JSON="${baseline_json}" \
  REMOTE_WRITE_PROMETHEUS_SUMMARY_JSON="${prometheus_json}" \
  REMOTE_WRITE_SNAPSHOT_TSV="${remote_write_tsv}" \
  REMOTE_WRITE_OUTPUT_DIR="${output_dir}" \
  REMOTE_WRITE_P95_WARN_RATIO=1.10 \
  REMOTE_WRITE_P95_FAIL_RATIO=1.50 \
    "${runner}" --print-plan
)"
grep -F "correlation=remote-write-check" <<<"${plan}" >/dev/null
grep -F "baseline_summary=${baseline_json}" <<<"${plan}" >/dev/null
grep -F "prometheus_summary=${prometheus_json}" <<<"${plan}" >/dev/null
grep -F "remote_write_snapshot=${remote_write_tsv}" <<<"${plan}" >/dev/null
grep -F "p95_warn_ratio=1.10" <<<"${plan}" >/dev/null
grep -F "p95_fail_ratio=1.50" <<<"${plan}" >/dev/null
grep -F "result_tsv=${output_dir}/remote-write-check-remote-write-latency-correlation.tsv" <<<"${plan}" >/dev/null
grep -F "report_md=${output_dir}/remote-write-check-remote-write-latency-correlation.md" <<<"${plan}" >/dev/null

echo "[prometheus-remote-write-correlation] report"
output="$(
  REMOTE_WRITE_CORRELATION_NAME=remote-write-check \
  REMOTE_WRITE_BASELINE_SUMMARY_JSON="${baseline_json}" \
  REMOTE_WRITE_PROMETHEUS_SUMMARY_JSON="${prometheus_json}" \
  REMOTE_WRITE_SNAPSHOT_TSV="${remote_write_tsv}" \
  REMOTE_WRITE_OUTPUT_DIR="${output_dir}" \
  REMOTE_WRITE_P95_WARN_RATIO=1.10 \
  REMOTE_WRITE_P95_FAIL_RATIO=1.50 \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
result_tsv="${output_dir}/remote-write-check-remote-write-latency-correlation.tsv"
test "${report_md}" = "${output_dir}/remote-write-check-remote-write-latency-correlation.md"
grep -F $'metric\tbaseline\tprometheus\tdelta\tratio\tstatus' "${result_tsv}" >/dev/null
grep -F $'p95_latency_ms\t2.3\t2.5\t0.200000\t1.086957\tpass' "${result_tsv}" >/dev/null
grep -F $'max_latency_ms\t40.0\t60.0\t20.000000\t1.500000\twarn' "${result_tsv}" >/dev/null
grep -F "correlation_status=warn" "${report_md}" >/dev/null
grep -F "prometheus.remote_write.samples_total: 12345" "${report_md}" >/dev/null
grep -F "prometheus.remote_write.failed_samples_total: 0" "${report_md}" >/dev/null
grep -F "p95 latency ratio: 1.086957" "${report_md}" >/dev/null

echo "[prometheus-remote-write-correlation] runner contract"
grep -F "aquila_transaction_hot_first_ms" "${runner}" >/dev/null
grep -F "aquila_transaction_cold_cursor_ms" "${runner}" >/dev/null
grep -F "REMOTE_WRITE_P95_WARN_RATIO" "${runner}" >/dev/null
grep -F "REMOTE_WRITE_P95_FAIL_RATIO" "${runner}" >/dev/null
grep -F "prometheus.remote_write.samples_total" "${runner}" >/dev/null
grep -F "remote-write-latency-correlation.tsv" "${runner}" >/dev/null

echo "[prometheus-remote-write-correlation] invalid input fails"
if REMOTE_WRITE_CORRELATION_NAME=bad-threshold \
  REMOTE_WRITE_BASELINE_SUMMARY_JSON="${baseline_json}" \
  REMOTE_WRITE_PROMETHEUS_SUMMARY_JSON="${prometheus_json}" \
  REMOTE_WRITE_P95_WARN_RATIO=2 \
  REMOTE_WRITE_P95_FAIL_RATIO=1.5 \
    "${runner}" --print-plan >/dev/null 2>&1; then
  echo "warn ratio greater than fail ratio unexpectedly succeeded" >&2
  exit 1
fi
if REMOTE_WRITE_CORRELATION_NAME=missing-input "${runner}" >/dev/null 2>&1; then
  echo "missing correlation input unexpectedly succeeded" >&2
  exit 1
fi
