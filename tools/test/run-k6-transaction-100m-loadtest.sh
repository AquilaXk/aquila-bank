#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-k6-transaction-100m-loadtest.sh [--print-plan|--no-up] [--no-deps]

Required environment:
  K6_HOT_ACCOUNT_ID
  K6_HOT_ACCOUNT_IDS   optional comma-separated hot accounts for fairness replay
  K6_HOT_FROM
  K6_HOT_TO
  K6_COLD_ACCOUNT_ID
  K6_COLD_ACCOUNT_IDS  optional comma-separated cold accounts for fairness replay
  K6_COLD_FROM
  K6_COLD_TO

Optional environment:
  K6_REPORT_NAME       default transaction-100m-<timestamp>
  K6_RUN_ID            default K6_REPORT_NAME; propagated to k6 tags and run context
  K6_VUS               default 8
  K6_DURATION          default 1m
  K6_SCENARIO_MODE     constant-vus|constant-arrival-rate|burst, default constant-vus
  K6_RATE              iterations per K6_TIME_UNIT for constant-arrival-rate, default 8
  K6_TIME_UNIT         default 1s
  K6_PRE_ALLOCATED_VUS default K6_VUS
  K6_MAX_VUS           default K6_PRE_ALLOCATED_VUS
  K6_BURST_RATE        iterations per second for burst mode, default 16
  K6_BURST_DURATION    default 20s
  K6_BURST_HEADROOM_PREFLIGHT fail before k6 when burst VU headroom is too small, default true
  K6_BURST_MIN_HEADROOM_VUS default K6_BURST_RATE
  K6_WARMUP_DURATION   warmup phase before measured phase, default 10s
  K6_WARMUP_MODE       arrival-rate, default arrival-rate
  K6_WARMUP_RATE       warmup arrival rate, default 2
  K6_WARMUP_TIME_UNIT  default 1s
  K6_WARMUP_PRE_ALLOCATED_VUS default 1
  K6_WARMUP_MAX_VUS    default 2
  K6_LIMIT             default 50
  K6_HOT_DEEP_CURSOR_BOOKED_AT default 2026-04-15T00:00:00Z
  K6_HOT_DEEP_CURSOR_ID default 9223372036854775807
  K6_COLD_DEEP_CURSOR_BOOKED_AT default 2026-01-15T00:00:00Z
  K6_COLD_DEEP_CURSOR_ID default 9223372036854775807
  K6_HOT_P99_THRESHOLD_MS default 750
  K6_COLD_P99_THRESHOLD_MS default 1500
  K6_HOT_P999_THRESHOLD_MS default 1200
  K6_COLD_P999_THRESHOLD_MS default 2500
  K6_HOT_MAX_THRESHOLD_MS default 3000
  K6_COLD_MAX_THRESHOLD_MS default 5000
  K6_HOT_DEEP_P95_THRESHOLD_MS default K6_HOT_P95_THRESHOLD_MS
  K6_COLD_DEEP_P95_THRESHOLD_MS default K6_COLD_P95_THRESHOLD_MS
  K6_HOT_DEEP_P99_THRESHOLD_MS default K6_HOT_P99_THRESHOLD_MS
  K6_COLD_DEEP_P99_THRESHOLD_MS default K6_COLD_P99_THRESHOLD_MS
  K6_AUTH_TOKEN        bearer token, optional when bootstrap header auth is enabled
  K6_BASE_URL          optional backend base URL for local generator; default k6 script value
  K6_ARCHIVE_RESULTS   copy markdown summary to docs/performance-results, default true
  K6_ARCHIVE_FAILED_SUMMARY archive summary even when hard gate fails, default true
  K6_ARCHIVE_OUTPUT_ROOT default docs/performance-results
  K6_PREFLIGHT         check PostgreSQL OOM/index readiness before k6, default true
  K6_POSTGRES_HEALTH_GATE require PostgreSQL Docker health=healthy before k6, default true
  K6_POSTGRES_RECOVERY_GATE require pg_is_in_recovery()=false before k6, default true
  K6_POSTGRES_RECOVERY_STABLE_SECONDS re-check non-recovery after this delay, default 10
  K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS wait after recovery gate before measured run, default 30
  K6_POSTGRES_EXPORTER_STABLE_GATE require pg_up=1 before k6 when prometheus mode, default true
  K6_POSTGRES_EXPORTER_STABLE_TIMEOUT_SECONDS default 60
  K6_OUTBOX_PREFLIGHT  run local outbox backlog gate before k6, default false
  K6_OUTBOX_PREFLIGHT_BASE_URL default http://localhost:${LOADTEST_BACKEND_PORT:-18080}
  K6_EXPLAIN_SNAPSHOT  write pre/post hot/cold query plans, default true
  K6_OBSERVABILITY_MODE prometheus|summary-only, default prometheus
  K6_OVERLOAD_MODE     treat 429 as expected rejected samples, default false
  K6_OVERLOAD_429_RATE_THRESHOLD
                       max 429 rate in constant overload mode, default 0.015
  K6_BURST_429_RATE_THRESHOLD
                       max 429 rate in burst overload mode, default 0.10
  K6_OVERLOAD_503_RATE_THRESHOLD
                       max 503 rate in overload mode, default 0
  K6_MAX_RETRY_AFTER_SLEEP_SECONDS
                       cap Retry-After backoff in overload mode, default 1
  K6_MAX_RETRY_AFTER_SLEEP_MS
                       cap Retry-After millis/jitter backoff, default K6_MAX_RETRY_AFTER_SLEEP_SECONDS * 1000
  K6_RETRY_AFTER_ADAPTIVE_PACING
                       increase Retry-After sleep while 429 repeats in a VU, default true
  K6_RETRY_AFTER_ADAPTIVE_MAX_MULTIPLIER
                       cap adaptive Retry-After multiplier, default 6
  K6_PREEMPTIVE_PACING enable request-before token pacing, default false
  K6_PREEMPTIVE_PACING_RPS global target request rate split by K6_VUS, default 0
  K6_PREEMPTIVE_PACING_MAX_SLEEP_MS default 250
  K6_PREEMPTIVE_PACING_JITTER_MS default 25
  K6_WORKLOAD_SHAPE    fixed-order|weighted-random, default fixed-order
  K6_WORKLOAD_SEED     deterministic weighted-random seed, default 1
  K6_WORKLOAD_WEIGHTS  comma-separated query_shape:weight list
  K6_RUN_PURPOSE      smoke|capacity|profile|benchmark, default smoke
  K6_SUMMARY_GATE     require non-empty k6 work and no generator sizing loss, default true
  K6_BACKEND_READINESS_GATE wait for actuator readiness before k6, default true
  K6_BACKEND_READINESS_BASE_URL default http://localhost:${LOADTEST_BACKEND_PORT:-18080}
  K6_BACKEND_READINESS_PATH default /actuator/health/readiness
  K6_BACKEND_READINESS_TIMEOUT_SECONDS default 120
  K6_GENERATOR_MODE   local|docker-context, default local
  K6_DOCKER_CONTEXT   docker context for remote k6 generator, required when mode=docker-context
  K6_REMOTE_BASE_URL  backend URL reachable from remote k6, required when mode=docker-context
  K6_REMOTE_PROMETHEUS_RW_SERVER_URL
                       Prometheus remote-write URL reachable from remote k6, required when mode=docker-context
  K6_REMOTE_WORKDIR   repo path visible from docker context host, default current working directory
  K6_REMOTE_PREFLIGHT validate remote context/backend/prometheus reachability, default true
  K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS default 30
  K6_REMOTE_PREFLIGHT_IMAGE default curlimages/curl:8.11.1
  K6_REMOTE_READINESS_PATH default /actuator/health/readiness
  K6_REMOTE_ARTIFACT_IMAGE default busybox:1.36
  K6_REMOTE_COLLECT_ARTIFACTS copy remote summary md/json to local report dir, default true
  LOADTEST_PROMETHEUS_CPUS/MEMORY default 0.25/256m
  LOADTEST_GRAFANA_CPUS/MEMORY default 0.20/256m
  LOADTEST_ALERTMANAGER_CPUS/MEMORY default 0.10/128m
  LOADTEST_POSTGRES_EXPORTER_CPUS/MEMORY default 0.10/128m

Examples:
  K6_HOT_ACCOUNT_ID=101 K6_HOT_FROM=2026-04-01T00:00:00Z K6_HOT_TO=2026-04-30T00:00:00Z \
  K6_COLD_ACCOUNT_ID=202 K6_COLD_FROM=2026-01-01T00:00:00Z K6_COLD_TO=2026-01-31T00:00:00Z \
    tools/test/run-k6-transaction-100m-loadtest.sh
USAGE
}

require_env() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "${value}" ]]; then
    echo "${name} is required" >&2
    exit 1
  fi
}

require_positive_integer() {
  local name="$1"
  local value="${!name:-}"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer" >&2
    exit 1
  fi
}

require_non_negative_integer() {
  local name="$1"
  local value="${!name:-}"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${name} must be a non-negative integer" >&2
    exit 1
  fi
}

require_positive_number() {
  local name="$1"
  local value="${!name:-}"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be a positive number" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value > 0) }' \
    || {
      echo "${name} must be greater than zero" >&2
      exit 1
    }
}

require_rate() {
  local name="$1"
  local value="${!name:-}"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be a rate between 0 and 1" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value >= 0 && value <= 1) }' \
    || {
      echo "${name} must be a rate between 0 and 1" >&2
      exit 1
    }
}

require_generator_mode() {
  case "${K6_GENERATOR_MODE}" in
    local|docker-context)
      ;;
    *)
      echo "K6_GENERATOR_MODE must be local or docker-context" >&2
      exit 1
      ;;
  esac
}

require_observability_mode() {
  case "${K6_OBSERVABILITY_MODE}" in
    prometheus|summary-only)
      ;;
    *)
      echo "K6_OBSERVABILITY_MODE must be prometheus or summary-only" >&2
      exit 1
      ;;
  esac
}

require_scenario_mode() {
  case "${K6_SCENARIO_MODE}" in
    constant-vus|constant-arrival-rate|burst)
      ;;
    *)
      echo "K6_SCENARIO_MODE must be constant-vus, constant-arrival-rate, or burst" >&2
      exit 1
      ;;
  esac
}

require_workload_shape() {
  case "${K6_WORKLOAD_SHAPE}" in
    fixed-order|weighted-random)
      ;;
    *)
      echo "K6_WORKLOAD_SHAPE must be fixed-order or weighted-random" >&2
      exit 1
      ;;
  esac
}

require_run_purpose() {
  case "${K6_RUN_PURPOSE}" in
    smoke|capacity|profile|benchmark)
      ;;
    *)
      echo "K6_RUN_PURPOSE must be smoke, capacity, profile, or benchmark" >&2
      exit 1
      ;;
  esac
}

