#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-t3micro-defensive-full-aggregate.sh [--print-plan|--dry-run]

Environment:
  T3MICRO_FULL_AGGREGATE_NAME default t3micro-defensive-full-<timestamp>
  T3MICRO_FULL_OUTPUT_DIR     default docs/performance-results
  T3MICRO_FULL_BASE_URL       default http://localhost:${LOADTEST_BACKEND_PORT:-18080}
  T3MICRO_FULL_RUN_CAPACITY   default true
  T3MICRO_FULL_RUN_SSE        default true
  T3MICRO_FULL_RUN_ADMISSION  default true
  T3MICRO_FULL_RUN_OUTBOX     default true
  T3MICRO_FULL_RUN_K6         default true
  T3MICRO_FULL_K6_REPORT_NAME default <name>-k6
  T3MICRO_FULL_MEMORY_SUMMARY_TSV default build/reports/t3micro/<name>-memory-summary.tsv
  T3MICRO_FULL_REQUIRED_GATES default capacity,sse,admission,outbox,k6,memory

Examples:
  tools/test/run-t3micro-defensive-full-aggregate.sh --print-plan
  T3MICRO_FULL_MEMORY_SUMMARY_TSV=build/reports/t3micro/run-memory-summary.tsv \
    tools/test/run-t3micro-defensive-full-aggregate.sh
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

require_bool() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_known_gates() {
  local csv="$1"
  local gate
  IFS=',' read -r -a gates <<<"${csv}"
  for gate in "${gates[@]}"; do
    case "${gate}" in
      capacity|sse|admission|outbox|k6|memory) ;;
      *)
        echo "unknown required gate: ${gate}" >&2
        exit 1
        ;;
    esac
  done
}

name="${T3MICRO_FULL_AGGREGATE_NAME:-t3micro-defensive-full-$(date +%Y-%m-%d-%H%M%S)}"
output_dir="${T3MICRO_FULL_OUTPUT_DIR:-docs/performance-results}"
base_url="${T3MICRO_FULL_BASE_URL:-http://localhost:${LOADTEST_BACKEND_PORT:-18080}}"
base_url="${base_url%/}"
run_capacity="${T3MICRO_FULL_RUN_CAPACITY:-true}"
run_sse="${T3MICRO_FULL_RUN_SSE:-true}"
run_admission="${T3MICRO_FULL_RUN_ADMISSION:-true}"
run_outbox="${T3MICRO_FULL_RUN_OUTBOX:-true}"
run_k6="${T3MICRO_FULL_RUN_K6:-true}"
k6_report_name="${T3MICRO_FULL_K6_REPORT_NAME:-${name}-k6}"
required_gates="${T3MICRO_FULL_REQUIRED_GATES:-capacity,sse,admission,outbox,k6,memory}"

capacity_name="${name}-capacity"
sse_name="${name}-sse"
admission_name="${name}-admission"
outbox_name="${name}-outbox"
capacity_result="${output_dir}/${capacity_name}.md"
sse_result="${output_dir}/${sse_name}.md"
admission_summary="build/reports/admission/${admission_name}/http-admission-summary.tsv"
outbox_summary="build/reports/outbox/${outbox_name}/outbox-provider-backlog-summary.tsv"
k6_summary="build/reports/k6/${k6_report_name}-summary.md"
memory_summary="${T3MICRO_FULL_MEMORY_SUMMARY_TSV:-build/reports/t3micro/${name}-memory-summary.tsv}"
aggregate_output="${output_dir}/${name}.md"

require_bool "T3MICRO_FULL_RUN_CAPACITY" "${run_capacity}"
require_bool "T3MICRO_FULL_RUN_SSE" "${run_sse}"
require_bool "T3MICRO_FULL_RUN_ADMISSION" "${run_admission}"
require_bool "T3MICRO_FULL_RUN_OUTBOX" "${run_outbox}"
require_bool "T3MICRO_FULL_RUN_K6" "${run_k6}"
require_known_gates "${required_gates}"

