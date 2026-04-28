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
  K6_MULTI_RESOURCE_SAMPLING default true
  K6_MULTI_RESOURCE_CONTAINERS default aquila-bank-backend-loadtest,aquila-bank-postgres,prometheus,postgres-exporter
  K6_MULTI_RESOURCE_SAMPLE_INTERVAL_SECONDS default 2
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
resource_sampling="${K6_MULTI_RESOURCE_SAMPLING:-true}"
resource_containers="${K6_MULTI_RESOURCE_CONTAINERS:-aquila-bank-backend-loadtest,aquila-bank-postgres,prometheus,postgres-exporter}"
resource_sample_interval_seconds="${K6_MULTI_RESOURCE_SAMPLE_INTERVAL_SECONDS:-2}"
scenarios_csv="${K6_MULTI_SCENARIOS:-vu3:constant-vus:3:30s:false:16:3:3,vu16-overload:constant-vus:16:30s:true:16:16:16,burst-256:burst:16:20s:true:256:256:256}"
execution_plan_tsv="${output_dir}/${name}-execution-plan.tsv"
resource_summary_tsv="${output_dir}/${name}-peak-resource-summary.tsv"

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
require_bool "K6_MULTI_RESOURCE_SAMPLING" "${resource_sampling}"
require_non_negative_integer "K6_MULTI_POST_FIRST_RECOVERY_NOISE_WINDOW_SECONDS" "${post_first_noise_window}"
require_positive_integer "K6_MULTI_RESOURCE_SAMPLE_INTERVAL_SECONDS" "${resource_sample_interval_seconds}"
validate_scenarios

