#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-cold-start-cache-warm-gate.sh [--print-plan]

Required runtime environment for actual runs:
  K6_HOT_ACCOUNT_ID
  K6_HOT_FROM
  K6_HOT_TO
  K6_COLD_ACCOUNT_ID
  K6_COLD_FROM
  K6_COLD_TO

Optional environment:
  COLD_WARM_NAME                         default transaction-100m-cold-warm-<timestamp>
  COLD_WARM_BUILD_BACKEND                default true
  COLD_WARM_FORCE_RECREATE               default true
  COLD_WARM_COLD_START_DURATION          default 1m
  COLD_WARM_WARMUP_DURATION              default 2m
  COLD_WARM_WARM_READ_DURATION           default 1m
  COLD_WARM_VUS                          default 8
  COLD_WARM_LIMIT                        default 50
  COLD_WARM_READINESS_TIMEOUT_SECONDS    default 120
  COLD_WARM_HARD_THRESHOLD_ENABLED       default true
  COLD_WARM_COLD_START_P95_THRESHOLD_MS  default 1000
  COLD_WARM_WARM_HOT_P95_THRESHOLD_MS    default 350
  COLD_WARM_WARM_COLD_P95_THRESHOLD_MS   default 750
  COLD_WARM_429_RATE_THRESHOLD           default 0
  COLD_WARM_503_RATE_THRESHOLD           default 0

Examples:
  tools/test/run-transaction-100m-cold-start-cache-warm-gate.sh --print-plan
  COLD_WARM_WARMUP_DURATION=5m tools/test/run-transaction-100m-cold-start-cache-warm-gate.sh
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

name="${COLD_WARM_NAME:-transaction-100m-cold-warm-$(date +%Y-%m-%d-%H%M%S)}"
build_backend="${COLD_WARM_BUILD_BACKEND:-true}"
force_recreate="${COLD_WARM_FORCE_RECREATE:-true}"
cold_start_duration="${COLD_WARM_COLD_START_DURATION:-1m}"
warmup_duration="${COLD_WARM_WARMUP_DURATION:-2m}"
warm_read_duration="${COLD_WARM_WARM_READ_DURATION:-1m}"
vus="${COLD_WARM_VUS:-8}"
limit="${COLD_WARM_LIMIT:-50}"
readiness_timeout_seconds="${COLD_WARM_READINESS_TIMEOUT_SECONDS:-120}"
hard_threshold_enabled="${COLD_WARM_HARD_THRESHOLD_ENABLED:-true}"
cold_start_p95_threshold_ms="${COLD_WARM_COLD_START_P95_THRESHOLD_MS:-1000}"
warm_hot_p95_threshold_ms="${COLD_WARM_WARM_HOT_P95_THRESHOLD_MS:-350}"
warm_cold_p95_threshold_ms="${COLD_WARM_WARM_COLD_P95_THRESHOLD_MS:-750}"
transaction_429_rate_threshold="${COLD_WARM_429_RATE_THRESHOLD:-0}"
transaction_503_rate_threshold="${COLD_WARM_503_RATE_THRESHOLD:-0}"
backend_health_url="${COLD_WARM_BACKEND_HEALTH_URL:-http://localhost:${BACKEND_PORT:-8080}/actuator/health}"
prometheus_url="${PROMETHEUS_URL:-http://localhost:9090}"
prometheus_base_url="${prometheus_url%/}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
report_dir="build/reports/k6/${name}"
summary_tsv="${report_dir}/cold-warm-summary.tsv"
report_md="${report_dir}/cold-warm-cache-state-slo.md"
threshold_failed=false

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

require_bool_value "COLD_WARM_BUILD_BACKEND" "${build_backend}"
require_bool_value "COLD_WARM_FORCE_RECREATE" "${force_recreate}"
require_bool_value "COLD_WARM_HARD_THRESHOLD_ENABLED" "${hard_threshold_enabled}"
require_duration_value "COLD_WARM_COLD_START_DURATION" "${cold_start_duration}"
require_duration_value "COLD_WARM_WARMUP_DURATION" "${warmup_duration}"
require_duration_value "COLD_WARM_WARM_READ_DURATION" "${warm_read_duration}"
require_positive_integer_value "COLD_WARM_VUS" "${vus}"
require_positive_integer_value "COLD_WARM_LIMIT" "${limit}"
require_positive_integer_value "COLD_WARM_READINESS_TIMEOUT_SECONDS" "${readiness_timeout_seconds}"
require_positive_number_value "COLD_WARM_COLD_START_P95_THRESHOLD_MS" "${cold_start_p95_threshold_ms}"
require_positive_number_value "COLD_WARM_WARM_HOT_P95_THRESHOLD_MS" "${warm_hot_p95_threshold_ms}"
require_positive_number_value "COLD_WARM_WARM_COLD_P95_THRESHOLD_MS" "${warm_cold_p95_threshold_ms}"
require_rate_value "COLD_WARM_429_RATE_THRESHOLD" "${transaction_429_rate_threshold}"
require_rate_value "COLD_WARM_503_RATE_THRESHOLD" "${transaction_503_rate_threshold}"