mode="run"
run_dependencies="true"
summary_gate_json=""
summary_gate_log=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --assert-summary)
      if [[ "$#" -lt 3 ]]; then
        usage
        exit 1
      fi
      mode="assert-summary"
      summary_gate_json="$2"
      summary_gate_log="$3"
      shift 2
      ;;
    --no-up)
      mode="no-up"
      ;;
    --no-deps)
      run_dependencies="false"
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

K6_VUS="${K6_VUS:-8}"
K6_SCENARIO_MODE="${K6_SCENARIO_MODE:-constant-vus}"
K6_RATE="${K6_RATE:-8}"
K6_TIME_UNIT="${K6_TIME_UNIT:-1s}"
K6_BURST_RATE="${K6_BURST_RATE:-16}"
K6_BURST_DURATION="${K6_BURST_DURATION:-20s}"
K6_BURST_HEADROOM_PREFLIGHT="${K6_BURST_HEADROOM_PREFLIGHT:-true}"
K6_BURST_MIN_HEADROOM_VUS="${K6_BURST_MIN_HEADROOM_VUS:-${K6_BURST_RATE}}"
K6_GENERATOR_MODE="${K6_GENERATOR_MODE:-local}"
K6_PRE_ALLOCATED_VUS="${K6_PRE_ALLOCATED_VUS:-}"
if [[ -z "${K6_PRE_ALLOCATED_VUS}" ]]; then
  if [[ "${K6_SCENARIO_MODE}" == "burst" ]]; then
    K6_PRE_ALLOCATED_VUS="${K6_BURST_MIN_HEADROOM_VUS}"
  else
    K6_PRE_ALLOCATED_VUS="${K6_VUS}"
  fi
fi
K6_MAX_VUS="${K6_MAX_VUS:-}"
if [[ -z "${K6_MAX_VUS}" ]]; then
  if [[ "${K6_SCENARIO_MODE}" == "burst" ]]; then
    if [[ "${K6_BURST_RATE}" =~ ^[1-9][0-9]*$ ]]; then
      if [[ "${K6_GENERATOR_MODE}" == "docker-context" ]]; then
        K6_MAX_VUS="${K6_PRE_ALLOCATED_VUS}"
      else
        burst_local_max="$((K6_BURST_RATE * 2))"
        if [[ "${K6_PRE_ALLOCATED_VUS}" =~ ^[1-9][0-9]*$ && "${K6_PRE_ALLOCATED_VUS}" -gt "${burst_local_max}" ]]; then
          burst_local_max="${K6_PRE_ALLOCATED_VUS}"
        fi
        K6_MAX_VUS="${burst_local_max}"
      fi
    else
      K6_MAX_VUS="${K6_BURST_RATE}"
    fi
  else
    K6_MAX_VUS="${K6_PRE_ALLOCATED_VUS}"
  fi
fi
K6_WARMUP_DURATION="${K6_WARMUP_DURATION:-10s}"
K6_WARMUP_MODE="${K6_WARMUP_MODE:-arrival-rate}"
K6_WARMUP_RATE="${K6_WARMUP_RATE:-2}"
K6_WARMUP_TIME_UNIT="${K6_WARMUP_TIME_UNIT:-1s}"
K6_WARMUP_PRE_ALLOCATED_VUS="${K6_WARMUP_PRE_ALLOCATED_VUS:-1}"
K6_WARMUP_MAX_VUS="${K6_WARMUP_MAX_VUS:-2}"
K6_LIMIT="${K6_LIMIT:-50}"
K6_HOT_DEEP_CURSOR_BOOKED_AT="${K6_HOT_DEEP_CURSOR_BOOKED_AT:-2026-04-15T00:00:00Z}"
K6_HOT_DEEP_CURSOR_ID="${K6_HOT_DEEP_CURSOR_ID:-9223372036854775807}"
K6_COLD_DEEP_CURSOR_BOOKED_AT="${K6_COLD_DEEP_CURSOR_BOOKED_AT:-2026-01-15T00:00:00Z}"
K6_COLD_DEEP_CURSOR_ID="${K6_COLD_DEEP_CURSOR_ID:-9223372036854775807}"
K6_HOT_P95_THRESHOLD_MS="${K6_HOT_P95_THRESHOLD_MS:-350}"
K6_COLD_P95_THRESHOLD_MS="${K6_COLD_P95_THRESHOLD_MS:-750}"
K6_HOT_P99_THRESHOLD_MS="${K6_HOT_P99_THRESHOLD_MS:-750}"
K6_COLD_P99_THRESHOLD_MS="${K6_COLD_P99_THRESHOLD_MS:-1500}"
K6_HOT_P999_THRESHOLD_MS="${K6_HOT_P999_THRESHOLD_MS:-1200}"
K6_COLD_P999_THRESHOLD_MS="${K6_COLD_P999_THRESHOLD_MS:-2500}"
K6_HOT_MAX_THRESHOLD_MS="${K6_HOT_MAX_THRESHOLD_MS:-3000}"
K6_COLD_MAX_THRESHOLD_MS="${K6_COLD_MAX_THRESHOLD_MS:-5000}"
K6_HOT_DEEP_P95_THRESHOLD_MS="${K6_HOT_DEEP_P95_THRESHOLD_MS:-${K6_HOT_P95_THRESHOLD_MS}}"
K6_COLD_DEEP_P95_THRESHOLD_MS="${K6_COLD_DEEP_P95_THRESHOLD_MS:-${K6_COLD_P95_THRESHOLD_MS}}"
K6_HOT_DEEP_P99_THRESHOLD_MS="${K6_HOT_DEEP_P99_THRESHOLD_MS:-${K6_HOT_P99_THRESHOLD_MS}}"
K6_COLD_DEEP_P99_THRESHOLD_MS="${K6_COLD_DEEP_P99_THRESHOLD_MS:-${K6_COLD_P99_THRESHOLD_MS}}"
K6_HOT_DEEP_P999_THRESHOLD_MS="${K6_HOT_DEEP_P999_THRESHOLD_MS:-${K6_HOT_P999_THRESHOLD_MS}}"
K6_COLD_DEEP_P999_THRESHOLD_MS="${K6_COLD_DEEP_P999_THRESHOLD_MS:-${K6_COLD_P999_THRESHOLD_MS}}"
K6_HOT_DEEP_MAX_THRESHOLD_MS="${K6_HOT_DEEP_MAX_THRESHOLD_MS:-${K6_HOT_MAX_THRESHOLD_MS}}"
K6_COLD_DEEP_MAX_THRESHOLD_MS="${K6_COLD_DEEP_MAX_THRESHOLD_MS:-${K6_COLD_MAX_THRESHOLD_MS}}"
K6_HTTP_FAILED_RATE="${K6_HTTP_FAILED_RATE:-0.01}"
K6_ARCHIVE_RESULTS="${K6_ARCHIVE_RESULTS:-true}"
K6_ARCHIVE_FAILED_SUMMARY="${K6_ARCHIVE_FAILED_SUMMARY:-true}"
K6_ARCHIVE_OUTPUT_ROOT="${K6_ARCHIVE_OUTPUT_ROOT:-docs/performance-results}"
K6_PREFLIGHT="${K6_PREFLIGHT:-true}"
K6_POSTGRES_HEALTH_GATE="${K6_POSTGRES_HEALTH_GATE:-true}"
K6_POSTGRES_RECOVERY_GATE="${K6_POSTGRES_RECOVERY_GATE:-true}"
K6_POSTGRES_RECOVERY_STABLE_SECONDS="${K6_POSTGRES_RECOVERY_STABLE_SECONDS:-10}"
K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS="${K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS:-30}"
K6_POSTGRES_EXPORTER_STABLE_GATE="${K6_POSTGRES_EXPORTER_STABLE_GATE:-true}"
K6_POSTGRES_EXPORTER_STABLE_TIMEOUT_SECONDS="${K6_POSTGRES_EXPORTER_STABLE_TIMEOUT_SECONDS:-60}"
K6_OUTBOX_PREFLIGHT="${K6_OUTBOX_PREFLIGHT:-false}"
K6_OUTBOX_PREFLIGHT_BASE_URL="${K6_OUTBOX_PREFLIGHT_BASE_URL:-http://localhost:${LOADTEST_BACKEND_PORT:-18080}}"
K6_EXPLAIN_SNAPSHOT="${K6_EXPLAIN_SNAPSHOT:-true}"
K6_OBSERVABILITY_MODE="${K6_OBSERVABILITY_MODE:-prometheus}"
K6_OVERLOAD_MODE="${K6_OVERLOAD_MODE:-false}"
K6_OVERLOAD_429_RATE_THRESHOLD="${K6_OVERLOAD_429_RATE_THRESHOLD:-0.015}"
K6_BURST_429_RATE_THRESHOLD="${K6_BURST_429_RATE_THRESHOLD:-0.10}"
K6_OVERLOAD_503_RATE_THRESHOLD="${K6_OVERLOAD_503_RATE_THRESHOLD:-0}"
K6_MAX_RETRY_AFTER_SLEEP_SECONDS="${K6_MAX_RETRY_AFTER_SLEEP_SECONDS:-1}"
if [[ -z "${K6_MAX_RETRY_AFTER_SLEEP_MS:-}" && "${K6_MAX_RETRY_AFTER_SLEEP_SECONDS}" =~ ^[0-9]+$ ]]; then
  K6_MAX_RETRY_AFTER_SLEEP_MS="$((K6_MAX_RETRY_AFTER_SLEEP_SECONDS * 1000))"
else
  K6_MAX_RETRY_AFTER_SLEEP_MS="${K6_MAX_RETRY_AFTER_SLEEP_MS:-1000}"
