#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-prometheus-remote-write-oci-timeline.sh [--print-plan]

Environment:
  PROM_REMOTE_OCI_NAME                             default transaction-read-prometheus-remote-write-oci-<timestamp>
  PROM_REMOTE_OCI_INPUT_TSV                        required TSV with OCI Prometheus remote-write timeline evidence
  PROM_REMOTE_OCI_OUTPUT_DIR                       default build/reports/k6/<gate>
  PROM_REMOTE_OCI_MAX_REMOTE_WRITE_LATENCY_SECONDS default 0.1
  PROM_REMOTE_OCI_MAX_P999_MS                      default 500
  PROM_REMOTE_OCI_MAX_EDGE_429_RATE                default 0.10
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

name="${PROM_REMOTE_OCI_NAME:-transaction-read-prometheus-remote-write-oci-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${PROM_REMOTE_OCI_INPUT_TSV:-}"
output_dir="${PROM_REMOTE_OCI_OUTPUT_DIR:-build/reports/k6/${name}}"
max_remote_write_latency_seconds="${PROM_REMOTE_OCI_MAX_REMOTE_WRITE_LATENCY_SECONDS:-0.1}"
max_p999_ms="${PROM_REMOTE_OCI_MAX_P999_MS:-500}"
max_edge_429_rate="${PROM_REMOTE_OCI_MAX_EDGE_429_RATE:-0.10}"
summary_tsv="${output_dir}/${name}-prometheus-remote-write-oci-timeline.tsv"
report_md="${output_dir}/${name}-prometheus-remote-write-oci-timeline.md"
meta_file="${output_dir}/${name}-prometheus-remote-write-oci-timeline.meta"

require_non_negative_number() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${key} must be a non-negative number: ${value}" >&2
    exit 1
  fi
}

require_rate() {
  local key="$1"
  local value="$2"
  require_non_negative_number "${key}" "${value}"
  awk -v value="${value}" 'BEGIN { exit !(value >= 0 && value <= 1) }' || {
    echo "${key} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  }
}

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[transaction-read-prometheus-remote-write-oci] name=${name}"
  echo "[transaction-read-prometheus-remote-write-oci] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-prometheus-remote-write-oci] output_dir=${output_dir}"
  echo "[transaction-read-prometheus-remote-write-oci] max_remote_write_latency_seconds=${max_remote_write_latency_seconds}"
  echo "[transaction-read-prometheus-remote-write-oci] max_p999_ms=${max_p999_ms}"
  echo "[transaction-read-prometheus-remote-write-oci] max_edge_429_rate=${max_edge_429_rate}"
  echo "[transaction-read-prometheus-remote-write-oci] summary_tsv=${summary_tsv}"
  echo "[transaction-read-prometheus-remote-write-oci] report_md=${report_md}"
}

require_non_negative_number "PROM_REMOTE_OCI_MAX_REMOTE_WRITE_LATENCY_SECONDS" "${max_remote_write_latency_seconds}"
require_non_negative_number "PROM_REMOTE_OCI_MAX_P999_MS" "${max_p999_ms}"
require_rate "PROM_REMOTE_OCI_MAX_EDGE_429_RATE" "${max_edge_429_rate}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "PROM_REMOTE_OCI_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "PROM_REMOTE_OCI_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

awk -F '\t' \
  -v max_remote_write_latency="${max_remote_write_latency_seconds}" \
  -v max_p999_ms="${max_p999_ms}" \
  -v max_edge_429_rate="${max_edge_429_rate}" '
