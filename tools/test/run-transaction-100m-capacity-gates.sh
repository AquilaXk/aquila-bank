#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-capacity-gates.sh [--print-plan]

Required runtime environment for actual runs:
  K6_HOT_ACCOUNT_ID
  K6_HOT_FROM
  K6_HOT_TO
  K6_COLD_ACCOUNT_ID
  K6_COLD_FROM
  K6_COLD_TO

Optional environment:
  CAPACITY_NAME              default transaction-100m-capacity-<timestamp>
  CAPACITY_BUILD_BACKEND     default true
  CAPACITY_RUN_SINGLE_HOST   default true
  CAPACITY_RUN_CPU_SPLIT     default true
  CAPACITY_RUN_LONG_SOAK     default true
  CAPACITY_CONTINUE_ON_FAILURE default true
  CAPACITY_LONG_SOAK_DURATION default 30m
  CAPACITY_READINESS_TIMEOUT_SECONDS default 120
  CAPACITY_METRIC_SCRAPE_WAIT_SECONDS default 6
  CAPACITY_CPU_SAMPLE_INTERVAL_SECONDS default 5
  CAPACITY_HARD_THRESHOLD_ENABLED default true
  CAPACITY_HOT_P95_THRESHOLD_MS default 350
  CAPACITY_COLD_P95_THRESHOLD_MS default 750
  CAPACITY_STRICT_429_RATE_THRESHOLD default 0
  CAPACITY_OVERLOAD_429_RATE_THRESHOLD default 0.20
  CAPACITY_BACKEND_CPU_THRESHOLD_PERCENT default 120
  CAPACITY_POSTGRES_CPU_THRESHOLD_PERCENT default 90
  CAPACITY_HIKARI_PENDING_THRESHOLD default 0
  PROMETHEUS_URL             default http://localhost:9090

Profile format:
  name:admission:vus:backend_cpus:backend_memory:postgres_cpus:postgres_memory:db_pool:overload_mode:duration

Examples:
  tools/test/run-transaction-100m-capacity-gates.sh --print-plan
  CAPACITY_RUN_CPU_SPLIT=false CAPACITY_LONG_SOAK_DURATION=30m \
    tools/test/run-transaction-100m-capacity-gates.sh
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

capacity_name="${CAPACITY_NAME:-transaction-100m-capacity-$(date +%Y-%m-%d-%H%M%S)}"
build_backend="${CAPACITY_BUILD_BACKEND:-true}"
run_single_host="${CAPACITY_RUN_SINGLE_HOST:-true}"
run_cpu_split="${CAPACITY_RUN_CPU_SPLIT:-true}"
run_long_soak="${CAPACITY_RUN_LONG_SOAK:-true}"
continue_on_failure="${CAPACITY_CONTINUE_ON_FAILURE:-true}"
long_soak_duration="${CAPACITY_LONG_SOAK_DURATION:-30m}"
readiness_timeout_seconds="${CAPACITY_READINESS_TIMEOUT_SECONDS:-120}"
metric_scrape_wait_seconds="${CAPACITY_METRIC_SCRAPE_WAIT_SECONDS:-6}"
cpu_sample_interval_seconds="${CAPACITY_CPU_SAMPLE_INTERVAL_SECONDS:-5}"
hard_threshold_enabled="${CAPACITY_HARD_THRESHOLD_ENABLED:-true}"
hot_p95_threshold_ms="${CAPACITY_HOT_P95_THRESHOLD_MS:-350}"
cold_p95_threshold_ms="${CAPACITY_COLD_P95_THRESHOLD_MS:-750}"
strict_429_rate_threshold="${CAPACITY_STRICT_429_RATE_THRESHOLD:-0}"
overload_429_rate_threshold="${CAPACITY_OVERLOAD_429_RATE_THRESHOLD:-0.20}"
backend_cpu_threshold_percent="${CAPACITY_BACKEND_CPU_THRESHOLD_PERCENT:-120}"
postgres_cpu_threshold_percent="${CAPACITY_POSTGRES_CPU_THRESHOLD_PERCENT:-90}"
hikari_pending_threshold="${CAPACITY_HIKARI_PENDING_THRESHOLD:-0}"
prometheus_url="${PROMETHEUS_URL:-http://localhost:9090}"
prometheus_base_url="${prometheus_url%/}"
backend_health_url="${CAPACITY_BACKEND_HEALTH_URL:-http://localhost:${BACKEND_PORT:-8080}/actuator/health}"
report_dir="build/reports/k6/${capacity_name}"
summary_tsv="${report_dir}/capacity-summary.tsv"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
CPU_SAMPLER_PID=""
threshold_failed=false

