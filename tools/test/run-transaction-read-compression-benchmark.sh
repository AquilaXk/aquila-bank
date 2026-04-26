#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-compression-benchmark.sh [--print-plan]

Profile format:
  name:enabled:min_response_size_bytes

Optional environment:
  COMPRESSION_ADMISSION_MAX default K6_VUS or 8
  COMPRESSION_BACKEND_HEALTH_URL default http://localhost:${BACKEND_PORT:-8080}/actuator/health
  COMPRESSION_READINESS_TIMEOUT_SECONDS default 60
USAGE
}

mode="run"
if [[ "${1:-}" == "--print-plan" ]]; then
  mode="print-plan"
  shift
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
[[ "$#" -eq 0 ]] || { usage; exit 1; }

benchmark_name="${COMPRESSION_BENCHMARK_NAME:-transaction-read-compression-$(date +%Y-%m-%d-%H%M%S)}"
profiles="${COMPRESSION_PROFILES:-off:false:0,on-2kb:true:2048,on-8kb:true:8192}"
admission_max="${COMPRESSION_ADMISSION_MAX:-${K6_VUS:-8}}"
backend_health_url="${COMPRESSION_BACKEND_HEALTH_URL:-http://localhost:${BACKEND_PORT:-8080}/actuator/health}"
readiness_timeout_seconds="${COMPRESSION_READINESS_TIMEOUT_SECONDS:-60}"
report_dir="build/reports/k6/${benchmark_name}"
summary_tsv="${report_dir}/compression-summary.tsv"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)

restore_backend() {
  docker compose "${compose_files[@]}" --profile loadtest up -d --force-recreate aquila-bank-backend >/dev/null 2>&1 || true
}

wait_for_backend_readiness() {
  local deadline=$((SECONDS + readiness_timeout_seconds))
  local body=""

  echo "[transaction-compression] waiting backend readiness: ${backend_health_url}"
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

validate_profile() {
  local profile="$1"
  IFS=':' read -r name enabled min_size extra <<<"${profile}"
  if [[ -n "${extra:-}" || -z "${min_size:-}" ]]; then
    echo "COMPRESSION_PROFILES profile must have 3 fields: ${profile}" >&2
    exit 1
  fi
  [[ "${name}" =~ ^[a-z0-9][a-z0-9-]*$ ]] || { echo "bad profile name: ${name}" >&2; exit 1; }
  [[ "${enabled}" == "true" || "${enabled}" == "false" ]] || { echo "enabled must be true/false" >&2; exit 1; }
  [[ "${min_size}" =~ ^[0-9]+$ ]] || { echo "min size must be non-negative integer" >&2; exit 1; }
}

profile_names() {
  IFS=',' read -r -a items <<<"$1"
  local result="" item
  for item in "${items[@]}"; do
    [[ -z "${result}" ]] || result+=","
    result+="${item%%:*}"
  done
  echo "${result}"
}

validate_profiles() {
  IFS=',' read -r -a items <<<"$1"
  local item
  for item in "${items[@]}"; do
    validate_profile "${item}"
  done
}

require_positive_integer() {
  local name="$1"
  local value="${!name:-}"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer" >&2
    exit 1
  fi
}

validate_profiles "${profiles}"
require_positive_integer admission_max
require_positive_integer readiness_timeout_seconds

echo "[transaction-compression] benchmark=${benchmark_name}"
echo "[transaction-compression] profiles=$(profile_names "${profiles}")"
echo "[transaction-compression] admission max=${admission_max}"
echo "[transaction-compression] backend_health_url=${backend_health_url}"
echo "[transaction-compression] summary=${summary_tsv}"

if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

mkdir -p "${report_dir}"
printf "profile\tstatus\tenabled\tmin_response_size_bytes\tsummary_json\tlog_path\n" >"${summary_tsv}"
trap restore_backend EXIT

IFS=',' read -r -a items <<<"${profiles}"
for item in "${items[@]}"; do
  IFS=':' read -r name enabled min_size <<<"${item}"
  report_name="${benchmark_name}-${name}"
  log_path="${report_dir}/${report_name}.log"
  java_options="-XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=40 -Dserver.compression.enabled=${enabled} -Dserver.compression.min-response-size=${min_size}B"
  SERVER_COMPRESSION_ENABLED="${enabled}" \
  SERVER_COMPRESSION_MIN_RESPONSE_SIZE="${min_size}" \
  OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX="${admission_max}" \
  LOADTEST_BACKEND_JAVA_TOOL_OPTIONS="${java_options}" \
    docker compose "${compose_files[@]}" --profile loadtest up -d --force-recreate aquila-bank-backend prometheus grafana
  wait_for_backend_readiness
  set +e
  K6_REPORT_NAME="${report_name}" K6_ARCHIVE_RESULTS=false \
    tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps >"${log_path}" 2>&1
  status=$?
  set -e
  printf "%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${name}" "${status}" "${enabled}" "${min_size}" \
    "build/reports/k6/${report_name}-summary.json" "${log_path}" >>"${summary_tsv}"
done
