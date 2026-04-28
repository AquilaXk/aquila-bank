#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-offhost-capacity-baseline-report.sh [--print-plan|--dry-run]

Environment:
  OFFHOST_BASELINE_NAME default transaction-100m-offhost-capacity-baseline-<timestamp>
  OFFHOST_BASELINE_OUTPUT_DIR default docs/performance-results
  OFFHOST_BASELINE_ENV_FILE optional local env file for off-host capacity
  OFFHOST_BASELINE_RUN_CAPACITY default true
  OFFHOST_BASELINE_DOCTOR_CONNECTIVITY default true
  OFFHOST_BASELINE_LONG_SOAK_DURATION default 30m
  OFFHOST_BASELINE_CAPACITY_SUMMARY_TSV optional existing capacity summary TSV
  OFFHOST_BASELINE_CAPACITY_RUN_CONTEXT_ENV optional capacity run context env
  OFFHOST_BASELINE_CAPACITY_PREREQUISITE_ENV optional prerequisite failure env

Examples:
  OFFHOST_BASELINE_ENV_FILE=.env/offhost-capacity.env \
    tools/test/run-transaction-100m-offhost-capacity-baseline-report.sh
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

name="${OFFHOST_BASELINE_NAME:-transaction-100m-offhost-capacity-baseline-$(date +%Y-%m-%d-%H%M%S)}"
output_dir="${OFFHOST_BASELINE_OUTPUT_DIR:-docs/performance-results}"
output_path="${output_dir}/${name}.md"
env_file="${OFFHOST_BASELINE_ENV_FILE:-${CAPACITY_ENV_FILE:-}}"
run_capacity="${OFFHOST_BASELINE_RUN_CAPACITY:-true}"
doctor_connectivity="${OFFHOST_BASELINE_DOCTOR_CONNECTIVITY:-true}"
long_soak_duration="${OFFHOST_BASELINE_LONG_SOAK_DURATION:-30m}"
capacity_name="${OFFHOST_BASELINE_CAPACITY_NAME:-${name}}"
report_dir="build/reports/k6/${capacity_name}"
capacity_summary="${OFFHOST_BASELINE_CAPACITY_SUMMARY_TSV:-${report_dir}/capacity-summary.tsv}"
capacity_run_context="${OFFHOST_BASELINE_CAPACITY_RUN_CONTEXT_ENV:-${report_dir}/capacity-run-context.env}"
capacity_prerequisite="${OFFHOST_BASELINE_CAPACITY_PREREQUISITE_ENV:-${report_dir}/capacity-prerequisite-failure.env}"

require_bool() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_duration() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*(s|m|h)$ ]]; then
    echo "${name} must use a positive duration such as 30m: ${value}" >&2
    exit 1
  fi
}

require_bool "OFFHOST_BASELINE_RUN_CAPACITY" "${run_capacity}"
require_bool "OFFHOST_BASELINE_DOCTOR_CONNECTIVITY" "${doctor_connectivity}"
require_duration "OFFHOST_BASELINE_LONG_SOAK_DURATION" "${long_soak_duration}"

print_plan() {
  echo "[transaction-100m-offhost-baseline] mode=${mode}"
  echo "[transaction-100m-offhost-baseline] name=${name}"
  echo "[transaction-100m-offhost-baseline] output=${output_path}"
  echo "[transaction-100m-offhost-baseline] env_file=${env_file:-missing}"
  echo "[transaction-100m-offhost-baseline] run_capacity=${run_capacity}"
  echo "[transaction-100m-offhost-baseline] doctor_connectivity=${doctor_connectivity}"
  echo "[transaction-100m-offhost-baseline] long_soak_duration=${long_soak_duration}"
  echo "[transaction-100m-offhost-baseline] capacity_name=${capacity_name}"
  echo "[transaction-100m-offhost-baseline] capacity_summary=${capacity_summary}"
  echo "[transaction-100m-offhost-baseline] capacity_run_context=${capacity_run_context}"
  echo "[transaction-100m-offhost-baseline] capacity_prerequisite=${capacity_prerequisite}"
}

