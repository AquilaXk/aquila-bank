#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-t3micro-defensive-gates-aggregate-report.sh [--print-plan|--dry-run]

Environment:
  T3MICRO_AGGREGATE_NAME       default t3micro-defensive-gates-aggregate-<timestamp>
  T3MICRO_AGGREGATE_OUTPUT_DIR default docs/performance-results
  T3MICRO_CAPACITY_RESULT_MD   optional Docker capacity archive markdown
  T3MICRO_SSE_RESULT_MD        optional SSE reconnect archive markdown
  T3MICRO_ADMISSION_SUMMARY_TSV optional admission summary TSV
  T3MICRO_OUTBOX_SUMMARY_TSV   optional outbox summary TSV

Examples:
  tools/test/run-t3micro-defensive-gates-aggregate-report.sh --print-plan
  T3MICRO_CAPACITY_RESULT_MD=docs/performance-results/docker-t3micro-capacity.md \
    tools/test/run-t3micro-defensive-gates-aggregate-report.sh
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

name="${T3MICRO_AGGREGATE_NAME:-t3micro-defensive-gates-aggregate-$(date +%Y-%m-%d-%H%M%S)}"
output_dir="${T3MICRO_AGGREGATE_OUTPUT_DIR:-docs/performance-results}"
output_path="${output_dir}/${name}.md"
capacity_result="${T3MICRO_CAPACITY_RESULT_MD:-}"
sse_result="${T3MICRO_SSE_RESULT_MD:-}"
admission_summary="${T3MICRO_ADMISSION_SUMMARY_TSV:-}"
outbox_summary="${T3MICRO_OUTBOX_SUMMARY_TSV:-}"

print_plan() {
  echo "[t3micro-defensive-aggregate] mode=${mode}"
  echo "[t3micro-defensive-aggregate] output=${output_path}"
  echo "[t3micro-defensive-aggregate] capacity=${capacity_result:-missing}"
  echo "[t3micro-defensive-aggregate] sse=${sse_result:-missing}"
  echo "[t3micro-defensive-aggregate] admission=${admission_summary:-missing}"
  echo "[t3micro-defensive-aggregate] outbox=${outbox_summary:-missing}"
}

md_value() {
  local file="$1"
  local key="$2"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "missing"
    return 0
  fi
  local value
  value="$(awk -F ': ' -v key="- ${key}" '$1 == key {print $2}' "${file}" | tail -1)"
  printf "%s" "${value:-missing}"
}

tsv_value() {
  local file="$1"
  local key="$2"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "missing"
    return 0
  fi
  awk -F '\t' -v key="${key}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == key) column = i
      }
      next
    }
    NR == 2 && column { print $column }
  ' "${file}" | tail -1
}

write_report() {
  mkdir -p "${output_dir}"
  {
    echo "# ${name}"
    echo
    echo "## Inputs"
    echo
    echo "- capacityResult: ${capacity_result:-missing}"
    echo "- sseResult: ${sse_result:-missing}"
    echo "- admissionSummary: ${admission_summary:-missing}"
    echo "- outboxSummary: ${outbox_summary:-missing}"
    echo
    echo "## Gate Summary"
    echo
    echo "| Gate | Status | Peak CPU % | Peak Memory MiB | Backlog / Reject Signal | Source |"
    echo "| --- | --- | ---: | ---: | --- | --- |"
    echo "| capacity | $(md_value "${capacity_result}" "capacity smoke status") | $(md_value "${capacity_result}" "peakCpuPercent") | $(md_value "${capacity_result}" "peakMemoryMiB") | repeat=$(md_value "${capacity_result}" "repeat") | ${capacity_result:-missing} |"
    echo "| sse reconnect | $(md_value "${sse_result}" "status") | $(md_value "${sse_result}" "peakCpuPercent") | $(md_value "${sse_result}" "peakMemoryMiB") | clients=$(md_value "${sse_result}" "reconnectClients") rounds=$(md_value "${sse_result}" "reconnectRounds") | ${sse_result:-missing} |"
    echo "| http admission | n/a | n/a | n/a | rejected=$(tsv_value "${admission_summary}" "rejected_count") failed_rate=$(tsv_value "${admission_summary}" "failed_rate") | ${admission_summary:-missing} |"
    echo "| outbox backlog | n/a | n/a | n/a | lag=$(tsv_value "${outbox_summary}" "lag_seconds") failed=$(tsv_value "${outbox_summary}" "failed_count") dlq=$(tsv_value "${outbox_summary}" "dlq_count") | ${outbox_summary:-missing} |"
    echo
    echo "## Notes"
    echo
    echo "- missing 값은 해당 gate가 아직 같은 aggregate run에 연결되지 않았음을 뜻합니다."
    echo "- token, 운영 URL, raw Authorization header는 기록하지 않습니다."
  } >"${output_path}"
  echo "${output_path}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  echo "write ${output_path}"
  exit 0
fi
write_report