function value(name, fallback) {
  if (!(name in col) || col[name] == "") return fallback
  return $(col[name])
}
function add_reason(reason_value) {
  if (reason == "ok") {
    reason = reason_value
  } else {
    reason = reason "," reason_value
  }
  status = "fail"
}
function require_ref(name, reason_value) {
  ref = value(name, "")
  if (ref == "" || ref == "n/a") add_reason(reason_value)
}
function max3(a, b, c) {
  max = a
  if (b > max) max = b
  if (c > max) max = c
  return max
}
NR == 1 {
  for (i = 1; i <= NF; i++) {
    col[$i] = i
  }
  print "run_id\tstatus\treason\twindow_start_utc\twindow_end_utc\tremote_write_samples_total\tremote_write_failed_samples_total\tremote_write_highest_latency_seconds\tk6_p999_ms\tnginx_upstream_p999_ms\tspring_p999_ms\thikari_pending_max\tpg_wait_p95_ms\tedge_429_rate\tfive_xx_count"
  next
}
{
  run_id = value("run_id", "")
  samples = value("remote_write_samples_total", "0") + 0
  failed = value("remote_write_failed_samples_total", "1") + 0
  latency = value("remote_write_highest_latency_seconds", "999999") + 0
  k6_p999 = value("k6_p999_ms", "999999") + 0
  nginx_p999 = value("nginx_upstream_p999_ms", "999999") + 0
  spring_p999 = value("spring_p999_ms", "999999") + 0
  hikari_pending = value("hikari_pending_max", "999999") + 0
  edge_429 = value("edge_429_rate", "1") + 0
  five_xx = value("five_xx_count", "1") + 0
  status = "pass"
  reason = "ok"

  if (run_id == "") add_reason("run-id-missing")
  if (value("window_start_utc", "") == "" || value("window_end_utc", "") == "") add_reason("window-missing")
  require_ref("k6_summary_ref", "k6-summary-missing")
  require_ref("prometheus_snapshot_ref", "prometheus-snapshot-missing")
  require_ref("grafana_dashboard_ref", "grafana-ref-missing")
  if (samples <= 0) add_reason("remote-write-samples<=0")
  if (failed > 0) add_reason("remote-write-failed>0")
  if (latency > max_remote_write_latency) add_reason("remote-write-latency>" max_remote_write_latency)
  if (max3(k6_p999, nginx_p999, spring_p999) > max_p999_ms) add_reason("p999>" max_p999_ms)
  if (hikari_pending > 0) add_reason("hikari-pending>0")
  if (edge_429 > max_edge_429_rate) add_reason("edge429>" max_edge_429_rate)
  if (five_xx > 0) add_reason("5xx>0")
  if (status == "fail") fail_count++

  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    run_id, status, reason, value("window_start_utc", ""), value("window_end_utc", ""),
    value("remote_write_samples_total", "0"), value("remote_write_failed_samples_total", "0"),
    value("remote_write_highest_latency_seconds", "0"), value("k6_p999_ms", "0"),
    value("nginx_upstream_p999_ms", "0"), value("spring_p999_ms", "0"),
    value("hikari_pending_max", "0"), value("pg_wait_p95_ms", "0"), value("edge_429_rate", "0"),
    value("five_xx_count", "0")
}
END {
  print "fail_count=" (fail_count + 0) > "/dev/stderr"
}
' "${input_tsv}" >"${summary_tsv}" 2>"${meta_file}"

fail_count="$(awk -F '=' '/^fail_count=/ { print $2 }' "${meta_file}")"
gate_status="pass"
if [[ "${fail_count}" != "0" ]]; then
  gate_status="fail"
fi

timeline_table="$(awk -F '\t' '
  BEGIN {
    print "| Run id | Status | Reason | Window start | Window end | RW samples | RW failed | RW max latency s | k6 p99.9 | Nginx p99.9 | Spring p99.9 | Hikari pending | Edge 429 | 5xx |"
    print "| --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $14, $15
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Prometheus Remote-write OCI Timeline

## Summary

- gate_status=${gate_status}
- timeline contract: k6 + Prometheus remote-write + Grafana window
- remote-write failed samples target: 0
- remote-write max latency: <= ${max_remote_write_latency_seconds}s
- p99.9 max: <= ${max_p999_ms}ms
- edge 429 max rate: <= ${max_edge_429_rate}
- Hikari pending/5xx target: 0

## Timeline

${timeline_table}

## Contract Notes

- summary-only 결과는 최종 운영 evidence가 아니며, Prometheus remote-write와 같은 시간창의 Grafana/metric snapshot을 같이 남긴다.
- p99.9 spike는 k6, Nginx upstream, Spring histogram, PostgreSQL wait를 같은 run id 시간창으로 묶어 해석한다.
- remote-write receiver 지연이나 failed sample이 있으면 timeline 증거 신뢰도가 떨어지므로 gate에서 실패한다.

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read Prometheus remote-write OCI timeline failed: ${summary_tsv}" >&2
  exit 1
fi
