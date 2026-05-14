#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-offhost-capacity-env-doctor.sh [--print-plan|--print-env-template|--dry-run]

Environment:
  OFFHOST_CAPACITY_ENV_FILE optional local env file to source
  OFFHOST_CAPACITY_CHECK_CONNECTIVITY default true
  CAPACITY_K6_DOCKER_CONTEXT required
  CAPACITY_K6_REMOTE_BASE_URL required
  CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL required
  CAPACITY_REMOTE_PROMETHEUS_RW_REQUIRED default true
  CAPACITY_K6_REMOTE_WORKDIR default current working directory
  CAPACITY_K6_HOST_METRICS_TSV optional TSV artifact for generator/target CPU and network metrics
  CAPACITY_K6_HOST_METRICS_TIMELINE_TSV optional load-coupled generator/target CPU and network timeline TSV ref
  CAPACITY_K6_VU16_SUMMARY_JSON optional VU16 saturation summary JSON ref
  CAPACITY_K6_BURST_MATRIX_TSV optional burst 32/48/64/80/96 matrix TSV ref
  CAPACITY_K6_SOURCE_EVIDENCE_TSV optional source-level edge/backend 429 and latency TSV ref
  CAPACITY_K6_GENERATOR_HOST_METRICS_TSV optional generator host CPU/network TSV ref
  CAPACITY_K6_TARGET_HOST_METRICS_TSV optional app/DB target host CPU/network TSV ref
  CAPACITY_K6_HOST_METRICS_RUN_ID optional host metrics run id ref
  CAPACITY_REQUIRE_HOST_METRICS default true
  CAPACITY_REMOTE_PREFLIGHT_TIMEOUT_SECONDS default 30
  CAPACITY_REMOTE_PREFLIGHT_IMAGE default curlimages/curl:8.11.1
  CAPACITY_REMOTE_READINESS_PATH default /actuator/health/readiness
  CAPACITY_K6_AUTH_TOKEN_REQUIRED default false
  CAPACITY_K6_AUTH_TOKEN optional bearer token; printed only as present/missing

Examples:
  OFFHOST_CAPACITY_ENV_FILE=.env/offhost-capacity.env \
    tools/test/run-offhost-capacity-env-doctor.sh
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --print-env-template)
      mode="print-env-template"
      ;;
    --dry-run)
      mode="dry-run"
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

print_env_template() {
  cat tools/test/offhost-capacity.env.example
}

if [[ "${mode}" == "print-env-template" ]]; then
  print_env_template
  exit 0
fi

env_file="${OFFHOST_CAPACITY_ENV_FILE:-${CAPACITY_ENV_FILE:-}}"
if [[ -n "${env_file}" ]]; then
  if [[ ! -f "${env_file}" ]]; then
    echo "OFFHOST_CAPACITY_ENV_FILE not found: ${env_file}" >&2
    exit 1
  fi
  set -a
  # 로컬 전용 원격 endpoint 값만 shell env로 주입하고 저장소에는 기록하지 않습니다.
  source "${env_file}"
  set +a
fi

