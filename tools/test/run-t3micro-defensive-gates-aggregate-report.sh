#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-t3micro-defensive-gates-aggregate-report.sh [--print-plan|--dry-run]

Environment:
  T3MICRO_AGGREGATE_NAME       default t3micro-defensive-gates-aggregate-<timestamp>
  T3MICRO_AGGREGATE_OUTPUT_DIR default docs/performance-results
  T3MICRO_CAPACITY_RESULT_MD   optional Docker capacity archive markdown
  T3MICRO_CAPACITY_SUMMARY_TSV optional off-host transaction 100m capacity summary TSV
  T3MICRO_CAPACITY_RUN_CONTEXT_ENV optional run context next to capacity summary TSV
  T3MICRO_CAPACITY_PREREQUISITE_ENV optional capacity prerequisite failure env
  T3MICRO_SSE_RESULT_MD        optional SSE reconnect archive markdown
  T3MICRO_ADMISSION_SUMMARY_TSV optional admission summary TSV
  T3MICRO_OUTBOX_SUMMARY_TSV   optional outbox summary TSV
  T3MICRO_K6_SUMMARY_MD        optional k6 transaction 100m summary markdown
  T3MICRO_MEMORY_SUMMARY_TSV    optional component peak memory TSV
  T3MICRO_AGGREGATE_REQUIRED_GATES comma list: capacity,sse,admission,outbox,k6,memory
  T3MICRO_TOTAL_MEMORY_BUDGET_MIB default 900
  T3MICRO_AGGREGATE_AUTO_INPUTS default true
  T3MICRO_AGGREGATE_SEARCH_ROOTS default "docs/performance-results build/reports"
  T3MICRO_AGGREGATE_RUN_ID optional run id used to scope auto inputs
  T3MICRO_AGGREGATE_AUTO_INPUT_PREFIX default <aggregate name>, used when run id is empty
  T3MICRO_K6_PROFILE_SELECTOR representative|latest, default representative

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
capacity_summary="${T3MICRO_CAPACITY_SUMMARY_TSV:-}"
capacity_run_context="${T3MICRO_CAPACITY_RUN_CONTEXT_ENV:-}"
capacity_prerequisite="${T3MICRO_CAPACITY_PREREQUISITE_ENV:-}"
sse_result="${T3MICRO_SSE_RESULT_MD:-}"
admission_summary="${T3MICRO_ADMISSION_SUMMARY_TSV:-}"
outbox_summary="${T3MICRO_OUTBOX_SUMMARY_TSV:-}"
k6_summary="${T3MICRO_K6_SUMMARY_MD:-}"
memory_summary="${T3MICRO_MEMORY_SUMMARY_TSV:-}"
required_gates="${T3MICRO_AGGREGATE_REQUIRED_GATES:-}"
total_memory_budget_mib="${T3MICRO_TOTAL_MEMORY_BUDGET_MIB:-900}"
auto_inputs="${T3MICRO_AGGREGATE_AUTO_INPUTS:-true}"
search_roots="${T3MICRO_AGGREGATE_SEARCH_ROOTS:-docs/performance-results build/reports}"
aggregate_run_id="${T3MICRO_AGGREGATE_RUN_ID:-}"
auto_input_prefix="${T3MICRO_AGGREGATE_AUTO_INPUT_PREFIX:-${name}}"
k6_profile_selector="${T3MICRO_K6_PROFILE_SELECTOR:-representative}"

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

require_k6_profile_selector() {
  case "${k6_profile_selector}" in
    representative|latest)
      ;;
    *)
      echo "T3MICRO_K6_PROFILE_SELECTOR must be representative or latest: ${k6_profile_selector}" >&2
      exit 1
      ;;
  esac
}

auto_scope_token_variants() {
  local token="$1"
  local variant
  [[ -z "${token}" ]] && return 0
  echo "${token}"
  for variant in \
    "${token#t3micro-defensive-}" \
    "${token#t3micro-}" \
    "${token#defensive-}" \
    "${token#transaction-100m-}"; do
    if [[ -n "${variant}" && "${variant}" != "${token}" ]]; then
      echo "${variant}"
    fi
  done
}

