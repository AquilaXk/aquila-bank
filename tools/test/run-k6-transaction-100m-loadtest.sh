#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-k6-transaction-100m-loadtest.sh [--print-plan|--no-up] [--no-deps]

Required environment:
  K6_HOT_ACCOUNT_ID
  K6_HOT_FROM
  K6_HOT_TO
  K6_COLD_ACCOUNT_ID
  K6_COLD_FROM
  K6_COLD_TO

Optional environment:
  K6_REPORT_NAME       default transaction-100m-<timestamp>
  K6_VUS               default 8
  K6_DURATION          default 1m
  K6_LIMIT             default 50
  K6_HOT_P99_THRESHOLD_MS default 750
  K6_COLD_P99_THRESHOLD_MS default 1500
  K6_HOT_MAX_THRESHOLD_MS default 3000
  K6_COLD_MAX_THRESHOLD_MS default 5000
  K6_AUTH_TOKEN        bearer token, optional when bootstrap header auth is enabled
  K6_ARCHIVE_RESULTS   copy markdown summary to docs/performance-results, default true
  K6_PREFLIGHT         check PostgreSQL OOM/index readiness before k6, default true
  K6_OBSERVABILITY_MODE prometheus|summary-only, default prometheus
  K6_OVERLOAD_MODE     treat 429 as expected rejected samples, default false
  K6_OVERLOAD_429_RATE_THRESHOLD
                       max 429 rate in overload mode, default 0.05
  K6_MAX_RETRY_AFTER_SLEEP_SECONDS
                       cap Retry-After backoff in overload mode, default 1
  K6_GENERATOR_MODE   local|docker-context, default local
  K6_DOCKER_CONTEXT   docker context for remote k6 generator, required when mode=docker-context
  K6_REMOTE_BASE_URL  backend URL reachable from remote k6, required when mode=docker-context
  K6_REMOTE_PROMETHEUS_RW_SERVER_URL
                       Prometheus remote-write URL reachable from remote k6, required when mode=docker-context
  K6_REMOTE_WORKDIR   repo path visible from docker context host, default current working directory

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

mode="run"
run_dependencies="true"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
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
K6_LIMIT="${K6_LIMIT:-50}"
K6_HOT_P95_THRESHOLD_MS="${K6_HOT_P95_THRESHOLD_MS:-350}"
K6_COLD_P95_THRESHOLD_MS="${K6_COLD_P95_THRESHOLD_MS:-750}"
K6_HOT_P99_THRESHOLD_MS="${K6_HOT_P99_THRESHOLD_MS:-750}"
K6_COLD_P99_THRESHOLD_MS="${K6_COLD_P99_THRESHOLD_MS:-1500}"
K6_HOT_MAX_THRESHOLD_MS="${K6_HOT_MAX_THRESHOLD_MS:-3000}"
K6_COLD_MAX_THRESHOLD_MS="${K6_COLD_MAX_THRESHOLD_MS:-5000}"
K6_HTTP_FAILED_RATE="${K6_HTTP_FAILED_RATE:-0.01}"
K6_ARCHIVE_RESULTS="${K6_ARCHIVE_RESULTS:-true}"
K6_PREFLIGHT="${K6_PREFLIGHT:-true}"
K6_OBSERVABILITY_MODE="${K6_OBSERVABILITY_MODE:-prometheus}"
K6_OVERLOAD_MODE="${K6_OVERLOAD_MODE:-false}"
K6_OVERLOAD_429_RATE_THRESHOLD="${K6_OVERLOAD_429_RATE_THRESHOLD:-0.05}"
K6_MAX_RETRY_AFTER_SLEEP_SECONDS="${K6_MAX_RETRY_AFTER_SLEEP_SECONDS:-1}"
K6_GENERATOR_MODE="${K6_GENERATOR_MODE:-local}"
K6_DOCKER_CONTEXT="${K6_DOCKER_CONTEXT:-}"
K6_REMOTE_BASE_URL="${K6_REMOTE_BASE_URL:-}"
K6_REMOTE_PROMETHEUS_RW_SERVER_URL="${K6_REMOTE_PROMETHEUS_RW_SERVER_URL:-}"
K6_REMOTE_WORKDIR="${K6_REMOTE_WORKDIR:-$(pwd)}"
K6_REPORT_NAME="${K6_REPORT_NAME:-transaction-100m-$(date +%Y-%m-%d-%H%M%S)}"
loadtest_db_port="${LOADTEST_DB_PORT:-15432}"
loadtest_backend_port="${LOADTEST_BACKEND_PORT:-18080}"
loadtest_prometheus_port="${LOADTEST_PROMETHEUS_PORT:-19090}"
loadtest_grafana_port="${LOADTEST_GRAFANA_PORT:-13001}"
loadtest_alertmanager_port="${LOADTEST_ALERTMANAGER_PORT:-19093}"
loadtest_postgres_exporter_port="${LOADTEST_POSTGRES_EXPORTER_PORT:-19187}"
loadtest_postgres_container="${LOADTEST_POSTGRES_CONTAINER_NAME:-aquila-bank-postgres-loadtest}"
loadtest_backend_container="${LOADTEST_BACKEND_CONTAINER_NAME:-aquila-bank-backend-loadtest}"
export K6_VUS K6_LIMIT K6_HOT_P95_THRESHOLD_MS K6_COLD_P95_THRESHOLD_MS K6_HOT_P99_THRESHOLD_MS K6_COLD_P99_THRESHOLD_MS K6_HOT_MAX_THRESHOLD_MS K6_COLD_MAX_THRESHOLD_MS K6_HTTP_FAILED_RATE K6_ARCHIVE_RESULTS K6_PREFLIGHT K6_OBSERVABILITY_MODE K6_OVERLOAD_MODE K6_OVERLOAD_429_RATE_THRESHOLD K6_MAX_RETRY_AFTER_SLEEP_SECONDS K6_GENERATOR_MODE K6_DOCKER_CONTEXT K6_REMOTE_BASE_URL K6_REMOTE_PROMETHEUS_RW_SERVER_URL K6_REMOTE_WORKDIR K6_REPORT_NAME

