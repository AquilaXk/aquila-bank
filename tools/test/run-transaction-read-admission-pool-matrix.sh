#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-admission-pool-matrix.sh [--print-plan]

Required runtime environment for actual runs:
  K6_HOT_ACCOUNT_ID
  K6_HOT_FROM
  K6_HOT_TO
  K6_COLD_ACCOUNT_ID
  K6_COLD_FROM
  K6_COLD_TO

Optional environment:
  MATRIX_NAME              default transaction-read-admission-pool-<timestamp>
  MATRIX_ADMISSION_VALUES  default 4,8,12
  MATRIX_DB_POOL_VALUES    default 4,8,12
  MATRIX_SERVER_THREAD_VALUES default 16,24,32
  MATRIX_VU_VALUES         default 8,16
  MATRIX_CONTINUE_ON_FAILURE default true
  MATRIX_BUILD_BACKEND     default true
  MATRIX_READINESS_TIMEOUT_SECONDS default 90
  MATRIX_METRIC_SCRAPE_WAIT_SECONDS default 6
  MATRIX_CPU_SAMPLE_INTERVAL_SECONDS default 2
  MATRIX_BACKEND_HEALTH_URL default http://localhost:${BACKEND_PORT:-8080}/actuator/health
  PROMETHEUS_URL           default http://localhost:9090
  K6_DURATION              default 1m
  K6_LIMIT                 default 50

Examples:
  tools/test/run-transaction-read-admission-pool-matrix.sh --print-plan
  MATRIX_ADMISSION_VALUES=4,12 MATRIX_DB_POOL_VALUES=4,12 MATRIX_SERVER_THREAD_VALUES=16,32 MATRIX_VU_VALUES=8,16 \
    tools/test/run-transaction-read-admission-pool-matrix.sh
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

matrix_name="${MATRIX_NAME:-transaction-read-admission-pool-$(date +%Y-%m-%d-%H%M%S)}"
admission_values="${MATRIX_ADMISSION_VALUES-4,8,12}"
db_pool_values="${MATRIX_DB_POOL_VALUES-4,8,12}"
server_thread_values="${MATRIX_SERVER_THREAD_VALUES-16,24,32}"
vu_values="${MATRIX_VU_VALUES-8,16}"
continue_on_failure="${MATRIX_CONTINUE_ON_FAILURE:-true}"
build_backend="${MATRIX_BUILD_BACKEND:-true}"
prometheus_url="${PROMETHEUS_URL:-http://localhost:9090}"
prometheus_base_url="${prometheus_url%/}"
readiness_timeout_seconds="${MATRIX_READINESS_TIMEOUT_SECONDS:-90}"
metric_scrape_wait_seconds="${MATRIX_METRIC_SCRAPE_WAIT_SECONDS:-6}"
cpu_sample_interval_seconds="${MATRIX_CPU_SAMPLE_INTERVAL_SECONDS:-2}"
backend_health_url="${MATRIX_BACKEND_HEALTH_URL:-http://localhost:${BACKEND_PORT:-8080}/actuator/health}"
report_dir="build/reports/k6/${matrix_name}"
summary_tsv="${report_dir}/matrix-summary.tsv"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
CPU_SAMPLER_PID=""

require_csv_positive_integers() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "${name} must not be empty" >&2
    exit 1
  fi
  IFS=',' read -r -a items <<<"${value}"
  local item
  for item in "${items[@]}"; do
    if ! [[ "${item}" =~ ^[1-9][0-9]*$ ]]; then
      echo "${name} must contain positive integers: ${value}" >&2
      exit 1
    fi
  done
}

csv_count() {
  local value="$1"
  IFS=',' read -r -a items <<<"${value}"
  echo "${#items[@]}"
}

