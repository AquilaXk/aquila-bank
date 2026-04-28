#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-p999-spike-profile.sh [--print-plan]

Environment:
  P999_SPIKE_PROFILE_NAME             default transaction-read-p999-spike-<timestamp>
  P999_SPIKE_RUN_ID                   default P999_SPIKE_PROFILE_NAME
  P999_SPIKE_K6_SUMMARY_JSON          required k6 summary JSON
  P999_SPIKE_PROMETHEUS_SNAPSHOT_TSV  optional metric/value snapshot TSV
  P999_SPIKE_OUTPUT_DIR               default build/reports/profiling/<profile>
  P999_SPIKE_WARN_MS                  default 30
  P999_SPIKE_FAIL_MS                  default 80

Snapshot metric keys:
  db.hikari.pending.max
  db.hikari.active.max
  jvm.gc.pause.max.seconds
  tomcat.threads.busy.max
  http.server.requests.max.seconds
  postgres.cpu.max.percent
  backend.cpu.max.percent
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

profile_name="${P999_SPIKE_PROFILE_NAME:-transaction-read-p999-spike-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${P999_SPIKE_RUN_ID:-${profile_name}}"
k6_summary_json="${P999_SPIKE_K6_SUMMARY_JSON:-}"
prometheus_snapshot_tsv="${P999_SPIKE_PROMETHEUS_SNAPSHOT_TSV:-}"
output_dir="${P999_SPIKE_OUTPUT_DIR:-build/reports/profiling/${profile_name}}"
warn_ms="${P999_SPIKE_WARN_MS:-30}"
fail_ms="${P999_SPIKE_FAIL_MS:-80}"
boundary_tsv="${output_dir}/${profile_name}-p999-boundary.tsv"
report_md="${output_dir}/${profile_name}-p999-spike-profile.md"

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

require_positive_number_value "P999_SPIKE_WARN_MS" "${warn_ms}"
require_positive_number_value "P999_SPIKE_FAIL_MS" "${fail_ms}"
if number_greater_than "${warn_ms}" "${fail_ms}"; then
  echo "P999_SPIKE_WARN_MS must be less than or equal to P999_SPIKE_FAIL_MS" >&2
  exit 1
fi

print_plan() {
  echo "[transaction-read-p999-spike] profile=${profile_name}"
  echo "[transaction-read-p999-spike] run_id=${run_id}"
  echo "[transaction-read-p999-spike] k6_summary_json=${k6_summary_json:-missing}"
  echo "[transaction-read-p999-spike] prometheus_snapshot=${prometheus_snapshot_tsv:-missing}"
  echo "[transaction-read-p999-spike] warn_ms=${warn_ms}"
  echo "[transaction-read-p999-spike] fail_ms=${fail_ms}"
  echo "[transaction-read-p999-spike] boundary_tsv=${boundary_tsv}"
  echo "[transaction-read-p999-spike] report_md=${report_md}"
  echo "[transaction-read-p999-spike] layers=http,db,gc,http-thread,backend"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ -z "${k6_summary_json}" || ! -s "${k6_summary_json}" ]]; then
  echo "P999_SPIKE_K6_SUMMARY_JSON is required: ${k6_summary_json:-missing}" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

metric_value() {
  local metric="$1"
  local field="$2"
  jq -r --arg metric "${metric}" --arg field "${field}" \
    '.metrics[$metric].values[$field] // "n/a"' "${k6_summary_json}"
}

snapshot_value() {
  local key="$1"
  if [[ -z "${prometheus_snapshot_tsv}" || ! -s "${prometheus_snapshot_tsv}" ]]; then
    echo "n/a"
    return 0
  fi
  awk -F '\t' -v key="${key}" '$1 == key {value = $2; found = 1} END {print found ? value : "n/a"}' "${prometheus_snapshot_tsv}"
}

