#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-replica-100m-offload.sh [--print-plan|--no-up] [--no-deps]

Required runtime environment:
  TRANSACTION_READ_REPLICA_URL
  TRANSACTION_READ_REPLICA_USERNAME
  TRANSACTION_READ_REPLICA_PASSWORD
  K6_HOT_ACCOUNT_ID K6_HOT_FROM K6_HOT_TO
  K6_COLD_ACCOUNT_ID K6_COLD_FROM K6_COLD_TO

Optional environment:
  REPLICA_OFFLOAD_NAME default transaction-read-replica-100m-<timestamp>
  REPLICA_OFFLOAD_BUILD_BACKEND default true
  REPLICA_OFFLOAD_ADMISSION_MAX default K6_VUS or 8
  REPLICA_OFFLOAD_POOL_MAX_SIZE default 2
  REPLICA_OFFLOAD_LAG_THRESHOLD_MS default 3000
  REPLICA_OFFLOAD_T3_GUARD_ENABLED default false
  REPLICA_OFFLOAD_ROUTE_WAIT_SECONDS default 15
  REPLICA_OFFLOAD_HEALTH_URL default http://localhost:${BACKEND_PORT:-8080}/actuator/health
  REPLICA_OFFLOAD_PROMETHEUS_URL default http://localhost:${BACKEND_PORT:-8080}/actuator/prometheus
  REPLICA_OFFLOAD_ALLOW_NO_ROUTE_DELTA default false
USAGE
}

mode="run"
run_dependencies="true"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan) mode="print-plan" ;;
    --no-up) mode="no-up" ;;
    --no-deps) run_dependencies="false" ;;
    -h|--help) usage; exit 0 ;;
    *) usage; exit 1 ;;
  esac
  shift
done

name="${REPLICA_OFFLOAD_NAME:-transaction-read-replica-100m-$(date +%Y-%m-%d-%H%M%S)}"
build_backend="${REPLICA_OFFLOAD_BUILD_BACKEND:-true}"
admission_max="${REPLICA_OFFLOAD_ADMISSION_MAX:-${K6_VUS:-8}}"
pool_max_size="${REPLICA_OFFLOAD_POOL_MAX_SIZE:-2}"
lag_threshold_ms="${REPLICA_OFFLOAD_LAG_THRESHOLD_MS:-3000}"
t3_guard_enabled="${REPLICA_OFFLOAD_T3_GUARD_ENABLED:-false}"
route_wait_seconds="${REPLICA_OFFLOAD_ROUTE_WAIT_SECONDS:-15}"
health_url="${REPLICA_OFFLOAD_HEALTH_URL:-http://localhost:${BACKEND_PORT:-8080}/actuator/health}"
prometheus_url="${REPLICA_OFFLOAD_PROMETHEUS_URL:-http://localhost:${BACKEND_PORT:-8080}/actuator/prometheus}"
allow_no_route_delta="${REPLICA_OFFLOAD_ALLOW_NO_ROUTE_DELTA:-false}"
k6_report_name="${K6_REPORT_NAME:-${name}-k6}"
report_dir="build/reports/k6/${name}"
summary_tsv="${report_dir}/replica-offload-summary.tsv"
summary_md="${report_dir}/replica-offload-summary.md"
log_path="${report_dir}/${k6_report_name}.log"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)

require_positive_integer_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer" >&2
    exit 1
  fi
}

require_boolean_value() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false" >&2
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

wait_for_backend_readiness() {
  local deadline=$((SECONDS + 90))
  local body=""

  echo "[transaction-replica-100m-offload] waiting backend readiness: ${health_url}"
  while ((SECONDS < deadline)); do
    body="$(curl -sS --max-time 2 "${health_url}" 2>/dev/null || true)"
    # 전체 health는 optional outbox 상태 때문에 DOWN일 수 있어 readinessState만 확인합니다.
    if grep -F '"readinessState":{"status":"UP"}' <<<"${body}" >/dev/null; then
      return 0
    fi
    sleep 1
  done

  echo "backend readiness timeout: ${health_url}" >&2
  if [[ -n "${body}" ]]; then
    echo "${body}" >&2
  fi
  exit 1
}

