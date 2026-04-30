#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-hotpath-profile.sh [--print-plan]

Required runtime environment for actual runs:
  K6_HOT_ACCOUNT_ID
  K6_HOT_FROM
  K6_HOT_TO
  K6_COLD_ACCOUNT_ID
  K6_COLD_FROM
  K6_COLD_TO

Optional environment:
  PROFILE_NAME              default transaction-read-hotpath-<timestamp>
  PROFILE_SPIKE_PROFILE     steady|burst-hot-first, default steady
  PROFILE_DURATION          default 75s
  PROFILE_ADMISSION         default 8
  PROFILE_DB_POOL_MAX_SIZE  default 4
  PROFILE_BUILD_BACKEND     default true
  PROFILE_K6_GENERATOR_MODE default docker-context
  PROFILE_K6_SCENARIO_MODE  constant-vus|constant-arrival-rate|burst, default by spike profile
  PROFILE_K6_DOCKER_CONTEXT docker context for off-host k6, required when generator mode=docker-context
  PROFILE_K6_REMOTE_BASE_URL backend URL reachable from off-host k6
  PROFILE_K6_REMOTE_PROMETHEUS_RW_SERVER_URL Prometheus remote-write URL reachable from off-host k6
  PROFILE_K6_REMOTE_WORKDIR repo path visible from docker context host, default current working directory
  PROFILE_READINESS_TIMEOUT_SECONDS default 90
  PROFILE_JFR_DUMP_TIMEOUT_SECONDS default 60
  PROFILE_BACKEND_HEALTH_URL default http://localhost:${BACKEND_PORT:-8080}/actuator/health
  K6_BURST_RATE             default 256 when PROFILE_SPIKE_PROFILE=burst-hot-first
  K6_BURST_DURATION         default 20s
  K6_BURST_429_RATE_THRESHOLD default 0.10
  K6_VUS                    default 8
  K6_DURATION               default 1m
  K6_LIMIT                  default 50
  K6_REPORT_NAME            default <profile-name>-k6
  K6_RUN_ID                 default K6_REPORT_NAME; Prometheus/k6 summary correlation id

Examples:
  tools/test/run-transaction-read-hotpath-profile.sh --print-plan
  K6_HOT_ACCOUNT_ID=910000001 K6_COLD_ACCOUNT_ID=910000002 \
    tools/test/run-transaction-read-hotpath-profile.sh
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

profile_name="${PROFILE_NAME:-transaction-read-hotpath-$(date +%Y-%m-%d-%H%M%S)}"
profile_spike_profile="${PROFILE_SPIKE_PROFILE:-steady}"
case "${profile_spike_profile}" in
  steady)
    default_k6_scenario_mode="constant-vus"
    default_k6_overload_mode="false"
    ;;
  burst-hot-first)
    default_k6_scenario_mode="burst"
    default_k6_overload_mode="true"
    ;;
  *)
    echo "PROFILE_SPIKE_PROFILE must be steady or burst-hot-first: ${profile_spike_profile}" >&2
    exit 1
    ;;
