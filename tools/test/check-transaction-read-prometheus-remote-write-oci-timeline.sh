#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-prometheus-remote-write-oci-timeline.sh"

echo "[transaction-read-prometheus-remote-write-oci] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/remote-write-oci.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
run_id	window_start_utc	window_end_utc	k6_summary_ref	prometheus_snapshot_ref	grafana_dashboard_ref	remote_write_samples_total	remote_write_failed_samples_total	remote_write_highest_latency_seconds	k6_p999_ms	nginx_upstream_p999_ms	spring_p999_ms	hikari_pending_max	pg_wait_p95_ms	edge_429_rate	five_xx_count
run-p999-001	2026-05-02T03:20:00Z	2026-05-02T03:50:00Z	oci://perf/run-p999-001/k6.json	oci://perf/run-p999-001/prometheus.tsv	https://grafana.example/d/tx-read?from=run-p999-001	123456	0	0.025	430	410	120	0	8	0.06	0
run-mixed-001	2026-05-02T02:00:00Z	2026-05-02T02:30:00Z	oci://perf/run-mixed-001/k6.json	oci://perf/run-mixed-001/prometheus.tsv	https://grafana.example/d/tx-read?from=run-mixed-001	223456	0	0.030	450	420	130	0	6	0.08	0
TSV

echo "[transaction-read-prometheus-remote-write-oci] print plan"
plan="$(
  PROM_REMOTE_OCI_NAME=prom-oci-check \
  PROM_REMOTE_OCI_INPUT_TSV="${input_tsv}" \
  PROM_REMOTE_OCI_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=prom-oci-check" <<<"${plan}" >/dev/null
grep -F "max_remote_write_latency_seconds=0.1" <<<"${plan}" >/dev/null
grep -F "max_p999_ms=500" <<<"${plan}" >/dev/null
grep -F "max_edge_429_rate=0.10" <<<"${plan}" >/dev/null

echo "[transaction-read-prometheus-remote-write-oci] pass report"
output="$(
  PROM_REMOTE_OCI_NAME=prom-oci-check \
  PROM_REMOTE_OCI_INPUT_TSV="${input_tsv}" \
  PROM_REMOTE_OCI_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/prom-oci-check-prometheus-remote-write-oci-timeline.tsv"
test "${report_md}" = "${output_dir}/prom-oci-check-prometheus-remote-write-oci-timeline.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "timeline contract: k6 + Prometheus remote-write + Grafana window" "${report_md}" >/dev/null
grep -F $'run-p999-001\tpass\tok\t2026-05-02T03:20:00Z\t2026-05-02T03:50:00Z\t123456\t0\t0.025\t430\t410\t120\t0\t8\t0.06\t0' "${summary_tsv}" >/dev/null

echo "[transaction-read-prometheus-remote-write-oci] fail report"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "run-mixed-001" { $6 = "n/a"; $8 = 2; $9 = 0.250; $10 = 650; $13 = 1; $15 = 0.12; $16 = 1 } { print }' \
  "${input_tsv}" >"${input_tsv}.fail"
if PROM_REMOTE_OCI_NAME=prom-oci-fail \
  PROM_REMOTE_OCI_INPUT_TSV="${input_tsv}.fail" \
  PROM_REMOTE_OCI_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "Prometheus remote-write OCI timeline unexpectedly passed failure fixture" >&2
  exit 1
fi

PROM_REMOTE_OCI_NAME=prom-oci-fail \
PROM_REMOTE_OCI_INPUT_TSV="${input_tsv}.fail" \
PROM_REMOTE_OCI_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F $'run-mixed-001\tfail\t' "${output_dir}/prom-oci-fail-prometheus-remote-write-oci-timeline.tsv" >/dev/null
grep -F "grafana-ref-missing" "${output_dir}/prom-oci-fail-prometheus-remote-write-oci-timeline.tsv" >/dev/null
grep -F "remote-write-failed>0" "${output_dir}/prom-oci-fail-prometheus-remote-write-oci-timeline.tsv" >/dev/null
grep -F "remote-write-latency>0.1" "${output_dir}/prom-oci-fail-prometheus-remote-write-oci-timeline.tsv" >/dev/null
grep -F "p999>500" "${output_dir}/prom-oci-fail-prometheus-remote-write-oci-timeline.tsv" >/dev/null
grep -F "hikari-pending>0" "${output_dir}/prom-oci-fail-prometheus-remote-write-oci-timeline.tsv" >/dev/null
grep -F "edge429>0.10" "${output_dir}/prom-oci-fail-prometheus-remote-write-oci-timeline.tsv" >/dev/null
grep -F "5xx>0" "${output_dir}/prom-oci-fail-prometheus-remote-write-oci-timeline.tsv" >/dev/null