file_matches_scope_token() {
  local path="$1"
  local token="$2"
  [[ -z "${token}" ]] && return 1
  [[ "${path}" == *"${token}"* ]] && return 0
  grep -F "${token}" "${path}" >/dev/null 2>&1 && return 0
  return 1
}

latest_file() {
  local pattern="$1"
  local matches=()
  local root path
  for root in ${search_roots}; do
    if [[ -d "${root}" ]]; then
      while IFS= read -r path; do
        if file_matches_auto_scope "${path}"; then
          matches+=("${path}")
        fi
      done < <(find "${root}" -type f -name "${pattern}" 2>/dev/null)
    fi
  done
  if [[ "${#matches[@]}" -eq 0 ]]; then
    return 0
  fi
  ls -t "${matches[@]}" 2>/dev/null | head -1
}

file_matches_auto_scope() {
  local path="$1"
  local token
  if [[ -n "${aggregate_run_id}" ]]; then
    while IFS= read -r token; do
      file_matches_scope_token "${path}" "${token}" && return 0
    done < <(auto_scope_token_variants "${aggregate_run_id}")
    return 1
  fi
  if [[ -n "${auto_input_prefix}" ]]; then
    while IFS= read -r token; do
      file_matches_scope_token "${path}" "${token}" && return 0
    done < <(auto_scope_token_variants "${auto_input_prefix}")
    return 1
  fi
  return 0
}

md_field_value() {
  local file="$1"
  local key="$2"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "missing"
    return 0
  fi
  awk -F ': ' -v key="- ${key}" '$1 == key {print $2}' "${file}" | tail -1
}

number_is_gt_zero() {
  local value="$1"
  [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]] || return 1
  awk -v value="${value}" 'BEGIN { exit !(value > 0) }'
}

k6_json_metric_count() {
  local json_path="$1"
  local metric="$2"
  [[ -f "${json_path}" ]] || {
    echo "0"
    return 0
  }
  if ! command -v jq >/dev/null 2>&1; then
    echo "0"
    return 0
  fi
  jq -r --arg metric "${metric}" '
    (.metrics[$metric].values // {}) as $values
    | if $values.count != null then
        $values.count
      elif ($values.passes != null or $values.fails != null) then
        (($values.passes // 0) + ($values.fails // 0))
      else
        0
      end
  ' "${json_path}" 2>/dev/null || echo "0"
}

k6_summary_score() {
  local file="$1"
  local purpose status scenario overload source_json checks_rate rate_503 count_503 burst_rate pre_allocated
  local score=0
  local failed=false

  purpose="$(md_field_value "${file}" "resultPurpose")"
  status="$(md_field_value "${file}" "resultStatus")"
  scenario="$(md_field_value "${file}" "scenario mode")"
  overload="$(md_field_value "${file}" "overload mode")"
  source_json="$(md_field_value "${file}" "sourceJson")"
  checks_rate="$(md_field_value "${file}" "checks rate")"
  rate_503="$(md_field_value "${file}" "transaction 503 rate")"
  count_503="$(md_field_value "${file}" "transaction 503 count")"
  burst_rate="$(md_field_value "${file}" "burst rate")"
  pre_allocated="$(md_field_value "${file}" "pre allocated VUs")"

  if [[ "${status}" =~ ^[0-9]+$ && "${status}" -ne 0 ]]; then
    failed=true
  fi
  if [[ "${checks_rate}" == "0" ]]; then
    failed=true
  fi
  if number_is_gt_zero "${rate_503}" || number_is_gt_zero "${count_503}"; then
    failed=true
  fi
  if [[ "${scenario}" == "burst" ]]; then
    burst_rate="${burst_rate%%/*}"
    if [[ "${burst_rate}" =~ ^[0-9]+$ && "${pre_allocated}" =~ ^[0-9]+$ && "${pre_allocated}" -lt "${burst_rate}" ]]; then
      failed=true
    fi
  fi
  if [[ -n "${source_json}" && "${source_json}" != "missing" && -f "${source_json}" ]]; then
    if number_is_gt_zero "$(k6_json_metric_count "${source_json}" "dropped_iterations")" \
      || number_is_gt_zero "$(k6_json_metric_count "${source_json}" "interrupted_iterations")"; then
      failed=true
    fi
  fi

  if [[ "${failed}" == "false" ]]; then
    score=$((score + 10000))
  fi
  case "${purpose}" in
    smoke) score=$((score + 3000)) ;;
    capacity) score=$((score + 2500)) ;;
    profile) score=$((score + 2000)) ;;
    benchmark) score=$((score + 1500)) ;;
    *) score=$((score + 1000)) ;;
  esac
  case "${scenario}" in
    constant-vus) score=$((score + 300)) ;;
    constant-arrival-rate) score=$((score + 250)) ;;
    burst) score=$((score + 100)) ;;
    *) score=$((score + 50)) ;;
  esac
  if [[ "${overload}" == "true" ]]; then
    score=$((score - 50))
  fi
  echo "${score}"
}

