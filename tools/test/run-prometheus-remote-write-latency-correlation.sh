#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-prometheus-remote-write-latency-correlation.sh [--print-plan]

Environment:
  REMOTE_WRITE_CORRELATION_NAME             default prometheus-remote-write-correlation-<timestamp>
  REMOTE_WRITE_BASELINE_SUMMARY_JSON        required summary-only k6 summary JSON
  REMOTE_WRITE_PROMETHEUS_SUMMARY_JSON      required prometheus-mode k6 summary JSON
  REMOTE_WRITE_SNAPSHOT_TSV                 optional metric/value remote-write snapshot
  REMOTE_WRITE_OUTPUT_DIR                   default build/reports/k6/<name>
  REMOTE_WRITE_P95_WARN_RATIO               default 1.10
  REMOTE_WRITE_P95_FAIL_RATIO               default 1.50
  REMOTE_WRITE_TAIL_WARN_RATIO              default 1.25
  REMOTE_WRITE_TAIL_FAIL_RATIO              default 2.00

Snapshot metric keys:
  prometheus.remote_write.samples_total
  prometheus.remote_write.failed_samples_total
  prometheus.remote_write.highest_latency_seconds
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

name="${REMOTE_WRITE_CORRELATION_NAME:-prometheus-remote-write-correlation-$(date +%Y-%m-%d-%H%M%S)}"
baseline_summary="${REMOTE_WRITE_BASELINE_SUMMARY_JSON:-}"
prometheus_summary="${REMOTE_WRITE_PROMETHEUS_SUMMARY_JSON:-}"
remote_write_snapshot="${REMOTE_WRITE_SNAPSHOT_TSV:-}"
output_dir="${REMOTE_WRITE_OUTPUT_DIR:-build/reports/k6/${name}}"
p95_warn_ratio="${REMOTE_WRITE_P95_WARN_RATIO:-1.10}"
p95_fail_ratio="${REMOTE_WRITE_P95_FAIL_RATIO:-1.50}"
tail_warn_ratio="${REMOTE_WRITE_TAIL_WARN_RATIO:-1.25}"
tail_fail_ratio="${REMOTE_WRITE_TAIL_FAIL_RATIO:-2.00}"
result_tsv="${output_dir}/${name}-remote-write-latency-correlation.tsv"
report_md="${output_dir}/${name}-remote-write-latency-correlation.md"

require_positive_number_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be a positive number: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value > 0) }' || {
    echo "${name} must be greater than zero: ${value}" >&2
    exit 1
  }
}

number_greater_than() {
  awk -v value="$1" -v threshold="$2" 'BEGIN { exit !(value > threshold) }'
}

require_positive_number_value "REMOTE_WRITE_P95_WARN_RATIO" "${p95_warn_ratio}"
require_positive_number_value "REMOTE_WRITE_P95_FAIL_RATIO" "${p95_fail_ratio}"
require_positive_number_value "REMOTE_WRITE_TAIL_WARN_RATIO" "${tail_warn_ratio}"
require_positive_number_value "REMOTE_WRITE_TAIL_FAIL_RATIO" "${tail_fail_ratio}"
if number_greater_than "${p95_warn_ratio}" "${p95_fail_ratio}"; then
  echo "REMOTE_WRITE_P95_WARN_RATIO must be less than or equal to REMOTE_WRITE_P95_FAIL_RATIO" >&2
  exit 1
fi
if number_greater_than "${tail_warn_ratio}" "${tail_fail_ratio}"; then
  echo "REMOTE_WRITE_TAIL_WARN_RATIO must be less than or equal to REMOTE_WRITE_TAIL_FAIL_RATIO" >&2
  exit 1
fi

