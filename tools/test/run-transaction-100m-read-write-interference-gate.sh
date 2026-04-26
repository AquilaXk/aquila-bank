#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-read-write-interference-gate.sh [--print-plan]

Required runtime environment for actual runs:
  K6_HOT_ACCOUNT_ID
  K6_HOT_FROM
  K6_HOT_TO
  K6_COLD_ACCOUNT_ID
  K6_COLD_FROM
  K6_COLD_TO
  INTERFERENCE_WRITE_SOURCE_ACCOUNT_ID
  INTERFERENCE_WRITE_TARGET_ACCOUNT_ID

Optional environment:
  INTERFERENCE_NAME                     default transaction-100m-read-write-interference-<timestamp>
  INTERFERENCE_BUILD_BACKEND            default true
  INTERFERENCE_FORCE_RECREATE           default true
  INTERFERENCE_DURATION                 default 1m
  INTERFERENCE_READ_VUS                 default 8
  INTERFERENCE_WRITE_VUS                default 2
  INTERFERENCE_LIMIT                    default 50
  INTERFERENCE_WRITE_AMOUNT_MINOR       default 1
  INTERFERENCE_READ_HOT_P95_THRESHOLD_MS default 350
  INTERFERENCE_READ_COLD_P95_THRESHOLD_MS default 750
  INTERFERENCE_READ_429_RATE_THRESHOLD  default 0
  INTERFERENCE_WRITE_429_RATE_THRESHOLD default 0.05
  INTERFERENCE_WRITE_SUCCESS_RATE_THRESHOLD default 0.95
  INTERFERENCE_READINESS_TIMEOUT_SECONDS default 120
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

name="${INTERFERENCE_NAME:-transaction-100m-read-write-interference-$(date +%Y-%m-%d-%H%M%S)}"
build_backend="${INTERFERENCE_BUILD_BACKEND:-true}"
force_recreate="${INTERFERENCE_FORCE_RECREATE:-true}"
duration="${INTERFERENCE_DURATION:-1m}"
read_vus="${INTERFERENCE_READ_VUS:-8}"
write_vus="${INTERFERENCE_WRITE_VUS:-2}"
limit="${INTERFERENCE_LIMIT:-50}"
write_amount_minor="${INTERFERENCE_WRITE_AMOUNT_MINOR:-1}"
read_hot_p95_threshold_ms="${INTERFERENCE_READ_HOT_P95_THRESHOLD_MS:-350}"
read_cold_p95_threshold_ms="${INTERFERENCE_READ_COLD_P95_THRESHOLD_MS:-750}"
read_429_rate_threshold="${INTERFERENCE_READ_429_RATE_THRESHOLD:-0}"
write_429_rate_threshold="${INTERFERENCE_WRITE_429_RATE_THRESHOLD:-0.05}"
write_success_rate_threshold="${INTERFERENCE_WRITE_SUCCESS_RATE_THRESHOLD:-0.95}"
readiness_timeout_seconds="${INTERFERENCE_READINESS_TIMEOUT_SECONDS:-120}"
backend_health_url="${INTERFERENCE_BACKEND_HEALTH_URL:-http://localhost:${BACKEND_PORT:-8080}/actuator/health}"
prometheus_url="${PROMETHEUS_URL:-http://localhost:9090}"
prometheus_base_url="${prometheus_url%/}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
report_dir="build/reports/k6/${name}"
summary_tsv="${report_dir}/read-write-interference-summary.tsv"
read_report_name="${name}-read"
write_report_name="${name}-write"
read_log_path="${report_dir}/${read_report_name}.log"
write_log_path="${report_dir}/${write_report_name}.log"
read_summary_json="build/reports/k6/${read_report_name}-summary.json"
write_summary_json="build/reports/k6/${write_report_name}-summary.json"
threshold_failed=false
WRITE_PID=""

require_bool_value() {
  local key="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${key} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_positive_integer_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${key} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_positive_number_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${key} must be a positive number: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value > 0) }' \
    || {
      echo "${key} must be greater than zero: ${value}" >&2
      exit 1
    }
}

require_rate_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${key} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value >= 0 && value <= 1) }' \
    || {
      echo "${key} must be a rate between 0 and 1: ${value}" >&2
      exit 1
    }
}

require_duration_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*(s|m|h)$ ]]; then
    echo "${key} must use a positive duration such as 60s, 30m, or 1h: ${value}" >&2
    exit 1
  fi
}

require_env() {
  local key="$1"
  local value="${!key:-}"
  if [[ -z "${value}" ]]; then
    echo "${key} is required" >&2
    exit 1
  fi
}