fi
K6_RETRY_AFTER_ADAPTIVE_PACING="${K6_RETRY_AFTER_ADAPTIVE_PACING:-true}"
K6_RETRY_AFTER_ADAPTIVE_MAX_MULTIPLIER="${K6_RETRY_AFTER_ADAPTIVE_MAX_MULTIPLIER:-6}"
K6_PREEMPTIVE_PACING="${K6_PREEMPTIVE_PACING:-false}"
K6_PREEMPTIVE_PACING_RPS="${K6_PREEMPTIVE_PACING_RPS:-0}"
K6_PREEMPTIVE_PACING_MAX_SLEEP_MS="${K6_PREEMPTIVE_PACING_MAX_SLEEP_MS:-250}"
K6_PREEMPTIVE_PACING_JITTER_MS="${K6_PREEMPTIVE_PACING_JITTER_MS:-25}"
K6_WORKLOAD_SHAPE="${K6_WORKLOAD_SHAPE:-fixed-order}"
K6_WORKLOAD_SEED="${K6_WORKLOAD_SEED:-1}"
K6_WORKLOAD_WEIGHTS="${K6_WORKLOAD_WEIGHTS:-hot_first:20,hot_cursor:20,hot_deep_cursor:10,cold_first:20,cold_cursor:20,cold_deep_cursor:10}"
K6_HOT_ACCOUNT_IDS="${K6_HOT_ACCOUNT_IDS:-}"
K6_COLD_ACCOUNT_IDS="${K6_COLD_ACCOUNT_IDS:-}"
K6_RUN_PURPOSE="${K6_RUN_PURPOSE:-smoke}"
K6_SUMMARY_GATE="${K6_SUMMARY_GATE:-true}"
K6_BACKEND_READINESS_GATE="${K6_BACKEND_READINESS_GATE:-true}"
K6_BACKEND_READINESS_BASE_URL="${K6_BACKEND_READINESS_BASE_URL:-http://localhost:${LOADTEST_BACKEND_PORT:-18080}}"
K6_BACKEND_READINESS_PATH="${K6_BACKEND_READINESS_PATH:-/actuator/health/readiness}"
K6_BACKEND_READINESS_TIMEOUT_SECONDS="${K6_BACKEND_READINESS_TIMEOUT_SECONDS:-120}"
K6_DOCKER_CONTEXT="${K6_DOCKER_CONTEXT:-}"
K6_REMOTE_BASE_URL="${K6_REMOTE_BASE_URL:-}"
K6_REMOTE_PROMETHEUS_RW_SERVER_URL="${K6_REMOTE_PROMETHEUS_RW_SERVER_URL:-}"
K6_REMOTE_WORKDIR="${K6_REMOTE_WORKDIR:-$(pwd)}"
K6_REMOTE_PREFLIGHT="${K6_REMOTE_PREFLIGHT:-true}"
K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS="${K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS:-30}"
K6_REMOTE_PREFLIGHT_IMAGE="${K6_REMOTE_PREFLIGHT_IMAGE:-curlimages/curl:8.11.1}"
K6_REMOTE_READINESS_PATH="${K6_REMOTE_READINESS_PATH:-/actuator/health/readiness}"
K6_REMOTE_ARTIFACT_IMAGE="${K6_REMOTE_ARTIFACT_IMAGE:-busybox:1.36}"
K6_REMOTE_COLLECT_ARTIFACTS="${K6_REMOTE_COLLECT_ARTIFACTS:-true}"
K6_REPORT_NAME="${K6_REPORT_NAME:-transaction-100m-$(date +%Y-%m-%d-%H%M%S)}"
K6_RUN_ID="${K6_RUN_ID:-${K6_REPORT_NAME}}"
loadtest_db_port="${LOADTEST_DB_PORT:-15432}"
loadtest_backend_port="${LOADTEST_BACKEND_PORT:-18080}"
loadtest_prometheus_port="${LOADTEST_PROMETHEUS_PORT:-19090}"
loadtest_grafana_port="${LOADTEST_GRAFANA_PORT:-13001}"
loadtest_alertmanager_port="${LOADTEST_ALERTMANAGER_PORT:-19093}"
loadtest_postgres_exporter_port="${LOADTEST_POSTGRES_EXPORTER_PORT:-19187}"
loadtest_prometheus_cpus="${LOADTEST_PROMETHEUS_CPUS:-0.25}"
loadtest_prometheus_memory="${LOADTEST_PROMETHEUS_MEMORY:-256m}"
loadtest_grafana_cpus="${LOADTEST_GRAFANA_CPUS:-0.20}"
loadtest_grafana_memory="${LOADTEST_GRAFANA_MEMORY:-256m}"
loadtest_alertmanager_cpus="${LOADTEST_ALERTMANAGER_CPUS:-0.10}"
loadtest_alertmanager_memory="${LOADTEST_ALERTMANAGER_MEMORY:-128m}"
loadtest_postgres_exporter_cpus="${LOADTEST_POSTGRES_EXPORTER_CPUS:-0.10}"
loadtest_postgres_exporter_memory="${LOADTEST_POSTGRES_EXPORTER_MEMORY:-128m}"
loadtest_postgres_container="${LOADTEST_POSTGRES_CONTAINER_NAME:-aquila-bank-postgres-loadtest}"
loadtest_backend_container="${LOADTEST_BACKEND_CONTAINER_NAME:-aquila-bank-backend-loadtest}"
export K6_VUS K6_SCENARIO_MODE K6_RATE K6_TIME_UNIT K6_PRE_ALLOCATED_VUS K6_MAX_VUS K6_BURST_RATE K6_BURST_DURATION K6_BURST_HEADROOM_PREFLIGHT K6_BURST_MIN_HEADROOM_VUS K6_WARMUP_DURATION K6_WARMUP_MODE K6_WARMUP_RATE K6_WARMUP_TIME_UNIT K6_WARMUP_PRE_ALLOCATED_VUS K6_WARMUP_MAX_VUS K6_LIMIT K6_HOT_ACCOUNT_IDS K6_COLD_ACCOUNT_IDS K6_HOT_DEEP_CURSOR_BOOKED_AT K6_HOT_DEEP_CURSOR_ID K6_COLD_DEEP_CURSOR_BOOKED_AT K6_COLD_DEEP_CURSOR_ID K6_HOT_P95_THRESHOLD_MS K6_COLD_P95_THRESHOLD_MS K6_HOT_P99_THRESHOLD_MS K6_COLD_P99_THRESHOLD_MS K6_HOT_P999_THRESHOLD_MS K6_COLD_P999_THRESHOLD_MS K6_HOT_MAX_THRESHOLD_MS K6_COLD_MAX_THRESHOLD_MS K6_HOT_DEEP_P95_THRESHOLD_MS K6_COLD_DEEP_P95_THRESHOLD_MS K6_HOT_DEEP_P99_THRESHOLD_MS K6_COLD_DEEP_P99_THRESHOLD_MS K6_HOT_DEEP_P999_THRESHOLD_MS K6_COLD_DEEP_P999_THRESHOLD_MS K6_HOT_DEEP_MAX_THRESHOLD_MS K6_COLD_DEEP_MAX_THRESHOLD_MS K6_HTTP_FAILED_RATE K6_ARCHIVE_RESULTS K6_ARCHIVE_FAILED_SUMMARY K6_PREFLIGHT K6_POSTGRES_HEALTH_GATE K6_POSTGRES_RECOVERY_GATE K6_POSTGRES_RECOVERY_STABLE_SECONDS K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS K6_POSTGRES_EXPORTER_STABLE_GATE K6_POSTGRES_EXPORTER_STABLE_TIMEOUT_SECONDS K6_OUTBOX_PREFLIGHT K6_OUTBOX_PREFLIGHT_BASE_URL K6_EXPLAIN_SNAPSHOT K6_OBSERVABILITY_MODE K6_OVERLOAD_MODE K6_OVERLOAD_429_RATE_THRESHOLD K6_BURST_429_RATE_THRESHOLD K6_OVERLOAD_503_RATE_THRESHOLD K6_MAX_RETRY_AFTER_SLEEP_SECONDS K6_MAX_RETRY_AFTER_SLEEP_MS K6_RETRY_AFTER_ADAPTIVE_PACING K6_RETRY_AFTER_ADAPTIVE_MAX_MULTIPLIER K6_PREEMPTIVE_PACING K6_PREEMPTIVE_PACING_RPS K6_PREEMPTIVE_PACING_MAX_SLEEP_MS K6_PREEMPTIVE_PACING_JITTER_MS K6_WORKLOAD_SHAPE K6_WORKLOAD_SEED K6_WORKLOAD_WEIGHTS K6_RUN_PURPOSE K6_SUMMARY_GATE K6_BACKEND_READINESS_GATE K6_BACKEND_READINESS_BASE_URL K6_BACKEND_READINESS_PATH K6_BACKEND_READINESS_TIMEOUT_SECONDS K6_GENERATOR_MODE K6_DOCKER_CONTEXT K6_REMOTE_BASE_URL K6_REMOTE_PROMETHEUS_RW_SERVER_URL K6_REMOTE_WORKDIR K6_REMOTE_PREFLIGHT K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS K6_REMOTE_PREFLIGHT_IMAGE K6_REMOTE_READINESS_PATH K6_REMOTE_ARTIFACT_IMAGE K6_REMOTE_COLLECT_ARTIFACTS K6_REPORT_NAME K6_RUN_ID

require_positive_integer K6_VUS
require_positive_integer K6_RATE
require_positive_integer K6_PRE_ALLOCATED_VUS
require_positive_integer K6_MAX_VUS
require_positive_integer K6_BURST_RATE
require_positive_integer K6_BURST_MIN_HEADROOM_VUS
if ! [[ "${K6_WARMUP_DURATION}" =~ ^[0-9]+(s|m|h)$ ]]; then
  echo "K6_WARMUP_DURATION must use a duration such as 0s, 10s, or 1m" >&2
  exit 1
fi
if [[ "${K6_WARMUP_MODE}" != "arrival-rate" ]]; then
  echo "K6_WARMUP_MODE must be arrival-rate" >&2
  exit 1
fi
if ! [[ "${K6_WARMUP_TIME_UNIT}" =~ ^[0-9]+(s|m|h)$ ]]; then
  echo "K6_WARMUP_TIME_UNIT must use a duration such as 1s or 1m" >&2
  exit 1
