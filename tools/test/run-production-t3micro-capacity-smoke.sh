#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-production-t3micro-capacity-smoke.sh [--print-plan]

Environment:
  SOAK_REPEAT                                 repeat count, default 1
  PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE         default 4
  PRODUCTION_T3MICRO_SERVER_THREADS_MAX       default 16
  PRODUCTION_T3MICRO_SSE_MAX_TOTAL_SESSIONS   default 64
  PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX  default 4

Examples:
  tools/test/run-production-t3micro-capacity-smoke.sh
  SOAK_REPEAT=3 tools/test/run-production-t3micro-capacity-smoke.sh
  tools/test/run-production-t3micro-capacity-smoke.sh --print-plan
USAGE
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer" >&2
    exit 1
  fi
}

print_plan=false
if [[ "${1:-}" == "--print-plan" ]]; then
  print_plan=true
  shift
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -ne 0 ]]; then
  usage
  exit 1
fi

repeat="${SOAK_REPEAT:-1}"
db_pool_max_size="${PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE:-4}"
server_threads_max="${PRODUCTION_T3MICRO_SERVER_THREADS_MAX:-16}"
sse_max_total_sessions="${PRODUCTION_T3MICRO_SSE_MAX_TOTAL_SESSIONS:-64}"
notification_stream_max="${PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX:-4}"
sse_pressure_budget=$((sse_max_total_sessions * 875 / 1000))

require_positive_integer "SOAK_REPEAT" "${repeat}"
require_positive_integer "PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE" "${db_pool_max_size}"
require_positive_integer "PRODUCTION_T3MICRO_SERVER_THREADS_MAX" "${server_threads_max}"
require_positive_integer "PRODUCTION_T3MICRO_SSE_MAX_TOTAL_SESSIONS" "${sse_max_total_sessions}"
require_positive_integer "PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX" "${notification_stream_max}"

echo "[production-t3micro-capacity] repeat=${repeat}"
echo "[production-t3micro-capacity] source=tools/test/run-t3micro-mixed-workload-soak.sh"
echo "[production-t3micro-capacity] runtime budget: DB_POOL_MAX_SIZE=${db_pool_max_size} SERVER_THREADS_MAX=${server_threads_max} NOTIFICATION_SSE_MAX_TOTAL_SESSIONS=${sse_max_total_sessions}"
echo "[production-t3micro-capacity] admission budget: OPS_API_ADMISSION_CONTROL_NOTIFICATION_STREAM_MAX=${notification_stream_max}"
echo "[production-t3micro-capacity] guard budget: OPS_T3MICRO_SATURATION_GUARD_ENABLED=true SSE pressure warning>=${sse_pressure_budget}"

SOAK_REPEAT="${repeat}" tools/test/run-t3micro-mixed-workload-soak.sh --print-plan

if [[ "${print_plan}" == "true" ]]; then
  exit 0
fi

DB_POOL_MAX_SIZE="${db_pool_max_size}" \
SERVER_THREADS_MAX="${server_threads_max}" \
NOTIFICATION_SSE_MAX_TOTAL_SESSIONS="${sse_max_total_sessions}" \
OPS_API_ADMISSION_CONTROL_ENABLED=true \
OPS_API_ADMISSION_CONTROL_NOTIFICATION_STREAM_MAX="${notification_stream_max}" \
OPS_T3MICRO_SATURATION_GUARD_ENABLED=true \
SOAK_REPEAT="${repeat}" \
tools/test/run-t3micro-mixed-workload-soak.sh