esac
profile_duration="${PROFILE_DURATION-75s}"
profile_admission="${PROFILE_ADMISSION:-8}"
profile_db_pool_max_size="${PROFILE_DB_POOL_MAX_SIZE:-4}"
profile_build_backend="${PROFILE_BUILD_BACKEND:-true}"
profile_k6_generator_mode="${PROFILE_K6_GENERATOR_MODE:-docker-context}"
profile_k6_scenario_mode="${PROFILE_K6_SCENARIO_MODE:-${K6_SCENARIO_MODE:-${default_k6_scenario_mode}}}"
profile_k6_docker_context="${PROFILE_K6_DOCKER_CONTEXT:-${K6_DOCKER_CONTEXT:-}}"
profile_k6_remote_base_url="${PROFILE_K6_REMOTE_BASE_URL:-${K6_REMOTE_BASE_URL:-}}"
profile_k6_remote_prometheus_rw_server_url="${PROFILE_K6_REMOTE_PROMETHEUS_RW_SERVER_URL:-${K6_REMOTE_PROMETHEUS_RW_SERVER_URL:-}}"
profile_k6_remote_workdir="${PROFILE_K6_REMOTE_WORKDIR:-${K6_REMOTE_WORKDIR:-$(pwd)}}"
profile_readiness_timeout_seconds="${PROFILE_READINESS_TIMEOUT_SECONDS:-90}"
profile_jfr_dump_timeout_seconds="${PROFILE_JFR_DUMP_TIMEOUT_SECONDS:-60}"
backend_health_url="${PROFILE_BACKEND_HEALTH_URL:-http://localhost:${BACKEND_PORT:-8080}/actuator/health}"
k6_vus="${K6_VUS:-8}"
k6_duration="${K6_DURATION:-1m}"
k6_burst_rate="${K6_BURST_RATE:-256}"
k6_burst_duration="${K6_BURST_DURATION:-20s}"
k6_overload_mode="${K6_OVERLOAD_MODE:-${default_k6_overload_mode}}"
k6_burst_429_rate_threshold="${K6_BURST_429_RATE_THRESHOLD:-0.10}"
k6_limit="${K6_LIMIT:-50}"
k6_report_name="${K6_REPORT_NAME:-${profile_name}-k6}"
k6_run_id="${K6_RUN_ID:-${k6_report_name}}"
report_dir="build/reports/profiling/${profile_name}"
jfr_container_path="/tmp/${profile_name}.jfr"
jfr_artifact="${report_dir}/${profile_name}.jfr"
jfr_summary_txt="${report_dir}/${profile_name}-jfr-summary.txt"
jfr_hot_methods_txt="${report_dir}/${profile_name}-jfr-hot-methods.txt"
jfr_cpu_hot_methods_txt="${report_dir}/${profile_name}-jfr-cpu-time-hot-methods.txt"
jfr_allocation_by_class_txt="${report_dir}/${profile_name}-jfr-allocation-by-class.txt"
jfr_allocation_by_site_txt="${report_dir}/${profile_name}-jfr-allocation-by-site.txt"
hot_first_spike_metrics_txt="${report_dir}/${profile_name}-hot-first-spike.txt"
run_log="${report_dir}/${profile_name}.log"
summary_md="${report_dir}/${profile_name}-summary.md"
k6_summary_json="build/reports/k6/${k6_report_name}-summary.json"
k6_summary_md="build/reports/k6/${k6_report_name}-summary.md"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_duration() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*(s|m)$ ]]; then
    echo "${name} must use a positive second/minute duration, for example 75s or 2m: ${value}" >&2
    exit 1
  fi
}

require_rate() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^(0|0\.[0-9]+|1|1\.0+)$ ]]; then
    echo "${name} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  fi
}

require_env() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "${value}" ]]; then
    echo "${name} is required" >&2
    exit 1
  fi
}

require_k6_scenario_mode() {
  local name="$1"
  local value="$2"
  case "${value}" in
    constant-vus|constant-arrival-rate|burst)
      ;;
    *)
      echo "${name} must be constant-vus, constant-arrival-rate, or burst: ${value}" >&2
      exit 1
      ;;
  esac
}

require_generator_mode() {
  local name="$1"
  local value="$2"
  case "${value}" in
    docker-context)
      ;;
    local)
      echo "${name}=local is limited to smoke runners; profile requires docker-context" >&2
      exit 1
      ;;
    *)
      echo "${name} must be docker-context: ${value}" >&2
      exit 1
      ;;
  esac
}

stop_backend_before_bootjar() {
  # JFR profile도 host jar를 mount하므로 build 전 기존 backend JVM을 내립니다.
  docker compose "${compose_files[@]}" --profile loadtest stop aquila-bank-backend >/dev/null 2>&1 || true
}