route_count() {
  curl -sS --max-time 5 "${prometheus_url}" |
    awk '
      /^aquila_transaction_read_replica_route_decisions_total\{/ &&
      /query_shape="archive"/ &&
      /route="replica"/ &&
      /reason="replica_healthy"/ {
        value = $NF
      }
      END {
        if (value == "") {
          print 0
        } else {
          print value
        }
      }'
}

replica_lag_ms() {
  curl -sS --max-time 5 "${prometheus_url}" |
    awk '
      /^aquila_transaction_read_replica_lag_ms/ {
        value = $NF
      }
      END {
        if (value == "") {
          print "n/a"
        } else {
          print value
        }
      }'
}

wait_for_route_delta() {
  local before="$1"
  local after="$before"
  local deadline=$((SECONDS + route_wait_seconds))

  while ((SECONDS <= deadline)); do
    after="$(route_count)"
    awk -v before="${before}" -v after="${after}" 'BEGIN { exit !(after > before) }' && {
      printf '%s\n' "${after}"
      return 0
    }
    sleep 1
  done

  printf '%s\n' "${after}"
  return 1
}

require_positive_integer_value REPLICA_OFFLOAD_ADMISSION_MAX "${admission_max}"
require_positive_integer_value REPLICA_OFFLOAD_POOL_MAX_SIZE "${pool_max_size}"
require_positive_integer_value REPLICA_OFFLOAD_LAG_THRESHOLD_MS "${lag_threshold_ms}"
require_positive_integer_value REPLICA_OFFLOAD_ROUTE_WAIT_SECONDS "${route_wait_seconds}"
require_boolean_value REPLICA_OFFLOAD_BUILD_BACKEND "${build_backend}"
require_boolean_value REPLICA_OFFLOAD_ALLOW_NO_ROUTE_DELTA "${allow_no_route_delta}"
require_boolean_value REPLICA_OFFLOAD_T3_GUARD_ENABLED "${t3_guard_enabled}"

echo "[transaction-replica-100m-offload] name=${name}"
echo "[transaction-replica-100m-offload] k6 report=${k6_report_name}"
echo "[transaction-replica-100m-offload] admission max=${admission_max} replica_pool=${pool_max_size} lag_threshold_ms=${lag_threshold_ms}"
echo "[transaction-replica-100m-offload] t3 saturation guard=${t3_guard_enabled}"
echo "[transaction-replica-100m-offload] route_metric=aquila_transaction_read_replica_route_decisions_total{query_shape=\"archive\",route=\"replica\",reason=\"replica_healthy\"}"
echo "[transaction-replica-100m-offload] k6 runner=tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps"
echo "[transaction-replica-100m-offload] summary=${summary_tsv}"
if [[ "${run_dependencies}" == "true" ]]; then
  echo "[transaction-replica-100m-offload] dependencies=compose-default"
else
  echo "[transaction-replica-100m-offload] dependencies=no-deps"
fi

if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

require_env TRANSACTION_READ_REPLICA_URL
require_env TRANSACTION_READ_REPLICA_USERNAME
require_env TRANSACTION_READ_REPLICA_PASSWORD
require_env K6_HOT_ACCOUNT_ID
require_env K6_HOT_FROM
require_env K6_HOT_TO
require_env K6_COLD_ACCOUNT_ID
require_env K6_COLD_FROM
require_env K6_COLD_TO

mkdir -p "${report_dir}"
printf "name\tstatus\troute_before\troute_after\troute_delta\treplica_lag_ms\tk6_summary_json\tlog_path\n" >"${summary_tsv}"

if [[ "${mode}" != "no-up" ]]; then
  if [[ "${build_backend}" == "true" ]]; then
    tools/test/with-resource-lock.sh back-gradle-replica-offload-bootjar ./back/gradlew -p back bootJar
  fi
  docker compose "${compose_files[@]}" --profile loadtest up -d postgres alertmanager postgres-exporter
  TRANSACTION_READ_REPLICA_ENABLED=true \
  TRANSACTION_READ_REPLICA_URL="${TRANSACTION_READ_REPLICA_URL}" \
  TRANSACTION_READ_REPLICA_USERNAME="${TRANSACTION_READ_REPLICA_USERNAME}" \
  TRANSACTION_READ_REPLICA_PASSWORD="${TRANSACTION_READ_REPLICA_PASSWORD}" \
  TRANSACTION_READ_REPLICA_POOL_MAX_SIZE="${pool_max_size}" \
  TRANSACTION_READ_REPLICA_LAG_THRESHOLD_MS="${lag_threshold_ms}" \
  OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX="${admission_max}" \
  OPS_T3MICRO_SATURATION_GUARD_ENABLED="${t3_guard_enabled}" \
    docker compose "${compose_files[@]}" --profile loadtest up -d --force-recreate aquila-bank-backend prometheus grafana
fi

wait_for_backend_readiness
before="$(route_count)"
lag_before="$(replica_lag_ms)"

set +e
K6_REPORT_NAME="${k6_report_name}" K6_ARCHIVE_RESULTS=false \
  tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps >"${log_path}" 2>&1
status=$?
set -e

after="$(wait_for_route_delta "${before}" || true)"
delta="$(awk -v before="${before}" -v after="${after}" 'BEGIN { print after - before }')"
lag_after="$(replica_lag_ms)"
printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
  "${name}" "${status}" "${before}" "${after}" "${delta}" "${lag_after}" \
  "build/reports/k6/${k6_report_name}-summary.json" "${log_path}" >>"${summary_tsv}"

{
  echo "# Transaction Read Replica 100m Offload"
  echo
  echo "- name: ${name}"
  echo "- k6 status: ${status}"
  echo "- archive replica route counter: ${before} -> ${after}"
  echo "- route delta: ${delta}"
  echo "- replica lag ms: ${lag_before} -> ${lag_after}"
  echo "- k6 summary json: build/reports/k6/${k6_report_name}-summary.json"
  echo "- run log: ${log_path}"
} >"${summary_md}"

if [[ "${status}" -ne 0 ]]; then
  exit "${status}"
fi
if ! awk -v delta="${delta}" 'BEGIN { exit !(delta > 0) }'; then
  if [[ "${allow_no_route_delta}" == "true" ]]; then
    echo "[transaction-replica-100m-offload] route delta was ${delta}; allowed by REPLICA_OFFLOAD_ALLOW_NO_ROUTE_DELTA=true"
  else
    echo "archive replica route counter did not increase" >&2
    exit 88
  fi
fi