single_host_profiles="${CAPACITY_SINGLE_HOST_PROFILES:-single-host-default:3:8:0.40:512m:0.60:384m:4:true:1m,single-host-high-traffic:8:8:0.80:640m:0.60:384m:6:false:1m}"
cpu_split_profiles="${CAPACITY_CPU_SPLIT_PROFILES:-cpu-backend040-postgres060:8:8:0.40:512m:0.60:384m:4:false:1m,cpu-backend100-postgres060:8:8:1.00:640m:0.60:384m:6:false:1m}"
long_soak_profile="${CAPACITY_LONG_SOAK_PROFILE:-long-soak-high-traffic:8:8:0.80:640m:0.60:384m:6:false:${long_soak_duration}}"

require_bool() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_positive_integer_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_non_negative_integer_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${name} must be zero or a positive integer: ${value}" >&2
    exit 1
  fi
}

require_positive_number_value() {
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

require_rate_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value >= 0 && value <= 1) }' \
    || {
      echo "${name} must be a rate between 0 and 1: ${value}" >&2
      exit 1
    }
}

require_duration_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*(s|m|h)$ ]]; then
    echo "${name} must use a positive duration such as 60s, 30m, or 1h: ${value}" >&2
    exit 1
  fi
}

require_memory_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*[mMgG]$ ]]; then
    echo "${name} must use Docker memory notation such as 384m or 1g: ${value}" >&2
    exit 1
  fi
}

require_cpu_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be a positive CPU number: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value > 0) }' \
    || {
      echo "${name} must be greater than zero: ${value}" >&2
      exit 1
    }
}

validate_profile() {
  local group="$1"
  local profile="$2"
  IFS=':' read -r name admission vus backend_cpus backend_memory postgres_cpus postgres_memory db_pool overload_mode duration extra <<<"${profile}"
  if [[ -n "${extra:-}" || -z "${duration:-}" ]]; then
    echo "${group} profile must have 10 fields: ${profile}" >&2
    exit 1
  fi
  if [[ ! "${name}" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
    echo "${group} profile name must be kebab-case: ${name}" >&2
    exit 1
  fi
  require_positive_integer_value "${group}.admission" "${admission}"
  require_positive_integer_value "${group}.vus" "${vus}"
  require_cpu_value "${group}.backend_cpus" "${backend_cpus}"
  require_memory_value "${group}.backend_memory" "${backend_memory}"
  require_cpu_value "${group}.postgres_cpus" "${postgres_cpus}"
  require_memory_value "${group}.postgres_memory" "${postgres_memory}"
  require_positive_integer_value "${group}.db_pool" "${db_pool}"
  require_bool "${group}.overload_mode" "${overload_mode}"
  require_duration_value "${group}.duration" "${duration}"
}

validate_profiles() {
  local group="$1"
  local csv="$2"
  if [[ -z "${csv}" ]]; then
    echo "${group} profiles must not be empty" >&2
    exit 1
  fi
  IFS=',' read -r -a profiles <<<"${csv}"
  local profile
  for profile in "${profiles[@]}"; do
    validate_profile "${group}" "${profile}"
  done
}

profile_names() {
  local csv="$1"
  IFS=',' read -r -a profiles <<<"${csv}"
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

require_env() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "${value}" ]]; then
    echo "${name} is required" >&2
    exit 1
  fi
}

