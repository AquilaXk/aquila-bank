#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-outbox-provider-backlog-local-gate.sh [--print-plan|--dry-run]

Environment:
  OUTBOX_LOCAL_BUILD_BACKEND default true
  OUTBOX_LOCAL_TOKEN_MODE    generate|provided, default generate
  OUTBOX_LOCAL_READINESS_TIMEOUT_SECONDS default 120
  OUTBOX_BACKLOG_BASE_URL    default http://localhost:${LOADTEST_BACKEND_PORT:-18080}
  OUTBOX_LOCAL_READINESS_PATH default /actuator/health/readiness
  OUTBOX_BACKLOG_TOKEN       required when OUTBOX_LOCAL_TOKEN_MODE=provided

Examples:
  tools/test/run-outbox-provider-backlog-local-gate.sh --print-plan
  tools/test/run-outbox-provider-backlog-local-gate.sh --dry-run
  OUTBOX_BACKLOG_NAME=local-outbox tools/test/run-outbox-provider-backlog-local-gate.sh
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
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

build_backend="${OUTBOX_LOCAL_BUILD_BACKEND:-true}"
token_mode="${OUTBOX_LOCAL_TOKEN_MODE:-generate}"
readiness_timeout_seconds="${OUTBOX_LOCAL_READINESS_TIMEOUT_SECONDS:-120}"
readiness_path="${OUTBOX_LOCAL_READINESS_PATH:-/actuator/health/readiness}"
base_url="${OUTBOX_BACKLOG_BASE_URL:-http://localhost:${LOADTEST_BACKEND_PORT:-18080}}"
token_issuer="${SECURITY_INTERNAL_SERVICE_TOKEN_ISSUER:-dev-internal-service}"
token_audience="${SECURITY_INTERNAL_SERVICE_TOKEN_AUDIENCE:-aquila-internal-api}"
token_key_id="${SECURITY_INTERNAL_SERVICE_TOKEN_ACTIVE_KEY_ID:-ops-202604}"
token_secret="${SECURITY_INTERNAL_SERVICE_TOKEN_KEYS_OPS_202604:-dev-internal-service-secret-ops-202604}"
base_url="${base_url%/}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)

require_bool_value "OUTBOX_LOCAL_BUILD_BACKEND" "${build_backend}"
require_positive_integer_value "OUTBOX_LOCAL_READINESS_TIMEOUT_SECONDS" "${readiness_timeout_seconds}"
case "${token_mode}" in
  generate|provided) ;;
  *) echo "OUTBOX_LOCAL_TOKEN_MODE must be generate or provided: ${token_mode}" >&2; exit 1 ;;
esac

print_plan() {
  echo "[outbox-backlog-local] mode=${mode}"
  echo "[outbox-backlog-local] build_backend=${build_backend}"
  echo "[outbox-backlog-local] token_mode=${token_mode}"
  echo "[outbox-backlog-local] readiness_timeout_seconds=${readiness_timeout_seconds}"
  echo "[outbox-backlog-local] readiness_path=${readiness_path}"
  echo "[outbox-backlog-local] base_url=${base_url}"
  echo "[outbox-backlog-local] token_issuer=${token_issuer}"
  echo "[outbox-backlog-local] token_audience=${token_audience}"
  echo "[outbox-backlog-local] token_key_id=${token_key_id}"
  echo "[outbox-backlog-local] notification_ops_enabled=true"
  echo "[outbox-backlog-local] services=postgres,aquila-bank-backend"
  echo "[outbox-backlog-local] gate=tools/test/run-outbox-provider-small-batch-backlog-gate.sh"
}

print_dry_run() {
  if [[ "${build_backend}" == "true" ]]; then
    echo "tools/test/with-resource-lock.sh back-gradle-outbox-local-bootjar ./back/gradlew -p back bootJar"
  fi
  echo "OUTBOX_OPS_ENABLED=true NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED=true NOTIFICATION_CHANNEL_PROVIDER_OPS_ENABLED=true SECURITY_INTERNAL_SERVICE_TOKEN_ISSUER=${token_issuer} SECURITY_INTERNAL_SERVICE_TOKEN_AUDIENCE=${token_audience} docker compose ${compose_files[*]} --profile loadtest up -d postgres aquila-bank-backend"
  if [[ "${token_mode}" == "generate" ]]; then
    echo "OUTBOX_BACKLOG_TOKEN=\$(tools/test/issue-internal-service-token.sh internal:outbox-ops outbox-backlog-local)"
  else
    echo "OUTBOX_BACKLOG_TOKEN=***"
  fi
  echo "OUTBOX_BACKLOG_BASE_URL=${base_url} tools/test/run-outbox-provider-small-batch-backlog-gate.sh"
}

wait_for_backend() {
  local deadline=$((SECONDS + readiness_timeout_seconds))
  while ((SECONDS < deadline)); do
    if curl -fsS "${base_url}${readiness_path}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "backend readiness did not become healthy: ${base_url}" >&2
  exit 1
}

start_runtime() {
  if [[ "${build_backend}" == "true" ]]; then
    tools/test/with-resource-lock.sh back-gradle-outbox-local-bootjar ./back/gradlew -p back bootJar
  fi
  OUTBOX_OPS_ENABLED=true \
  OUTBOX_OPS_TOKEN="${OUTBOX_OPS_TOKEN:-local-outbox-ops-token}" \
  NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED=true \
  NOTIFICATION_CHANNEL_PROVIDER_OPS_ENABLED=true \
  SECURITY_INTERNAL_SERVICE_TOKEN_ISSUER="${token_issuer}" \
  SECURITY_INTERNAL_SERVICE_TOKEN_AUDIENCE="${token_audience}" \
  SECURITY_INTERNAL_SERVICE_TOKEN_ACTIVE_KEY_ID="${token_key_id}" \
  SECURITY_INTERNAL_SERVICE_TOKEN_KEYS_OPS_202604="${token_secret}" \
    docker compose "${compose_files[@]}" --profile loadtest up -d postgres aquila-bank-backend
  wait_for_backend
}

resolve_token() {
  if [[ "${token_mode}" == "provided" ]]; then
    if [[ -z "${OUTBOX_BACKLOG_TOKEN:-}" ]]; then
      echo "OUTBOX_BACKLOG_TOKEN is required when OUTBOX_LOCAL_TOKEN_MODE=provided" >&2
      exit 1
    fi
    printf "%s\n" "${OUTBOX_BACKLOG_TOKEN}"
    return 0
  fi
  SECURITY_INTERNAL_SERVICE_TOKEN_ISSUER="${token_issuer}" \
  SECURITY_INTERNAL_SERVICE_TOKEN_AUDIENCE="${token_audience}" \
  SECURITY_INTERNAL_SERVICE_TOKEN_ACTIVE_KEY_ID="${token_key_id}" \
  SECURITY_INTERNAL_SERVICE_TOKEN_KEYS_OPS_202604="${token_secret}" \
    tools/test/issue-internal-service-token.sh internal:outbox-ops outbox-backlog-local
}

run_gate() {
  local token
  token="$(resolve_token)"
  OUTBOX_BACKLOG_BASE_URL="${base_url}" \
  OUTBOX_BACKLOG_TOKEN="${token}" \
    tools/test/run-outbox-provider-small-batch-backlog-gate.sh
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_dry_run
  exit 0
fi

start_runtime
run_gate