require_bool_value "INTERFERENCE_BUILD_BACKEND" "${build_backend}"
require_bool_value "INTERFERENCE_FORCE_RECREATE" "${force_recreate}"
require_duration_value "INTERFERENCE_DURATION" "${duration}"
require_positive_integer_value "INTERFERENCE_READ_VUS" "${read_vus}"
require_positive_integer_value "INTERFERENCE_WRITE_VUS" "${write_vus}"
require_positive_integer_value "INTERFERENCE_LIMIT" "${limit}"
require_positive_integer_value "INTERFERENCE_WRITE_AMOUNT_MINOR" "${write_amount_minor}"
require_positive_integer_value "INTERFERENCE_READINESS_TIMEOUT_SECONDS" "${readiness_timeout_seconds}"
require_positive_number_value "INTERFERENCE_READ_HOT_P95_THRESHOLD_MS" "${read_hot_p95_threshold_ms}"
require_positive_number_value "INTERFERENCE_READ_COLD_P95_THRESHOLD_MS" "${read_cold_p95_threshold_ms}"
require_rate_value "INTERFERENCE_READ_429_RATE_THRESHOLD" "${read_429_rate_threshold}"
require_rate_value "INTERFERENCE_WRITE_429_RATE_THRESHOLD" "${write_429_rate_threshold}"
require_rate_value "INTERFERENCE_WRITE_SUCCESS_RATE_THRESHOLD" "${write_success_rate_threshold}"