fi
require_positive_integer K6_WARMUP_RATE
require_positive_integer K6_WARMUP_PRE_ALLOCATED_VUS
require_positive_integer K6_WARMUP_MAX_VUS
require_positive_integer K6_LIMIT
require_positive_integer K6_HOT_DEEP_CURSOR_ID
require_positive_integer K6_COLD_DEEP_CURSOR_ID
require_positive_number K6_HOT_P95_THRESHOLD_MS
require_positive_number K6_COLD_P95_THRESHOLD_MS
require_positive_number K6_HOT_P99_THRESHOLD_MS
require_positive_number K6_COLD_P99_THRESHOLD_MS
require_positive_number K6_HOT_P999_THRESHOLD_MS
require_positive_number K6_COLD_P999_THRESHOLD_MS
require_positive_number K6_HOT_MAX_THRESHOLD_MS
require_positive_number K6_COLD_MAX_THRESHOLD_MS
require_positive_number K6_HOT_DEEP_P95_THRESHOLD_MS
require_positive_number K6_COLD_DEEP_P95_THRESHOLD_MS
require_positive_number K6_HOT_DEEP_P99_THRESHOLD_MS
require_positive_number K6_COLD_DEEP_P99_THRESHOLD_MS
require_positive_number K6_HOT_DEEP_P999_THRESHOLD_MS
require_positive_number K6_COLD_DEEP_P999_THRESHOLD_MS
require_positive_number K6_HOT_DEEP_MAX_THRESHOLD_MS
require_positive_number K6_COLD_DEEP_MAX_THRESHOLD_MS
require_rate K6_HTTP_FAILED_RATE
require_bool_value() {
  local name="$1"
  local value="${!name:-}"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false" >&2
    exit 1
  fi
}
require_bool_value K6_OUTBOX_PREFLIGHT
require_bool_value K6_EXPLAIN_SNAPSHOT
require_bool_value K6_ARCHIVE_RESULTS
require_bool_value K6_ARCHIVE_FAILED_SUMMARY
require_bool_value K6_SUMMARY_GATE
require_bool_value K6_BURST_HEADROOM_PREFLIGHT
require_bool_value K6_BACKEND_READINESS_GATE
require_bool_value K6_POSTGRES_HEALTH_GATE
require_bool_value K6_POSTGRES_RECOVERY_GATE
require_bool_value K6_POSTGRES_EXPORTER_STABLE_GATE
require_positive_integer K6_BACKEND_READINESS_TIMEOUT_SECONDS
require_non_negative_integer K6_POSTGRES_RECOVERY_STABLE_SECONDS
require_non_negative_integer K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS
require_positive_integer K6_POSTGRES_EXPORTER_STABLE_TIMEOUT_SECONDS
require_bool_value K6_REMOTE_PREFLIGHT
require_bool_value K6_REMOTE_COLLECT_ARTIFACTS
require_positive_integer K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS
require_non_negative_integer K6_MAX_RETRY_AFTER_SLEEP_SECONDS
require_non_negative_integer K6_MAX_RETRY_AFTER_SLEEP_MS
require_bool_value K6_RETRY_AFTER_ADAPTIVE_PACING
require_positive_integer K6_RETRY_AFTER_ADAPTIVE_MAX_MULTIPLIER
require_bool_value K6_PREEMPTIVE_PACING
require_non_negative_integer K6_PREEMPTIVE_PACING_RPS
require_non_negative_integer K6_PREEMPTIVE_PACING_MAX_SLEEP_MS
require_non_negative_integer K6_PREEMPTIVE_PACING_JITTER_MS
require_non_negative_integer K6_WORKLOAD_SEED
require_rate K6_OVERLOAD_429_RATE_THRESHOLD
require_rate K6_BURST_429_RATE_THRESHOLD
require_rate K6_OVERLOAD_503_RATE_THRESHOLD
require_generator_mode
require_observability_mode
require_scenario_mode
require_workload_shape
require_run_purpose

assert_burst_headroom_preflight() {
  if [[ "${K6_SCENARIO_MODE}" != "burst" || "${K6_BURST_HEADROOM_PREFLIGHT}" != "true" ]]; then
    return 0
  fi
  if ((K6_PRE_ALLOCATED_VUS < K6_BURST_MIN_HEADROOM_VUS)); then
    echo "burst headroom preflight failed: K6_PRE_ALLOCATED_VUS=${K6_PRE_ALLOCATED_VUS} min=${K6_BURST_MIN_HEADROOM_VUS}" >&2
    exit 1
  fi
  if ((K6_MAX_VUS < K6_BURST_MIN_HEADROOM_VUS)); then
    echo "burst headroom preflight failed: K6_MAX_VUS=${K6_MAX_VUS} min=${K6_BURST_MIN_HEADROOM_VUS}" >&2
    exit 1
  fi
}

assert_burst_headroom_preflight

if [[ "${K6_GENERATOR_MODE}" == "local" && "${K6_RUN_PURPOSE}" != "smoke" ]]; then
  echo "K6_GENERATOR_MODE=local is limited to K6_RUN_PURPOSE=smoke" >&2
  exit 1
fi

if [[ "${K6_GENERATOR_MODE}" == "docker-context" ]]; then
  require_env K6_DOCKER_CONTEXT
  require_env K6_REMOTE_BASE_URL
  if [[ "${K6_OBSERVABILITY_MODE}" == "prometheus" ]]; then
    require_env K6_REMOTE_PROMETHEUS_RW_SERVER_URL
  fi
fi

compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
report_dir="build/reports/k6"
summary_md="${report_dir}/${K6_REPORT_NAME}-summary.md"
summary_json="${report_dir}/${K6_REPORT_NAME}-summary.json"
k6_runner_log="${report_dir}/${K6_REPORT_NAME}-runner.log"
run_context_env="${report_dir}/${K6_REPORT_NAME}-run-context.env"
archive_output_dir="${K6_ARCHIVE_OUTPUT_ROOT%/}/k6-${K6_RUN_PURPOSE}"
psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}")

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "${command_name} is required" >&2
    exit 1
  fi
}

