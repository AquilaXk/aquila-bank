#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-p999-spike-profile.sh"

echo "[transaction-read-p999-spike] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_json="${temp_dir}/transaction-summary.json"
snapshot_tsv="${temp_dir}/prometheus-snapshot.tsv"
output_dir="${temp_dir}/profile-output"

cat >"${summary_json}" <<'JSON'
{
  "metrics": {
    "http_req_failed": {"values": {"rate": 0}},
    "http_reqs": {"values": {"count": 1200}},
    "aquila_transaction_429_rate": {"values": {"rate": 0.00275}},
    "aquila_transaction_503_rate": {"values": {"rate": 0}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": 2.1, "p(99)": 6.0, "p(99.9)": 42.5, "max": 81.2}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": 2.2, "p(99)": 6.2, "p(99.9)": 38.1, "max": 76.3}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": 2.4, "p(99)": 6.4, "p(99.9)": 44.3, "max": 79.9}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": 2.5, "p(99)": 6.8, "p(99.9)": 40.1, "max": 78.4}}
  }
}
JSON

cat >"${snapshot_tsv}" <<'TSV'
metric	value
db.hikari.pending.max	0
db.hikari.active.max	5
jvm.gc.pause.max.seconds	0.008
tomcat.threads.busy.max	9
http.server.requests.max.seconds	0.082
postgres.cpu.max.percent	45.2
backend.cpu.max.percent	88.1
TSV

echo "[transaction-read-p999-spike] print plan"
plan="$(
  P999_SPIKE_PROFILE_NAME=transaction-p999-check \
  P999_SPIKE_K6_SUMMARY_JSON="${summary_json}" \
  P999_SPIKE_PROMETHEUS_SNAPSHOT_TSV="${snapshot_tsv}" \
  P999_SPIKE_OUTPUT_DIR="${output_dir}" \
  P999_SPIKE_WARN_MS=30 \
  P999_SPIKE_FAIL_MS=120 \
    "${runner}" --print-plan
)"
grep -F "profile=transaction-p999-check" <<<"${plan}" >/dev/null
grep -F "run_id=transaction-p999-check" <<<"${plan}" >/dev/null
grep -F "k6_summary_json=${summary_json}" <<<"${plan}" >/dev/null
grep -F "prometheus_snapshot=${snapshot_tsv}" <<<"${plan}" >/dev/null
grep -F "warn_ms=30" <<<"${plan}" >/dev/null
grep -F "fail_ms=120" <<<"${plan}" >/dev/null
grep -F "boundary_tsv=${output_dir}/transaction-p999-check-p999-boundary.tsv" <<<"${plan}" >/dev/null
grep -F "report_md=${output_dir}/transaction-p999-check-p999-spike-profile.md" <<<"${plan}" >/dev/null

echo "[transaction-read-p999-spike] report"
output="$(
  P999_SPIKE_PROFILE_NAME=transaction-p999-check \
  P999_SPIKE_K6_SUMMARY_JSON="${summary_json}" \
  P999_SPIKE_PROMETHEUS_SNAPSHOT_TSV="${snapshot_tsv}" \
  P999_SPIKE_OUTPUT_DIR="${output_dir}" \
  P999_SPIKE_WARN_MS=30 \
  P999_SPIKE_FAIL_MS=120 \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
boundary_tsv="${output_dir}/transaction-p999-check-p999-boundary.tsv"
test "${report_md}" = "${output_dir}/transaction-p999-check-p999-spike-profile.md"
test -s "${boundary_tsv}"
grep -F $'layer\tmetric\tvalue\tsource' "${boundary_tsv}" >/dev/null
grep -F $'http\tmax_latency_ms\t81.2\tk6:aquila_transaction_hot_first_ms' "${boundary_tsv}" >/dev/null
grep -F $'http\tp999_latency_ms\t44.3\tk6:aquila_transaction_cold_first_ms' "${boundary_tsv}" >/dev/null
grep -F $'db\tconnection_pool_pending\t0\tprometheus-snapshot' "${boundary_tsv}" >/dev/null
grep -F $'gc\tpause_max_seconds\t0.008\tprometheus-snapshot' "${boundary_tsv}" >/dev/null
grep -F $'http-thread\tbusy_threads\t9\tprometheus-snapshot' "${boundary_tsv}" >/dev/null
grep -F "tail_status=warn" "${report_md}" >/dev/null
grep -F "max latency ms: 81.2" "${report_md}" >/dev/null
grep -F "p99.9 latency ms: 44.3" "${report_md}" >/dev/null

echo "[transaction-read-p999-spike] runner contract"
grep -F "aquila_transaction_hot_first_ms" "${runner}" >/dev/null
grep -F "aquila_transaction_cold_cursor_ms" "${runner}" >/dev/null
grep -F "db.hikari.pending.max" "${runner}" >/dev/null
grep -F "jvm.gc.pause.max.seconds" "${runner}" >/dev/null
grep -F "tomcat.threads.busy.max" "${runner}" >/dev/null
grep -F "http.server.requests.max.seconds" "${runner}" >/dev/null
grep -F "p999-boundary.tsv" "${runner}" >/dev/null

echo "[transaction-read-p999-spike] invalid input fails"
if P999_SPIKE_PROFILE_NAME=bad-threshold \
  P999_SPIKE_K6_SUMMARY_JSON="${summary_json}" \
  P999_SPIKE_WARN_MS=100 \
  P999_SPIKE_FAIL_MS=30 \
    "${runner}" --print-plan >/dev/null 2>&1; then
  echo "warn threshold greater than fail threshold unexpectedly succeeded" >&2
  exit 1
fi
if P999_SPIKE_PROFILE_NAME=missing-json "${runner}" >/dev/null 2>&1; then
  echo "missing k6 summary unexpectedly succeeded" >&2
  exit 1
fi