require_bool "CAPACITY_BUILD_BACKEND" "${build_backend}"
require_bool "CAPACITY_RUN_SINGLE_HOST" "${run_single_host}"
require_bool "CAPACITY_RUN_CPU_SPLIT" "${run_cpu_split}"
require_bool "CAPACITY_RUN_LONG_SOAK" "${run_long_soak}"
require_bool "CAPACITY_CONTINUE_ON_FAILURE" "${continue_on_failure}"
require_bool "CAPACITY_HARD_THRESHOLD_ENABLED" "${hard_threshold_enabled}"
require_duration_value "CAPACITY_LONG_SOAK_DURATION" "${long_soak_duration}"
require_positive_integer_value "CAPACITY_READINESS_TIMEOUT_SECONDS" "${readiness_timeout_seconds}"
require_non_negative_integer_value "CAPACITY_METRIC_SCRAPE_WAIT_SECONDS" "${metric_scrape_wait_seconds}"
require_positive_integer_value "CAPACITY_CPU_SAMPLE_INTERVAL_SECONDS" "${cpu_sample_interval_seconds}"
require_positive_number_value "CAPACITY_HOT_P95_THRESHOLD_MS" "${hot_p95_threshold_ms}"
require_positive_number_value "CAPACITY_COLD_P95_THRESHOLD_MS" "${cold_p95_threshold_ms}"
require_rate_value "CAPACITY_STRICT_429_RATE_THRESHOLD" "${strict_429_rate_threshold}"
require_rate_value "CAPACITY_OVERLOAD_429_RATE_THRESHOLD" "${overload_429_rate_threshold}"
require_positive_number_value "CAPACITY_BACKEND_CPU_THRESHOLD_PERCENT" "${backend_cpu_threshold_percent}"
require_positive_number_value "CAPACITY_POSTGRES_CPU_THRESHOLD_PERCENT" "${postgres_cpu_threshold_percent}"
require_non_negative_integer_value "CAPACITY_HIKARI_PENDING_THRESHOLD" "${hikari_pending_threshold}"
validate_profiles "CAPACITY_SINGLE_HOST_PROFILES" "${single_host_profiles}"
validate_profiles "CAPACITY_CPU_SPLIT_PROFILES" "${cpu_split_profiles}"
validate_profile "CAPACITY_LONG_SOAK_PROFILE" "${long_soak_profile}"

print_plan() {
  echo "[transaction-100m-capacity] capacity=${capacity_name}"
  echo "[transaction-100m-capacity] build_backend=${build_backend}"
  echo "[transaction-100m-capacity] run_single_host=${run_single_host}"
  echo "[transaction-100m-capacity] run_cpu_split=${run_cpu_split}"
  echo "[transaction-100m-capacity] run_long_soak=${run_long_soak}"
  echo "[transaction-100m-capacity] continue_on_failure=${continue_on_failure}"
  echo "[transaction-100m-capacity] single_host_profiles=$(profile_names "${single_host_profiles}")"
  echo "[transaction-100m-capacity] cpu_split_profiles=$(profile_names "${cpu_split_profiles}")"
  echo "[transaction-100m-capacity] long_soak_profile=${long_soak_profile%%:*} duration=${long_soak_duration}"
  echo "[transaction-100m-capacity] prometheus_url=${prometheus_url}"
  echo "[transaction-100m-capacity] backend_health_url=${backend_health_url}"
  echo "[transaction-100m-capacity] readiness_timeout_seconds=${readiness_timeout_seconds}"
  echo "[transaction-100m-capacity] metric_scrape_wait_seconds=${metric_scrape_wait_seconds}"
  echo "[transaction-100m-capacity] cpu_sample_interval_seconds=${cpu_sample_interval_seconds}"
  echo "[transaction-100m-capacity] hard_thresholds=${hard_threshold_enabled}"
  echo "[transaction-100m-capacity] hot_p95_threshold_ms=${hot_p95_threshold_ms}"
  echo "[transaction-100m-capacity] cold_p95_threshold_ms=${cold_p95_threshold_ms}"
  echo "[transaction-100m-capacity] strict_429_rate_threshold=${strict_429_rate_threshold}"
  echo "[transaction-100m-capacity] overload_429_rate_threshold=${overload_429_rate_threshold}"
  echo "[transaction-100m-capacity] backend_cpu_threshold_percent=${backend_cpu_threshold_percent}"
  echo "[transaction-100m-capacity] postgres_cpu_threshold_percent=${postgres_cpu_threshold_percent}"
  echo "[transaction-100m-capacity] hikari_pending_threshold=${hikari_pending_threshold}"
  echo "[transaction-100m-capacity] summary=${summary_tsv}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

require_env K6_HOT_ACCOUNT_ID
require_env K6_HOT_FROM
require_env K6_HOT_TO
require_env K6_COLD_ACCOUNT_ID
require_env K6_COLD_FROM
require_env K6_COLD_TO

mkdir -p "${report_dir}" build/reports/k6

if [[ "${build_backend}" == "true" ]]; then
  echo "[transaction-100m-capacity] building backend bootJar"
  tools/test/with-resource-lock.sh back-gradle-loadtest-bootjar ./back/gradlew -p back bootJar
fi

metric_from_json() {
  local json_path="$1"
  local metric="$2"
  local value_name="$3"
  if [[ ! -f "${json_path}" ]] || ! command -v jq >/dev/null 2>&1; then
    echo "n/a"
    return 0
  fi
  jq -r --arg metric "${metric}" --arg value_name "${value_name}" \
    '.metrics[$metric].values[$value_name] // "n/a"' "${json_path}"
}

prometheus_value() {
  local query="$1"
  if ! command -v curl >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
    echo "n/a"
    return 0
  fi
  curl -fsS --get "${prometheus_base_url}/api/v1/query" \
    --data-urlencode "query=${query}" 2>/dev/null \
    | jq -r '.data.result[0].value[1] // "0"' 2>/dev/null \
    || echo "n/a"
}

wait_for_backend_readiness() {
  local deadline=$((SECONDS + readiness_timeout_seconds))
  local body=""
  echo "[transaction-100m-capacity] waiting backend readiness: ${backend_health_url}"
  while ((SECONDS < deadline)); do
    body="$(curl -sS --max-time 2 "${backend_health_url}" 2>/dev/null || true)"
    # 전체 health는 optional runtime 때문에 DOWN일 수 있어 readinessState만 확인합니다.
    if grep -F '"readinessState":{"status":"UP"}' <<<"${body}" >/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "backend readiness timeout: ${backend_health_url}" >&2
  [[ -z "${body}" ]] || echo "${body}" >&2
  exit 1
}

wait_for_prometheus_readiness() {
  local deadline=$((SECONDS + readiness_timeout_seconds))
  echo "[transaction-100m-capacity] waiting prometheus readiness: ${prometheus_base_url}/-/ready"
  while ((SECONDS < deadline)); do
    if curl -fsS --max-time 2 "${prometheus_base_url}/-/ready" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "prometheus readiness timeout: ${prometheus_base_url}/-/ready" >&2
  exit 1
}

start_cpu_sampler() {
  local stats_path="$1"
  : >"${stats_path}"
  (
    while true; do
      docker stats --no-stream --format '{{.Name}}	{{.CPUPerc}}' \
          aquila-bank-backend-loadtest aquila-bank-postgres 2>/dev/null \
        | awk -F '\t' -v ts="$(date +%s)" '{
            gsub("%", "", $2)
            print ts "\t" $1 "\t" $2
          }' >>"${stats_path}" || true
      sleep "${cpu_sample_interval_seconds}"
    done
  ) &
  CPU_SAMPLER_PID="$!"
}