k6_metric_count() {
  local path="$1"
  local metric="$2"
  jq -r --arg metric "${metric}" '
    (.metrics[$metric].values // {}) as $values
    | if $values.count != null then
        $values.count
      elif ($values.passes != null or $values.fails != null) then
        (($values.passes // 0) + ($values.fails // 0))
      elif ($values.rate != null and (($values.rate | tonumber) > 0)) then
        1
      else
        0
      end
  ' "${path}"
}

assert_k6_summary_gate() {
  local json_path="$1"
  local log_path="$2"
  local current_status="$3"
  local iterations checks interrupted dropped
  local gate_failed=false

  if [[ "${K6_SUMMARY_GATE}" != "true" ]]; then
    return "${current_status}"
  fi

  require_command jq
  if [[ ! -s "${json_path}" ]]; then
    echo "k6 summary json is required for hard gate: ${json_path}" >&2
    return 1
  fi

  iterations="$(k6_metric_count "${json_path}" iterations)"
  checks="$(k6_metric_count "${json_path}" checks)"
  interrupted="$(k6_metric_count "${json_path}" interrupted_iterations)"
  dropped="$(k6_metric_count "${json_path}" dropped_iterations)"

  if ! awk -v value="${iterations}" 'BEGIN { exit !(value > 0) }'; then
    echo "k6 hard gate failed: iterations must be > 0, actual=${iterations}" >&2
    gate_failed=true
  fi
  if ! awk -v value="${checks}" 'BEGIN { exit !(value > 0) }'; then
    echo "k6 hard gate failed: checks must be > 0, actual=${checks}" >&2
    gate_failed=true
  fi
  if ! awk -v value="${interrupted}" 'BEGIN { exit !(value == 0) }'; then
    echo "k6 hard gate failed: interrupted_iterations must be 0, actual=${interrupted}" >&2
    gate_failed=true
  fi
  if ! awk -v value="${dropped}" 'BEGIN { exit !(value == 0) }'; then
    echo "k6 hard gate failed: dropped_iterations must be 0, actual=${dropped}" >&2
    gate_failed=true
  fi
  if [[ -s "${log_path}" ]] && grep -Fi "Insufficient VUs" "${log_path}" >/dev/null; then
    echo "k6 hard gate failed: Insufficient VUs warning detected in ${log_path}" >&2
    gate_failed=true
  fi

  if [[ "${gate_failed}" == "true" ]]; then
    return 1
  fi
  return "${current_status}"
}

print_plan() {
  echo "[k6-transaction-100m] compose files: ${compose_files[*]}"
  echo "[k6-transaction-100m] backend: aquila-bank-backend:8080 with OCI A1 4 OCPU / 24GB budget"
  echo "[k6-transaction-100m] ports: db=${loadtest_db_port} backend=${loadtest_backend_port} prometheus=${loadtest_prometheus_port} grafana=${loadtest_grafana_port} alertmanager=${loadtest_alertmanager_port} postgres-exporter=${loadtest_postgres_exporter_port}"
  echo "[k6-transaction-100m] containers: postgres=${loadtest_postgres_container} backend=${loadtest_backend_container}"
  if [[ "${K6_OBSERVABILITY_MODE}" == "prometheus" ]]; then
    echo "[k6-transaction-100m] observability: prometheus:9090 grafana:3000 alertmanager:9093 postgres-exporter:9187"
  else
    echo "[k6-transaction-100m] observability: summary-only local markdown/json"
  fi
  echo "[k6-transaction-100m] observability resources: prometheus=${loadtest_prometheus_cpus}/${loadtest_prometheus_memory} grafana=${loadtest_grafana_cpus}/${loadtest_grafana_memory} alertmanager=${loadtest_alertmanager_cpus}/${loadtest_alertmanager_memory} postgres-exporter=${loadtest_postgres_exporter_cpus}/${loadtest_postgres_exporter_memory}"
  echo "[k6-transaction-100m] observability mode=${K6_OBSERVABILITY_MODE}"
  echo "[k6-transaction-100m] k6 report name: ${K6_REPORT_NAME}"
  echo "[k6-transaction-100m] base url=${K6_BASE_URL:-script-default}"
  echo "[k6-transaction-100m] run id=${K6_RUN_ID}"
  echo "[k6-transaction-100m] run context=${run_context_env}"
  echo "[k6-transaction-100m] k6 vus=${K6_VUS} duration=${K6_DURATION:-1m} limit=${K6_LIMIT}"
  echo "[k6-transaction-100m] scenario mode=${K6_SCENARIO_MODE}"
  echo "[k6-transaction-100m] workload shape=${K6_WORKLOAD_SHAPE} seed=${K6_WORKLOAD_SEED} weights=${K6_WORKLOAD_WEIGHTS}"
  echo "[k6-transaction-100m] multi-account hot_ids=${K6_HOT_ACCOUNT_IDS:-${K6_HOT_ACCOUNT_ID:-missing}} cold_ids=${K6_COLD_ACCOUNT_IDS:-${K6_COLD_ACCOUNT_ID:-missing}}"
  echo "[k6-transaction-100m] arrival rate=${K6_RATE} timeUnit=${K6_TIME_UNIT} preAllocatedVUs=${K6_PRE_ALLOCATED_VUS} maxVUs=${K6_MAX_VUS}"
  echo "[k6-transaction-100m] burst rate=${K6_BURST_RATE} duration=${K6_BURST_DURATION} preAllocatedVUs=${K6_PRE_ALLOCATED_VUS} maxVUs=${K6_MAX_VUS}"
  echo "[k6-transaction-100m] burst headroom preflight=${K6_BURST_HEADROOM_PREFLIGHT} minVUs=${K6_BURST_MIN_HEADROOM_VUS}"
  echo "[k6-transaction-100m] warmup duration=${K6_WARMUP_DURATION}"
  echo "[k6-transaction-100m] warmup mode=${K6_WARMUP_MODE} rate=${K6_WARMUP_RATE} timeUnit=${K6_WARMUP_TIME_UNIT} preAllocatedVUs=${K6_WARMUP_PRE_ALLOCATED_VUS} maxVUs=${K6_WARMUP_MAX_VUS} contamination=off"
  echo "[k6-transaction-100m] hot p95 threshold ms=${K6_HOT_P95_THRESHOLD_MS}"
  echo "[k6-transaction-100m] cold p95 threshold ms=${K6_COLD_P95_THRESHOLD_MS}"
  echo "[k6-transaction-100m] hot p99 threshold ms=${K6_HOT_P99_THRESHOLD_MS}"
  echo "[k6-transaction-100m] cold p99 threshold ms=${K6_COLD_P99_THRESHOLD_MS}"
  echo "[k6-transaction-100m] hot p99.9 threshold ms=${K6_HOT_P999_THRESHOLD_MS}"
  echo "[k6-transaction-100m] cold p99.9 threshold ms=${K6_COLD_P999_THRESHOLD_MS}"
  echo "[k6-transaction-100m] hot max threshold ms=${K6_HOT_MAX_THRESHOLD_MS}"
  echo "[k6-transaction-100m] cold max threshold ms=${K6_COLD_MAX_THRESHOLD_MS}"
  echo "[k6-transaction-100m] hot deep cursor=${K6_HOT_DEEP_CURSOR_BOOKED_AT}|${K6_HOT_DEEP_CURSOR_ID}"
  echo "[k6-transaction-100m] cold deep cursor=${K6_COLD_DEEP_CURSOR_BOOKED_AT}|${K6_COLD_DEEP_CURSOR_ID}"
  echo "[k6-transaction-100m] hot deep p95 threshold ms=${K6_HOT_DEEP_P95_THRESHOLD_MS}"
  echo "[k6-transaction-100m] cold deep p95 threshold ms=${K6_COLD_DEEP_P95_THRESHOLD_MS}"
  echo "[k6-transaction-100m] hot deep p99 threshold ms=${K6_HOT_DEEP_P99_THRESHOLD_MS}"
  echo "[k6-transaction-100m] cold deep p99 threshold ms=${K6_COLD_DEEP_P99_THRESHOLD_MS}"
  echo "[k6-transaction-100m] hot deep p99.9 threshold ms=${K6_HOT_DEEP_P999_THRESHOLD_MS}"
  echo "[k6-transaction-100m] cold deep p99.9 threshold ms=${K6_COLD_DEEP_P999_THRESHOLD_MS}"
  echo "[k6-transaction-100m] hot deep max threshold ms=${K6_HOT_DEEP_MAX_THRESHOLD_MS}"
  echo "[k6-transaction-100m] cold deep max threshold ms=${K6_COLD_DEEP_MAX_THRESHOLD_MS}"
  echo "[k6-transaction-100m] http failed rate threshold=${K6_HTTP_FAILED_RATE}"
  echo "[k6-transaction-100m] overload mode=${K6_OVERLOAD_MODE} max retry-after sleep seconds=${K6_MAX_RETRY_AFTER_SLEEP_SECONDS}"
  echo "[k6-transaction-100m] max retry-after sleep ms=${K6_MAX_RETRY_AFTER_SLEEP_MS}"
  echo "[k6-transaction-100m] retry-after adaptive pacing=${K6_RETRY_AFTER_ADAPTIVE_PACING} max multiplier=${K6_RETRY_AFTER_ADAPTIVE_MAX_MULTIPLIER}"
  echo "[k6-transaction-100m] preemptive pacing=${K6_PREEMPTIVE_PACING} rps=${K6_PREEMPTIVE_PACING_RPS} max sleep ms=${K6_PREEMPTIVE_PACING_MAX_SLEEP_MS} jitter ms=${K6_PREEMPTIVE_PACING_JITTER_MS}"
  echo "[k6-transaction-100m] overload 429 rate threshold=${K6_OVERLOAD_429_RATE_THRESHOLD}"
  echo "[k6-transaction-100m] burst 429 rate threshold=${K6_BURST_429_RATE_THRESHOLD}"
  echo "[k6-transaction-100m] overload 503 rate threshold=${K6_OVERLOAD_503_RATE_THRESHOLD}"
  echo "[k6-transaction-100m] run purpose=${K6_RUN_PURPOSE}"
  echo "[k6-transaction-100m] summary gate=${K6_SUMMARY_GATE}"
  echo "[k6-transaction-100m] archive failed summary=${K6_ARCHIVE_FAILED_SUMMARY}"
  if [[ "${K6_GENERATOR_MODE}" == "docker-context" && "${K6_REMOTE_PREFLIGHT}" == "true" ]]; then
    echo "[k6-transaction-100m] backend readiness gate=remote-preflight base=${K6_REMOTE_BASE_URL} path=${K6_REMOTE_READINESS_PATH} timeout=${K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS}"
  else
    echo "[k6-transaction-100m] backend readiness gate=${K6_BACKEND_READINESS_GATE} base=${K6_BACKEND_READINESS_BASE_URL} path=${K6_BACKEND_READINESS_PATH} timeout=${K6_BACKEND_READINESS_TIMEOUT_SECONDS}"
  fi
  echo "[k6-transaction-100m] postgres health gate=${K6_POSTGRES_HEALTH_GATE} required_status=healthy"
  echo "[k6-transaction-100m] postgres recovery gate=${K6_POSTGRES_RECOVERY_GATE} stable_seconds=${K6_POSTGRES_RECOVERY_STABLE_SECONDS}"
  echo "[k6-transaction-100m] postgres recovery noise window seconds=${K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS}"
  echo "[k6-transaction-100m] postgres exporter stable gate=${K6_POSTGRES_EXPORTER_STABLE_GATE} timeout=${K6_POSTGRES_EXPORTER_STABLE_TIMEOUT_SECONDS}"
  echo "[k6-transaction-100m] generator mode=${K6_GENERATOR_MODE}"
  if [[ "${K6_GENERATOR_MODE}" == "docker-context" ]]; then
    echo "[k6-transaction-100m] generator runner=docker --context ${K6_DOCKER_CONTEXT} run grafana/k6:0.54.0"
    echo "[k6-transaction-100m] remote base url=${K6_REMOTE_BASE_URL}"
    if [[ "${K6_OBSERVABILITY_MODE}" == "prometheus" ]]; then
      echo "[k6-transaction-100m] remote prometheus rw=${K6_REMOTE_PROMETHEUS_RW_SERVER_URL}"
    else
      echo "[k6-transaction-100m] remote prometheus rw=disabled"
    fi
    echo "[k6-transaction-100m] remote workdir=${K6_REMOTE_WORKDIR}"
    echo "[k6-transaction-100m] remote preflight=${K6_REMOTE_PREFLIGHT} timeout=${K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS} readiness_path=${K6_REMOTE_READINESS_PATH}"
    echo "[k6-transaction-100m] remote preflight image=${K6_REMOTE_PREFLIGHT_IMAGE}"
    echo "[k6-transaction-100m] remote artifact image=${K6_REMOTE_ARTIFACT_IMAGE}"
    echo "[k6-transaction-100m] remote artifact collect=${K6_REMOTE_COLLECT_ARTIFACTS}"
    echo "[k6-transaction-100m] remote summary local=${summary_md%summary.md}summary.{md,json}"
    if [[ "${K6_OBSERVABILITY_MODE}" == "prometheus" ]]; then
      echo "[k6-transaction-100m] remote prometheus preflight=enabled"
    else
      echo "[k6-transaction-100m] remote prometheus preflight=disabled"
    fi
  else
    if [[ "${K6_OBSERVABILITY_MODE}" == "prometheus" ]]; then
      echo "[k6-transaction-100m] generator runner=docker compose service k6-transaction-read-100m"
    else
      echo "[k6-transaction-100m] generator runner=docker compose service k6-transaction-read-100m without prometheus remote-write"
    fi
  fi
  echo "[k6-transaction-100m] preflight=${K6_PREFLIGHT}"
  echo "[k6-transaction-100m] outbox_preflight=${K6_OUTBOX_PREFLIGHT}"
  echo "[k6-transaction-100m] outbox_preflight_base_url=${K6_OUTBOX_PREFLIGHT_BASE_URL}"
  echo "[k6-transaction-100m] explain_snapshot=${K6_EXPLAIN_SNAPSHOT}"
  if [[ "${run_dependencies}" == "true" ]]; then
    echo "[k6-transaction-100m] dependencies=compose-default"
  else
    echo "[k6-transaction-100m] dependencies=no-deps"
  fi
  echo "[k6-transaction-100m] required dataset: prepared 100m transaction read model hot/cold accounts"
  echo "[k6-transaction-100m] archive results: ${K6_ARCHIVE_RESULTS}"
  echo "[k6-transaction-100m] archive output dir=${archive_output_dir}"
}

print_plan

if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "assert-summary" ]]; then
  assert_k6_summary_gate "${summary_gate_json}" "${summary_gate_log}" 0
  exit $?
fi

if [[ -z "${K6_HOT_ACCOUNT_ID:-}" && -z "${K6_HOT_ACCOUNT_IDS:-}" ]]; then
  echo "K6_HOT_ACCOUNT_ID or K6_HOT_ACCOUNT_IDS is required" >&2
  exit 1
fi
require_env K6_HOT_FROM
require_env K6_HOT_TO
if [[ -z "${K6_COLD_ACCOUNT_ID:-}" && -z "${K6_COLD_ACCOUNT_IDS:-}" ]]; then
  echo "K6_COLD_ACCOUNT_ID or K6_COLD_ACCOUNT_IDS is required" >&2
  exit 1
fi
require_env K6_COLD_FROM
require_env K6_COLD_TO

mkdir -p "${report_dir}"

write_run_context() {
  {
    echo "K6_RUN_ID=${K6_RUN_ID}"
    echo "K6_REPORT_NAME=${K6_REPORT_NAME}"
    echo "K6_RUN_PURPOSE=${K6_RUN_PURPOSE}"
    echo "K6_SCENARIO_MODE=${K6_SCENARIO_MODE}"
    echo "K6_WARMUP_MODE=${K6_WARMUP_MODE}"
    echo "K6_WARMUP_RATE=${K6_WARMUP_RATE}"
    echo "K6_WARMUP_TIME_UNIT=${K6_WARMUP_TIME_UNIT}"
    echo "K6_WORKLOAD_SHAPE=${K6_WORKLOAD_SHAPE}"
    echo "K6_WORKLOAD_SEED=${K6_WORKLOAD_SEED}"
    echo "K6_WORKLOAD_WEIGHTS=${K6_WORKLOAD_WEIGHTS}"
    echo "K6_HOT_ACCOUNT_IDS=${K6_HOT_ACCOUNT_IDS:-${K6_HOT_ACCOUNT_ID:-}}"
    echo "K6_COLD_ACCOUNT_IDS=${K6_COLD_ACCOUNT_IDS:-${K6_COLD_ACCOUNT_ID:-}}"
    echo "K6_HOT_DEEP_CURSOR=${K6_HOT_DEEP_CURSOR_BOOKED_AT}|${K6_HOT_DEEP_CURSOR_ID}"
    echo "K6_COLD_DEEP_CURSOR=${K6_COLD_DEEP_CURSOR_BOOKED_AT}|${K6_COLD_DEEP_CURSOR_ID}"
    echo "RECOVERY_GATE=${K6_POSTGRES_RECOVERY_GATE}"
    echo "RECOVERY_STABLE_SECONDS=${K6_POSTGRES_RECOVERY_STABLE_SECONDS}"
    echo "RECOVERY_NOISE_WINDOW_SECONDS=${K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS}"
    echo "BURST_HEADROOM_PREFLIGHT=${K6_BURST_HEADROOM_PREFLIGHT}"
    echo "BURST_MIN_HEADROOM_VUS=${K6_BURST_MIN_HEADROOM_VUS}"
    echo "PREEMPTIVE_PACING=${K6_PREEMPTIVE_PACING}"
    echo "PREEMPTIVE_PACING_RPS=${K6_PREEMPTIVE_PACING_RPS}"
    echo "POSTGRES_CONTAINER=${loadtest_postgres_container}"
    echo "SUMMARY_JSON=${summary_json}"
    echo "RUNNER_LOG=${k6_runner_log}"
  } >"${run_context_env}"
  echo "[k6-transaction-100m] run context written=${run_context_env}"
}

write_run_context

assert_k6_preflight() {
  if [[ "${K6_PREFLIGHT}" != "true" ]]; then
    echo "[k6-transaction-100m] preflight skipped"
    return 0
  fi

  local oom_killed
  oom_killed="$(docker inspect "${loadtest_postgres_container}" --format '{{.State.OOMKilled}}' 2>/dev/null || echo unknown)"
  if [[ "${oom_killed}" == "true" ]]; then
    echo "PostgreSQL container has OOMKilled=true. Recreate postgres before running k6." >&2
    exit 1
  fi

  assert_postgres_health_preflight
  assert_postgres_recovery_preflight

  local missing_indexes
  missing_indexes="$("${psql_base[@]}" --no-align --tuples-only --command "
    WITH required(index_name) AS (
      VALUES
        ('idx_transaction_read_model_account_cursor'),
        ('idx_transaction_read_model_archive_account_cursor')
    )
    SELECT index_name
    FROM required
    WHERE to_regclass('public.' || index_name) IS NULL
    ORDER BY index_name;
  ")"
  if [[ -n "${missing_indexes}" ]]; then
    echo "Required transaction read indexes are missing:" >&2
    echo "${missing_indexes}" >&2
    exit 1
  fi

  wait_for_postgres_exporter_stability
}

assert_postgres_health_preflight() {
  if [[ "${K6_POSTGRES_HEALTH_GATE}" != "true" ]]; then
    echo "[k6-transaction-100m] postgres health gate skipped"
    return 0
  fi

  local health_status
  health_status="$(docker inspect "${loadtest_postgres_container}" --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' 2>/dev/null || echo missing)"
  if [[ "${health_status}" != "healthy" ]]; then
    echo "postgres health status must be healthy: actual=${health_status}" >&2
    exit 1
  fi
}

query_postgres_recovery_state() {
  local output
  if ! output="$("${psql_base[@]}" --no-align --tuples-only --command "SELECT pg_is_in_recovery();" 2>&1)"; then
    echo "${output}"
    return 1
  fi
  tr -d '[:space:]' <<<"${output}"
}

assert_postgres_recovery_preflight() {
  if [[ "${K6_POSTGRES_RECOVERY_GATE}" != "true" ]]; then
    echo "[k6-transaction-100m] postgres recovery gate skipped"
    return 0
  fi

  local in_recovery
  if ! in_recovery="$(query_postgres_recovery_state)"; then
    echo "PostgreSQL recovery preflight query failed: ${in_recovery}" >&2
    exit 1
  fi
  if [[ "${in_recovery}" != "f" ]]; then
    echo "pg_is_in_recovery()=false required before k6: actual=${in_recovery}" >&2
    exit 1
  fi
  if ((K6_POSTGRES_RECOVERY_STABLE_SECONDS > 0)); then
    sleep "${K6_POSTGRES_RECOVERY_STABLE_SECONDS}"
    if ! in_recovery="$(query_postgres_recovery_state)"; then
      echo "PostgreSQL recovery preflight recheck failed: ${in_recovery}" >&2
      exit 1
    fi
    if [[ "${in_recovery}" != "f" ]]; then
      echo "pg_is_in_recovery()=false required after stable wait: actual=${in_recovery}" >&2
      exit 1
    fi
  fi
  echo "[k6-transaction-100m] pg_is_in_recovery()=false stable_seconds=${K6_POSTGRES_RECOVERY_STABLE_SECONDS}"
}

wait_for_postgres_recovery_noise_window() {
  if [[ "${K6_POSTGRES_RECOVERY_GATE}" != "true" || "${K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS}" -eq 0 ]]; then
    return 0
  fi
  echo "[k6-transaction-100m] isolating post-crash recovery noise window seconds=${K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS}"
  sleep "${K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS}"
}

wait_for_postgres_exporter_stability() {
  if [[ "${K6_OBSERVABILITY_MODE}" != "prometheus" ]]; then
    echo "[k6-transaction-100m] postgres exporter stable gate skipped for summary-only"
    return 0
  fi
  if [[ "${K6_POSTGRES_EXPORTER_STABLE_GATE}" != "true" ]]; then
    echo "[k6-transaction-100m] postgres exporter stable gate skipped"
    return 0
  fi

  require_command curl
  local url="http://localhost:${loadtest_postgres_exporter_port}/metrics"
  local deadline=$((SECONDS + K6_POSTGRES_EXPORTER_STABLE_TIMEOUT_SECONDS))
  local metrics
  echo "[k6-transaction-100m] waiting postgres exporter stability: ${url}"
  while ((SECONDS < deadline)); do
    if metrics="$(curl -fsS --max-time 2 "${url}" 2>/dev/null)" \
      && awk '$1 ~ /^pg_up/ && $2 == 1 {found=1} END {exit !found}' <<<"${metrics}"; then
      return 0
    fi
    sleep 2
  done
  echo "postgres exporter pg_up did not become stable: ${url}" >&2
  exit 1
}

run_outbox_preflight() {
  if [[ "${K6_OUTBOX_PREFLIGHT}" != "true" ]]; then
    echo "[k6-transaction-100m] outbox preflight skipped"
    return 0
  fi
  OUTBOX_LOCAL_BUILD_BACKEND=false \
  OUTBOX_BACKLOG_BASE_URL="${K6_OUTBOX_PREFLIGHT_BASE_URL}" \
    tools/test/run-outbox-provider-backlog-local-gate.sh
}

wait_for_backend_readiness() {
  if [[ "${K6_GENERATOR_MODE}" == "docker-context" && "${K6_REMOTE_PREFLIGHT}" == "true" ]]; then
    echo "[k6-transaction-100m] backend readiness skipped for docker-context; remote preflight owns readiness"
    return 0
  fi
  if [[ "${K6_BACKEND_READINESS_GATE}" != "true" ]]; then
    echo "[k6-transaction-100m] backend readiness skipped"
    return 0
  fi

  require_command curl
  local url="${K6_BACKEND_READINESS_BASE_URL%/}${K6_BACKEND_READINESS_PATH}"
  local deadline=$((SECONDS + K6_BACKEND_READINESS_TIMEOUT_SECONDS))
  echo "[k6-transaction-100m] waiting backend readiness: ${url}"
  while ((SECONDS < deadline)); do
    if curl -fsS --max-time 2 "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "backend readiness timeout: ${url}" >&2
  exit 1
}

assert_remote_k6_preflight() {
  if [[ "${K6_GENERATOR_MODE}" != "docker-context" ]]; then
    return 0
  fi
  if [[ "${K6_REMOTE_PREFLIGHT}" != "true" ]]; then
    echo "[k6-transaction-100m] remote k6 preflight skipped"
    return 0
  fi

  local readiness_url="${K6_REMOTE_BASE_URL%/}${K6_REMOTE_READINESS_PATH}"
  echo "[k6-transaction-100m] remote docker context preflight: ${K6_DOCKER_CONTEXT}"
  docker --context "${K6_DOCKER_CONTEXT}" info >/dev/null
  echo "[k6-transaction-100m] remote backend readiness preflight: ${readiness_url}"
  docker --context "${K6_DOCKER_CONTEXT}" run --rm "${K6_REMOTE_PREFLIGHT_IMAGE}" \
    -fsS --max-time "${K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS}" "${readiness_url}" >/dev/null

  if [[ "${K6_OBSERVABILITY_MODE}" == "prometheus" ]]; then
    echo "[k6-transaction-100m] remote prometheus remote-write preflight: ${K6_REMOTE_PROMETHEUS_RW_SERVER_URL}"
    docker --context "${K6_DOCKER_CONTEXT}" run --rm --entrypoint sh "${K6_REMOTE_PREFLIGHT_IMAGE}" \
      -c 'status="$(curl -sS -o /dev/null -w "%{http_code}" --max-time "$1" -X POST "$2" || echo 000)"; case "${status}" in 2*|3*|4*) exit 0 ;; *) echo "remote prometheus remote-write preflight failed: status=${status}" >&2; exit 1 ;; esac' \
      sh "${K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS}" "${K6_REMOTE_PROMETHEUS_RW_SERVER_URL}"
  fi
}

run_explain_snapshot() {
  local phase="$1"
  if [[ "${K6_EXPLAIN_SNAPSHOT}" != "true" ]]; then
    echo "[k6-transaction-100m] explain snapshot skipped phase=${phase}"
    return 0
  fi
  K6_EXPLAIN_PHASE="${phase}" tools/test/run-transaction-100m-k6-explain-snapshot.sh
}

stop_backend_before_bootjar() {
  # host jar 교체 중 실행 중인 JVM이 classpath를 다시 읽지 않도록 backend만 먼저 내립니다.
  docker compose "${compose_files[@]}" --profile loadtest stop aquila-bank-backend >/dev/null 2>&1 || true
}

if [[ "${mode}" != "no-up" ]]; then
  stop_backend_before_bootjar

  echo "[k6-transaction-100m] building backend bootJar"
  tools/test/with-resource-lock.sh back-gradle-loadtest-bootjar ./back/gradlew -p back bootJar

  echo "[k6-transaction-100m] starting loadtest services"
  docker compose "${compose_files[@]}" --profile loadtest up -d postgres
  docker compose "${compose_files[@]}" --profile loadtest up -d --force-recreate aquila-bank-backend
  if [[ "${K6_OBSERVABILITY_MODE}" == "prometheus" ]]; then
    docker compose "${compose_files[@]}" --profile loadtest up -d \
      prometheus grafana alertmanager postgres-exporter
  fi
fi

wait_for_backend_readiness
assert_k6_preflight
wait_for_postgres_recovery_noise_window
run_outbox_preflight
assert_remote_k6_preflight
run_explain_snapshot pre

run_k6_local() {
  local run_args
  local k6_command
  run_args=(--rm)
  if [[ "${run_dependencies}" != "true" ]]; then
    run_args+=(--no-deps)
  fi
  k6_command=(run)
  if [[ "${K6_OBSERVABILITY_MODE}" == "prometheus" ]]; then
    k6_command+=(--out experimental-prometheus-rw)
  fi
  k6_command+=(/scripts/transaction-read-100m.js)

  # summary-only는 k6 결과 파일만 남겨 same-host observability 비용을 제외합니다.
  docker compose "${compose_files[@]}" --profile loadtest run "${run_args[@]}" \
    -e K6_REPORT_NAME="${K6_REPORT_NAME}" \
    -e K6_RUN_ID="${K6_RUN_ID}" \
    -e BASE_URL="${K6_BASE_URL:-}" \
    -e K6_OBSERVABILITY_MODE="${K6_OBSERVABILITY_MODE}" \
    -e K6_PROMETHEUS_RW_TREND_STATS="p(50),p(90),p(95),p(99),p(99.9),min,max,avg" \
    -e AQUILA_K6_SCENARIO_MODE="${K6_SCENARIO_MODE}" \
    -e AQUILA_K6_RATE="${K6_RATE}" \
    -e AQUILA_K6_TIME_UNIT="${K6_TIME_UNIT}" \
    -e AQUILA_K6_PRE_ALLOCATED_VUS="${K6_PRE_ALLOCATED_VUS}" \
    -e AQUILA_K6_MAX_VUS="${K6_MAX_VUS}" \
    -e AQUILA_K6_BURST_RATE="${K6_BURST_RATE}" \
    -e AQUILA_K6_BURST_DURATION="${K6_BURST_DURATION}" \
    -e AQUILA_K6_WARMUP_DURATION="${K6_WARMUP_DURATION}" \
    -e AQUILA_K6_WARMUP_MODE="${K6_WARMUP_MODE}" \
    -e AQUILA_K6_WARMUP_RATE="${K6_WARMUP_RATE}" \
    -e AQUILA_K6_WARMUP_TIME_UNIT="${K6_WARMUP_TIME_UNIT}" \
    -e AQUILA_K6_WARMUP_PRE_ALLOCATED_VUS="${K6_WARMUP_PRE_ALLOCATED_VUS}" \
    -e AQUILA_K6_WARMUP_MAX_VUS="${K6_WARMUP_MAX_VUS}" \
    -e K6_HOT_ACCOUNT_ID="${K6_HOT_ACCOUNT_ID}" \
    -e K6_HOT_ACCOUNT_IDS="${K6_HOT_ACCOUNT_IDS}" \
    -e K6_HOT_FROM="${K6_HOT_FROM}" \
    -e K6_HOT_TO="${K6_HOT_TO}" \
    -e K6_HOT_DEEP_CURSOR_BOOKED_AT="${K6_HOT_DEEP_CURSOR_BOOKED_AT}" \
    -e K6_HOT_DEEP_CURSOR_ID="${K6_HOT_DEEP_CURSOR_ID}" \
    -e K6_COLD_ACCOUNT_ID="${K6_COLD_ACCOUNT_ID}" \
    -e K6_COLD_ACCOUNT_IDS="${K6_COLD_ACCOUNT_IDS}" \
    -e K6_COLD_FROM="${K6_COLD_FROM}" \
    -e K6_COLD_TO="${K6_COLD_TO}" \
    -e K6_COLD_DEEP_CURSOR_BOOKED_AT="${K6_COLD_DEEP_CURSOR_BOOKED_AT}" \
    -e K6_COLD_DEEP_CURSOR_ID="${K6_COLD_DEEP_CURSOR_ID}" \
    -e K6_AUTH_TOKEN="${K6_AUTH_TOKEN:-}" \
    -e AQUILA_K6_VUS="${K6_VUS}" \
    -e AQUILA_K6_DURATION="${K6_DURATION:-1m}" \
    -e K6_LIMIT="${K6_LIMIT}" \
    -e K6_HOT_P95_THRESHOLD_MS="${K6_HOT_P95_THRESHOLD_MS}" \
    -e K6_COLD_P95_THRESHOLD_MS="${K6_COLD_P95_THRESHOLD_MS}" \
    -e K6_HOT_P99_THRESHOLD_MS="${K6_HOT_P99_THRESHOLD_MS}" \
    -e K6_COLD_P99_THRESHOLD_MS="${K6_COLD_P99_THRESHOLD_MS}" \
    -e K6_HOT_P999_THRESHOLD_MS="${K6_HOT_P999_THRESHOLD_MS}" \
    -e K6_COLD_P999_THRESHOLD_MS="${K6_COLD_P999_THRESHOLD_MS}" \
    -e K6_HOT_MAX_THRESHOLD_MS="${K6_HOT_MAX_THRESHOLD_MS}" \
    -e K6_COLD_MAX_THRESHOLD_MS="${K6_COLD_MAX_THRESHOLD_MS}" \
    -e K6_HOT_DEEP_P95_THRESHOLD_MS="${K6_HOT_DEEP_P95_THRESHOLD_MS}" \
    -e K6_COLD_DEEP_P95_THRESHOLD_MS="${K6_COLD_DEEP_P95_THRESHOLD_MS}" \
    -e K6_HOT_DEEP_P99_THRESHOLD_MS="${K6_HOT_DEEP_P99_THRESHOLD_MS}" \
    -e K6_COLD_DEEP_P99_THRESHOLD_MS="${K6_COLD_DEEP_P99_THRESHOLD_MS}" \
    -e K6_HOT_DEEP_P999_THRESHOLD_MS="${K6_HOT_DEEP_P999_THRESHOLD_MS}" \
    -e K6_COLD_DEEP_P999_THRESHOLD_MS="${K6_COLD_DEEP_P999_THRESHOLD_MS}" \
    -e K6_HOT_DEEP_MAX_THRESHOLD_MS="${K6_HOT_DEEP_MAX_THRESHOLD_MS}" \
    -e K6_COLD_DEEP_MAX_THRESHOLD_MS="${K6_COLD_DEEP_MAX_THRESHOLD_MS}" \
    -e K6_HTTP_FAILED_RATE="${K6_HTTP_FAILED_RATE}" \
    -e K6_OVERLOAD_MODE="${K6_OVERLOAD_MODE}" \
    -e K6_OVERLOAD_429_RATE_THRESHOLD="${K6_OVERLOAD_429_RATE_THRESHOLD}" \
    -e K6_BURST_429_RATE_THRESHOLD="${K6_BURST_429_RATE_THRESHOLD}" \
    -e K6_OVERLOAD_503_RATE_THRESHOLD="${K6_OVERLOAD_503_RATE_THRESHOLD}" \
    -e K6_MAX_RETRY_AFTER_SLEEP_SECONDS="${K6_MAX_RETRY_AFTER_SLEEP_SECONDS}" \
    -e K6_MAX_RETRY_AFTER_SLEEP_MS="${K6_MAX_RETRY_AFTER_SLEEP_MS}" \
    -e K6_RETRY_AFTER_ADAPTIVE_PACING="${K6_RETRY_AFTER_ADAPTIVE_PACING}" \
    -e K6_RETRY_AFTER_ADAPTIVE_MAX_MULTIPLIER="${K6_RETRY_AFTER_ADAPTIVE_MAX_MULTIPLIER}" \
    -e K6_PREEMPTIVE_PACING="${K6_PREEMPTIVE_PACING}" \
    -e K6_PREEMPTIVE_PACING_RPS="${K6_PREEMPTIVE_PACING_RPS}" \
    -e K6_PREEMPTIVE_PACING_MAX_SLEEP_MS="${K6_PREEMPTIVE_PACING_MAX_SLEEP_MS}" \
    -e K6_PREEMPTIVE_PACING_JITTER_MS="${K6_PREEMPTIVE_PACING_JITTER_MS}" \
    -e K6_WORKLOAD_SHAPE="${K6_WORKLOAD_SHAPE}" \
    -e K6_WORKLOAD_SEED="${K6_WORKLOAD_SEED}" \
    -e K6_WORKLOAD_WEIGHTS="${K6_WORKLOAD_WEIGHTS}" \
    k6-transaction-read-100m \
    "${k6_command[@]}"
}

run_k6_docker_context() {
  local remote_report_dir="${K6_REMOTE_WORKDIR}/build/reports/k6"
  local k6_command
  k6_command=(run)
  if [[ "${K6_OBSERVABILITY_MODE}" == "prometheus" ]]; then
    k6_command+=(--out experimental-prometheus-rw)
  fi
  k6_command+=(/scripts/transaction-read-100m.js)

  # backend/PostgreSQL CPU와 k6 CPU를 분리하기 위한 별도 Docker context 실행 경로.
  echo "[k6-transaction-100m] running remote k6 generator on docker context ${K6_DOCKER_CONTEXT}"
  echo "[k6-transaction-100m] remote reports: ${remote_report_dir}/${K6_REPORT_NAME}-summary.{md,json}"

  docker --context "${K6_DOCKER_CONTEXT}" run --rm \
    -e BASE_URL="${K6_REMOTE_BASE_URL}" \
    -e K6_PROMETHEUS_RW_SERVER_URL="${K6_REMOTE_PROMETHEUS_RW_SERVER_URL}" \
    -e K6_PROMETHEUS_RW_TREND_STATS="p(50),p(90),p(95),p(99),p(99.9),min,max,avg" \
    -e K6_REPORT_NAME="${K6_REPORT_NAME}" \
    -e K6_RUN_ID="${K6_RUN_ID}" \
    -e K6_OBSERVABILITY_MODE="${K6_OBSERVABILITY_MODE}" \
    -e AQUILA_K6_SCENARIO_MODE="${K6_SCENARIO_MODE}" \
    -e AQUILA_K6_RATE="${K6_RATE}" \
    -e AQUILA_K6_TIME_UNIT="${K6_TIME_UNIT}" \
    -e AQUILA_K6_PRE_ALLOCATED_VUS="${K6_PRE_ALLOCATED_VUS}" \
    -e AQUILA_K6_MAX_VUS="${K6_MAX_VUS}" \
    -e AQUILA_K6_BURST_RATE="${K6_BURST_RATE}" \
    -e AQUILA_K6_BURST_DURATION="${K6_BURST_DURATION}" \
    -e AQUILA_K6_WARMUP_DURATION="${K6_WARMUP_DURATION}" \
    -e AQUILA_K6_WARMUP_MODE="${K6_WARMUP_MODE}" \
    -e AQUILA_K6_WARMUP_RATE="${K6_WARMUP_RATE}" \
    -e AQUILA_K6_WARMUP_TIME_UNIT="${K6_WARMUP_TIME_UNIT}" \
    -e AQUILA_K6_WARMUP_PRE_ALLOCATED_VUS="${K6_WARMUP_PRE_ALLOCATED_VUS}" \
    -e AQUILA_K6_WARMUP_MAX_VUS="${K6_WARMUP_MAX_VUS}" \
    -e K6_HOT_ACCOUNT_ID="${K6_HOT_ACCOUNT_ID}" \
    -e K6_HOT_ACCOUNT_IDS="${K6_HOT_ACCOUNT_IDS}" \
    -e K6_HOT_FROM="${K6_HOT_FROM}" \
    -e K6_HOT_TO="${K6_HOT_TO}" \
    -e K6_HOT_DEEP_CURSOR_BOOKED_AT="${K6_HOT_DEEP_CURSOR_BOOKED_AT}" \
    -e K6_HOT_DEEP_CURSOR_ID="${K6_HOT_DEEP_CURSOR_ID}" \
    -e K6_COLD_ACCOUNT_ID="${K6_COLD_ACCOUNT_ID}" \
    -e K6_COLD_ACCOUNT_IDS="${K6_COLD_ACCOUNT_IDS}" \
    -e K6_COLD_FROM="${K6_COLD_FROM}" \
    -e K6_COLD_TO="${K6_COLD_TO}" \
    -e K6_COLD_DEEP_CURSOR_BOOKED_AT="${K6_COLD_DEEP_CURSOR_BOOKED_AT}" \
    -e K6_COLD_DEEP_CURSOR_ID="${K6_COLD_DEEP_CURSOR_ID}" \
    -e K6_AUTH_TOKEN="${K6_AUTH_TOKEN:-}" \
    -e AQUILA_K6_VUS="${K6_VUS}" \
    -e AQUILA_K6_DURATION="${K6_DURATION:-1m}" \
    -e K6_LIMIT="${K6_LIMIT}" \
    -e K6_HOT_P95_THRESHOLD_MS="${K6_HOT_P95_THRESHOLD_MS}" \
    -e K6_COLD_P95_THRESHOLD_MS="${K6_COLD_P95_THRESHOLD_MS}" \
    -e K6_HOT_P99_THRESHOLD_MS="${K6_HOT_P99_THRESHOLD_MS}" \
    -e K6_COLD_P99_THRESHOLD_MS="${K6_COLD_P99_THRESHOLD_MS}" \
    -e K6_HOT_P999_THRESHOLD_MS="${K6_HOT_P999_THRESHOLD_MS}" \
    -e K6_COLD_P999_THRESHOLD_MS="${K6_COLD_P999_THRESHOLD_MS}" \
    -e K6_HOT_MAX_THRESHOLD_MS="${K6_HOT_MAX_THRESHOLD_MS}" \
    -e K6_COLD_MAX_THRESHOLD_MS="${K6_COLD_MAX_THRESHOLD_MS}" \
    -e K6_HOT_DEEP_P95_THRESHOLD_MS="${K6_HOT_DEEP_P95_THRESHOLD_MS}" \
    -e K6_COLD_DEEP_P95_THRESHOLD_MS="${K6_COLD_DEEP_P95_THRESHOLD_MS}" \
    -e K6_HOT_DEEP_P99_THRESHOLD_MS="${K6_HOT_DEEP_P99_THRESHOLD_MS}" \
    -e K6_COLD_DEEP_P99_THRESHOLD_MS="${K6_COLD_DEEP_P99_THRESHOLD_MS}" \
    -e K6_HOT_DEEP_P999_THRESHOLD_MS="${K6_HOT_DEEP_P999_THRESHOLD_MS}" \
    -e K6_COLD_DEEP_P999_THRESHOLD_MS="${K6_COLD_DEEP_P999_THRESHOLD_MS}" \
    -e K6_HOT_DEEP_MAX_THRESHOLD_MS="${K6_HOT_DEEP_MAX_THRESHOLD_MS}" \
    -e K6_COLD_DEEP_MAX_THRESHOLD_MS="${K6_COLD_DEEP_MAX_THRESHOLD_MS}" \
    -e K6_HTTP_FAILED_RATE="${K6_HTTP_FAILED_RATE}" \
    -e K6_OVERLOAD_MODE="${K6_OVERLOAD_MODE}" \
    -e K6_OVERLOAD_429_RATE_THRESHOLD="${K6_OVERLOAD_429_RATE_THRESHOLD}" \
    -e K6_BURST_429_RATE_THRESHOLD="${K6_BURST_429_RATE_THRESHOLD}" \
    -e K6_OVERLOAD_503_RATE_THRESHOLD="${K6_OVERLOAD_503_RATE_THRESHOLD}" \
    -e K6_MAX_RETRY_AFTER_SLEEP_SECONDS="${K6_MAX_RETRY_AFTER_SLEEP_SECONDS}" \
    -e K6_MAX_RETRY_AFTER_SLEEP_MS="${K6_MAX_RETRY_AFTER_SLEEP_MS}" \
    -e K6_RETRY_AFTER_ADAPTIVE_PACING="${K6_RETRY_AFTER_ADAPTIVE_PACING}" \
    -e K6_RETRY_AFTER_ADAPTIVE_MAX_MULTIPLIER="${K6_RETRY_AFTER_ADAPTIVE_MAX_MULTIPLIER}" \
    -e K6_PREEMPTIVE_PACING="${K6_PREEMPTIVE_PACING}" \
    -e K6_PREEMPTIVE_PACING_RPS="${K6_PREEMPTIVE_PACING_RPS}" \
    -e K6_PREEMPTIVE_PACING_MAX_SLEEP_MS="${K6_PREEMPTIVE_PACING_MAX_SLEEP_MS}" \
    -e K6_PREEMPTIVE_PACING_JITTER_MS="${K6_PREEMPTIVE_PACING_JITTER_MS}" \
    -e K6_WORKLOAD_SHAPE="${K6_WORKLOAD_SHAPE}" \
    -e K6_WORKLOAD_SEED="${K6_WORKLOAD_SEED}" \
    -e K6_WORKLOAD_WEIGHTS="${K6_WORKLOAD_WEIGHTS}" \
    -v "${K6_REMOTE_WORKDIR}/ops/k6:/scripts:ro" \
    -v "${remote_report_dir}:/reports" \
    grafana/k6:0.54.0 \
    "${k6_command[@]}"
}

remote_report_dir() {
  echo "${K6_REMOTE_WORKDIR}/build/reports/k6"
}

normalize_remote_report_permissions() {
  if [[ "${K6_GENERATOR_MODE}" != "docker-context" || "${K6_REMOTE_COLLECT_ARTIFACTS}" != "true" ]]; then
    return 0
  fi

  # remote Docker host bind mount 소유자가 달라도 local 수집 container가 읽을 수 있게 둡니다.
  docker --context "${K6_DOCKER_CONTEXT}" run --rm \
    -v "$(remote_report_dir):/reports" \
    --entrypoint sh "${K6_REMOTE_ARTIFACT_IMAGE}" \
    -c 'chmod -R a+rX /reports 2>/dev/null || true' >/dev/null
}

collect_remote_artifact_file() {
  local remote_file="$1"
  local local_file="$2"
  local temp_file="${local_file}.tmp"

  if ! docker --context "${K6_DOCKER_CONTEXT}" run --rm \
    -v "$(remote_report_dir):/reports:ro" \
    --entrypoint sh "${K6_REMOTE_ARTIFACT_IMAGE}" \
    -c 'test -s "/reports/$1" && cat "/reports/$1"' \
    sh "${remote_file}" >"${temp_file}"; then
    rm -f "${temp_file}"
    return 1
  fi
  if [[ ! -s "${temp_file}" ]]; then
    rm -f "${temp_file}"
    return 1
  fi
  mv "${temp_file}" "${local_file}"
}

collect_remote_k6_artifacts() {
  local current_status="$1"
  local result_status="${current_status}"

  if [[ "${K6_GENERATOR_MODE}" != "docker-context" || "${K6_REMOTE_COLLECT_ARTIFACTS}" != "true" ]]; then
    return "${result_status}"
  fi

  mkdir -p "${report_dir}"
  normalize_remote_report_permissions

  if ! collect_remote_artifact_file "${K6_REPORT_NAME}-summary.md" "${summary_md}"; then
    echo "remote k6 summary markdown collection failed: $(remote_report_dir)/${K6_REPORT_NAME}-summary.md" >&2
    result_status=1
  fi
  if ! collect_remote_artifact_file "${K6_REPORT_NAME}-summary.json" "${summary_json}"; then
    echo "remote k6 summary JSON collection failed: $(remote_report_dir)/${K6_REPORT_NAME}-summary.json" >&2
    result_status=1
  fi

  if [[ -s "${summary_md}" && -s "${summary_json}" ]]; then
    echo "[k6-transaction-100m] remote summary collected: ${summary_md} ${summary_json}"
  fi
  return "${result_status}"
}

set +e
if [[ "${K6_GENERATOR_MODE}" == "docker-context" ]]; then
  run_k6_docker_context
else
  run_k6_local
fi 2>&1 | tee "${k6_runner_log}"
status=${PIPESTATUS[0]}
set -e
set +e
collect_remote_k6_artifacts "${status}"
status=$?
set -e
set +e
assert_k6_summary_gate "${summary_json}" "${k6_runner_log}" "${status}"
status=$?
set -e

if ! run_explain_snapshot post; then
  echo "[k6-transaction-100m] post explain snapshot failed; preserving k6 status=${status}" >&2
  if [[ "${status}" -eq 0 ]]; then
    status=1
  fi
fi

archive_requested=false
if [[ "${K6_ARCHIVE_RESULTS}" == "true" ]]; then
  archive_requested=true
fi
if [[ "${K6_ARCHIVE_FAILED_SUMMARY}" == "true" && "${status}" -ne 0 ]]; then
  archive_requested=true
fi

if [[ "${archive_requested}" == "true" && -f "${summary_md}" ]]; then
  PERFORMANCE_RESULT_STATUS="${status}" \
  PERFORMANCE_RESULT_PURPOSE="${K6_RUN_PURPOSE}" \
  PERFORMANCE_RESULT_OUTPUT_DIR="${archive_output_dir}" \
    tools/test/archive-k6-transaction-100m-result.sh "${summary_md}" "${summary_json}"
elif [[ "${archive_requested}" == "true" ]]; then
  echo "k6 summary markdown was not produced: ${summary_md}" >&2
  if [[ "${K6_GENERATOR_MODE}" == "docker-context" ]]; then
    echo "remote generator writes summaries under ${K6_REMOTE_WORKDIR}/build/reports/k6 on docker context ${K6_DOCKER_CONTEXT}" >&2
  fi
fi

exit "${status}"
