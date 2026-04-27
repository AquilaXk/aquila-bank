#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-admission-cpu-calibration.sh [--print-plan]

Required runtime environment for actual runs:
  K6_HOT_ACCOUNT_ID
  K6_HOT_FROM
  K6_HOT_TO
  K6_COLD_ACCOUNT_ID
  K6_COLD_FROM
  K6_COLD_TO

Optional environment:
  CALIBRATION_NAME default transaction-read-admission-cpu-<timestamp>
  CALIBRATION_BUILD_BACKEND default true
  CALIBRATION_PROFILES default admission3-vu8:3:8:0.80:640m:0.60:384m:4:true:1m,admission6-vu16:6:16:0.80:640m:0.60:384m:6:true:1m,admission8-vu16:8:16:0.80:640m:0.60:384m:8:true:1m
  CALIBRATION_BACKEND_CPU_THRESHOLD_PERCENT default 120
  CALIBRATION_K6_GENERATOR_MODE default docker-context
  CALIBRATION_K6_DOCKER_CONTEXT docker context for off-host k6
  CALIBRATION_K6_REMOTE_BASE_URL backend URL reachable from off-host k6
  CALIBRATION_K6_REMOTE_PROMETHEUS_RW_SERVER_URL Prometheus remote-write URL reachable from off-host k6
  CALIBRATION_K6_REMOTE_WORKDIR repo path visible from docker context host, default current working directory

Examples:
  tools/test/run-transaction-read-admission-cpu-calibration.sh --print-plan
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
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

calibration_name="${CALIBRATION_NAME:-transaction-read-admission-cpu-$(date +%Y-%m-%d-%H%M%S)}"
calibration_build_backend="${CALIBRATION_BUILD_BACKEND:-true}"
calibration_profiles="${CALIBRATION_PROFILES:-admission3-vu8:3:8:0.80:640m:0.60:384m:4:true:1m,admission6-vu16:6:16:0.80:640m:0.60:384m:6:true:1m,admission8-vu16:8:16:0.80:640m:0.60:384m:8:true:1m}"
backend_cpu_threshold_percent="${CALIBRATION_BACKEND_CPU_THRESHOLD_PERCENT:-120}"
calibration_k6_generator_mode="${CALIBRATION_K6_GENERATOR_MODE:-docker-context}"
calibration_k6_docker_context="${CALIBRATION_K6_DOCKER_CONTEXT:-${K6_DOCKER_CONTEXT:-}}"
calibration_k6_remote_base_url="${CALIBRATION_K6_REMOTE_BASE_URL:-${K6_REMOTE_BASE_URL:-}}"
calibration_k6_remote_prometheus_rw_server_url="${CALIBRATION_K6_REMOTE_PROMETHEUS_RW_SERVER_URL:-${K6_REMOTE_PROMETHEUS_RW_SERVER_URL:-}}"
calibration_k6_remote_workdir="${CALIBRATION_K6_REMOTE_WORKDIR:-${K6_REMOTE_WORKDIR:-$(pwd)}}"
summary_tsv="build/reports/k6/${calibration_name}/capacity-summary.tsv"

require_bool() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_positive_number() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be a positive number: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value > 0) }' \
    || {
      echo "${name} must be greater than zero: ${value}" >&2
      exit 1
    }
}