stop_cpu_sampler() {
  if [[ -n "${CPU_SAMPLER_PID}" ]]; then
    kill "${CPU_SAMPLER_PID}" >/dev/null 2>&1 || true
    wait "${CPU_SAMPLER_PID}" >/dev/null 2>&1 || true
    CPU_SAMPLER_PID=""
  fi
}

container_cpu_max_percent() {
  local stats_path="$1"
  local container_name="$2"
  if [[ ! -f "${stats_path}" ]]; then
    echo "n/a"
    return 0
  fi
  awk -F '\t' -v name="${container_name}" '
    $2 == name {
      value = $3 + 0
      if (!found || value > max) {
        max = value
      }
      found = 1
    }
    END {
      if (found) {
        printf "%.2f", max
      } else {
        print "n/a"
      }
    }
  ' "${stats_path}"
}

write_header() {
  printf "phase\tprofile\tstatus\tadmission\tvus\tbackend_cpus\tbackend_memory\tpostgres_cpus\tpostgres_memory\tdb_pool\toverload_mode\tduration\thttp_failed_rate\thttp_reqs\ttransaction_429_rate\thot_first_p95_ms\thot_cursor_p95_ms\tcold_first_p95_ms\tcold_cursor_p95_ms\tbackend_cpu_percent\tpostgres_cpu_percent\thikari_active\thikari_pending\thikari_max\tlog_path\tsummary_json\n" >"${summary_tsv}"
}

append_summary() {
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" "$@" >>"${summary_tsv}"
}

is_numeric_value() {
  [[ "$1" =~ ^[0-9]+([.][0-9]+)?$ ]]
}

number_greater_than() {
  local value="$1"
  local threshold="$2"
  awk -v value="${value}" -v threshold="${threshold}" 'BEGIN { exit !(value > threshold) }'
}