max_metric_for_field() {
  local field="$1"
  local best_value="n/a"
  local best_metric="n/a"
  local metric value
  for metric in \
    aquila_transaction_hot_first_ms \
    aquila_transaction_hot_cursor_ms \
    aquila_transaction_cold_first_ms \
    aquila_transaction_cold_cursor_ms
  do
    value="$(metric_value "${metric}" "${field}")"
    if [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
      if [[ "${best_value}" == "n/a" ]] || number_greater_than "${value}" "${best_value}"; then
        best_value="${value}"
        best_metric="${metric}"
      fi
    fi
  done
  printf "%s\t%s\n" "${best_value}" "${best_metric}"
}

mkdir -p "${output_dir}"

read -r max_latency max_latency_metric <<<"$(max_metric_for_field "max")"
read -r p999_latency p999_latency_metric <<<"$(max_metric_for_field "p(99.9)")"

tail_status="pass"
if [[ "${max_latency}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  if number_greater_than "${max_latency}" "${fail_ms}"; then
    tail_status="fail"
  elif number_greater_than "${max_latency}" "${warn_ms}"; then
    tail_status="warn"
  fi
fi

{
  printf "layer\tmetric\tvalue\tsource\n"
  printf "http\tmax_latency_ms\t%s\tk6:%s\n" "${max_latency}" "${max_latency_metric}"
  printf "http\tp999_latency_ms\t%s\tk6:%s\n" "${p999_latency}" "${p999_latency_metric}"
  printf "http\thttp_failed_rate\t%s\tk6:http_req_failed\n" "$(metric_value http_req_failed rate)"
  printf "http\ttransaction_429_rate\t%s\tk6:aquila_transaction_429_rate\n" "$(metric_value aquila_transaction_429_rate rate)"
  printf "http\ttransaction_503_rate\t%s\tk6:aquila_transaction_503_rate\n" "$(metric_value aquila_transaction_503_rate rate)"
  printf "db\tconnection_pool_pending\t%s\tprometheus-snapshot\n" "$(snapshot_value db.hikari.pending.max)"
  printf "db\tconnection_pool_active\t%s\tprometheus-snapshot\n" "$(snapshot_value db.hikari.active.max)"
  printf "db\tpostgres_cpu_percent\t%s\tprometheus-snapshot\n" "$(snapshot_value postgres.cpu.max.percent)"
  printf "gc\tpause_max_seconds\t%s\tprometheus-snapshot\n" "$(snapshot_value jvm.gc.pause.max.seconds)"
  printf "http-thread\tbusy_threads\t%s\tprometheus-snapshot\n" "$(snapshot_value tomcat.threads.busy.max)"
  printf "http-thread\tserver_request_max_seconds\t%s\tprometheus-snapshot\n" "$(snapshot_value http.server.requests.max.seconds)"
  printf "backend\tcpu_percent\t%s\tprometheus-snapshot\n" "$(snapshot_value backend.cpu.max.percent)"
} >"${boundary_tsv}"

cat >"${report_md}" <<REPORT
# Transaction Read p99.9 Spike Profile

## Summary

- profile: ${profile_name}
- run id: ${run_id}
- tail_status=${tail_status}
- warn threshold ms: ${warn_ms}
- fail threshold ms: ${fail_ms}
- max latency ms: ${max_latency}
- max latency source: ${max_latency_metric}
- p99.9 latency ms: ${p999_latency}
- p99.9 latency source: ${p999_latency_metric}

## Boundary Artifacts

- boundary TSV: ${boundary_tsv}
- k6 summary JSON: ${k6_summary_json}
- Prometheus snapshot TSV: ${prometheus_snapshot_tsv:-missing}

## Notes

- DB/GC/connection pool/HTTP thread 값은 Prometheus snapshot이 없으면 \`n/a\`로 남깁니다.
- p99.9/max spike 원인 확정이 아니라 경계 분리를 위한 artifact입니다.
- 운영 URL, token, Authorization header는 기록하지 않습니다.
REPORT

echo "${report_md}"

if [[ "${tail_status}" == "fail" ]]; then
  echo "p99.9 spike profile exceeded fail threshold: max=${max_latency} fail=${fail_ms}" >&2
  exit 1
fi