validate_profile() {
  local profile="$1"
  IFS=':' read -r name admission vus backend_cpus backend_memory postgres_cpus postgres_memory db_pool overload_mode duration extra <<<"${profile}"
  if [[ -n "${extra:-}" || -z "${duration:-}" ]]; then
    echo "CALIBRATION_PROFILES profile must have 10 fields: ${profile}" >&2
    exit 1
  fi
  if [[ ! "${name}" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
    echo "CALIBRATION_PROFILES profile name must be kebab-case: ${name}" >&2
    exit 1
  fi
  [[ "${admission}" =~ ^[1-9][0-9]*$ ]] || {
    echo "CALIBRATION_PROFILES admission must be positive: ${profile}" >&2
    exit 1
  }
  [[ "${vus}" =~ ^[1-9][0-9]*$ ]] || {
    echo "CALIBRATION_PROFILES vus must be positive: ${profile}" >&2
    exit 1
  }
  [[ "${backend_cpus}" =~ ^[0-9]+([.][0-9]+)?$ ]] || {
    echo "CALIBRATION_PROFILES backend cpus must be numeric: ${profile}" >&2
    exit 1
  }
  [[ "${backend_memory}" =~ ^[1-9][0-9]*[mMgG]$ ]] || {
    echo "CALIBRATION_PROFILES backend memory must use Docker memory notation: ${profile}" >&2
    exit 1
  }
  [[ "${postgres_cpus}" =~ ^[0-9]+([.][0-9]+)?$ ]] || {
    echo "CALIBRATION_PROFILES postgres cpus must be numeric: ${profile}" >&2
    exit 1
  }
  [[ "${postgres_memory}" =~ ^[1-9][0-9]*[mMgG]$ ]] || {
    echo "CALIBRATION_PROFILES postgres memory must use Docker memory notation: ${profile}" >&2
    exit 1
  }
  [[ "${db_pool}" =~ ^[1-9][0-9]*$ ]] || {
    echo "CALIBRATION_PROFILES db pool must be positive: ${profile}" >&2
    exit 1
  }
  [[ "${overload_mode}" == "true" || "${overload_mode}" == "false" ]] || {
    echo "CALIBRATION_PROFILES overload mode must be true or false: ${profile}" >&2
    exit 1
  }
  [[ "${duration}" =~ ^[1-9][0-9]*(s|m|h)$ ]] || {
    echo "CALIBRATION_PROFILES duration must use 60s, 30m, or 1h notation: ${profile}" >&2
    exit 1
  }
}

validate_profiles() {
  if [[ -z "${calibration_profiles}" ]]; then
    echo "CALIBRATION_PROFILES must not be empty" >&2
    exit 1
  fi
  IFS=',' read -r -a profiles <<<"${calibration_profiles}"
  local profile
  for profile in "${profiles[@]}"; do
    validate_profile "${profile}"
  done
}

profile_names() {
  IFS=',' read -r -a profiles <<<"${calibration_profiles}"
  local result=""
  local profile name
  for profile in "${profiles[@]}"; do
    name="${profile%%:*}"
    if [[ -n "${result}" ]]; then
      result+=","
    fi
    result+="${name}"
  done
  echo "${result}"
}

require_generator_mode() {
  case "${calibration_k6_generator_mode}" in
    docker-context)
      ;;
    local)
      echo "CALIBRATION_K6_GENERATOR_MODE=local is limited to smoke runners; calibration requires docker-context" >&2
      exit 1
      ;;
    *)
      echo "CALIBRATION_K6_GENERATOR_MODE must be docker-context: ${calibration_k6_generator_mode}" >&2
      exit 1
      ;;
  esac
}

require_bool "CALIBRATION_BUILD_BACKEND" "${calibration_build_backend}"
require_positive_number "CALIBRATION_BACKEND_CPU_THRESHOLD_PERCENT" "${backend_cpu_threshold_percent}"
validate_profiles
require_generator_mode

if [[ "${calibration_k6_generator_mode}" == "docker-context" ]]; then
  [[ -n "${calibration_k6_docker_context}" ]] || {
    echo "CALIBRATION_K6_DOCKER_CONTEXT is required when CALIBRATION_K6_GENERATOR_MODE=docker-context" >&2
    exit 1
  }
  [[ -n "${calibration_k6_remote_base_url}" ]] || {
    echo "CALIBRATION_K6_REMOTE_BASE_URL is required when CALIBRATION_K6_GENERATOR_MODE=docker-context" >&2
    exit 1
  }
  [[ -n "${calibration_k6_remote_prometheus_rw_server_url}" ]] || {
    echo "CALIBRATION_K6_REMOTE_PROMETHEUS_RW_SERVER_URL is required when CALIBRATION_K6_GENERATOR_MODE=docker-context" >&2
    exit 1
  }
fi

print_plan() {
  echo "[transaction-read-admission-cpu-calibration] calibration=${calibration_name}"
  echo "[transaction-read-admission-cpu-calibration] profiles=$(profile_names)"
  echo "[transaction-read-admission-cpu-calibration] build_backend=${calibration_build_backend}"
  echo "[transaction-read-admission-cpu-calibration] backend_cpu_threshold_percent=${backend_cpu_threshold_percent}"
  echo "[transaction-read-admission-cpu-calibration] k6_generator_mode=${calibration_k6_generator_mode}"
  echo "[transaction-read-admission-cpu-calibration] k6_docker_context=${calibration_k6_docker_context:-missing}"
  echo "[transaction-read-admission-cpu-calibration] k6_remote_base_url=${calibration_k6_remote_base_url:-missing}"
  echo "[transaction-read-admission-cpu-calibration] k6_remote_workdir=${calibration_k6_remote_workdir}"
  echo "[transaction-read-admission-cpu-calibration] summary=${summary_tsv}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

CAPACITY_NAME="${calibration_name}" \
CAPACITY_BUILD_BACKEND="${calibration_build_backend}" \
CAPACITY_RUN_SINGLE_HOST=true \
CAPACITY_RUN_CPU_SPLIT=false \
CAPACITY_RUN_LONG_SOAK=false \
CAPACITY_SINGLE_HOST_PROFILES="${calibration_profiles}" \
CAPACITY_BACKEND_CPU_THRESHOLD_PERCENT="${backend_cpu_threshold_percent}" \
CAPACITY_K6_GENERATOR_MODE="${calibration_k6_generator_mode}" \
CAPACITY_K6_DOCKER_CONTEXT="${calibration_k6_docker_context}" \
CAPACITY_K6_REMOTE_BASE_URL="${calibration_k6_remote_base_url}" \
CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL="${calibration_k6_remote_prometheus_rw_server_url}" \
CAPACITY_K6_REMOTE_WORKDIR="${calibration_k6_remote_workdir}" \
  tools/test/run-transaction-100m-capacity-gates.sh