latest_k6_summary() {
  if [[ "${k6_profile_selector}" == "latest" ]]; then
    latest_file '*transaction-100m*-summary.md'
    return 0
  fi

  local matches=()
  local root path
  for root in ${search_roots}; do
    if [[ -d "${root}" ]]; then
      while IFS= read -r path; do
        if file_matches_auto_scope "${path}"; then
          matches+=("${path}")
        fi
      done < <(find "${root}" -type f -name '*transaction-100m*-summary.md' 2>/dev/null)
    fi
  done
  if [[ "${#matches[@]}" -eq 0 ]]; then
    return 0
  fi
  {
    for path in "${matches[@]}"; do
      printf "%08d\t%s\n" "$(k6_summary_score "${path}")" "${path}"
    done
  } | sort -r | head -1 | cut -f2-
}

capacity_summary_matches_auto_scope() {
  local summary="$1"
  local context="$2"
  if file_matches_auto_scope "${summary}"; then
    return 0
  fi
  [[ -f "${context}" ]] || return 1
  file_matches_auto_scope "${context}"
}

capacity_context_for_summary() {
  local summary="$1"
  local dir
  dir="$(dirname "${summary}")"
  echo "${dir}/capacity-run-context.env"
}

capacity_context_is_offhost() {
  local context="$1"
  [[ -f "${context}" ]] || return 1
  grep -Fx "CAPACITY_RUN_PURPOSE=capacity" "${context}" >/dev/null \
    && grep -Fx "CAPACITY_GENERATOR_MODE=docker-context" "${context}" >/dev/null \
    && grep -E '^CAPACITY_K6_DOCKER_CONTEXT=.' "${context}" >/dev/null \
    && grep -E '^CAPACITY_K6_REMOTE_BASE_URL=.' "${context}" >/dev/null \
    && grep -E '^CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=.' "${context}" >/dev/null
}

latest_capacity_summary() {
  local matches=()
  local root path context
  for root in ${search_roots}; do
    if [[ -d "${root}" ]]; then
      while IFS= read -r path; do
        context="$(capacity_context_for_summary "${path}")"
        if capacity_context_is_offhost "${context}" && capacity_summary_matches_auto_scope "${path}" "${context}"; then
          matches+=("${path}")
        fi
      done < <(find "${root}" -type f -name "capacity-summary.tsv" 2>/dev/null)
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
  capacity_summary="${capacity_summary:-$(latest_capacity_summary)}"
  if [[ -z "${capacity_run_context}" && -n "${capacity_summary}" ]]; then
    capacity_run_context="$(capacity_context_for_summary "${capacity_summary}")"
  fi
  capacity_prerequisite="${capacity_prerequisite:-$(latest_file 'capacity-prerequisite-failure.env')}"
  capacity_result="${capacity_result:-$(latest_file 'docker-t3micro-capacity*.md')}"
  sse_result="${sse_result:-$(latest_file '*sse*.md')}"
  admission_summary="${admission_summary:-$(latest_file 'http-admission-summary.tsv')}"
  outbox_summary="${outbox_summary:-$(latest_file 'outbox-provider-backlog-summary.tsv')}"
  k6_summary="${k6_summary:-$(latest_k6_summary)}"
  memory_summary="${memory_summary:-$(latest_file '*memory-summary.tsv')}"
}