print_plan() {
  echo "[transaction-100m-cold-warm] name=${name}"
  echo "[transaction-100m-cold-warm] build_backend=${build_backend}"
  echo "[transaction-100m-cold-warm] force_recreate=${force_recreate}"
  echo "[transaction-100m-cold-warm] cold_start_duration=${cold_start_duration}"
  echo "[transaction-100m-cold-warm] warmup_duration=${warmup_duration}"
  echo "[transaction-100m-cold-warm] warm_read_duration=${warm_read_duration}"
  echo "[transaction-100m-cold-warm] vus=${vus}"
  echo "[transaction-100m-cold-warm] limit=${limit}"
  echo "[transaction-100m-cold-warm] backend_health_url=${backend_health_url}"
  echo "[transaction-100m-cold-warm] prometheus_url=${prometheus_url}"
  echo "[transaction-100m-cold-warm] readiness_timeout_seconds=${readiness_timeout_seconds}"
  echo "[transaction-100m-cold-warm] hard_thresholds=${hard_threshold_enabled}"
  echo "[transaction-100m-cold-warm] cold_start_p95_threshold_ms=${cold_start_p95_threshold_ms}"
  echo "[transaction-100m-cold-warm] warm_hot_p95_threshold_ms=${warm_hot_p95_threshold_ms}"
  echo "[transaction-100m-cold-warm] warm_cold_p95_threshold_ms=${warm_cold_p95_threshold_ms}"
  echo "[transaction-100m-cold-warm] transaction_429_rate_threshold=${transaction_429_rate_threshold}"
  echo "[transaction-100m-cold-warm] transaction_503_rate_threshold=${transaction_503_rate_threshold}"
  echo "[transaction-100m-cold-warm] runner=tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps"
  echo "[transaction-100m-cold-warm] summary=${summary_tsv}"
  echo "[transaction-100m-cold-warm] report=${report_md}"
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
  echo "[transaction-100m-cold-warm] waiting backend readiness: ${backend_health_url}"
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
  echo "[transaction-100m-cold-warm] waiting prometheus readiness: ${prometheus_base_url}/-/ready"
  while ((SECONDS < deadline)); do
    if curl -fsS --max-time 2 "${prometheus_base_url}/-/ready" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "prometheus readiness timeout: ${prometheus_base_url}/-/ready" >&2
  exit 1
}

write_header() {
  printf "phase\tcache_state\tstatus\tvus\tduration\tlimit\thttp_failed_rate\thttp_reqs\ttransaction_429_rate\ttransaction_503_rate\ttransaction_503_count\tfirst_p95_ms\tdeep_p95_ms\thot_first_p95_ms\thot_cursor_p95_ms\tcold_first_p95_ms\tcold_cursor_p95_ms\tlog_path\tsummary_json\n" >"${summary_tsv}"
}

append_summary() {
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" "$@" >>"${summary_tsv}"
}

is_numeric_value() {
  [[ "$1" =~ ^[0-9]+([.][0-9]+)?$ ]]
}

number_greater_than() {
  local value="$1"
  local threshold="$2"
  awk -v value="${value}" -v threshold="${threshold}" 'BEGIN { exit !(value > threshold) }'
}

max_numeric_value() {
  local result="n/a"
  local value
  for value in "$@"; do
    if is_numeric_value "${value}" && { [[ "${result}" == "n/a" ]] || number_greater_than "${value}" "${result}"; }; then
      result="${value}"
    fi
  done
  echo "${result}"
}

record_threshold_violation() {
  local phase="$1"
  local metric="$2"
  local value="$3"
  local threshold="$4"
  echo "[transaction-100m-cold-warm] threshold violation phase=${phase} metric=${metric} value=${value} threshold=${threshold}" >&2
  threshold_failed=true
}

check_metric_lte() {
  local phase="$1"
  local metric="$2"
  local value="$3"
  local threshold="$4"

  if ! is_numeric_value "${value}"; then
    record_threshold_violation "${phase}" "${metric}" "${value}" "${threshold}"
    return 0
  fi
  if number_greater_than "${value}" "${threshold}"; then
    record_threshold_violation "${phase}" "${metric}" "${value}" "${threshold}"
  fi
}

