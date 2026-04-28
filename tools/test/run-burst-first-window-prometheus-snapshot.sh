#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-burst-first-window-prometheus-snapshot.sh [--print-plan|--dry-run]

Environment:
  BURST_WINDOW_SNAPSHOT_NAME default burst-first-window-prometheus-<timestamp>
  BURST_WINDOW_RUN_ID required for actual run
  BURST_WINDOW_PROMETHEUS_URL default http://localhost:9090
  BURST_WINDOW_SECONDS default 3
  BURST_WINDOW_OUTPUT_DIR default build/reports/profiling/<name>

Examples:
  BURST_WINDOW_RUN_ID=transaction-100m-burst-256 \
    tools/test/run-burst-first-window-prometheus-snapshot.sh
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --dry-run)
      mode="dry-run"
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

name="${BURST_WINDOW_SNAPSHOT_NAME:-burst-first-window-prometheus-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${BURST_WINDOW_RUN_ID:-}"
prometheus_url="${BURST_WINDOW_PROMETHEUS_URL:-http://localhost:9090}"
prometheus_url="${prometheus_url%/}"
window_seconds="${BURST_WINDOW_SECONDS:-3}"
output_dir="${BURST_WINDOW_OUTPUT_DIR:-build/reports/profiling/${name}}"
snapshot_tsv="${output_dir}/${name}-prometheus-first-${window_seconds}s.tsv"

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_url() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^https?://[^[:space:]]+$ ]]; then
    echo "${name} must be an http(s) URL: ${value}" >&2
    exit 1
  fi
}

require_positive_integer "BURST_WINDOW_SECONDS" "${window_seconds}"
require_url "BURST_WINDOW_PROMETHEUS_URL" "${prometheus_url}"

query_for_key() {
  local key="$1"
  case "${key}" in
    k6.vus.max)
      printf 'max(max_over_time(k6_vus{run_id="%s"}[%ss]))' "${run_id}" "${window_seconds}"
      ;;
    k6.vus.active.max)
      printf 'max(max_over_time(k6_vus{run_id="%s"}[%ss]))' "${run_id}" "${window_seconds}"
      ;;
    backend.cpu.max.percent)
      printf 'max(max_over_time(rate(container_cpu_usage_seconds_total{name=~"aquila-bank-backend.*"}[%ss])[%ss:1s])) * 100' "${window_seconds}" "${window_seconds}"
      ;;
    postgres.cpu.max.percent)
      printf 'max(max_over_time(rate(container_cpu_usage_seconds_total{name=~"aquila-bank-postgres.*"}[%ss])[%ss:1s])) * 100' "${window_seconds}" "${window_seconds}"
      ;;
    db.hikari.pending.max)
      printf 'max(max_over_time(hikaricp_connections_pending{pool="aquila-bank-pool"}[%ss]))' "${window_seconds}"
      ;;
    *)
      echo "unknown snapshot key: ${key}" >&2
      exit 1
      ;;
  esac
}

snapshot_keys=(
  k6.vus.max
  k6.vus.active.max
  backend.cpu.max.percent
  postgres.cpu.max.percent
  db.hikari.pending.max
)

print_plan() {
  echo "[burst-first-window-prometheus] mode=${mode}"
  echo "[burst-first-window-prometheus] name=${name}"
  echo "[burst-first-window-prometheus] run_id=${run_id:-missing}"
  echo "[burst-first-window-prometheus] prometheus_url=${prometheus_url}"
  echo "[burst-first-window-prometheus] window_seconds=${window_seconds}"
  echo "[burst-first-window-prometheus] snapshot_tsv=${snapshot_tsv}"
}

print_queries() {
  local key
  for key in "${snapshot_keys[@]}"; do
    printf "%s\t%s\n" "${key}" "$(query_for_key "${key}")"
  done
}

prometheus_value() {
  local query="$1"
  curl -fsS --get "${prometheus_url}/api/v1/query" \
    --data-urlencode "query=${query}" \
    | jq -r '.data.result[0].value[1] // "n/a"'
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ -z "${run_id}" ]]; then
  echo "BURST_WINDOW_RUN_ID is required" >&2
  exit 1
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_queries
  exit 0
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
{
  printf "metric\tvalue\n"
  for key in "${snapshot_keys[@]}"; do
    printf "%s\t%s\n" "${key}" "$(prometheus_value "$(query_for_key "${key}")")"
  done
} >"${snapshot_tsv}"

echo "${snapshot_tsv}"