print_plan() {
  echo "[t3micro-defensive-full-aggregate] mode=${mode}"
  echo "[t3micro-defensive-full-aggregate] name=${name}"
  echo "[t3micro-defensive-full-aggregate] base_url=${base_url}"
  echo "[t3micro-defensive-full-aggregate] run_capacity=${run_capacity}"
  echo "[t3micro-defensive-full-aggregate] run_sse=${run_sse}"
  echo "[t3micro-defensive-full-aggregate] run_admission=${run_admission}"
  echo "[t3micro-defensive-full-aggregate] run_outbox=${run_outbox}"
  echo "[t3micro-defensive-full-aggregate] run_k6=${run_k6}"
  echo "[t3micro-defensive-full-aggregate] capacity_result=${capacity_result}"
  echo "[t3micro-defensive-full-aggregate] sse_result=${sse_result}"
  echo "[t3micro-defensive-full-aggregate] admission_summary=${admission_summary}"
  echo "[t3micro-defensive-full-aggregate] outbox_summary=${outbox_summary}"
  echo "[t3micro-defensive-full-aggregate] k6_summary=${k6_summary}"
  echo "[t3micro-defensive-full-aggregate] memory_summary=${memory_summary}"
  echo "[t3micro-defensive-full-aggregate] aggregate_output=${aggregate_output}"
  echo "[t3micro-defensive-full-aggregate] required_gates=${required_gates}"
}

print_dry_run() {
  if [[ "${run_capacity}" == "true" ]]; then
    echo "DOCKER_T3MICRO_RESULT_NAME=${capacity_name} tools/test/run-docker-t3micro-capacity-smoke.sh"
  fi
  if [[ "${run_sse}" == "true" ]]; then
    echo "SSE_T3MICRO_RESULT_NAME=${sse_name} tools/test/run-sse-reconnect-storm-t3micro-gate.sh"
  fi
  if [[ "${run_admission}" == "true" ]]; then
    echo "ADMISSION_NAME=${admission_name} ADMISSION_BASE_URL=${base_url} tools/test/run-defensive-runtime-http-admission-compose.sh"
  fi
  if [[ "${run_outbox}" == "true" ]]; then
    echo "OUTBOX_BACKLOG_NAME=${outbox_name} OUTBOX_BACKLOG_BASE_URL=${base_url} tools/test/run-outbox-provider-backlog-local-gate.sh"
  fi
  if [[ "${run_k6}" == "true" ]]; then
    echo "K6_REPORT_NAME=${k6_report_name} tools/test/run-k6-transaction-100m-loadtest.sh"
  fi
  echo "T3MICRO_AGGREGATE_REQUIRED_GATES=${required_gates} T3MICRO_CAPACITY_RESULT_MD=${capacity_result} T3MICRO_SSE_RESULT_MD=${sse_result} T3MICRO_ADMISSION_SUMMARY_TSV=${admission_summary} T3MICRO_OUTBOX_SUMMARY_TSV=${outbox_summary} T3MICRO_K6_SUMMARY_MD=${k6_summary} T3MICRO_MEMORY_SUMMARY_TSV=${memory_summary} tools/test/run-t3micro-defensive-gates-aggregate-report.sh"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_dry_run
  exit 0
fi

if [[ "${run_capacity}" == "true" ]]; then
  DOCKER_T3MICRO_RESULT_NAME="${capacity_name}" tools/test/run-docker-t3micro-capacity-smoke.sh
fi
if [[ "${run_sse}" == "true" ]]; then
  SSE_T3MICRO_RESULT_NAME="${sse_name}" tools/test/run-sse-reconnect-storm-t3micro-gate.sh
fi
if [[ "${run_admission}" == "true" ]]; then
  ADMISSION_NAME="${admission_name}" \
  ADMISSION_BASE_URL="${base_url}" \
    tools/test/run-defensive-runtime-http-admission-compose.sh
fi
if [[ "${run_outbox}" == "true" ]]; then
  OUTBOX_BACKLOG_NAME="${outbox_name}" \
  OUTBOX_BACKLOG_BASE_URL="${base_url}" \
    tools/test/run-outbox-provider-backlog-local-gate.sh
fi
if [[ "${run_k6}" == "true" ]]; then
  K6_REPORT_NAME="${k6_report_name}" tools/test/run-k6-transaction-100m-loadtest.sh
fi

T3MICRO_AGGREGATE_NAME="${name}" \
T3MICRO_AGGREGATE_OUTPUT_DIR="${output_dir}" \
T3MICRO_CAPACITY_RESULT_MD="${capacity_result}" \
T3MICRO_SSE_RESULT_MD="${sse_result}" \
T3MICRO_ADMISSION_SUMMARY_TSV="${admission_summary}" \
T3MICRO_OUTBOX_SUMMARY_TSV="${outbox_summary}" \
T3MICRO_K6_SUMMARY_MD="${k6_summary}" \
T3MICRO_MEMORY_SUMMARY_TSV="${memory_summary}" \
T3MICRO_AGGREGATE_REQUIRED_GATES="${required_gates}" \
  tools/test/run-t3micro-defensive-gates-aggregate-report.sh
