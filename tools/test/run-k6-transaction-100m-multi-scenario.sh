#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-k6-transaction-100m-multi-scenario.sh [--print-plan|--dry-run]

Environment:
  K6_MULTI_NAME default transaction-100m-multi-scenario-<timestamp>
  K6_MULTI_RUN_ID default K6_MULTI_NAME
  K6_MULTI_OUTPUT_DIR default build/reports/k6/<name>
  K6_MULTI_REUSE_BACKEND default true
  K6_MULTI_POST_FIRST_RECOVERY_NOISE_WINDOW_SECONDS default 0
  K6_MULTI_SCENARIOS comma list of name:mode:vus:duration:overload:burst_rate:preAllocated:max

Default scenarios:
  vu3:constant-vus:3:30s:false:16:3:3,vu16-overload:constant-vus:16:30s:true:16:16:16,burst-256:burst:16:20s:true:256:256:256
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

name="${K6_MULTI_NAME:-transaction-100m-multi-scenario-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${K6_MULTI_RUN_ID:-${name}}"
output_dir="${K6_MULTI_OUTPUT_DIR:-build/reports/k6/${name}}"
reuse_backend="${K6_MULTI_REUSE_BACKEND:-true}"
post_first_noise_window="${K6_MULTI_POST_FIRST_RECOVERY_NOISE_WINDOW_SECONDS:-0}"
scenarios_csv="${K6_MULTI_SCENARIOS:-vu3:constant-vus:3:30s:false:16:3:3,vu16-overload:constant-vus:16:30s:true:16:16:16,burst-256:burst:16:20s:true:256:256:256}"
execution_plan_tsv="${output_dir}/${name}-execution-plan.tsv"

require_bool() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_non_negative_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${name} must be zero or a positive integer: ${value}" >&2
    exit 1
  fi
}

require_duration() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*(s|m|h)$ ]]; then
    echo "${name} must use a positive duration such as 30s or 1m: ${value}" >&2
    exit 1
  fi
}

require_scenario_mode() {
  local value="$1"
  case "${value}" in
    constant-vus|constant-arrival-rate|burst)
      ;;
    *)
      echo "scenario mode must be constant-vus, constant-arrival-rate, or burst: ${value}" >&2
      exit 1
      ;;
  esac
}