require_bool "T3MICRO_AGGREGATE_AUTO_INPUTS" "${auto_inputs}"
require_non_negative_number "T3MICRO_TOTAL_MEMORY_BUDGET_MIB" "${total_memory_budget_mib}"
require_k6_profile_selector
resolve_auto_inputs

gate_path() {
  local gate="$1"
  case "${gate}" in
    capacity) echo "${capacity_summary:-${capacity_prerequisite}}" ;;
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
  echo "[t3micro-defensive-aggregate] auto_input_run_id=${aggregate_run_id:-missing}"
  echo "[t3micro-defensive-aggregate] auto_input_prefix=${auto_input_prefix:-missing}"
  echo "[t3micro-defensive-aggregate] capacity=${capacity_result:-missing}"
  echo "[t3micro-defensive-aggregate] capacity_summary=${capacity_summary:-missing}"
  echo "[t3micro-defensive-aggregate] capacity_run_context=${capacity_run_context:-missing}"
  echo "[t3micro-defensive-aggregate] capacity_prerequisite=${capacity_prerequisite:-missing}"
  echo "[t3micro-defensive-aggregate] sse=${sse_result:-missing}"
  echo "[t3micro-defensive-aggregate] admission=${admission_summary:-missing}"
  echo "[t3micro-defensive-aggregate] outbox=${outbox_summary:-missing}"
  echo "[t3micro-defensive-aggregate] k6=${k6_summary:-missing}"
  echo "[t3micro-defensive-aggregate] memory=${memory_summary:-missing}"
  echo "[t3micro-defensive-aggregate] k6_profile_selector=${k6_profile_selector}"
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

env_value() {
  local file="$1"
  local key="$2"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "missing"
    return 0
  fi
  awk -F '=' -v key="${key}" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "${file}"
}

capacity_prerequisite_status_value() {
  env_value "${capacity_prerequisite}" "CAPACITY_PREREQUISITE_STATUS"
}

capacity_prerequisite_signal_value() {
  printf "reason=%s missing=%s" \
    "$(env_value "${capacity_prerequisite}" "CAPACITY_PREREQUISITE_FAILURE_REASON")" \
    "$(env_value "${capacity_prerequisite}" "CAPACITY_PREREQUISITE_MISSING_VARS")"
}

capacity_summary_status() {
  local file="$1"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "missing"
    return 0
  fi
  awk -F '\t' '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "status") status_column = i
      }
      next
    }
    status_column {
      rows += 1
      if ($status_column != "0") failed = 1
    }
    END {
      if (!status_column || rows == 0) {
        print "invalid"
      } else if (failed) {
        print "fail"
      } else {
        print "pass"
      }
    }
  ' "${file}"
}

capacity_summary_status_for_grade() {
  local file="$1"
  local grade="$2"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "missing"
    return 0
  fi
  awk -F '\t' -v grade="${grade}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "phase") phase_column = i
        if ($i == "profile") profile_column = i
        if ($i == "status") status_column = i
      }
      next
    }
    phase_column && profile_column && status_column {
      is_soak = ($phase_column == "long-soak" || $profile_column ~ /soak/)
      if ((grade == "soak" && !is_soak) || (grade == "capacity" && is_soak)) {
        next
      }
      rows += 1
      if ($status_column != "0") failed = 1
    }
    END {
      if (!phase_column || !profile_column || !status_column || rows == 0) {
        print "missing"
      } else if (failed) {
        print "fail"
      } else {
        print "pass"
      }
    }
  ' "${file}"
}

capacity_profile_count() {
  local file="$1"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "missing"
    return 0
  fi
  awk 'NR > 1 {count += 1} END {print count + 0}' "${file}"
}

