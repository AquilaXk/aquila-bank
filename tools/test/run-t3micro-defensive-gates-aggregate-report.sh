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
  T3MICRO_K6_SUMMARY_MD        optional k6 transaction 100m summary markdown
  T3MICRO_MEMORY_SUMMARY_TSV    optional component peak memory TSV
  T3MICRO_AGGREGATE_REQUIRED_GATES comma list: capacity,sse,admission,outbox,k6,memory
  T3MICRO_TOTAL_MEMORY_BUDGET_MIB default 900
  T3MICRO_AGGREGATE_AUTO_INPUTS default true
  T3MICRO_AGGREGATE_SEARCH_ROOTS default "docs/performance-results build/reports"

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
k6_summary="${T3MICRO_K6_SUMMARY_MD:-}"
memory_summary="${T3MICRO_MEMORY_SUMMARY_TSV:-}"
required_gates="${T3MICRO_AGGREGATE_REQUIRED_GATES:-}"
total_memory_budget_mib="${T3MICRO_TOTAL_MEMORY_BUDGET_MIB:-900}"
auto_inputs="${T3MICRO_AGGREGATE_AUTO_INPUTS:-true}"
search_roots="${T3MICRO_AGGREGATE_SEARCH_ROOTS:-docs/performance-results build/reports}"

require_bool() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_non_negative_number() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be zero or a positive number: ${value}" >&2
    exit 1
  fi
}

latest_file() {
  local pattern="$1"
  local matches=()
  local root path
  for root in ${search_roots}; do
    if [[ -d "${root}" ]]; then
      while IFS= read -r path; do
        matches+=("${path}")
      done < <(find "${root}" -type f -name "${pattern}" 2>/dev/null)
    fi
  done
  if [[ "${#matches[@]}" -eq 0 ]]; then
    return 0
  fi
  ls -t "${matches[@]}" 2>/dev/null | head -1
}

resolve_auto_inputs() {
  if [[ "${auto_inputs}" != "true" ]]; then
    return 0
  fi
  capacity_result="${capacity_result:-$(latest_file '*capacity*.md')}"
  sse_result="${sse_result:-$(latest_file '*sse*.md')}"
  admission_summary="${admission_summary:-$(latest_file 'http-admission-summary.tsv')}"
  outbox_summary="${outbox_summary:-$(latest_file 'outbox-provider-backlog-summary.tsv')}"
  k6_summary="${k6_summary:-$(latest_file '*transaction-100m*-summary.md')}"
  memory_summary="${memory_summary:-$(latest_file '*memory-summary.tsv')}"
}

require_bool "T3MICRO_AGGREGATE_AUTO_INPUTS" "${auto_inputs}"
require_non_negative_number "T3MICRO_TOTAL_MEMORY_BUDGET_MIB" "${total_memory_budget_mib}"
resolve_auto_inputs

gate_path() {
  local gate="$1"
  case "${gate}" in
    capacity) echo "${capacity_result}" ;;
    sse) echo "${sse_result}" ;;
    admission) echo "${admission_summary}" ;;
    outbox) echo "${outbox_summary}" ;;
    k6) echo "${k6_summary}" ;;
    memory) echo "${memory_summary}" ;;
    *)
      echo "unknown required gate: ${gate}" >&2
      exit 1
      ;;
  esac
}

assert_required_gates() {
  if [[ -z "${required_gates}" ]]; then
    return 0
  fi
  local gate path
  IFS=',' read -r -a gates <<<"${required_gates}"
  for gate in "${gates[@]}"; do
    path="$(gate_path "${gate}")"
    if [[ -z "${path}" || ! -f "${path}" ]]; then
      echo "required aggregate input is missing: ${gate}" >&2
      exit 1
    fi
  done
}

print_plan() {
  echo "[t3micro-defensive-aggregate] mode=${mode}"
  echo "[t3micro-defensive-aggregate] output=${output_path}"
  echo "[t3micro-defensive-aggregate] auto_inputs=${auto_inputs}"
  echo "[t3micro-defensive-aggregate] search_roots=${search_roots}"
  echo "[t3micro-defensive-aggregate] capacity=${capacity_result:-missing}"
  echo "[t3micro-defensive-aggregate] sse=${sse_result:-missing}"
  echo "[t3micro-defensive-aggregate] admission=${admission_summary:-missing}"
  echo "[t3micro-defensive-aggregate] outbox=${outbox_summary:-missing}"
  echo "[t3micro-defensive-aggregate] k6=${k6_summary:-missing}"
  echo "[t3micro-defensive-aggregate] memory=${memory_summary:-missing}"
  echo "[t3micro-defensive-aggregate] required_gates=${required_gates:-none}"
  echo "[t3micro-defensive-aggregate] total_memory_budget_mib=${total_memory_budget_mib}"
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

memory_total_mib() {
  local file="$1"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "missing"
    return 0
  fi
  awk -F '\t' '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "peak_memory_mib") column = i
      }
      next
    }
    column && $column ~ /^[0-9]+([.][0-9]+)?$/ {
      total += $column
    }
    END {
      if (!column) {
        print "missing"
      } else {
        printf "%.2f", total + 0
      }
    }
  ' "${file}"
}