require_positive_integer K6_VUS
require_positive_integer K6_LIMIT
require_positive_number K6_HOT_P95_THRESHOLD_MS
require_positive_number K6_COLD_P95_THRESHOLD_MS
require_positive_number K6_HOT_P99_THRESHOLD_MS
require_positive_number K6_COLD_P99_THRESHOLD_MS
require_positive_number K6_HOT_MAX_THRESHOLD_MS
require_positive_number K6_COLD_MAX_THRESHOLD_MS
require_rate K6_HTTP_FAILED_RATE
require_non_negative_integer K6_MAX_RETRY_AFTER_SLEEP_SECONDS
require_rate K6_OVERLOAD_429_RATE_THRESHOLD
require_generator_mode
require_observability_mode

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
psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}")

print_plan() {
  echo "[k6-transaction-100m] compose files: ${compose_files[*]}"
  echo "[k6-transaction-100m] backend: aquila-bank-backend:8080 with t3.micro budget"
  echo "[k6-transaction-100m] ports: db=${loadtest_db_port} backend=${loadtest_backend_port} prometheus=${loadtest_prometheus_port} grafana=${loadtest_grafana_port} alertmanager=${loadtest_alertmanager_port} postgres-exporter=${loadtest_postgres_exporter_port}"
  echo "[k6-transaction-100m] containers: postgres=${loadtest_postgres_container} backend=${loadtest_backend_container}"
  if [[ "${K6_OBSERVABILITY_MODE}" == "prometheus" ]]; then
    echo "[k6-transaction-100m] observability: prometheus:9090 grafana:3000 alertmanager:9093 postgres-exporter:9187"
  else
    echo "[k6-transaction-100m] observability: summary-only local markdown/json"
  fi
  echo "[k6-transaction-100m] observability mode=${K6_OBSERVABILITY_MODE}"
  echo "[k6-transaction-100m] k6 report name: ${K6_REPORT_NAME}"
  echo "[k6-transaction-100m] k6 vus=${K6_VUS} duration=${K6_DURATION:-1m} limit=${K6_LIMIT}"
  echo "[k6-transaction-100m] hot p95 threshold ms=${K6_HOT_P95_THRESHOLD_MS}"
  echo "[k6-transaction-100m] cold p95 threshold ms=${K6_COLD_P95_THRESHOLD_MS}"
  echo "[k6-transaction-100m] hot p99 threshold ms=${K6_HOT_P99_THRESHOLD_MS}"
  echo "[k6-transaction-100m] cold p99 threshold ms=${K6_COLD_P99_THRESHOLD_MS}"
  echo "[k6-transaction-100m] hot max threshold ms=${K6_HOT_MAX_THRESHOLD_MS}"
  echo "[k6-transaction-100m] cold max threshold ms=${K6_COLD_MAX_THRESHOLD_MS}"
  echo "[k6-transaction-100m] http failed rate threshold=${K6_HTTP_FAILED_RATE}"
  echo "[k6-transaction-100m] overload mode=${K6_OVERLOAD_MODE} max retry-after sleep seconds=${K6_MAX_RETRY_AFTER_SLEEP_SECONDS}"
  echo "[k6-transaction-100m] overload 429 rate threshold=${K6_OVERLOAD_429_RATE_THRESHOLD}"
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
  else
    if [[ "${K6_OBSERVABILITY_MODE}" == "prometheus" ]]; then
      echo "[k6-transaction-100m] generator runner=docker compose service k6-transaction-read-100m"
    else
      echo "[k6-transaction-100m] generator runner=docker compose service k6-transaction-read-100m without prometheus remote-write"
    fi
  fi
  echo "[k6-transaction-100m] preflight=${K6_PREFLIGHT}"
  if [[ "${run_dependencies}" == "true" ]]; then
    echo "[k6-transaction-100m] dependencies=compose-default"
  else
    echo "[k6-transaction-100m] dependencies=no-deps"
  fi
  echo "[k6-transaction-100m] required dataset: prepared 100m transaction read model hot/cold accounts"
  echo "[k6-transaction-100m] archive results: ${K6_ARCHIVE_RESULTS}"
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

mkdir -p "${report_dir}"

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
}