capacity_profile_count_for_grade() {
  local file="$1"
  local grade="$2"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "missing"
    return 0
  fi
  awk -F '\t' -v grade="${grade}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "phase") phase_column = i
        if ($i == "profile") profile_column = i
      }
      next
    }
    phase_column && profile_column {
      is_soak = ($phase_column == "long-soak" || $profile_column ~ /soak/)
      if ((grade == "soak" && is_soak) || (grade == "capacity" && !is_soak)) {
        count += 1
      }
    }
    END { print count + 0 }
  ' "${file}"
}

capacity_tsv_column_max() {
  local file="$1"
  local key="$2"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "n/a"
    return 0
  fi
  awk -F '\t' -v key="${key}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == key) column = i
      }
      next
    }
    column && $column ~ /^[0-9]+([.][0-9]+)?$/ {
      value = $column + 0
      if (!found || value > max) {
        max = value
        text = $column
      }
      found = 1
    }
    END {
      if (found) {
        print text
      } else {
        print "n/a"
      }
    }
  ' "${file}"
}

capacity_tsv_column_max_for_grade() {
  local file="$1"
  local key="$2"
  local grade="$3"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "n/a"
    return 0
  fi
  awk -F '\t' -v key="${key}" -v grade="${grade}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == key) column = i
        if ($i == "phase") phase_column = i
        if ($i == "profile") profile_column = i
      }
      next
    }
    column && phase_column && profile_column {
      is_soak = ($phase_column == "long-soak" || $profile_column ~ /soak/)
      if ((grade == "soak" && !is_soak) || (grade == "capacity" && is_soak)) {
        next
      }
      if ($column ~ /^[0-9]+([.][0-9]+)?$/) {
        value = $column + 0
        if (!found || value > max) {
          max = value
          text = $column
        }
        found = 1
      }
    }
    END {
      if (found) {
        print text
      } else {
        print "n/a"
      }
    }
  ' "${file}"
}

capacity_cpu_peak() {
  local file="$1"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "n/a"
    return 0
  fi
  awk -F '\t' '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "backend_cpu_percent") backend_column = i
        if ($i == "postgres_cpu_percent") postgres_column = i
      }
      next
    }
    {
      for (i = 1; i <= NF; i++) {
        if ((i == backend_column || i == postgres_column) && $i ~ /^[0-9]+([.][0-9]+)?$/) {
          value = $i + 0
          if (!found || value > max) {
            max = value
            text = $i
          }
          found = 1
        }
      }
    }
    END {
      if (found) {
        print text
      } else {
        print "n/a"
      }
    }
  ' "${file}"
}

capacity_cpu_peak_for_grade() {
  local file="$1"
  local grade="$2"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    printf "n/a"
    return 0
  fi
  awk -F '\t' -v grade="${grade}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "phase") phase_column = i
        if ($i == "profile") profile_column = i
        if ($i == "backend_cpu_percent") backend_column = i
        if ($i == "postgres_cpu_percent") postgres_column = i
      }
      next
    }
    phase_column && profile_column {
      is_soak = ($phase_column == "long-soak" || $profile_column ~ /soak/)
      if ((grade == "soak" && !is_soak) || (grade == "capacity" && is_soak)) {
        next
      }
      for (i = 1; i <= NF; i++) {
        if ((i == backend_column || i == postgres_column) && $i ~ /^[0-9]+([.][0-9]+)?$/) {
          value = $i + 0
          if (!found || value > max) {
            max = value
            text = $i
          }
          found = 1
        }
      }
    }
    END {
      if (found) {
        print text
      } else {
        print "n/a"
      }
    }
  ' "${file}"
}

capacity_status_value() {
  if [[ -n "${capacity_summary}" ]]; then
    capacity_summary_status_for_grade "${capacity_summary}" capacity
  else
    md_value "${capacity_result}" "capacity smoke status"
  fi
}

capacity_soak_status_value() {
  capacity_summary_status_for_grade "${capacity_summary}" soak
}