docker_context="${CAPACITY_K6_DOCKER_CONTEXT:-${K6_DOCKER_CONTEXT:-}}"
remote_base_url="${CAPACITY_K6_REMOTE_BASE_URL:-${K6_REMOTE_BASE_URL:-}}"
remote_prometheus_rw_url="${CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL:-${K6_REMOTE_PROMETHEUS_RW_SERVER_URL:-}}"
remote_prometheus_rw_required="${CAPACITY_REMOTE_PROMETHEUS_RW_REQUIRED:-${K6_REMOTE_PROMETHEUS_RW_REQUIRED:-true}}"
remote_workdir="${CAPACITY_K6_REMOTE_WORKDIR:-${K6_REMOTE_WORKDIR:-$(pwd)}}"
host_metrics_run_id="${CAPACITY_K6_HOST_METRICS_RUN_ID:-${K6_HOST_METRICS_RUN_ID:-${CAPACITY_NAME:-offhost-capacity}}}"
host_metrics_default_dir="build/reports/k6/${host_metrics_run_id}"
host_metrics_tsv="${CAPACITY_K6_HOST_METRICS_TSV:-${K6_HOST_METRICS_TSV:-${host_metrics_default_dir}/host-metrics.tsv}}"
host_metrics_timeline_tsv="${CAPACITY_K6_HOST_METRICS_TIMELINE_TSV:-${K6_HOST_METRICS_TIMELINE_TSV:-${host_metrics_default_dir}/host-metrics-timeline.tsv}}"
vu16_summary_json="${CAPACITY_K6_VU16_SUMMARY_JSON:-${K6_VU16_SUMMARY_JSON:-}}"
burst_matrix_tsv="${CAPACITY_K6_BURST_MATRIX_TSV:-${K6_BURST_MATRIX_TSV:-}}"
source_evidence_tsv="${CAPACITY_K6_SOURCE_EVIDENCE_TSV:-${K6_SOURCE_EVIDENCE_TSV:-}}"
generator_host_metrics_tsv="${CAPACITY_K6_GENERATOR_HOST_METRICS_TSV:-${K6_GENERATOR_HOST_METRICS_TSV:-${host_metrics_default_dir}/generator-host-metrics.tsv}}"
target_host_metrics_tsv="${CAPACITY_K6_TARGET_HOST_METRICS_TSV:-${K6_TARGET_HOST_METRICS_TSV:-${host_metrics_default_dir}/target-host-metrics.tsv}}"
require_host_metrics="${CAPACITY_REQUIRE_HOST_METRICS:-true}"
check_connectivity="${OFFHOST_CAPACITY_CHECK_CONNECTIVITY:-true}"
timeout_seconds="${CAPACITY_REMOTE_PREFLIGHT_TIMEOUT_SECONDS:-${K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS:-30}}"
preflight_image="${CAPACITY_REMOTE_PREFLIGHT_IMAGE:-${K6_REMOTE_PREFLIGHT_IMAGE:-curlimages/curl:8.11.1}}"
readiness_path="${CAPACITY_REMOTE_READINESS_PATH:-${K6_REMOTE_READINESS_PATH:-/actuator/health/readiness}}"
readiness_url="${remote_base_url%/}${readiness_path}"
auth_token_required="${CAPACITY_K6_AUTH_TOKEN_REQUIRED:-false}"
auth_token="${CAPACITY_K6_AUTH_TOKEN:-${K6_AUTH_TOKEN:-}}"

require_bool() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_env_value() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "${name} is required" >&2
    exit 1
  fi
}

require_url() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^https?://[^[:space:]]+$ ]]; then
    echo "${name} must be an http(s) URL: ${value}" >&2
    exit 1
  fi
}

require_bool "OFFHOST_CAPACITY_CHECK_CONNECTIVITY" "${check_connectivity}"
require_bool "CAPACITY_K6_AUTH_TOKEN_REQUIRED" "${auth_token_required}"
require_bool "CAPACITY_REQUIRE_HOST_METRICS" "${require_host_metrics}"
require_bool "CAPACITY_REMOTE_PROMETHEUS_RW_REQUIRED" "${remote_prometheus_rw_required}"
require_positive_integer "CAPACITY_REMOTE_PREFLIGHT_TIMEOUT_SECONDS" "${timeout_seconds}"
require_env_value "CAPACITY_K6_DOCKER_CONTEXT" "${docker_context}"
require_env_value "CAPACITY_K6_REMOTE_BASE_URL" "${remote_base_url}"
require_env_value "CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL" "${remote_prometheus_rw_url}"
require_url "CAPACITY_K6_REMOTE_BASE_URL" "${remote_base_url}"
require_url "CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL" "${remote_prometheus_rw_url}"
if [[ "${auth_token_required}" == "true" ]]; then
  require_env_value "CAPACITY_K6_AUTH_TOKEN" "${auth_token}"