if [[ "${mode}" != "no-up" ]]; then
  echo "[k6-transaction-100m] building backend bootJar"
  tools/test/with-resource-lock.sh back-gradle-loadtest-bootjar ./back/gradlew -p back bootJar

  echo "[k6-transaction-100m] starting loadtest services"
  services=(postgres aquila-bank-backend)
  if [[ "${K6_OBSERVABILITY_MODE}" == "prometheus" ]]; then
    services+=(prometheus grafana alertmanager postgres-exporter)
  fi
  docker compose "${compose_files[@]}" --profile loadtest up -d "${services[@]}"
fi

assert_k6_preflight

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
    -e K6_OBSERVABILITY_MODE="${K6_OBSERVABILITY_MODE}" \
    -e K6_HOT_ACCOUNT_ID="${K6_HOT_ACCOUNT_ID}" \
    -e K6_HOT_FROM="${K6_HOT_FROM}" \
    -e K6_HOT_TO="${K6_HOT_TO}" \
    -e K6_COLD_ACCOUNT_ID="${K6_COLD_ACCOUNT_ID}" \
    -e K6_COLD_FROM="${K6_COLD_FROM}" \
    -e K6_COLD_TO="${K6_COLD_TO}" \
    -e K6_AUTH_TOKEN="${K6_AUTH_TOKEN:-}" \
    -e K6_VUS="${K6_VUS}" \
    -e K6_DURATION="${K6_DURATION:-1m}" \
    -e K6_LIMIT="${K6_LIMIT}" \
    -e K6_HOT_P95_THRESHOLD_MS="${K6_HOT_P95_THRESHOLD_MS}" \
    -e K6_COLD_P95_THRESHOLD_MS="${K6_COLD_P95_THRESHOLD_MS}" \
    -e K6_HOT_P99_THRESHOLD_MS="${K6_HOT_P99_THRESHOLD_MS}" \
    -e K6_COLD_P99_THRESHOLD_MS="${K6_COLD_P99_THRESHOLD_MS}" \
    -e K6_HOT_MAX_THRESHOLD_MS="${K6_HOT_MAX_THRESHOLD_MS}" \
    -e K6_COLD_MAX_THRESHOLD_MS="${K6_COLD_MAX_THRESHOLD_MS}" \
    -e K6_HTTP_FAILED_RATE="${K6_HTTP_FAILED_RATE}" \
    -e K6_OVERLOAD_MODE="${K6_OVERLOAD_MODE}" \
    -e K6_OVERLOAD_429_RATE_THRESHOLD="${K6_OVERLOAD_429_RATE_THRESHOLD}" \
    -e K6_MAX_RETRY_AFTER_SLEEP_SECONDS="${K6_MAX_RETRY_AFTER_SLEEP_SECONDS}" \
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
    -e K6_PROMETHEUS_RW_TREND_STATS="p(50),p(90),p(95),p(99),min,max,avg" \
    -e K6_REPORT_NAME="${K6_REPORT_NAME}" \
    -e K6_OBSERVABILITY_MODE="${K6_OBSERVABILITY_MODE}" \
    -e K6_HOT_ACCOUNT_ID="${K6_HOT_ACCOUNT_ID}" \
    -e K6_HOT_FROM="${K6_HOT_FROM}" \
    -e K6_HOT_TO="${K6_HOT_TO}" \
    -e K6_COLD_ACCOUNT_ID="${K6_COLD_ACCOUNT_ID}" \
    -e K6_COLD_FROM="${K6_COLD_FROM}" \
    -e K6_COLD_TO="${K6_COLD_TO}" \
    -e K6_AUTH_TOKEN="${K6_AUTH_TOKEN:-}" \
    -e K6_VUS="${K6_VUS}" \
    -e K6_DURATION="${K6_DURATION:-1m}" \
    -e K6_LIMIT="${K6_LIMIT}" \
    -e K6_HOT_P95_THRESHOLD_MS="${K6_HOT_P95_THRESHOLD_MS}" \
    -e K6_COLD_P95_THRESHOLD_MS="${K6_COLD_P95_THRESHOLD_MS}" \
    -e K6_HOT_P99_THRESHOLD_MS="${K6_HOT_P99_THRESHOLD_MS}" \
    -e K6_COLD_P99_THRESHOLD_MS="${K6_COLD_P99_THRESHOLD_MS}" \
    -e K6_HOT_MAX_THRESHOLD_MS="${K6_HOT_MAX_THRESHOLD_MS}" \
    -e K6_COLD_MAX_THRESHOLD_MS="${K6_COLD_MAX_THRESHOLD_MS}" \
    -e K6_HTTP_FAILED_RATE="${K6_HTTP_FAILED_RATE}" \
    -e K6_OVERLOAD_MODE="${K6_OVERLOAD_MODE}" \
    -e K6_OVERLOAD_429_RATE_THRESHOLD="${K6_OVERLOAD_429_RATE_THRESHOLD}" \
    -e K6_MAX_RETRY_AFTER_SLEEP_SECONDS="${K6_MAX_RETRY_AFTER_SLEEP_SECONDS}" \
    -v "${K6_REMOTE_WORKDIR}/ops/k6:/scripts:ro" \
    -v "${remote_report_dir}:/reports" \
    grafana/k6:0.54.0 \
    "${k6_command[@]}"
}

set +e
if [[ "${K6_GENERATOR_MODE}" == "docker-context" ]]; then
  run_k6_docker_context
else
  run_k6_local
fi
status=$?
set -e

if [[ "${K6_ARCHIVE_RESULTS}" == "true" && -f "${summary_md}" ]]; then
  tools/test/archive-k6-transaction-100m-result.sh "${summary_md}" "${summary_json}"
elif [[ "${K6_ARCHIVE_RESULTS}" == "true" ]]; then
  echo "k6 summary markdown was not produced: ${summary_md}" >&2
  if [[ "${K6_GENERATOR_MODE}" == "docker-context" ]]; then
    echo "remote generator writes summaries under ${K6_REMOTE_WORKDIR}/build/reports/k6 on docker context ${K6_DOCKER_CONTEXT}" >&2
  fi
fi

exit "${status}"