capacity_cpu_value() {
  if [[ -n "${capacity_summary}" ]]; then
    capacity_cpu_peak_for_grade "${capacity_summary}" capacity
  else
    md_value "${capacity_result}" "peakCpuPercent"
  fi
}

capacity_soak_cpu_value() {
  capacity_cpu_peak_for_grade "${capacity_summary}" soak
}

capacity_memory_value() {
  if [[ -n "${capacity_summary}" ]]; then
    printf "n/a"
  else
    md_value "${capacity_result}" "peakMemoryMiB"
  fi
}

capacity_signal_value() {
  if [[ -n "${capacity_summary}" ]]; then
    printf "429Rate=%s hikariPending=%s profiles=%s" \
      "$(capacity_tsv_column_max_for_grade "${capacity_summary}" "transaction_429_rate" capacity)" \
      "$(capacity_tsv_column_max_for_grade "${capacity_summary}" "hikari_pending" capacity)" \
      "$(capacity_profile_count_for_grade "${capacity_summary}" capacity)"
  else
    printf "repeat=%s" "$(md_value "${capacity_result}" "repeat")"
  fi
}

capacity_soak_signal_value() {
  printf "429Rate=%s hikariPending=%s profiles=%s" \
    "$(capacity_tsv_column_max_for_grade "${capacity_summary}" "transaction_429_rate" soak)" \
    "$(capacity_tsv_column_max_for_grade "${capacity_summary}" "hikari_pending" soak)" \
    "$(capacity_profile_count_for_grade "${capacity_summary}" soak)"
}

capacity_source_value() {
  if [[ -n "${capacity_summary}" ]]; then
    printf "%s" "${capacity_summary}"
  else
    printf "%s" "${capacity_result:-missing}"
  fi
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

assert_capacity_summary() {
  if [[ -z "${capacity_summary}" ]]; then
    return 0
  fi
  if [[ ! -f "${capacity_summary}" ]]; then
    echo "capacity summary not found: ${capacity_summary}" >&2
    exit 1
  fi
  if [[ -z "${capacity_run_context}" ]]; then
    capacity_run_context="$(capacity_context_for_summary "${capacity_summary}")"
  fi
  if ! capacity_context_is_offhost "${capacity_run_context}"; then
    echo "capacity summary requires off-host docker-context run context: ${capacity_run_context}" >&2
    exit 1
  fi
  case "$(capacity_summary_status "${capacity_summary}")" in
    pass) ;;
    fail)
      echo "capacity summary contains failed profiles: ${capacity_summary}" >&2
      exit 1
      ;;
    *)
      echo "capacity summary is invalid: ${capacity_summary}" >&2
      exit 1
      ;;
  esac
}

write_report() {
  mkdir -p "${output_dir}"
  {
    echo "# ${name}"
    echo
    echo "## Inputs"
    echo
    echo "- capacityResult: ${capacity_result:-missing}"
    echo "- capacitySummary: ${capacity_summary:-missing}"
    echo "- capacityRunContext: ${capacity_run_context:-missing}"
    echo "- capacityPrerequisite: ${capacity_prerequisite:-missing}"
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
    echo "| capacity smoke | $(md_value "${capacity_result}" "capacity smoke status") | $(md_value "${capacity_result}" "peakCpuPercent") | $(md_value "${capacity_result}" "peakMemoryMiB") | repeat=$(md_value "${capacity_result}" "repeat") | ${capacity_result:-missing} |"
    echo "| capacity prerequisite | $(capacity_prerequisite_status_value) | n/a | n/a | $(capacity_prerequisite_signal_value) | ${capacity_prerequisite:-missing} |"
    echo "| capacity | $(capacity_status_value) | $(capacity_cpu_value) | $(capacity_memory_value) | $(capacity_signal_value) | $(capacity_source_value) |"
    echo "| capacity soak | $(capacity_soak_status_value) | $(capacity_soak_cpu_value) | n/a | $(capacity_soak_signal_value) | ${capacity_summary:-missing} |"
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
assert_capacity_summary
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