check_phase_thresholds() {
  local phase="$1"
  local status="$2"
  local transaction_429_rate="$3"
  local transaction_503_rate="$4"
  local transaction_503_count="$5"
  local hot_first_p95="$6"
  local hot_cursor_p95="$7"
  local cold_first_p95="$8"
  local cold_cursor_p95="$9"

  if [[ "${hard_threshold_enabled}" != "true" ]]; then
    return 0
  fi

  if [[ "${status}" -ne 0 ]]; then
    record_threshold_violation "${phase}" "k6_status" "${status}" "0"
  fi

  check_metric_lte "${phase}" "transaction_429_rate" "${transaction_429_rate}" "${transaction_429_rate_threshold}"
  check_metric_lte "${phase}" "transaction_503_rate" "${transaction_503_rate}" "${transaction_503_rate_threshold}"
  check_metric_lte "${phase}" "transaction_503_count" "${transaction_503_count}" "0"
  if [[ "${phase}" == "cold-start" ]]; then
    check_metric_lte "${phase}" "hot_first_p95_ms" "${hot_first_p95}" "${cold_start_p95_threshold_ms}"
    check_metric_lte "${phase}" "hot_cursor_p95_ms" "${hot_cursor_p95}" "${cold_start_p95_threshold_ms}"
    check_metric_lte "${phase}" "cold_first_p95_ms" "${cold_first_p95}" "${cold_start_p95_threshold_ms}"
    check_metric_lte "${phase}" "cold_cursor_p95_ms" "${cold_cursor_p95}" "${cold_start_p95_threshold_ms}"
  else
    check_metric_lte "${phase}" "hot_first_p95_ms" "${hot_first_p95}" "${warm_hot_p95_threshold_ms}"
    check_metric_lte "${phase}" "hot_cursor_p95_ms" "${hot_cursor_p95}" "${warm_hot_p95_threshold_ms}"
    check_metric_lte "${phase}" "cold_first_p95_ms" "${cold_first_p95}" "${warm_cold_p95_threshold_ms}"
    check_metric_lte "${phase}" "cold_cursor_p95_ms" "${cold_cursor_p95}" "${warm_cold_p95_threshold_ms}"
  fi
}

start_loadtest_services() {
  local args
  args=(docker compose "${compose_files[@]}" --profile loadtest up -d)
  if [[ "${force_recreate}" == "true" ]]; then
    args+=(--force-recreate)
  fi
  args+=(postgres aquila-bank-backend prometheus grafana alertmanager postgres-exporter)
  "${args[@]}"
}

run_k6_phase() {
  local phase="$1"
  local duration="$2"
  local cache_state="$3"
  local report_name="${name}-${phase}"
  local log_path="${report_dir}/${report_name}.log"
  local summary_json="build/reports/k6/${report_name}-summary.json"
  local status http_failed_rate http_reqs transaction_429_rate transaction_503_rate transaction_503_count
  local hot_first_p95 hot_cursor_p95 cold_first_p95 cold_cursor_p95 first_p95 deep_p95

  echo "[transaction-100m-cold-warm] running phase=${phase} duration=${duration}"
  set +e
  K6_REPORT_NAME="${report_name}" \
  K6_VUS="${vus}" \
  K6_DURATION="${duration}" \
  K6_LIMIT="${limit}" \
  K6_OVERLOAD_MODE=false \
  K6_ARCHIVE_RESULTS=false \
    tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps >"${log_path}" 2>&1
  status=$?
  set -e

  http_failed_rate="$(metric_from_json "${summary_json}" "http_req_failed" "rate")"
  http_reqs="$(metric_from_json "${summary_json}" "http_reqs" "count")"
  transaction_429_rate="$(metric_from_json "${summary_json}" "aquila_transaction_429_rate" "rate")"
  transaction_503_rate="$(metric_from_json "${summary_json}" "aquila_transaction_503_rate" "rate")"
  transaction_503_count="$(metric_from_json "${summary_json}" "aquila_transaction_503_count" "count")"
  hot_first_p95="$(metric_from_json "${summary_json}" "aquila_transaction_hot_first_ms" "p(95)")"
  hot_cursor_p95="$(metric_from_json "${summary_json}" "aquila_transaction_hot_cursor_ms" "p(95)")"
  cold_first_p95="$(metric_from_json "${summary_json}" "aquila_transaction_cold_first_ms" "p(95)")"
  cold_cursor_p95="$(metric_from_json "${summary_json}" "aquila_transaction_cold_cursor_ms" "p(95)")"
  first_p95="$(max_numeric_value "${hot_first_p95}" "${cold_first_p95}")"
  deep_p95="$(max_numeric_value "${hot_cursor_p95}" "${cold_cursor_p95}")"

  append_summary \
    "${phase}" "${cache_state}" "${status}" "${vus}" "${duration}" "${limit}" \
    "${http_failed_rate}" "${http_reqs}" "${transaction_429_rate}" \
    "${transaction_503_rate}" "${transaction_503_count}" "${first_p95}" "${deep_p95}" \
    "${hot_first_p95}" "${hot_cursor_p95}" "${cold_first_p95}" "${cold_cursor_p95}" \
    "${log_path}" "${summary_json}"

  check_phase_thresholds \
    "${phase}" "${status}" "${transaction_429_rate}" "${transaction_503_rate}" "${transaction_503_count}" \
    "${hot_first_p95}" "${hot_cursor_p95}" "${cold_first_p95}" "${cold_cursor_p95}"
}