print_plan() {
  echo "[transaction-read-write-interference] name=${name}"
  echo "[transaction-read-write-interference] build_backend=${build_backend}"
  echo "[transaction-read-write-interference] force_recreate=${force_recreate}"
  echo "[transaction-read-write-interference] duration=${duration}"
  echo "[transaction-read-write-interference] read_vus=${read_vus}"
  echo "[transaction-read-write-interference] write_vus=${write_vus}"
  echo "[transaction-read-write-interference] limit=${limit}"
  echo "[transaction-read-write-interference] write_amount_minor=${write_amount_minor}"
  echo "[transaction-read-write-interference] read_hot_p95_threshold_ms=${read_hot_p95_threshold_ms}"
  echo "[transaction-read-write-interference] read_cold_p95_threshold_ms=${read_cold_p95_threshold_ms}"
  echo "[transaction-read-write-interference] read_429_rate_threshold=${read_429_rate_threshold}"
  echo "[transaction-read-write-interference] write_429_rate_threshold=${write_429_rate_threshold}"
  echo "[transaction-read-write-interference] write_success_rate_threshold=${write_success_rate_threshold}"
  echo "[transaction-read-write-interference] services=postgres,kafka,aquila-bank-backend,prometheus,grafana,alertmanager,postgres-exporter"
  echo "[transaction-read-write-interference] read_runner=tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps"
  echo "[transaction-read-write-interference] write_script=ops/k6/transfer-write-interference.js"
  echo "[transaction-read-write-interference] backend_health_url=${backend_health_url}"
  echo "[transaction-read-write-interference] prometheus_url=${prometheus_url}"
  echo "[transaction-read-write-interference] summary=${summary_tsv}"
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
require_env INTERFERENCE_WRITE_SOURCE_ACCOUNT_ID
require_env INTERFERENCE_WRITE_TARGET_ACCOUNT_ID

mkdir -p "${report_dir}" build/reports/k6

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

wait_for_backend_readiness() {
  local deadline=$((SECONDS + readiness_timeout_seconds))
  local body=""
  echo "[transaction-read-write-interference] waiting backend readiness: ${backend_health_url}"
  while ((SECONDS < deadline)); do
    body="$(curl -sS --max-time 2 "${backend_health_url}" 2>/dev/null || true)"
    # Kafka/outbox health는 warm-up 중 DOWN일 수 있어 readinessState만 확인합니다.
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
  echo "[transaction-read-write-interference] waiting prometheus readiness: ${prometheus_base_url}/-/ready"
  while ((SECONDS < deadline)); do
    if curl -fsS --max-time 2 "${prometheus_base_url}/-/ready" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "prometheus readiness timeout: ${prometheus_base_url}/-/ready" >&2
  exit 1
}

start_loadtest_services() {
  local args
  args=(docker compose "${compose_files[@]}" --profile loadtest up -d)
  if [[ "${force_recreate}" == "true" ]]; then
    args+=(--force-recreate)
  fi
  args+=(postgres kafka aquila-bank-backend prometheus grafana alertmanager postgres-exporter)

  KAFKA_ADVERTISED_HOST=kafka \
  OUTBOX_KAFKA_ENABLED=true \
  OUTBOX_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  OUTBOX_KAFKA_TOPIC_DEFAULT=bank.notification.outbox.v1 \
  OUTBOX_KAFKA_TOPIC_TRANSFER_BOOKED=bank.transfer.booked.v1 \
  OUTBOX_KAFKA_TOPIC_TRANSFER_REVERSED=bank.transfer.reversed.v1 \
  NOTIFICATION_INBOX_CONSUMER_ENABLED=true \
  NOTIFICATION_INBOX_CONSUMER_BOOTSTRAP_SERVERS=kafka:9092 \
  NOTIFICATION_INBOX_CONSUMER_CONCURRENCY=2 \
  NOTIFICATION_INBOX_CONSUMER_TRANSFER_BOOKED_TOPIC=bank.transfer.booked.v1 \
  NOTIFICATION_INBOX_CONSUMER_TRANSFER_REVERSED_TOPIC=bank.transfer.reversed.v1 \
  NOTIFICATION_INBOX_CONSUMER_DLQ_TOPIC=bank.transfer.booked.dlq.v1 \
  NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED=true \
  KAFKA_TOPIC_PROVISIONING_ENABLED=true \
  KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED=true \
  KAFKA_TOPIC_PROVISIONING_PARTITIONS=1 \
  KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR=1 \
  KAFKA_TOPIC_PROVISIONING_MIN_IN_SYNC_REPLICAS=1 \
    "${args[@]}"
}

start_write_pressure() {
  echo "[transaction-read-write-interference] starting transfer write k6"
  docker compose "${compose_files[@]}" --profile loadtest run --rm --no-deps \
    -e BASE_URL=http://aquila-bank-backend:8080 \
    -e K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write \
    -e K6_PROMETHEUS_RW_TREND_STATS="p(50),p(90),p(95),p(99),min,max,avg" \
    -e K6_REPORT_NAME="${write_report_name}" \
    -e K6_WRITE_SOURCE_ACCOUNT_ID="${INTERFERENCE_WRITE_SOURCE_ACCOUNT_ID}" \
    -e K6_WRITE_TARGET_ACCOUNT_ID="${INTERFERENCE_WRITE_TARGET_ACCOUNT_ID}" \
    -e K6_WRITE_AMOUNT_MINOR="${write_amount_minor}" \
    -e K6_WRITE_VUS="${write_vus}" \
    -e K6_WRITE_DURATION="${duration}" \
    -e K6_WRITE_429_RATE_THRESHOLD="${write_429_rate_threshold}" \
    -e K6_WRITE_SUCCESS_RATE_THRESHOLD="${write_success_rate_threshold}" \
    k6-transaction-read-100m \
    run \
    --out \
    experimental-prometheus-rw \
    /scripts/transfer-write-interference.js >"${write_log_path}" 2>&1 &
  WRITE_PID="$!"
}

stop_write_pressure() {
  if [[ -n "${WRITE_PID}" ]]; then
    kill "${WRITE_PID}" >/dev/null 2>&1 || true
    wait "${WRITE_PID}" >/dev/null 2>&1 || true
    WRITE_PID=""
  fi
}

write_header() {
  printf "read_status\twrite_status\tread_http_failed_rate\tread_http_reqs\tread_429_rate\tread_hot_first_p95_ms\tread_hot_cursor_p95_ms\tread_cold_first_p95_ms\tread_cold_cursor_p95_ms\twrite_http_failed_rate\twrite_http_reqs\twrite_429_rate\twrite_success_rate\twrite_duration_p95_ms\tread_log_path\twrite_log_path\tread_summary_json\twrite_summary_json\n" >"${summary_tsv}"
}

append_summary() {
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" "$@" >>"${summary_tsv}"
}

is_numeric_value() {
  [[ "$1" =~ ^[0-9]+([.][0-9]+)?$ ]]
}

number_greater_than() {
  local value="$1"
  local threshold="$2"
  awk -v value="${value}" -v threshold="${threshold}" 'BEGIN { exit !(value > threshold) }'
}

number_less_than() {
  local value="$1"
  local threshold="$2"
  awk -v value="${value}" -v threshold="${threshold}" 'BEGIN { exit !(value < threshold) }'
}

record_threshold_violation() {
  local metric="$1"
  local value="$2"
  local threshold="$3"
  echo "[transaction-read-write-interference] threshold violation metric=${metric} value=${value} threshold=${threshold}" >&2
  threshold_failed=true
}

check_metric_lte() {
  local metric="$1"
  local value="$2"
  local threshold="$3"
  if ! is_numeric_value "${value}"; then
    record_threshold_violation "${metric}" "${value}" "${threshold}"
    return 0
  fi
  if number_greater_than "${value}" "${threshold}"; then
    record_threshold_violation "${metric}" "${value}" "${threshold}"
  fi
}

check_metric_gte() {
  local metric="$1"
  local value="$2"
  local threshold="$3"
  if ! is_numeric_value "${value}"; then
    record_threshold_violation "${metric}" "${value}" "${threshold}"
    return 0
  fi
  if number_less_than "${value}" "${threshold}"; then
    record_threshold_violation "${metric}" "${value}" "${threshold}"
  fi
}

check_interference_thresholds() {
  local read_status="$1"
  local write_status="$2"
  local read_429_rate="$3"
  local read_hot_first_p95="$4"
  local read_hot_cursor_p95="$5"
  local read_cold_first_p95="$6"
  local read_cold_cursor_p95="$7"
  local write_429_rate="$8"
  local write_success_rate="$9"

  [[ "${read_status}" -eq 0 ]] || record_threshold_violation "read_status" "${read_status}" "0"
  [[ "${write_status}" -eq 0 ]] || record_threshold_violation "write_status" "${write_status}" "0"
  check_metric_lte "aquila_transaction_429_rate" "${read_429_rate}" "${read_429_rate_threshold}"
  check_metric_lte "read_hot_first_p95_ms" "${read_hot_first_p95}" "${read_hot_p95_threshold_ms}"
  check_metric_lte "read_hot_cursor_p95_ms" "${read_hot_cursor_p95}" "${read_hot_p95_threshold_ms}"
  check_metric_lte "read_cold_first_p95_ms" "${read_cold_first_p95}" "${read_cold_p95_threshold_ms}"
  check_metric_lte "read_cold_cursor_p95_ms" "${read_cold_cursor_p95}" "${read_cold_p95_threshold_ms}"
  check_metric_lte "aquila_transfer_write_429_rate" "${write_429_rate}" "${write_429_rate_threshold}"
  check_metric_gte "aquila_transfer_write_success_rate" "${write_success_rate}" "${write_success_rate_threshold}"
}

if [[ "${build_backend}" == "true" ]]; then
  echo "[transaction-read-write-interference] building backend bootJar"
  tools/test/with-resource-lock.sh back-gradle-interference-bootjar ./back/gradlew -p back bootJar
fi

trap stop_write_pressure EXIT

start_loadtest_services
wait_for_backend_readiness
wait_for_prometheus_readiness

start_write_pressure

set +e
K6_REPORT_NAME="${read_report_name}" \
K6_VUS="${read_vus}" \
K6_DURATION="${duration}" \
K6_LIMIT="${limit}" \
K6_OVERLOAD_MODE=false \
K6_ARCHIVE_RESULTS=false \
  tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps >"${read_log_path}" 2>&1
read_status=$?
wait "${WRITE_PID}"
write_status=$?
WRITE_PID=""
set -e

read_http_failed_rate="$(metric_from_json "${read_summary_json}" "http_req_failed" "rate")"
read_http_reqs="$(metric_from_json "${read_summary_json}" "http_reqs" "count")"
read_429_rate="$(metric_from_json "${read_summary_json}" "aquila_transaction_429_rate" "rate")"
read_hot_first_p95="$(metric_from_json "${read_summary_json}" "aquila_transaction_hot_first_ms" "p(95)")"
read_hot_cursor_p95="$(metric_from_json "${read_summary_json}" "aquila_transaction_hot_cursor_ms" "p(95)")"
read_cold_first_p95="$(metric_from_json "${read_summary_json}" "aquila_transaction_cold_first_ms" "p(95)")"
read_cold_cursor_p95="$(metric_from_json "${read_summary_json}" "aquila_transaction_cold_cursor_ms" "p(95)")"
write_http_failed_rate="$(metric_from_json "${write_summary_json}" "http_req_failed" "rate")"
write_http_reqs="$(metric_from_json "${write_summary_json}" "http_reqs" "count")"
write_429_rate="$(metric_from_json "${write_summary_json}" "aquila_transfer_write_429_rate" "rate")"
write_success_rate="$(metric_from_json "${write_summary_json}" "aquila_transfer_write_success_rate" "rate")"
write_duration_p95="$(metric_from_json "${write_summary_json}" "aquila_transfer_write_duration_ms" "p(95)")"

write_header
append_summary \
  "${read_status}" "${write_status}" \
  "${read_http_failed_rate}" "${read_http_reqs}" "${read_429_rate}" \
  "${read_hot_first_p95}" "${read_hot_cursor_p95}" "${read_cold_first_p95}" "${read_cold_cursor_p95}" \
  "${write_http_failed_rate}" "${write_http_reqs}" "${write_429_rate}" "${write_success_rate}" "${write_duration_p95}" \
  "${read_log_path}" "${write_log_path}" "${read_summary_json}" "${write_summary_json}"

check_interference_thresholds \
  "${read_status}" "${write_status}" "${read_429_rate}" \
  "${read_hot_first_p95}" "${read_hot_cursor_p95}" "${read_cold_first_p95}" "${read_cold_cursor_p95}" \
  "${write_429_rate}" "${write_success_rate}"

echo "[transaction-read-write-interference] summary=${summary_tsv}"
if [[ "${threshold_failed}" == "true" ]]; then
  exit 1
fi
