#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-burst-307-capacity-probe.sh [--print-plan|--dry-run]

Environment:
  BURST_307_PROBE_NAME default transaction-100m-burst-307-<timestamp>
  BURST_307_PROBE_RATE default 307
  BURST_307_PROBE_DURATION default 20s
  BURST_307_PROBE_VUS default BURST_307_PROBE_RATE
  BURST_307_PROBE_OVERLOAD_MODE default true
  BURST_307_PROBE_RUN_PURPOSE default profile

Examples:
  tools/test/run-transaction-100m-burst-307-capacity-probe.sh --print-plan
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

name="${BURST_307_PROBE_NAME:-transaction-100m-burst-307-$(date +%Y-%m-%d-%H%M%S)}"
rate="${BURST_307_PROBE_RATE:-307}"
duration="${BURST_307_PROBE_DURATION:-20s}"
vus="${BURST_307_PROBE_VUS:-${rate}}"
overload_mode="${BURST_307_PROBE_OVERLOAD_MODE:-true}"
run_purpose="${BURST_307_PROBE_RUN_PURPOSE:-profile}"
k6_report_name="${name}-burst-307"
archive_output_dir="docs/performance-results/k6-${run_purpose}"

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_duration() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*(s|m|h)$ ]]; then
    echo "${name} must use a positive duration such as 20s: ${value}" >&2
    exit 1
  fi
}

require_bool() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_positive_integer "BURST_307_PROBE_RATE" "${rate}"
require_positive_integer "BURST_307_PROBE_VUS" "${vus}"
require_duration "BURST_307_PROBE_DURATION" "${duration}"
require_bool "BURST_307_PROBE_OVERLOAD_MODE" "${overload_mode}"

print_plan() {
  echo "[transaction-100m-burst-307] mode=${mode}"
  echo "[transaction-100m-burst-307] name=${name}"
  echo "[transaction-100m-burst-307] rate=${rate}"
  echo "[transaction-100m-burst-307] duration=${duration}"
  echo "[transaction-100m-burst-307] vus=${vus}"
  echo "[transaction-100m-burst-307] overload_mode=${overload_mode}"
  echo "[transaction-100m-burst-307] run_purpose=${run_purpose}"
  echo "[transaction-100m-burst-307] k6_report_name=${k6_report_name}"
  echo "[transaction-100m-burst-307] archive_output_dir=${archive_output_dir}"
}

print_command() {
  echo "K6_REPORT_NAME=${k6_report_name} K6_RUN_PURPOSE=${run_purpose} K6_SCENARIO_MODE=burst K6_BURST_RATE=${rate} K6_BURST_DURATION=${duration} K6_PRE_ALLOCATED_VUS=${vus} K6_MAX_VUS=${vus} K6_OVERLOAD_MODE=${overload_mode} tools/test/run-k6-transaction-100m-loadtest.sh"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_command
  exit 0
fi

K6_REPORT_NAME="${k6_report_name}" \
K6_RUN_PURPOSE="${run_purpose}" \
K6_SCENARIO_MODE=burst \
K6_BURST_RATE="${rate}" \
K6_BURST_DURATION="${duration}" \
K6_PRE_ALLOCATED_VUS="${vus}" \
K6_MAX_VUS="${vus}" \
K6_OVERLOAD_MODE="${overload_mode}" \
  tools/test/run-k6-transaction-100m-loadtest.sh
