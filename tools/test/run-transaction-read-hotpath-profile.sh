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
  PROFILE_DURATION          default 75s
  PROFILE_ADMISSION         default 8
  PROFILE_DB_POOL_MAX_SIZE  default 4
  PROFILE_BUILD_BACKEND     default true
  PROFILE_READINESS_TIMEOUT_SECONDS default 90
  PROFILE_BACKEND_HEALTH_URL default http://localhost:${BACKEND_PORT:-8080}/actuator/health
  K6_VUS                    default 8
  K6_DURATION               default 1m
  K6_LIMIT                  default 50
  K6_REPORT_NAME            default <profile-name>-k6

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
profile_duration="${PROFILE_DURATION-75s}"
profile_admission="${PROFILE_ADMISSION:-8}"
profile_db_pool_max_size="${PROFILE_DB_POOL_MAX_SIZE:-4}"
profile_build_backend="${PROFILE_BUILD_BACKEND:-true}"
profile_readiness_timeout_seconds="${PROFILE_READINESS_TIMEOUT_SECONDS:-90}"
backend_health_url="${PROFILE_BACKEND_HEALTH_URL:-http://localhost:${BACKEND_PORT:-8080}/actuator/health}"
k6_vus="${K6_VUS:-8}"
k6_duration="${K6_DURATION:-1m}"
k6_limit="${K6_LIMIT:-50}"
k6_report_name="${K6_REPORT_NAME:-${profile_name}-k6}"
report_dir="build/reports/profiling/${profile_name}"
jfr_container_path="/tmp/${profile_name}.jfr"
jfr_artifact="${report_dir}/${profile_name}.jfr"
jfr_summary_txt="${report_dir}/${profile_name}-jfr-summary.txt"
jfr_hot_methods_txt="${report_dir}/${profile_name}-jfr-hot-methods.txt"
jfr_cpu_hot_methods_txt="${report_dir}/${profile_name}-jfr-cpu-time-hot-methods.txt"
jfr_allocation_by_class_txt="${report_dir}/${profile_name}-jfr-allocation-by-class.txt"
jfr_allocation_by_site_txt="${report_dir}/${profile_name}-jfr-allocation-by-site.txt"
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

require_env() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "${value}" ]]; then
    echo "${name} is required" >&2
    exit 1
  fi
}

require_duration "PROFILE_DURATION" "${profile_duration}"
require_duration "K6_DURATION" "${k6_duration}"
require_positive_integer "PROFILE_ADMISSION" "${profile_admission}"
require_positive_integer "PROFILE_DB_POOL_MAX_SIZE" "${profile_db_pool_max_size}"
require_positive_integer "PROFILE_READINESS_TIMEOUT_SECONDS" "${profile_readiness_timeout_seconds}"
require_positive_integer "K6_VUS" "${k6_vus}"
require_positive_integer "K6_LIMIT" "${k6_limit}"

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

print_plan() {
  echo "[transaction-read-hotpath-profile] profile=${profile_name}"
  echo "[transaction-read-hotpath-profile] profiler=jfr"
  echo "[transaction-read-hotpath-profile] admission=${profile_admission} db_pool=${profile_db_pool_max_size}"
  echo "[transaction-read-hotpath-profile] k6 vus=${k6_vus} duration=${k6_duration} limit=${k6_limit} report=${k6_report_name}"
  echo "[transaction-read-hotpath-profile] jfr duration=${profile_duration}"
  echo "[transaction-read-hotpath-profile] backend_health_url=${backend_health_url}"
  echo "[transaction-read-hotpath-profile] readiness_timeout_seconds=${profile_readiness_timeout_seconds}"
  echo "[transaction-read-hotpath-profile] artifact=${jfr_artifact}"
  echo "[transaction-read-hotpath-profile] jfr_summary=${jfr_summary_txt}"
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
docker compose "${compose_files[@]}" --profile loadtest run --rm --no-deps \
  -e K6_REPORT_NAME="${k6_report_name}" \
  -e K6_HOT_ACCOUNT_ID="${K6_HOT_ACCOUNT_ID}" \
  -e K6_HOT_FROM="${K6_HOT_FROM}" \
  -e K6_HOT_TO="${K6_HOT_TO}" \
  -e K6_COLD_ACCOUNT_ID="${K6_COLD_ACCOUNT_ID}" \
  -e K6_COLD_FROM="${K6_COLD_FROM}" \
  -e K6_COLD_TO="${K6_COLD_TO}" \
  -e K6_AUTH_TOKEN="${K6_AUTH_TOKEN:-}" \
  -e K6_VUS="${k6_vus}" \
  -e K6_DURATION="${k6_duration}" \
  -e K6_LIMIT="${k6_limit}" \
  k6-transaction-read-100m >"${run_log}" 2>&1
k6_status=$?
set -e

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

cat >"${summary_md}" <<SUMMARY
# Transaction Read Hotpath Profile

## Environment

- profile: ${profile_name}
- profiler: JFR
- admission: ${profile_admission}
- db pool max size: ${profile_db_pool_max_size}
- k6 vus: ${k6_vus}
- k6 duration: ${k6_duration}
- k6 limit: ${k6_limit}

## Artifacts

- jfr: ${jfr_artifact}
- jfr summary: ${jfr_summary_txt}
- jfr hot methods: ${jfr_hot_methods_txt}
- jfr cpu hot methods: ${jfr_cpu_hot_methods_txt}
- jfr allocation by class: ${jfr_allocation_by_class_txt}
- jfr allocation by site: ${jfr_allocation_by_site_txt}
- k6 summary json: ${k6_summary_json}
- k6 summary markdown: ${k6_summary_md}
- run log: ${run_log}

## Result

- k6 status: ${k6_status}

## Notes

- JFR artifact는 저장소에 commit하지 않고 `build/reports/profiling` 아래에만 둡니다.
- response mapping/serialization 최적화는 이 artifact를 분석한 뒤 별도 PR에서 진행합니다.
SUMMARY

echo "[transaction-read-hotpath-profile] summary=${summary_md}"
exit "${k6_status}"