memory_status() {
  local total="$1"
  if [[ "${total}" == "missing" ]]; then
    printf "missing"
    return 0
  fi
  awk -v total="${total}" -v budget="${total_memory_budget_mib}" 'BEGIN {
    if (total <= budget) {
      printf "pass"
    } else {
      printf "fail"
    }
  }'
}

assert_memory_budget() {
  local total
  total="$(memory_total_mib "${memory_summary}")"
  if [[ -n "${memory_summary}" && -f "${memory_summary}" && "${total}" == "missing" ]]; then
    echo "t3.micro aggregate memory summary is invalid: ${memory_summary}" >&2
    exit 1
  fi
  if [[ "${total}" == "missing" ]]; then
    return 0
  fi
  if [[ "$(memory_status "${total}")" == "fail" ]]; then
    echo "t3.micro aggregate memory budget exceeded: actual=${total} budget=${total_memory_budget_mib}" >&2
    exit 1
  fi
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
    echo "- k6Summary: ${k6_summary:-missing}"
    echo "- memorySummary: ${memory_summary:-missing}"
    echo
    echo "## Gate Summary"
    echo
    echo "| Gate | Status | Peak CPU % | Peak Memory MiB | Backlog / Reject Signal | Source |"
    echo "| --- | --- | ---: | ---: | --- | --- |"
    echo "| capacity | $(md_value "${capacity_result}" "capacity smoke status") | $(md_value "${capacity_result}" "peakCpuPercent") | $(md_value "${capacity_result}" "peakMemoryMiB") | repeat=$(md_value "${capacity_result}" "repeat") | ${capacity_result:-missing} |"
    echo "| sse reconnect | $(md_value "${sse_result}" "status") | $(md_value "${sse_result}" "peakCpuPercent") | $(md_value "${sse_result}" "peakMemoryMiB") | clients=$(md_value "${sse_result}" "reconnectClients") rounds=$(md_value "${sse_result}" "reconnectRounds") | ${sse_result:-missing} |"
    echo "| http admission | n/a | n/a | n/a | rejected=$(tsv_value "${admission_summary}" "rejected_count") failed_rate=$(tsv_value "${admission_summary}" "failed_rate") | ${admission_summary:-missing} |"
    echo "| outbox backlog | n/a | n/a | n/a | lag=$(tsv_value "${outbox_summary}" "lag_seconds") failed=$(tsv_value "${outbox_summary}" "failed_count") dlq=$(tsv_value "${outbox_summary}" "dlq_count") | ${outbox_summary:-missing} |"
    echo "| k6 transaction 100m | n/a | n/a | n/a | hotFirstP95=$(md_value "${k6_summary}" "hot first p95 ms") hotCursorP95=$(md_value "${k6_summary}" "hot cursor p95 ms") coldFirstP95=$(md_value "${k6_summary}" "cold first p95 ms") coldCursorP95=$(md_value "${k6_summary}" "cold cursor p95 ms") 429Rate=$(md_value "${k6_summary}" "transaction 429 rate") | ${k6_summary:-missing} |"
    local total_memory
    total_memory="$(memory_total_mib "${memory_summary}")"
    echo "| total memory | $(memory_status "${total_memory}") | n/a | ${total_memory} | budget=${total_memory_budget_mib} source=${memory_summary:-missing} | ${memory_summary:-missing} |"
    echo
    echo "## Notes"
    echo
    echo "- missing 값은 해당 gate가 아직 같은 aggregate run에 연결되지 않았음을 뜻합니다."
    echo "- token, 운영 URL, raw Authorization header는 기록하지 않습니다."
  } >"${output_path}"
  echo "${output_path}"
}

print_plan
assert_required_gates
assert_memory_budget
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  echo "write ${output_path}"
  exit 0
fi
write_report