write_report() {
  local rows=""
  local phase cache_state status phase_vus duration phase_limit http_failed_rate http_reqs
  local transaction_429_rate transaction_503_rate transaction_503_count first_p95 deep_p95
  local hot_first_p95 hot_cursor_p95 cold_first_p95 cold_cursor_p95 log_path summary_json

  while IFS=$'\t' read -r phase cache_state status phase_vus duration phase_limit http_failed_rate http_reqs \
    transaction_429_rate transaction_503_rate transaction_503_count first_p95 deep_p95 \
    hot_first_p95 hot_cursor_p95 cold_first_p95 cold_cursor_p95 log_path summary_json; do
    if [[ "${phase}" == "phase" ]]; then
      continue
    fi
    rows="${rows}"$'\n'"| ${phase} | ${cache_state} | ${status} | ${first_p95} | ${deep_p95} | ${transaction_429_rate} | ${transaction_503_rate} | ${transaction_503_count} | ${summary_json} |"
  done <"${summary_tsv}"

  cat >"${report_md}" <<REPORT
# Transaction 100m Cache-State SLO

## Summary

- gate: ${name}
- cold-start duration: ${cold_start_duration}
- warmup duration: ${warmup_duration}
- warm-cache read duration: ${warm_read_duration}
- first/deep p95 source: max(hot,cold) first page and deep cursor p95
- 429 rate threshold: ${transaction_429_rate_threshold}
- 503 rate threshold: ${transaction_503_rate_threshold}

## Cache-State Result

| phase | cache state | status | first p95 ms | deep p95 ms | 429 rate | 503 rate | 503 count | summary JSON |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |${rows}

## Artifacts

- summary TSV: ${summary_tsv}
- report: ${report_md}

## Notes

- cold-start와 warm-cache를 분리해 cache 상태가 섞인 p95/429 해석을 피합니다.
- 503은 admission 실패가 아니라 service failure로 보아 0 budget으로 판정합니다.
REPORT
}

run_warmup() {
  local report_name="${name}-warmup"
  local log_path="${report_dir}/${report_name}.log"
  local status

  echo "[transaction-100m-cold-warm] running phase=warmup duration=${warmup_duration}"
  set +e
  K6_REPORT_NAME="${report_name}" \
  K6_VUS="${vus}" \
  K6_DURATION="${warmup_duration}" \
  K6_LIMIT="${limit}" \
  K6_OVERLOAD_MODE=false \
  K6_ARCHIVE_RESULTS=false \
    tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps >"${log_path}" 2>&1
  status=$?
  set -e

  if [[ "${hard_threshold_enabled}" == "true" && "${status}" -ne 0 ]]; then
    record_threshold_violation "warmup" "k6_status" "${status}" "0"
  fi
}

if [[ "${build_backend}" == "true" ]]; then
  echo "[transaction-100m-cold-warm] building backend bootJar"
  tools/test/with-resource-lock.sh back-gradle-loadtest-bootjar ./back/gradlew -p back bootJar
fi

start_loadtest_services
wait_for_backend_readiness
wait_for_prometheus_readiness

write_header
run_k6_phase "cold-start" "${cold_start_duration}" "cold-start"
run_warmup
run_k6_phase "warm-read" "${warm_read_duration}" "warm-cache"
write_report

echo "[transaction-100m-cold-warm] summary=${summary_tsv}"
echo "[transaction-100m-cold-warm] report=${report_md}"
if [[ "${threshold_failed}" == "true" ]]; then
  exit 1
fi
