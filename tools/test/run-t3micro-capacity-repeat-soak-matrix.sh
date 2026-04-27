#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-t3micro-capacity-repeat-soak-matrix.sh [--print-plan|--dry-run]

Environment:
  T3MICRO_CAPACITY_SOAK_REPEATS default 1,3
  T3MICRO_CAPACITY_MATRIX_NAME  default t3micro-capacity-repeat

Examples:
  tools/test/run-t3micro-capacity-repeat-soak-matrix.sh --print-plan
  T3MICRO_CAPACITY_SOAK_REPEATS=1,3,5 tools/test/run-t3micro-capacity-repeat-soak-matrix.sh --dry-run
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

repeat_values="${T3MICRO_CAPACITY_SOAK_REPEATS:-1,3}"
matrix_name="${T3MICRO_CAPACITY_MATRIX_NAME:-t3micro-capacity-repeat}"
IFS=',' read -r -a repeats <<<"${repeat_values}"

for repeat in "${repeats[@]}"; do
  if ! [[ "${repeat}" =~ ^[1-9][0-9]*$ ]]; then
    echo "T3MICRO_CAPACITY_SOAK_REPEATS must contain positive integers: ${repeat_values}" >&2
    exit 1
  fi
done

print_plan() {
  echo "[t3micro-capacity-repeat-matrix] mode=${mode}"
  echo "[t3micro-capacity-repeat-matrix] repeats=${repeat_values}"
  echo "[t3micro-capacity-repeat-matrix] matrix_name=${matrix_name}"
  echo "[t3micro-capacity-repeat-matrix] runner=tools/test/run-docker-t3micro-capacity-smoke.sh"
}

print_dry_run() {
  local repeat
  for repeat in "${repeats[@]}"; do
    echo "SOAK_REPEAT=${repeat} DOCKER_T3MICRO_RESULT_NAME=${matrix_name}-repeat-${repeat} tools/test/run-docker-t3micro-capacity-smoke.sh"
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

for repeat in "${repeats[@]}"; do
  SOAK_REPEAT="${repeat}" \
  DOCKER_T3MICRO_RESULT_NAME="${matrix_name}-repeat-${repeat}" \
    tools/test/run-docker-t3micro-capacity-smoke.sh
done
