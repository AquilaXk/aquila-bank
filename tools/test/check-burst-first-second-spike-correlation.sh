#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-burst-first-second-spike-correlation.sh"

echo "[burst-first-second-spike] shell syntax"
bash -n "${script}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_json="${temp_dir}/burst-summary.json"
runner_log="${temp_dir}/burst-runner.log"
snapshot_tsv="${temp_dir}/burst-snapshot.tsv"
output_dir="${temp_dir}/burst-output"

cat >"${summary_json}" <<'JSON'
{
  "metrics": {
    "http_reqs": {"values": {"count": 4096}},
    "dropped_iterations": {"values": {"count": 10}},
    "interrupted_iterations": {"values": {"count": 0}},
    "aquila_transaction_429_rate": {"values": {"rate": 0.017}},
    "aquila_transaction_503_rate": {"values": {"rate": 0}},
    "aquila_transaction_hot_first_ms": {"values": {"p(99.9)": 1123.4, "max": 1292.6}}
  }
}
JSON
cat >"${runner_log}" <<'LOG'
WARN[0010] Insufficient VUs, reached 128 active VUs and cannot initialize more
LOG
cat >"${snapshot_tsv}" <<'TSV'
metric	value
k6.vus.max	128
k6.vus.active.max	128
backend.cpu.max.percent	91.2
postgres.cpu.max.percent	48.4
db.hikari.pending.max	0
TSV

echo "[burst-first-second-spike] plan"
plan="$(
  BURST_SPIKE_NAME=burst-spike-check \
  BURST_SPIKE_K6_SUMMARY_JSON="${summary_json}" \
  BURST_SPIKE_RUNNER_LOG="${runner_log}" \
  BURST_SPIKE_PROMETHEUS_SNAPSHOT_TSV="${snapshot_tsv}" \
  BURST_SPIKE_OUTPUT_DIR="${output_dir}" \
  BURST_SPIKE_WINDOW_SECONDS=3 \
    "${script}" --print-plan
)"
grep -F "name=burst-spike-check" <<<"${plan}" >/dev/null
grep -F "k6_summary_json=${summary_json}" <<<"${plan}" >/dev/null
grep -F "runner_log=${runner_log}" <<<"${plan}" >/dev/null
grep -F "prometheus_snapshot=${snapshot_tsv}" <<<"${plan}" >/dev/null
grep -F "window_seconds=3" <<<"${plan}" >/dev/null
grep -F "correlation_tsv=${output_dir}/burst-spike-check-first-3s-correlation.tsv" <<<"${plan}" >/dev/null

echo "[burst-first-second-spike] report"
output="$(
  BURST_SPIKE_NAME=burst-spike-check \
  BURST_SPIKE_K6_SUMMARY_JSON="${summary_json}" \
  BURST_SPIKE_RUNNER_LOG="${runner_log}" \
  BURST_SPIKE_PROMETHEUS_SNAPSHOT_TSV="${snapshot_tsv}" \
  BURST_SPIKE_OUTPUT_DIR="${output_dir}" \
  BURST_SPIKE_WINDOW_SECONDS=3 \
    "${script}"
)"
report_md="$(tail -1 <<<"${output}")"
correlation_tsv="${output_dir}/burst-spike-check-first-3s-correlation.tsv"
test "${report_md}" = "${output_dir}/burst-spike-check-first-3s-correlation.md"
test -s "${correlation_tsv}"
grep -F $'window_seconds\tmetric\tvalue\tsource' "${correlation_tsv}" >/dev/null
grep -F $'3\tdropped_iterations\t10\tk6-summary' "${correlation_tsv}" >/dev/null
grep -F $'3\tinsufficient_vus_detected\t1\trunner-log' "${correlation_tsv}" >/dev/null
grep -F $'3\tk6_vus_max\t128\tprometheus-snapshot' "${correlation_tsv}" >/dev/null
grep -F "dropped iterations: 10" "${report_md}" >/dev/null
grep -F "insufficient VUs detected: 1" "${report_md}" >/dev/null
grep -F "hot first p99.9 ms: 1123.4" "${report_md}" >/dev/null

echo "[burst-first-second-spike] invalid input fails"
if BURST_SPIKE_WINDOW_SECONDS=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "zero first window unexpectedly passed" >&2
  exit 1
fi
if BURST_SPIKE_NAME=missing-summary "${script}" >/dev/null 2>&1; then
  echo "missing burst summary unexpectedly passed" >&2
  exit 1
fi

echo "[burst-first-second-spike] runner contract"
grep -F "Insufficient VUs" "${script}" >/dev/null
grep -F "dropped_iterations" "${script}" >/dev/null
grep -F "k6.vus.active.max" "${script}" >/dev/null
grep -F "first-window 원인 확정값이 아니라" "${script}" >/dev/null