print_plan() {
  echo "[prometheus-remote-write-correlation] correlation=${name}"
  echo "[prometheus-remote-write-correlation] baseline_summary=${baseline_summary:-missing}"
  echo "[prometheus-remote-write-correlation] prometheus_summary=${prometheus_summary:-missing}"
  echo "[prometheus-remote-write-correlation] remote_write_snapshot=${remote_write_snapshot:-missing}"
  echo "[prometheus-remote-write-correlation] p95_warn_ratio=${p95_warn_ratio}"
  echo "[prometheus-remote-write-correlation] p95_fail_ratio=${p95_fail_ratio}"
  echo "[prometheus-remote-write-correlation] tail_warn_ratio=${tail_warn_ratio}"
  echo "[prometheus-remote-write-correlation] tail_fail_ratio=${tail_fail_ratio}"
  echo "[prometheus-remote-write-correlation] result_tsv=${result_tsv}"
  echo "[prometheus-remote-write-correlation] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ -z "${baseline_summary}" || ! -s "${baseline_summary}" ]]; then
  echo "REMOTE_WRITE_BASELINE_SUMMARY_JSON is required: ${baseline_summary:-missing}" >&2
  exit 1
fi
if [[ -z "${prometheus_summary}" || ! -s "${prometheus_summary}" ]]; then
  echo "REMOTE_WRITE_PROMETHEUS_SUMMARY_JSON is required: ${prometheus_summary:-missing}" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

metric_value() {
  local file="$1"
  local metric="$2"
  local field="$3"
  jq -r --arg metric "${metric}" --arg field "${field}" \
    '.metrics[$metric].values[$field] // "n/a"' "${file}"
}

max_metric_for_field() {
  local file="$1"
  local field="$2"
  local best_value="n/a"
  local metric value
  for metric in \
    aquila_transaction_hot_first_ms \
    aquila_transaction_hot_cursor_ms \
    aquila_transaction_cold_first_ms \
    aquila_transaction_cold_cursor_ms
  do
    value="$(metric_value "${file}" "${metric}" "${field}")"
    if [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
      if [[ "${best_value}" == "n/a" ]] || number_greater_than "${value}" "${best_value}"; then
        best_value="${value}"
      fi
    fi
  done
  echo "${best_value}"
}

snapshot_value() {
  local key="$1"
  if [[ -z "${remote_write_snapshot}" || ! -s "${remote_write_snapshot}" ]]; then
    echo "n/a"
    return 0
  fi
  awk -F '\t' -v key="${key}" '$1 == key {value = $2; found = 1} END {print found ? value : "n/a"}' "${remote_write_snapshot}"
}

delta_value() {
  awk -v baseline="$1" -v observed="$2" 'BEGIN { printf "%.6f", observed - baseline }'
}

ratio_value() {
  awk -v baseline="$1" -v observed="$2" 'BEGIN {
    if (baseline == 0) {
      if (observed == 0) printf "1.000000"; else printf "inf";
    } else {
      printf "%.6f", observed / baseline;
    }
  }'
}

status_for_ratio() {
  local ratio="$1"
  local warn="$2"
  local fail="$3"
  if [[ "${ratio}" == "inf" ]]; then
    echo "fail"
  elif number_greater_than "${ratio}" "${fail}"; then
    echo "fail"
  elif number_greater_than "${ratio}" "${warn}"; then
    echo "warn"
  else
    echo "pass"
  fi
}

write_row() {
  local metric="$1"
  local baseline="$2"
  local observed="$3"
  local warn="$4"
  local fail="$5"
  local delta ratio status
  delta="$(delta_value "${baseline}" "${observed}")"
  ratio="$(ratio_value "${baseline}" "${observed}")"
  status="$(status_for_ratio "${ratio}" "${warn}" "${fail}")"
  printf "%s\t%s\t%s\t%s\t%s\t%s\n" "${metric}" "${baseline}" "${observed}" "${delta}" "${ratio}" "${status}" >>"${result_tsv}"
}

mkdir -p "${output_dir}"
printf "metric\tbaseline\tprometheus\tdelta\tratio\tstatus\n" >"${result_tsv}"

baseline_p95="$(max_metric_for_field "${baseline_summary}" "p(95)")"
prometheus_p95="$(max_metric_for_field "${prometheus_summary}" "p(95)")"
baseline_p99="$(max_metric_for_field "${baseline_summary}" "p(99)")"
prometheus_p99="$(max_metric_for_field "${prometheus_summary}" "p(99)")"
baseline_p999="$(max_metric_for_field "${baseline_summary}" "p(99.9)")"
prometheus_p999="$(max_metric_for_field "${prometheus_summary}" "p(99.9)")"
baseline_max="$(max_metric_for_field "${baseline_summary}" "max")"
prometheus_max="$(max_metric_for_field "${prometheus_summary}" "max")"

write_row "p95_latency_ms" "${baseline_p95}" "${prometheus_p95}" "${p95_warn_ratio}" "${p95_fail_ratio}"
write_row "p99_latency_ms" "${baseline_p99}" "${prometheus_p99}" "${tail_warn_ratio}" "${tail_fail_ratio}"
write_row "p999_latency_ms" "${baseline_p999}" "${prometheus_p999}" "${tail_warn_ratio}" "${tail_fail_ratio}"
write_row "max_latency_ms" "${baseline_max}" "${prometheus_max}" "${tail_warn_ratio}" "${tail_fail_ratio}"
write_row "transaction_429_rate" \
  "$(metric_value "${baseline_summary}" aquila_transaction_429_rate rate)" \
  "$(metric_value "${prometheus_summary}" aquila_transaction_429_rate rate)" \
  "${tail_warn_ratio}" "${tail_fail_ratio}"
write_row "http_failed_rate" \
  "$(metric_value "${baseline_summary}" http_req_failed rate)" \
  "$(metric_value "${prometheus_summary}" http_req_failed rate)" \
  "${tail_warn_ratio}" "${tail_fail_ratio}"

correlation_status="pass"
if awk -F '\t' 'NR > 1 && $6 == "fail" {found = 1} END {exit !found}' "${result_tsv}"; then
  correlation_status="fail"
elif awk -F '\t' 'NR > 1 && $6 == "warn" {found = 1} END {exit !found}' "${result_tsv}"; then
  correlation_status="warn"
fi

p95_ratio="$(awk -F '\t' '$1 == "p95_latency_ms" {print $5}' "${result_tsv}")"

cat >"${report_md}" <<REPORT
# Prometheus Remote-write Latency Correlation

## Summary

- correlation: ${name}
- correlation_status=${correlation_status}
- baseline summary JSON: ${baseline_summary}
- prometheus summary JSON: ${prometheus_summary}
- p95 latency ratio: ${p95_ratio}

## Remote Write Snapshot

- prometheus.remote_write.samples_total: $(snapshot_value prometheus.remote_write.samples_total)
- prometheus.remote_write.failed_samples_total: $(snapshot_value prometheus.remote_write.failed_samples_total)
- prometheus.remote_write.highest_latency_seconds: $(snapshot_value prometheus.remote_write.highest_latency_seconds)

## Artifacts

- result TSV: ${result_tsv}
- remote-write snapshot TSV: ${remote_write_snapshot:-missing}

## Notes

- summary-only와 prometheus mode의 latency 차이만 비교하며, 원인 확정은 p99.9 spike artifact와 함께 판단합니다.
- 운영 URL, token, Authorization header는 기록하지 않습니다.
REPORT

echo "${report_md}"

if [[ "${correlation_status}" == "fail" ]]; then
  echo "remote-write latency correlation exceeded fail threshold" >&2
  exit 1
fi