require_csv_positive_integers "MATRIX_ADMISSION_VALUES" "${admission_values}"
require_csv_positive_integers "MATRIX_DB_POOL_VALUES" "${db_pool_values}"
require_csv_positive_integers "MATRIX_SERVER_THREAD_VALUES" "${server_thread_values}"
require_csv_positive_integers "MATRIX_VU_VALUES" "${vu_values}"
if ! [[ "${readiness_timeout_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "MATRIX_READINESS_TIMEOUT_SECONDS must be a positive integer" >&2
  exit 1
fi
if ! [[ "${metric_scrape_wait_seconds}" =~ ^[0-9]+$ ]]; then
  echo "MATRIX_METRIC_SCRAPE_WAIT_SECONDS must be zero or a positive integer" >&2
  exit 1
fi
if ! [[ "${cpu_sample_interval_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "MATRIX_CPU_SAMPLE_INTERVAL_SECONDS must be a positive integer" >&2
  exit 1
fi

admission_count="$(csv_count "${admission_values}")"
db_pool_count="$(csv_count "${db_pool_values}")"
server_thread_count="$(csv_count "${server_thread_values}")"
vu_count="$(csv_count "${vu_values}")"
combination_count=$((admission_count * db_pool_count * server_thread_count * vu_count))

print_plan() {
  echo "[transaction-read-matrix] matrix=${matrix_name}"
  echo "[transaction-read-matrix] admission_values=${admission_values}"
  echo "[transaction-read-matrix] db_pool_values=${db_pool_values}"
  echo "[transaction-read-matrix] server_thread_values=${server_thread_values}"
  echo "[transaction-read-matrix] vu_values=${vu_values}"
  echo "[transaction-read-matrix] combinations=${combination_count}"
  echo "[transaction-read-matrix] execution=serial"
  echo "[transaction-read-matrix] continue_on_failure=${continue_on_failure}"
  echo "[transaction-read-matrix] build_backend=${build_backend}"
  echo "[transaction-read-matrix] prometheus_url=${prometheus_url}"
  echo "[transaction-read-matrix] backend_health_url=${backend_health_url}"
  echo "[transaction-read-matrix] readiness_timeout_seconds=${readiness_timeout_seconds}"
  echo "[transaction-read-matrix] metric_scrape_wait_seconds=${metric_scrape_wait_seconds}"
  echo "[transaction-read-matrix] cpu_sample_interval_seconds=${cpu_sample_interval_seconds}"
  echo "[transaction-read-matrix] summary=${summary_tsv}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

mkdir -p "${report_dir}"

if [[ "${build_backend}" == "true" ]]; then
  echo "[transaction-read-matrix] building backend bootJar"
  tools/test/with-resource-lock.sh back-gradle-loadtest-bootjar ./back/gradlew -p back bootJar
fi

echo "[transaction-read-matrix] starting shared loadtest services"
docker compose "${compose_files[@]}" --profile loadtest up -d \
  postgres alertmanager postgres-exporter

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

numeric_delta() {
  local before="$1"
  local after="$2"
  awk -v before="${before}" -v after="${after}" 'BEGIN {
    if (before == "n/a" || after == "n/a") { print "n/a"; exit }
    { delta = after - before; if (delta < 0) delta = 0; print delta }
  }'
}

numeric_sum() {
  local left="$1"
  local right="$2"
  awk -v left="${left}" -v right="${right}" 'BEGIN {
    if (left == "n/a" || right == "n/a") { print "n/a"; exit }
    print left + right
  }'
}

numeric_subtract() {
  local left="$1"
  local right="$2"
  awk -v left="${left}" -v right="${right}" 'BEGIN {
    if (left == "n/a" || right == "n/a") { print "n/a"; exit }
    result = left - right
    if (result < 0) result = 0
    print result
  }'
}

ratio() {
  local numerator="$1"
  local denominator="$2"
  awk -v numerator="${numerator}" -v denominator="${denominator}" 'BEGIN {
    if (numerator == "n/a" || denominator == "n/a" || denominator <= 0) { print "n/a"; exit }
    printf "%.6f", numerator / denominator
  }'
}

log_count() {
  local log_path="$1"
  local pattern="$2"
  if [[ ! -f "${log_path}" ]]; then
    echo "0"
    return 0
  fi
  grep -F -c "${pattern}" "${log_path}" 2>/dev/null || true
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

container_cpu_percent() {
  local container_name="$1"
  local value
  value="$(
    docker stats --no-stream --format '{{.Name}}	{{.CPUPerc}}' "${container_name}" 2>/dev/null \
      | awk -F '\t' -v name="${container_name}" '$1 == name { gsub("%", "", $2); print $2 }' \
      || true
  )"
  echo "${value:-n/a}"
}

wait_for_backend_readiness() {
  local deadline=$((SECONDS + readiness_timeout_seconds))
  local body=""

  echo "[transaction-read-matrix] waiting backend readiness: ${backend_health_url}"
  while ((SECONDS < deadline)); do
    body="$(curl -sS --max-time 2 "${backend_health_url}" 2>/dev/null || true)"
    # 전체 health는 outbox 등 선택 기능 때문에 DOWN일 수 있어 readinessState만 본다.
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

wait_for_prometheus_readiness() {
  local deadline=$((SECONDS + readiness_timeout_seconds))

  echo "[transaction-read-matrix] waiting prometheus readiness: ${prometheus_base_url}/-/ready"
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
  printf "status\tadmission\tdb_pool\tserver_threads\tvus\treport\thttp_failed_rate\thttp_reqs\tadmission_429_rate\tadmission_accepted\tadmission_rejected\thot_first_p95_ms\thot_cursor_p95_ms\tcold_first_p95_ms\tcold_cursor_p95_ms\tbackend_cpu_percent\tpostgres_cpu_percent\thikari_active\thikari_pending\thikari_max\tlog_path\tsummary_json\n" >"${summary_tsv}"
}

append_summary() {
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" "$@" >>"${summary_tsv}"
}

run_combination() {
  local admission="$1"
  local db_pool="$2"
  local server_threads="$3"
  local vus="$4"
  local report_name="${matrix_name}-admission${admission}-pool${db_pool}-threads${server_threads}-vu${vus}"
  local log_path="${report_dir}/${report_name}.log"
  local summary_json="build/reports/k6/${report_name}-summary.json"
  local stats_path="${report_dir}/${report_name}-docker-stats.tsv"
  local accepted_before rejected_before accepted_after rejected_after
  local accepted_delta rejected_delta total_admission admission_429_rate
  local log_429_count
  local status http_failed_rate http_reqs hot_first hot_cursor cold_first cold_cursor
  local backend_cpu postgres_cpu hikari_active hikari_pending hikari_max

  echo "[transaction-read-matrix] running admission=${admission} db_pool=${db_pool} server_threads=${server_threads} vus=${vus}"

  OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX="${admission}" \
  DB_POOL_MAX_SIZE="${db_pool}" \
  SERVER_THREADS_MAX="${server_threads}" \
    docker compose "${compose_files[@]}" --profile loadtest up -d --force-recreate aquila-bank-backend prometheus grafana

  wait_for_backend_readiness
  wait_for_prometheus_readiness

  # 조합별 backend env가 docker compose run 의존성 처리로 바뀌지 않도록 k6는 --no-deps로 실행합니다.
  accepted_before="$(prometheus_value 'aquila_api_admission_requests_total{group="transaction-read",outcome="accepted"}')"
  rejected_before="$(prometheus_value 'aquila_api_admission_requests_total{group="transaction-read",outcome="rejected"}')"

  start_cpu_sampler "${stats_path}"
  set +e
  K6_REPORT_NAME="${report_name}" \
  K6_VUS="${vus}" \
    K6_ARCHIVE_RESULTS=false \
    tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps >"${log_path}" 2>&1
  status=$?
  set -e
  stop_cpu_sampler

  if [[ "${metric_scrape_wait_seconds}" -gt 0 ]]; then
    sleep "${metric_scrape_wait_seconds}"
  fi

  accepted_after="$(prometheus_value 'aquila_api_admission_requests_total{group="transaction-read",outcome="accepted"}')"
  rejected_after="$(prometheus_value 'aquila_api_admission_requests_total{group="transaction-read",outcome="rejected"}')"
  accepted_delta="$(numeric_delta "${accepted_before}" "${accepted_after}")"
  rejected_delta="$(numeric_delta "${rejected_before}" "${rejected_after}")"
  total_admission="$(numeric_sum "${accepted_delta}" "${rejected_delta}")"
  admission_429_rate="$(ratio "${rejected_delta}" "${total_admission}")"

  http_failed_rate="$(metric_from_json "${summary_json}" "http_req_failed" "rate")"
  http_reqs="$(metric_from_json "${summary_json}" "http_reqs" "count")"
  log_429_count="$(log_count "${log_path}" "returned HTTP 429")"
  # k6 status log는 scrape 지연 영향을 받지 않아 admission 429 rate의 1차 근거로 사용합니다.
  if [[ "${http_reqs}" != "n/a" ]]; then
    rejected_delta="${log_429_count}"
    accepted_delta="$(numeric_subtract "${http_reqs}" "${log_429_count}")"
    admission_429_rate="$(ratio "${log_429_count}" "${http_reqs}")"
  fi
  hot_first="$(metric_from_json "${summary_json}" "aquila_transaction_hot_first_ms" "p(95)")"
  hot_cursor="$(metric_from_json "${summary_json}" "aquila_transaction_hot_cursor_ms" "p(95)")"
  cold_first="$(metric_from_json "${summary_json}" "aquila_transaction_cold_first_ms" "p(95)")"
  cold_cursor="$(metric_from_json "${summary_json}" "aquila_transaction_cold_cursor_ms" "p(95)")"
  backend_cpu="$(container_cpu_max_percent "${stats_path}" aquila-bank-backend-loadtest)"
  postgres_cpu="$(container_cpu_max_percent "${stats_path}" aquila-bank-postgres)"
  if [[ "${backend_cpu}" == "n/a" ]]; then
    backend_cpu="$(container_cpu_percent aquila-bank-backend-loadtest)"
  fi
  if [[ "${postgres_cpu}" == "n/a" ]]; then
    postgres_cpu="$(container_cpu_percent aquila-bank-postgres)"
  fi
  hikari_active="$(prometheus_value 'max(hikaricp_connections_active{pool="aquila-bank-pool"})')"
  hikari_pending="$(prometheus_value 'max(hikaricp_connections_pending{pool="aquila-bank-pool"})')"
  hikari_max="$(prometheus_value 'max(hikaricp_connections_max{pool="aquila-bank-pool"})')"

  append_summary \
    "${status}" "${admission}" "${db_pool}" "${server_threads}" "${vus}" "${report_name}" \
    "${http_failed_rate}" "${http_reqs}" "${admission_429_rate}" "${accepted_delta}" "${rejected_delta}" \
    "${hot_first}" "${hot_cursor}" "${cold_first}" "${cold_cursor}" \
    "${backend_cpu}" "${postgres_cpu}" "${hikari_active}" "${hikari_pending}" "${hikari_max}" \
    "${log_path}" "${summary_json}"

  if [[ "${status}" -ne 0 && "${continue_on_failure}" != "true" ]]; then
    echo "[transaction-read-matrix] combination failed and MATRIX_CONTINUE_ON_FAILURE=false: ${report_name}" >&2
    exit "${status}"
  fi
}

write_header

IFS=',' read -r -a admissions <<<"${admission_values}"
IFS=',' read -r -a pools <<<"${db_pool_values}"
IFS=',' read -r -a server_threads_items <<<"${server_thread_values}"
IFS=',' read -r -a vus_items <<<"${vu_values}"

for admission in "${admissions[@]}"; do
  for db_pool in "${pools[@]}"; do
    for server_threads in "${server_threads_items[@]}"; do
      for vus in "${vus_items[@]}"; do
        run_combination "${admission}" "${db_pool}" "${server_threads}" "${vus}"
      done
    done
  done
done

echo "[transaction-read-matrix] summary=${summary_tsv}"