require_duration "PROFILE_DURATION" "${profile_duration}"
require_duration "K6_DURATION" "${k6_duration}"
require_positive_integer "PROFILE_ADMISSION" "${profile_admission}"
require_positive_integer "PROFILE_DB_POOL_MAX_SIZE" "${profile_db_pool_max_size}"
require_positive_integer "PROFILE_READINESS_TIMEOUT_SECONDS" "${profile_readiness_timeout_seconds}"
require_positive_integer "PROFILE_JFR_DUMP_TIMEOUT_SECONDS" "${profile_jfr_dump_timeout_seconds}"
require_positive_integer "K6_VUS" "${k6_vus}"
require_positive_integer "K6_LIMIT" "${k6_limit}"
require_positive_integer "K6_BURST_RATE" "${k6_burst_rate}"
require_duration "K6_BURST_DURATION" "${k6_burst_duration}"
require_rate "K6_BURST_429_RATE_THRESHOLD" "${k6_burst_429_rate_threshold}"
require_k6_scenario_mode "PROFILE_K6_SCENARIO_MODE" "${profile_k6_scenario_mode}"
require_generator_mode "PROFILE_K6_GENERATOR_MODE" "${profile_k6_generator_mode}"

if [[ "${profile_spike_profile}" == "burst-hot-first" && "${profile_k6_scenario_mode}" != "burst" ]]; then
  echo "PROFILE_SPIKE_PROFILE=burst-hot-first requires PROFILE_K6_SCENARIO_MODE=burst" >&2
  exit 1
fi

if [[ "${profile_k6_generator_mode}" == "docker-context" ]]; then
  [[ -n "${profile_k6_docker_context}" ]] || {
    echo "PROFILE_K6_DOCKER_CONTEXT is required when PROFILE_K6_GENERATOR_MODE=docker-context" >&2
    exit 1
  }
  [[ -n "${profile_k6_remote_base_url}" ]] || {
    echo "PROFILE_K6_REMOTE_BASE_URL is required when PROFILE_K6_GENERATOR_MODE=docker-context" >&2
    exit 1
  }
  [[ -n "${profile_k6_remote_prometheus_rw_server_url}" ]] || {
    echo "PROFILE_K6_REMOTE_PROMETHEUS_RW_SERVER_URL is required when PROFILE_K6_GENERATOR_MODE=docker-context" >&2
    exit 1
  }
fi

find_jfr_cli() {
  if command -v jfr >/dev/null 2>&1; then
    command -v jfr
    return 0
  fi
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local java_home
    java_home="$(/usr/libexec/java_home 2>/dev/null || true)"
    if [[ -x "${java_home}/bin/jfr" ]]; then
      echo "${java_home}/bin/jfr"
      return 0
    fi
  fi
  return 1
}

wait_for_backend_readiness() {
  local deadline=$((SECONDS + profile_readiness_timeout_seconds))
  local body=""

  echo "[transaction-read-hotpath-profile] waiting backend readiness: ${backend_health_url}"
  while ((SECONDS < deadline)); do
    body="$(curl -sS --max-time 2 "${backend_health_url}" 2>/dev/null || true)"
    # 전체 health는 optional outbox 상태 때문에 DOWN일 수 있어 readinessState만 확인합니다.
    if grep -F '"readinessState":{"status":"UP"}' <<<"${body}" >/dev/null; then
      return 0
    fi
    sleep 1
  done

  echo "backend readiness timeout: ${backend_health_url}" >&2
  if [[ -n "${body}" ]]; then
    echo "${body}" >&2
  fi
  exit 1
}

wait_for_jfr_dump() {
  local deadline=$((SECONDS + profile_jfr_dump_timeout_seconds))

  echo "[transaction-read-hotpath-profile] waiting JFR dump: ${jfr_container_path}"
  while ((SECONDS < deadline)); do
    if docker compose "${compose_files[@]}" --profile loadtest exec -T aquila-bank-backend \
        sh -c "test -s '${jfr_container_path}'" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "JFR dump timeout: ${jfr_container_path}" >&2
  return 1
}