capacity_status_for_grade() {
  local file="$1"
  local grade="$2"
  if [[ ! -f "${file}" ]]; then
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

capacity_column_max_for_grade() {
  local file="$1"
  local key="$2"
  local grade="$3"
  if [[ ! -f "${file}" ]]; then
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

env_value() {
  local file="$1"
  local key="$2"
  if [[ ! -f "${file}" ]]; then
    printf "missing"
    return 0
  fi
  awk -F '=' -v key="${key}" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "${file}"
}

run_doctor() {
  if [[ "${mode}" == "dry-run" ]]; then
    OFFHOST_CAPACITY_ENV_FILE="${env_file}" \
    OFFHOST_CAPACITY_CHECK_CONNECTIVITY=false \
      tools/test/run-offhost-capacity-env-doctor.sh --dry-run
    return 0
  fi
  OFFHOST_CAPACITY_ENV_FILE="${env_file}" \
  OFFHOST_CAPACITY_CHECK_CONNECTIVITY="${doctor_connectivity}" \
    tools/test/run-offhost-capacity-env-doctor.sh
}

run_capacity_gates() {
  if [[ "${run_capacity}" != "true" || "${mode}" == "dry-run" ]]; then
    return 0
  fi
  CAPACITY_ENV_FILE="${env_file}" \
  CAPACITY_NAME="${capacity_name}" \
  CAPACITY_LONG_SOAK_DURATION="${long_soak_duration}" \
    tools/test/run-transaction-100m-capacity-gates.sh
}

write_report() {
  mkdir -p "${output_dir}"
  {
    echo "# ${name}"
    echo
    echo "## Inputs"
    echo
    echo "- envFile: ${env_file:-missing}"
    echo "- capacitySummary: ${capacity_summary}"
    echo "- capacityRunContext: ${capacity_run_context}"
    echo "- capacityPrerequisite: ${capacity_prerequisite}"
    echo "- doctorConnectivity: ${doctor_connectivity}"
    echo
    echo "## Baseline Status"
    echo
    echo "| Grade | Status | Peak CPU % | 429 Rate | Hikari Pending | Source |"
    echo "| --- | --- | ---: | ---: | ---: | --- |"
    echo "| capacity | $(capacity_status_for_grade "${capacity_summary}" capacity) | $(capacity_column_max_for_grade "${capacity_summary}" backend_cpu_percent capacity) | $(capacity_column_max_for_grade "${capacity_summary}" transaction_429_rate capacity) | $(capacity_column_max_for_grade "${capacity_summary}" hikari_pending capacity) | ${capacity_summary} |"
    echo "| soak | $(capacity_status_for_grade "${capacity_summary}" soak) | $(capacity_column_max_for_grade "${capacity_summary}" backend_cpu_percent soak) | $(capacity_column_max_for_grade "${capacity_summary}" transaction_429_rate soak) | $(capacity_column_max_for_grade "${capacity_summary}" hikari_pending soak) | ${capacity_summary} |"
    echo "| prerequisite | $(env_value "${capacity_prerequisite}" CAPACITY_PREREQUISITE_STATUS) | n/a | n/a | n/a | reason=$(env_value "${capacity_prerequisite}" CAPACITY_PREREQUISITE_FAILURE_REASON) missing=$(env_value "${capacity_prerequisite}" CAPACITY_PREREQUISITE_MISSING_VARS) |"
    echo
    echo "## Notes"
    echo
    echo "- 실제 목표 달성 판단은 off-host k6 generator 기준 capacity/soak summary가 있을 때만 확정합니다."
    echo "- 운영 URL과 token은 리포트에 기록하지 않습니다."
  } >"${output_path}"
  echo "${output_path}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

run_doctor
run_capacity_gates
write_report