print_plan() {
  echo "[k6-transaction-100m-multi] mode=${mode}"
  echo "[k6-transaction-100m-multi] name=${name}"
  echo "[k6-transaction-100m-multi] run_id=${run_id}"
  echo "[k6-transaction-100m-multi] output_dir=${output_dir}"
  echo "[k6-transaction-100m-multi] reuse_backend=${reuse_backend}"
  echo "[k6-transaction-100m-multi] post_first_recovery_noise_window_seconds=${post_first_noise_window}"
  echo "[k6-transaction-100m-multi] resource_sampling=${resource_sampling}"
  echo "[k6-transaction-100m-multi] resource_containers=${resource_containers}"
  echo "[k6-transaction-100m-multi] resource_sample_interval_seconds=${resource_sample_interval_seconds}"
  echo "[k6-transaction-100m-multi] scenario_count=$(scenario_count)"
  echo "[k6-transaction-100m-multi] scenarios=${scenarios_csv}"
  echo "[k6-transaction-100m-multi] execution_plan=${execution_plan_tsv}"
  echo "[k6-transaction-100m-multi] resource_summary=${resource_summary_tsv}"
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

memory_to_mib_awk='
function to_mib(value) {
  gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
  value = tolower(value)
  gsub(/,/, ".", value)
  gsub(/[[:space:]]+/, "", value)
  if (value ~ /gib$/) { sub(/gib$/, "", value); return value * 1024 }
  if (value ~ /gb$/) { sub(/gb$/, "", value); return value * 1024 }
  if (value ~ /mib$/) { sub(/mib$/, "", value); return value + 0 }
  if (value ~ /mb$/) { sub(/mb$/, "", value); return value + 0 }
  if (value ~ /kib$/) { sub(/kib$/, "", value); return value / 1024 }
  if (value ~ /kb$/) { sub(/kb$/, "", value); return value / 1024 }
  if (value ~ /b$/) { sub(/b$/, "", value); return value / 1048576 }
  return value + 0
}'

write_peak_resource_summary_header() {
  mkdir -p "${output_dir}"
  printf "scenario\tcontainer\tpeak_cpu_percent\tpeak_memory_mib\tsamples\n" >"${resource_summary_tsv}"
}

start_peak_resource_sampler() {
  local scenario_name="$1"
  local stats_path="$2"
  local marker_path="$3"
  IFS=',' read -r -a containers <<<"${resource_containers}"
  : >"${stats_path}"
  touch "${marker_path}"
  (
    printf "timestamp\tscenario\tcontainer\tcpu_percent\tmemory_usage\tmemory_limit\n" >>"${stats_path}"
    while [[ -f "${marker_path}" ]]; do
      local line container cpu memory memory_usage memory_limit
      for container in "${containers[@]}"; do
        if line="$(docker stats --no-stream --format '{{.Name}}	{{.CPUPerc}}	{{.MemUsage}}' "${container}" 2>/dev/null)"; then
          cpu="$(awk -F '\t' '{print $2}' <<<"${line}")"
          memory="$(awk -F '\t' '{print $3}' <<<"${line}")"
          memory_usage="${memory%% / *}"
          memory_limit="${memory##* / }"
          printf "%s\t%s\t%s\t%s\t%s\t%s\n" \
            "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${scenario_name}" "${container}" \
            "${cpu}" "${memory_usage}" "${memory_limit}" >>"${stats_path}"
        fi
      done
      sleep "${resource_sample_interval_seconds}"
    done
  ) &
  echo "$!"
}

stop_peak_resource_sampler() {
  local marker_path="$1"
  local sampler_pid="$2"
  rm -f "${marker_path}"
  if [[ -n "${sampler_pid}" ]]; then
    wait "${sampler_pid}" 2>/dev/null || true
  fi
}

write_peak_resource_summary() {
  local scenario_name="$1"
  local stats_path="$2"
  if [[ ! -s "${stats_path}" ]]; then
    return 0
  fi
  awk -F '\t' "${memory_to_mib_awk}"'
    NR > 1 {
      cpu = $4
      gsub(/%$/, "", cpu)
      memory = to_mib($5)
      key = $2 "\t" $3
      samples[key] += 1
      if (!(key in peak_cpu) || cpu > peak_cpu[key]) peak_cpu[key] = cpu
      if (!(key in peak_memory) || memory > peak_memory[key]) peak_memory[key] = memory
    }
    END {
      for (key in samples) {
        split(key, parts, "\t")
        printf "%s\t%s\t%.2f\t%.2f\t%d\n", parts[1], parts[2], peak_cpu[key] + 0, peak_memory[key] + 0, samples[key]
      }
    }
  ' "${stats_path}" >>"${resource_summary_tsv}"
}

run_one_scenario() {
  local order="$1"
  local scenario_name="$2"
  local scenario_mode="$3"
  local vus="$4"
  local duration="$5"
  local overload="$6"
  local burst_rate="$7"
  local pre_allocated="$8"
  local max_vus="$9"
  local report_name="${name}-${scenario_name}"
  local runner_args
  runner_args="$(runner_args_for_order "${order}")"
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
}

run_scenarios() {
  IFS=',' read -r -a scenarios <<<"${scenarios_csv}"
  local order=1
  local scenario scenario_name scenario_mode vus duration overload burst_rate pre_allocated max_vus runner_args
  write_peak_resource_summary_header
  for scenario in "${scenarios[@]}"; do
    IFS=':' read -r scenario_name scenario_mode vus duration overload burst_rate pre_allocated max_vus <<<"${scenario}"
    runner_args="$(runner_args_for_order "${order}")"
    echo "[k6-transaction-100m-multi] running order=${order} scenario=${scenario_name} runner_args=${runner_args}"
    local stats_path="${output_dir}/${name}-${scenario_name}-resource-stats.tsv"
    local marker_path="${output_dir}/${name}-${scenario_name}-resource-sampler.marker"
    local sampler_pid=""
    if [[ "${resource_sampling}" == "true" ]]; then
      sampler_pid="$(start_peak_resource_sampler "${scenario_name}" "${stats_path}" "${marker_path}")"
    fi
    set +e
    run_one_scenario "${order}" "${scenario_name}" "${scenario_mode}" "${vus}" "${duration}" "${overload}" "${burst_rate}" "${pre_allocated}" "${max_vus}"
    local status=$?
    set -e
    if [[ "${resource_sampling}" == "true" ]]; then
      stop_peak_resource_sampler "${marker_path}" "${sampler_pid}"
      write_peak_resource_summary "${scenario_name}" "${stats_path}"
    fi
    if [[ "${status}" -ne 0 ]]; then
      exit "${status}"
    fi
    order=$((order + 1))
  done
}

print_plan
write_execution_plan
write_peak_resource_summary_header
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  echo "${execution_plan_tsv}"
  exit 0
fi

run_scenarios
echo "${execution_plan_tsv}"