print_plan() {
  echo "[transaction-read-hotpath-profile] profile=${profile_name}"
  echo "[transaction-read-hotpath-profile] profiler=jfr"
  echo "[transaction-read-hotpath-profile] spike_profile=${profile_spike_profile}"
  echo "[transaction-read-hotpath-profile] admission=${profile_admission} db_pool=${profile_db_pool_max_size}"
  echo "[transaction-read-hotpath-profile] k6 vus=${k6_vus} duration=${k6_duration} limit=${k6_limit} report=${k6_report_name}"
  echo "[transaction-read-hotpath-profile] k6_run_id=${k6_run_id}"
  echo "[transaction-read-hotpath-profile] k6 scenario=${profile_k6_scenario_mode}"
  echo "[transaction-read-hotpath-profile] k6 burst rate=${k6_burst_rate} duration=${k6_burst_duration}"
  echo "[transaction-read-hotpath-profile] k6_overload_mode=${k6_overload_mode}"
  echo "[transaction-read-hotpath-profile] k6_burst_429_threshold=${k6_burst_429_rate_threshold}"
  echo "[transaction-read-hotpath-profile] k6_generator_mode=${profile_k6_generator_mode}"
  echo "[transaction-read-hotpath-profile] k6_docker_context=${profile_k6_docker_context:-missing}"
  echo "[transaction-read-hotpath-profile] k6_remote_base_url=${profile_k6_remote_base_url:-missing}"
  echo "[transaction-read-hotpath-profile] k6_remote_workdir=${profile_k6_remote_workdir}"
  echo "[transaction-read-hotpath-profile] jfr duration=${profile_duration}"
  echo "[transaction-read-hotpath-profile] jfr_dump_timeout_seconds=${profile_jfr_dump_timeout_seconds}"
  echo "[transaction-read-hotpath-profile] backend_health_url=${backend_health_url}"
  echo "[transaction-read-hotpath-profile] readiness_timeout_seconds=${profile_readiness_timeout_seconds}"
  echo "[transaction-read-hotpath-profile] backend_timer=aquila_transaction_read_http_stage_seconds"
  echo "[transaction-read-hotpath-profile] backend_timer_labels=endpoint,stage,outcome"
  echo "[transaction-read-hotpath-profile] method_timer_stage=authorization,usecase,response_mapping,total"
  echo "[transaction-read-hotpath-profile] artifact=${jfr_artifact}"
  echo "[transaction-read-hotpath-profile] jfr_summary=${jfr_summary_txt}"
  echo "[transaction-read-hotpath-profile] hot_first_spike_metric=aquila_transaction_hot_first_ms"
  echo "[transaction-read-hotpath-profile] spike_metrics=${hot_first_spike_metrics_txt}"
  echo "[transaction-read-hotpath-profile] summary=${summary_md}"
  echo "[transaction-read-hotpath-profile] log=${run_log}"
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

if [[ "${profile_build_backend}" == "true" ]]; then
  stop_backend_before_bootjar

  echo "[transaction-read-hotpath-profile] building backend bootJar"
  tools/test/with-resource-lock.sh back-gradle-loadtest-bootjar ./back/gradlew -p back bootJar
fi

echo "[transaction-read-hotpath-profile] starting shared loadtest services"
docker compose "${compose_files[@]}" --profile loadtest up -d \
  postgres alertmanager postgres-exporter

jfr_options="-XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=40 -XX:StartFlightRecording=name=${profile_name},settings=profile,dumponexit=true,filename=${jfr_container_path},duration=${profile_duration}"

echo "[transaction-read-hotpath-profile] starting backend with JFR"
LOADTEST_BACKEND_JAVA_TOOL_OPTIONS="${jfr_options}" \
OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX="${profile_admission}" \
DB_POOL_MAX_SIZE="${profile_db_pool_max_size}" \
  docker compose "${compose_files[@]}" --profile loadtest up -d --force-recreate aquila-bank-backend prometheus grafana

wait_for_backend_readiness

echo "[transaction-read-hotpath-profile] running k6"
set +e
K6_RUN_PURPOSE=profile \
K6_GENERATOR_MODE="${profile_k6_generator_mode}" \
K6_SCENARIO_MODE="${profile_k6_scenario_mode}" \
K6_DOCKER_CONTEXT="${profile_k6_docker_context}" \
K6_REMOTE_BASE_URL="${profile_k6_remote_base_url}" \
K6_REMOTE_PROMETHEUS_RW_SERVER_URL="${profile_k6_remote_prometheus_rw_server_url}" \
K6_REMOTE_WORKDIR="${profile_k6_remote_workdir}" \
K6_REPORT_NAME="${k6_report_name}" \
K6_RUN_ID="${k6_run_id}" \
K6_OVERLOAD_MODE="${k6_overload_mode}" \
K6_BURST_429_RATE_THRESHOLD="${k6_burst_429_rate_threshold}" \
K6_HOT_ACCOUNT_ID="${K6_HOT_ACCOUNT_ID}" \
K6_HOT_FROM="${K6_HOT_FROM}" \
K6_HOT_TO="${K6_HOT_TO}" \
K6_COLD_ACCOUNT_ID="${K6_COLD_ACCOUNT_ID}" \
K6_COLD_FROM="${K6_COLD_FROM}" \
K6_COLD_TO="${K6_COLD_TO}" \
K6_AUTH_TOKEN="${K6_AUTH_TOKEN:-}" \
K6_VUS="${k6_vus}" \
K6_DURATION="${k6_duration}" \
K6_BURST_RATE="${k6_burst_rate}" \
K6_BURST_DURATION="${k6_burst_duration}" \
K6_LIMIT="${k6_limit}" \
K6_ARCHIVE_RESULTS=false \
K6_EXPLAIN_SNAPSHOT=false \
  tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps >"${run_log}" 2>&1
k6_status=$?
set -e

wait_for_jfr_dump || true

echo "[transaction-read-hotpath-profile] stopping backend to dump JFR"
docker compose "${compose_files[@]}" --profile loadtest stop aquila-bank-backend >/dev/null 2>&1 || true

echo "[transaction-read-hotpath-profile] copying JFR artifact"
docker cp "aquila-bank-backend-loadtest:${jfr_container_path}" "${jfr_artifact}" >/dev/null 2>&1 || true

if [[ ! -s "${jfr_artifact}" ]]; then
  echo "artifact missing or empty: ${jfr_artifact}" >&2
  exit 1
fi

if jfr_cli="$(find_jfr_cli)"; then
  "${jfr_cli}" summary "${jfr_artifact}" >"${jfr_summary_txt}" 2>&1 || true
  "${jfr_cli}" view --width 160 hot-methods "${jfr_artifact}" >"${jfr_hot_methods_txt}" 2>&1 || true
  "${jfr_cli}" view --width 160 cpu-time-hot-methods "${jfr_artifact}" >"${jfr_cpu_hot_methods_txt}" 2>&1 || true
  "${jfr_cli}" view --width 160 allocation-by-class "${jfr_artifact}" >"${jfr_allocation_by_class_txt}" 2>&1 || true
  "${jfr_cli}" view --width 160 allocation-by-site "${jfr_artifact}" >"${jfr_allocation_by_site_txt}" 2>&1 || true
else
  echo "jfr CLI is not available on host; inspect ${jfr_artifact} with a JDK." >"${jfr_summary_txt}"
  echo "jfr CLI is not available on host." >"${jfr_hot_methods_txt}"
  echo "jfr CLI is not available on host." >"${jfr_cpu_hot_methods_txt}"
  echo "jfr CLI is not available on host." >"${jfr_allocation_by_class_txt}"
  echo "jfr CLI is not available on host." >"${jfr_allocation_by_site_txt}"
fi

write_hot_first_spike_metrics() {
  if [[ ! -s "${k6_summary_json}" ]]; then
    echo "k6 summary json is not available: ${k6_summary_json}" >"${hot_first_spike_metrics_txt}"
    return 0
  fi
  if ! command -v jq >/dev/null 2>&1; then
    echo "jq is not available; inspect ${k6_summary_json}" >"${hot_first_spike_metrics_txt}"
    return 0
  fi
  jq -r --arg profile "${profile_spike_profile}" '
    def metric($name; $field):
      (.metrics[$name].values // {}) as $values
      | if $values[$field] != null then $values[$field] else "n/a" end;
    [
      "spike_profile=\($profile)",
      "metric=aquila_transaction_hot_first_ms",
      "hot_first_p95_ms=\(metric("aquila_transaction_hot_first_ms"; "p(95)"))",
      "hot_first_p99_ms=\(metric("aquila_transaction_hot_first_ms"; "p(99)"))",
      "hot_first_p999_ms=\(metric("aquila_transaction_hot_first_ms"; "p(99.9)"))",
      "hot_first_max_ms=\(metric("aquila_transaction_hot_first_ms"; "max"))",
      "transaction_429_rate=\(metric("aquila_transaction_429_rate"; "rate"))",
      "transaction_503_rate=\(metric("aquila_transaction_503_rate"; "rate"))",
      "transaction_503_count=\(metric("aquila_transaction_503_count"; "count"))"
    ][]
  ' "${k6_summary_json}" >"${hot_first_spike_metrics_txt}" 2>&1 || {
    echo "failed to extract hot-first spike metrics from ${k6_summary_json}" >"${hot_first_spike_metrics_txt}"
  }
}

write_hot_first_spike_metrics

cat >"${summary_md}" <<SUMMARY
# Transaction Read Hotpath Profile

## Environment

- profile: ${profile_name}
- profiler: JFR
- spike profile: ${profile_spike_profile}
- admission: ${profile_admission}
- db pool max size: ${profile_db_pool_max_size}
- k6 run id: ${k6_run_id}
- k6 scenario: ${profile_k6_scenario_mode}
- k6 vus: ${k6_vus}
- k6 duration: ${k6_duration}
- k6 burst rate: ${k6_burst_rate}
- k6 burst duration: ${k6_burst_duration}
- k6 overload mode: ${k6_overload_mode}
- k6 burst 429 threshold: ${k6_burst_429_rate_threshold}
- k6 limit: ${k6_limit}

## Artifacts

- jfr: ${jfr_artifact}
- jfr summary: ${jfr_summary_txt}
- jfr hot methods: ${jfr_hot_methods_txt}
- jfr cpu hot methods: ${jfr_cpu_hot_methods_txt}
- jfr allocation by class: ${jfr_allocation_by_class_txt}
- jfr allocation by site: ${jfr_allocation_by_site_txt}
- hot first spike metrics: ${hot_first_spike_metrics_txt}
- k6 summary json: ${k6_summary_json}
- k6 summary markdown: ${k6_summary_md}
- run log: ${run_log}

## Backend Method Timers

- backend method timer: \`aquila.transaction.read.http.stage\`
- prometheus metric: \`aquila_transaction_read_http_stage_seconds\`
- labels: \`endpoint\`, \`stage\`, \`outcome\`
- stages: \`authorization\`, \`usecase\`, \`response_mapping\`, \`total\`
- correlation: compare this timer with k6 \`run_id=${k6_run_id}\` and Nginx \`X-K6-Run-Id\` logs for the same window.

## Result

- k6 status: ${k6_status}

## Notes

- JFR artifact는 저장소에 commit하지 않고 \`build/reports/profiling\` 아래에만 둡니다.
- burst-hot-first profile은 \`aquila_transaction_hot_first_ms\` p99.9/max와 429/503 지표를 별도 텍스트로 분리합니다.
- response mapping/serialization 최적화는 이 artifact를 분석한 뒤 별도 PR에서 진행합니다.
SUMMARY

echo "[transaction-read-hotpath-profile] summary=${summary_md}"
exit "${k6_status}"
