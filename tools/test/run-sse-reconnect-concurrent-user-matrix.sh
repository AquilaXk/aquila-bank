#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-sse-reconnect-concurrent-user-matrix.sh [--print-plan|--dry-run]

Environment:
  SSE_RECONNECT_CLIENT_MATRIX default 1,3,8
  SSE_RECONNECT_ROUNDS        default 3
  SSE_RECONNECT_MATRIX_NAME   default sse-reconnect-concurrent

Examples:
  tools/test/run-sse-reconnect-concurrent-user-matrix.sh --print-plan
  SSE_RECONNECT_CLIENT_MATRIX=1,3,8 tools/test/run-sse-reconnect-concurrent-user-matrix.sh --dry-run
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

client_values="${SSE_RECONNECT_CLIENT_MATRIX:-1,3,8}"
rounds="${SSE_RECONNECT_ROUNDS:-3}"
matrix_name="${SSE_RECONNECT_MATRIX_NAME:-sse-reconnect-concurrent}"
IFS=',' read -r -a clients <<<"${client_values}"

if ! [[ "${rounds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "SSE_RECONNECT_ROUNDS must be a positive integer" >&2
  exit 1
fi
for client_count in "${clients[@]}"; do
  if ! [[ "${client_count}" =~ ^[1-9][0-9]*$ ]]; then
    echo "SSE_RECONNECT_CLIENT_MATRIX must contain positive integers: ${client_values}" >&2
    exit 1
  fi
done

print_plan() {
  echo "[sse-reconnect-concurrent-matrix] mode=${mode}"
  echo "[sse-reconnect-concurrent-matrix] clients=${client_values}"
  echo "[sse-reconnect-concurrent-matrix] rounds=${rounds}"
  echo "[sse-reconnect-concurrent-matrix] matrix_name=${matrix_name}"
  echo "[sse-reconnect-concurrent-matrix] runner=tools/test/run-sse-reconnect-storm-t3micro-gate.sh"
}

print_dry_run() {
  local client_count
  for client_count in "${clients[@]}"; do
    echo "SSE_RECONNECT_CLIENTS=${client_count} SSE_RECONNECT_ROUNDS=${rounds} SSE_T3MICRO_RESULT_NAME=${matrix_name}-clients-${client_count} tools/test/run-sse-reconnect-storm-t3micro-gate.sh"
  done
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_dry_run
  exit 0
fi

for client_count in "${clients[@]}"; do
  SSE_RECONNECT_CLIENTS="${client_count}" \
  SSE_RECONNECT_ROUNDS="${rounds}" \
  SSE_T3MICRO_RESULT_NAME="${matrix_name}-clients-${client_count}" \
    tools/test/run-sse-reconnect-storm-t3micro-gate.sh
done