validate_scenario() {
  local scenario="$1"
  local scenario_name scenario_mode vus duration overload burst_rate pre_allocated max_vus extra
  IFS=':' read -r scenario_name scenario_mode vus duration overload burst_rate pre_allocated max_vus extra <<<"${scenario}"
  if [[ -n "${extra:-}" || -z "${max_vus:-}" ]]; then
    echo "K6_MULTI_SCENARIOS entry must have 8 fields: ${scenario}" >&2
    exit 1
  fi
  if [[ ! "${scenario_name}" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
    echo "scenario name must be kebab-case: ${scenario_name}" >&2
    exit 1
  fi
  require_scenario_mode "${scenario_mode}"
  require_positive_integer "scenario.vus" "${vus}"
  require_duration "scenario.duration" "${duration}"
  require_bool "scenario.overload" "${overload}"
  require_positive_integer "scenario.burst_rate" "${burst_rate}"
  require_positive_integer "scenario.preAllocated" "${pre_allocated}"
  require_positive_integer "scenario.max" "${max_vus}"
}

scenario_count() {
  IFS=',' read -r -a scenarios <<<"${scenarios_csv}"
  echo "${#scenarios[@]}"
}

validate_scenarios() {
  IFS=',' read -r -a scenarios <<<"${scenarios_csv}"
  if [[ "${#scenarios[@]}" -eq 0 ]]; then
    echo "K6_MULTI_SCENARIOS must not be empty" >&2
    exit 1
  fi
  local scenario
  for scenario in "${scenarios[@]}"; do
    validate_scenario "${scenario}"
  done
}

require_bool "K6_MULTI_REUSE_BACKEND" "${reuse_backend}"
require_non_negative_integer "K6_MULTI_POST_FIRST_RECOVERY_NOISE_WINDOW_SECONDS" "${post_first_noise_window}"
validate_scenarios

print_plan() {
  echo "[k6-transaction-100m-multi] mode=${mode}"
  echo "[k6-transaction-100m-multi] name=${name}"
  echo "[k6-transaction-100m-multi] run_id=${run_id}"
  echo "[k6-transaction-100m-multi] output_dir=${output_dir}"
  echo "[k6-transaction-100m-multi] reuse_backend=${reuse_backend}"
  echo "[k6-transaction-100m-multi] post_first_recovery_noise_window_seconds=${post_first_noise_window}"
  echo "[k6-transaction-100m-multi] scenario_count=$(scenario_count)"
  echo "[k6-transaction-100m-multi] scenarios=${scenarios_csv}"
  echo "[k6-transaction-100m-multi] execution_plan=${execution_plan_tsv}"
}

runner_args_for_order() {
  local order="$1"
  if [[ "${reuse_backend}" == "true" && "${order}" -gt 1 ]]; then
    echo "--no-up --no-deps"
  else
    echo "default"
  fi
}

recovery_noise_for_order() {
  local order="$1"
  if [[ "${reuse_backend}" == "true" && "${order}" -gt 1 ]]; then
    echo "${post_first_noise_window}"
  else
    echo "${K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS:-30}"
  fi
}

write_execution_plan() {
  mkdir -p "${output_dir}"
  printf "order\tname\tmode\tvus\tduration\toverload\tburst_rate\tpre_allocated_vus\tmax_vus\treport_name\trunner_args\trecovery_noise_window_seconds\n" >"${execution_plan_tsv}"
  IFS=',' read -r -a scenarios <<<"${scenarios_csv}"
  local order=1
  local scenario scenario_name scenario_mode vus duration overload burst_rate pre_allocated max_vus
  for scenario in "${scenarios[@]}"; do
    IFS=':' read -r scenario_name scenario_mode vus duration overload burst_rate pre_allocated max_vus <<<"${scenario}"
    printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s-%s\t%s\t%s\n" \
      "${order}" "${scenario_name}" "${scenario_mode}" "${vus}" "${duration}" "${overload}" \
      "${burst_rate}" "${pre_allocated}" "${max_vus}" "${name}" "${scenario_name}" \
      "$(runner_args_for_order "${order}")" "$(recovery_noise_for_order "${order}")" >>"${execution_plan_tsv}"
    order=$((order + 1))
  done
}

run_scenarios() {
  IFS=',' read -r -a scenarios <<<"${scenarios_csv}"
  local order=1
  local scenario scenario_name scenario_mode vus duration overload burst_rate pre_allocated max_vus report_name runner_args
  for scenario in "${scenarios[@]}"; do
    IFS=':' read -r scenario_name scenario_mode vus duration overload burst_rate pre_allocated max_vus <<<"${scenario}"
    report_name="${name}-${scenario_name}"
    runner_args="$(runner_args_for_order "${order}")"
    echo "[k6-transaction-100m-multi] running order=${order} scenario=${scenario_name} runner_args=${runner_args}"
    if [[ "${runner_args}" == "default" ]]; then
      K6_REPORT_NAME="${report_name}" \
      K6_RUN_ID="${run_id}" \
      K6_SCENARIO_MODE="${scenario_mode}" \
      K6_VUS="${vus}" \
      K6_DURATION="${duration}" \
      K6_OVERLOAD_MODE="${overload}" \
      K6_BURST_RATE="${burst_rate}" \
      K6_BURST_DURATION="${duration}" \
      K6_PRE_ALLOCATED_VUS="${pre_allocated}" \
      K6_MAX_VUS="${max_vus}" \
        tools/test/run-k6-transaction-100m-loadtest.sh
    else
      K6_REPORT_NAME="${report_name}" \
      K6_RUN_ID="${run_id}" \
      K6_SCENARIO_MODE="${scenario_mode}" \
      K6_VUS="${vus}" \
      K6_DURATION="${duration}" \
      K6_OVERLOAD_MODE="${overload}" \
      K6_BURST_RATE="${burst_rate}" \
      K6_BURST_DURATION="${duration}" \
      K6_PRE_ALLOCATED_VUS="${pre_allocated}" \
      K6_MAX_VUS="${max_vus}" \
      K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS="${post_first_noise_window}" \
        tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps
    fi
    order=$((order + 1))
  done
}

print_plan
write_execution_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  echo "${execution_plan_tsv}"
  exit 0
fi

run_scenarios
echo "${execution_plan_tsv}"
