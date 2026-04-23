#!/usr/bin/env bash
set -euo pipefail

STAGING_BASE_URL="${STAGING_BASE_URL:-}"
STAGING_SMOKE_HEALTH_PATH="${STAGING_SMOKE_HEALTH_PATH:-/actuator/health}"
STAGING_SMOKE_READ_PATH="${STAGING_SMOKE_READ_PATH:-}"
STAGING_SMOKE_WRITE_PATH="${STAGING_SMOKE_WRITE_PATH:-}"
STAGING_SMOKE_WRITE_METHOD="${STAGING_SMOKE_WRITE_METHOD:-POST}"
STAGING_SMOKE_WRITE_BODY="${STAGING_SMOKE_WRITE_BODY:-{}}"
STAGING_SMOKE_TIMEOUT_SECONDS="${STAGING_SMOKE_TIMEOUT_SECONDS:-5}"
STAGING_SMOKE_AUTH_HEADER_NAME="${STAGING_SMOKE_AUTH_HEADER_NAME:-}"
STAGING_SMOKE_AUTH_HEADER_VALUE="${STAGING_SMOKE_AUTH_HEADER_VALUE:-}"

require_env() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "$value" ]]; then
    echo "[staging-smoke] missing required env: ${name}" >&2
    exit 1
  fi
}

join_url() {
  local base="${1%/}"
  local path="$2"
  if [[ "$path" != /* ]]; then
    path="/${path}"
  fi
  printf '%s%s' "$base" "$path"
}

auth_header_args=()
if [[ -n "$STAGING_SMOKE_AUTH_HEADER_NAME" || -n "$STAGING_SMOKE_AUTH_HEADER_VALUE" ]]; then
  require_env STAGING_SMOKE_AUTH_HEADER_NAME
  require_env STAGING_SMOKE_AUTH_HEADER_VALUE
  auth_header_args=(--header "${STAGING_SMOKE_AUTH_HEADER_NAME}: ${STAGING_SMOKE_AUTH_HEADER_VALUE}")
fi

request() {
  local label="$1"
  local method="$2"
  local url="$3"
  local body="${4:-}"
  echo "[staging-smoke] ${label}: ${method} ${url}"
  if [[ -n "$body" ]]; then
    curl --fail-with-body --show-error --silent --max-time "$STAGING_SMOKE_TIMEOUT_SECONDS" \
      --request "$method" \
      --header "Content-Type: application/json" \
      "${auth_header_args[@]}" \
      --data "$body" \
      "$url" >/dev/null
    return
  fi

  curl --fail-with-body --show-error --silent --max-time "$STAGING_SMOKE_TIMEOUT_SECONDS" \
    --request "$method" \
    "${auth_header_args[@]}" \
    "$url" >/dev/null
}

require_env STAGING_BASE_URL
require_env STAGING_SMOKE_READ_PATH
require_env STAGING_SMOKE_WRITE_PATH

request "health" "GET" "$(join_url "$STAGING_BASE_URL" "$STAGING_SMOKE_HEALTH_PATH")"
request "read" "GET" "$(join_url "$STAGING_BASE_URL" "$STAGING_SMOKE_READ_PATH")"
request \
  "write" \
  "$STAGING_SMOKE_WRITE_METHOD" \
  "$(join_url "$STAGING_BASE_URL" "$STAGING_SMOKE_WRITE_PATH")" \
  "$STAGING_SMOKE_WRITE_BODY"

echo "[staging-smoke] passed"