fi
if [[ "${require_host_metrics}" == "true" ]]; then
  require_env_value "CAPACITY_K6_GENERATOR_HOST_METRICS_TSV is required when CAPACITY_REQUIRE_HOST_METRICS=true" "${generator_host_metrics_tsv}"
  require_env_value "CAPACITY_K6_TARGET_HOST_METRICS_TSV is required when CAPACITY_REQUIRE_HOST_METRICS=true" "${target_host_metrics_tsv}"
  require_env_value "CAPACITY_K6_HOST_METRICS_TIMELINE_TSV is required when CAPACITY_REQUIRE_HOST_METRICS=true" "${host_metrics_timeline_tsv}"
fi

print_plan() {
  echo "[offhost-capacity-env-doctor] mode=${mode}"
  echo "[offhost-capacity-env-doctor] env_file=${env_file:-missing}"
  echo "[offhost-capacity-env-doctor] docker_context=${docker_context}"
  echo "[offhost-capacity-env-doctor] remote_base_url=${remote_base_url}"
  echo "[offhost-capacity-env-doctor] remote_prometheus_rw_url=${remote_prometheus_rw_url}"
  echo "[offhost-capacity-env-doctor] remote_prometheus_rw_required=${remote_prometheus_rw_required}"
  echo "[offhost-capacity-env-doctor] remote_workdir=${remote_workdir}"
  echo "[offhost-capacity-env-doctor] host_metrics_tsv=${host_metrics_tsv:-missing}"
  echo "[offhost-capacity-env-doctor] host_metrics_timeline_tsv=${host_metrics_timeline_tsv:-missing}"
  echo "[offhost-capacity-env-doctor] vu16_summary_json=${vu16_summary_json:-missing}"
  echo "[offhost-capacity-env-doctor] burst_matrix_tsv=${burst_matrix_tsv:-missing}"
  echo "[offhost-capacity-env-doctor] source_evidence_tsv=${source_evidence_tsv:-missing}"
  echo "[offhost-capacity-env-doctor] generator_host_metrics_tsv=${generator_host_metrics_tsv:-missing}"
  echo "[offhost-capacity-env-doctor] target_host_metrics_tsv=${target_host_metrics_tsv:-missing}"
  echo "[offhost-capacity-env-doctor] host_metrics_run_id=${host_metrics_run_id:-missing}"
  echo "[offhost-capacity-env-doctor] require_host_metrics=${require_host_metrics}"
  echo "[offhost-capacity-env-doctor] readiness_url=${readiness_url}"
  echo "[offhost-capacity-env-doctor] preflight_image=${preflight_image}"
  echo "[offhost-capacity-env-doctor] timeout_seconds=${timeout_seconds}"
  echo "[offhost-capacity-env-doctor] check_connectivity=${check_connectivity}"
  echo "[offhost-capacity-env-doctor] auth_token_required=${auth_token_required}"
  if [[ -n "${auth_token}" ]]; then
    echo "[offhost-capacity-env-doctor] auth_token=present"
  else
    echo "[offhost-capacity-env-doctor] auth_token=missing"
  fi
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  echo "[offhost-capacity-env-doctor] dry-run passed"
  exit 0
fi

if [[ "${check_connectivity}" != "true" ]]; then
  echo "[offhost-capacity-env-doctor] connectivity check skipped"
  exit 0
fi

docker context inspect "${docker_context}" >/dev/null
docker --context "${docker_context}" info >/dev/null
docker --context "${docker_context}" run --rm "${preflight_image}" \
  -fsS --max-time "${timeout_seconds}" "${readiness_url}" >/dev/null
docker --context "${docker_context}" run --rm --entrypoint sh "${preflight_image}" \
  -c 'status="$(curl -sS -o /dev/null -w "%{http_code}" --max-time "$1" -X POST "$2" || echo 000)"; case "${status}" in 2*|3*|4*) exit 0 ;; *) if [ "$3" = "false" ]; then echo "remote prometheus remote-write optional; continuing status=${status}" >&2; exit 0; fi; echo "remote prometheus remote-write preflight failed: status=${status}" >&2; exit 1 ;; esac' \
  sh "${timeout_seconds}" "${remote_prometheus_rw_url}" "${remote_prometheus_rw_required}"

echo "[offhost-capacity-env-doctor] connectivity passed"