record_threshold_violation() {
  local phase="$1"
  local profile="$2"
  local metric="$3"
  local value="$4"
  local threshold="$5"
  echo "[transaction-100m-capacity] hard threshold violation phase=${phase} profile=${profile} metric=${metric} value=${value} threshold=${threshold}" >&2
  threshold_failed=true
}

check_metric_lte() {
  local phase="$1"
  local profile="$2"
  local metric="$3"
  local value="$4"
  local threshold="$5"

  if ! is_numeric_value "${value}"; then
    record_threshold_violation "${phase}" "${profile}" "${metric}" "${value}" "${threshold}"
    return 0
  fi
  if number_greater_than "${value}" "${threshold}"; then
    record_threshold_violation "${phase}" "${profile}" "${metric}" "${value}" "${threshold}"
  fi
}

check_capacity_thresholds() {
  local phase="$1"
  local profile="$2"
  local status="$3"
  local overload_mode="$4"
  local transaction_429_rate="$5"
  local hot_first_p95="$6"
  local hot_cursor_p95="$7"
  local cold_first_p95="$8"
  local cold_cursor_p95="$9"
  local backend_cpu="${10}"
  local postgres_cpu="${11}"
  local hikari_pending="${12}"

  if [[ "${hard_threshold_enabled}" != "true" ]]; then
    return 0
  fi

  if [[ "${status}" -ne 0 ]]; then
    record_threshold_violation "${phase}" "${profile}" "k6_status" "${status}" "0"
  fi

  local rate_threshold="${strict_429_rate_threshold}"
  if [[ "${overload_mode}" == "true" ]]; then
    rate_threshold="${overload_429_rate_threshold}"
  fi

  check_metric_lte "${phase}" "${profile}" "transaction_429_rate" "${transaction_429_rate}" "${rate_threshold}"
  check_metric_lte "${phase}" "${profile}" "hot_first_p95_ms" "${hot_first_p95}" "${hot_p95_threshold_ms}"
  check_metric_lte "${phase}" "${profile}" "hot_cursor_p95_ms" "${hot_cursor_p95}" "${hot_p95_threshold_ms}"
  check_metric_lte "${phase}" "${profile}" "cold_first_p95_ms" "${cold_first_p95}" "${cold_p95_threshold_ms}"
  check_metric_lte "${phase}" "${profile}" "cold_cursor_p95_ms" "${cold_cursor_p95}" "${cold_p95_threshold_ms}"
  check_metric_lte "${phase}" "${profile}" "backend_cpu_percent" "${backend_cpu}" "${backend_cpu_threshold_percent}"
  check_metric_lte "${phase}" "${profile}" "postgres_cpu_percent" "${postgres_cpu}" "${postgres_cpu_threshold_percent}"
  check_metric_lte "${phase}" "${profile}" "hikari_pending" "${hikari_pending}" "${hikari_pending_threshold}"
}

run_profile() {
  local phase="$1"
  local profile="$2"
  IFS=':' read -r name admission vus backend_cpus backend_memory postgres_cpus postgres_memory db_pool overload_mode duration <<<"${profile}"
  local report_name="${capacity_name}-${name}"
  local log_path="${report_dir}/${report_name}.log"
  local summary_json="build/reports/k6/${report_name}-summary.json"
  local stats_path="${report_dir}/${report_name}-docker-stats.tsv"
  local http_failed_rate http_reqs transaction_429_rate
  local hot_first_p95 hot_cursor_p95 cold_first_p95 cold_cursor_p95
  local backend_cpu postgres_cpu hikari_active hikari_pending hikari_max
  local status

  echo "[transaction-100m-capacity] running phase=${phase} profile=${name}"

  T3MICRO_BACKEND_CPUS="${backend_cpus}" \
  T3MICRO_BACKEND_MEMORY="${backend_memory}" \
  T3MICRO_BACKEND_MEMORY_SWAP="${backend_memory}" \
  T3MICRO_POSTGRES_CPUS="${postgres_cpus}" \
  T3MICRO_POSTGRES_MEMORY="${postgres_memory}" \
  T3MICRO_POSTGRES_MEMORY_SWAP="${postgres_memory}" \
  OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX="${admission}" \
  DB_POOL_MAX_SIZE="${db_pool}" \
    docker compose "${compose_files[@]}" --profile loadtest up -d --force-recreate \
      postgres aquila-bank-backend prometheus grafana alertmanager postgres-exporter

  wait_for_backend_readiness
  wait_for_prometheus_readiness

  start_cpu_sampler "${stats_path}"
  set +e
  K6_REPORT_NAME="${report_name}" \
  K6_VUS="${vus}" \
  K6_DURATION="${duration}" \
  K6_OVERLOAD_MODE="${overload_mode}" \
  K6_ARCHIVE_RESULTS=false \
    tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps >"${log_path}" 2>&1
  status=$?
  set -e
  stop_cpu_sampler

  if [[ "${metric_scrape_wait_seconds}" -gt 0 ]]; then
    sleep "${metric_scrape_wait_seconds}"
  fi

  http_failed_rate="$(metric_from_json "${summary_json}" "http_req_failed" "rate")"
  http_reqs="$(metric_from_json "${summary_json}" "http_reqs" "count")"
  transaction_429_rate="$(metric_from_json "${summary_json}" "aquila_transaction_429_rate" "rate")"
  hot_first_p95="$(metric_from_json "${summary_json}" "aquila_transaction_hot_first_ms" "p(95)")"
  hot_cursor_p95="$(metric_from_json "${summary_json}" "aquila_transaction_hot_cursor_ms" "p(95)")"
  cold_first_p95="$(metric_from_json "${summary_json}" "aquila_transaction_cold_first_ms" "p(95)")"
  cold_cursor_p95="$(metric_from_json "${summary_json}" "aquila_transaction_cold_cursor_ms" "p(95)")"
  backend_cpu="$(container_cpu_max_percent "${stats_path}" aquila-bank-backend-loadtest)"
  postgres_cpu="$(container_cpu_max_percent "${stats_path}" aquila-bank-postgres)"
  hikari_active="$(prometheus_value 'max(hikaricp_connections_active{pool="aquila-bank-pool"})')"
  hikari_pending="$(prometheus_value 'max(hikaricp_connections_pending{pool="aquila-bank-pool"})')"
  hikari_max="$(prometheus_value 'max(hikaricp_connections_max{pool="aquila-bank-pool"})')"

  append_summary \
    "${phase}" "${name}" "${status}" "${admission}" "${vus}" \
    "${backend_cpus}" "${backend_memory}" "${postgres_cpus}" "${postgres_memory}" \
    "${db_pool}" "${overload_mode}" "${duration}" \
    "${http_failed_rate}" "${http_reqs}" "${transaction_429_rate}" \
    "${hot_first_p95}" "${hot_cursor_p95}" "${cold_first_p95}" "${cold_cursor_p95}" \
    "${backend_cpu}" "${postgres_cpu}" "${hikari_active}" "${hikari_pending}" "${hikari_max}" \
    "${log_path}" "${summary_json}"

  check_capacity_thresholds \
    "${phase}" "${name}" "${status}" "${overload_mode}" "${transaction_429_rate}" \
    "${hot_first_p95}" "${hot_cursor_p95}" "${cold_first_p95}" "${cold_cursor_p95}" \
    "${backend_cpu}" "${postgres_cpu}" "${hikari_pending}"

  if [[ "${status}" -ne 0 && "${continue_on_failure}" != "true" ]]; then
    echo "[transaction-100m-capacity] profile failed and CAPACITY_CONTINUE_ON_FAILURE=false: ${name}" >&2
    exit "${status}"
  fi
}

run_profiles() {
  local phase="$1"
  local csv="$2"
  IFS=',' read -r -a profiles <<<"${csv}"
  local profile
  for profile in "${profiles[@]}"; do
    run_profile "${phase}" "${profile}"
  done
}

write_header

if [[ "${run_single_host}" == "true" ]]; then
  run_profiles "single-host" "${single_host_profiles}"
fi
if [[ "${run_cpu_split}" == "true" ]]; then
  run_profiles "cpu-split" "${cpu_split_profiles}"
fi
if [[ "${run_long_soak}" == "true" ]]; then
  run_profile "long-soak" "${long_soak_profile}"
fi

if [[ "${threshold_failed}" == "true" ]]; then
  echo "[transaction-100m-capacity] hard threshold failed; see ${summary_tsv}" >&2
  exit 1
fi

echo "[transaction-100m-capacity] summary=${summary_tsv}"
