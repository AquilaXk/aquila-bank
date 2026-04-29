#!/usr/bin/env bash
set -euo pipefail

STAGING_BASE_URL="${STAGING_BASE_URL:-}"
STAGING_SMOKE_BASE_URL="${STAGING_SMOKE_BASE_URL:-${STAGING_BASE_URL}}"
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

if [[ -n "$STAGING_SMOKE_AUTH_HEADER_NAME" || -n "$STAGING_SMOKE_AUTH_HEADER_VALUE" ]]; then
  require_env STAGING_SMOKE_AUTH_HEADER_NAME
  require_env STAGING_SMOKE_AUTH_HEADER_VALUE
fi

should_send_body() {
  local method="$1"
  local body="$2"
  local method_upper

  [[ -n "$body" ]] || return 1
  method_upper="$(printf '%s' "$method" | tr '[:lower:]' '[:upper:]')"

  # GET 계열 smoke는 프록시/서버별 body 처리 차이가 커서 전송하지 않는다.
  case "$method_upper" in
    GET | HEAD | DELETE)
      return 1
      ;;
  esac

  return 0
}

request() {
  local label="$1"
  local method="$2"
  local url="$3"
  local body="${4:-}"
  local curl_args=(
    --fail-with-body
    --show-error
    --silent
    --max-time "$STAGING_SMOKE_TIMEOUT_SECONDS"
    --request "$method"
  )

  if [[ -n "$STAGING_SMOKE_AUTH_HEADER_NAME" ]]; then
    curl_args+=(--header "${STAGING_SMOKE_AUTH_HEADER_NAME}: ${STAGING_SMOKE_AUTH_HEADER_VALUE}")
  fi

  echo "[staging-smoke] ${label}: ${method} ${url}"
  if should_send_body "$method" "$body"; then
    curl_args+=(--header "Content-Type: application/json" --data "$body")
  fi

  curl "${curl_args[@]}" "$url" >/dev/null
}

require_env STAGING_SMOKE_BASE_URL
require_env STAGING_SMOKE_READ_PATH
require_env STAGING_SMOKE_WRITE_PATH

request "health" "GET" "$(join_url "$STAGING_SMOKE_BASE_URL" "$STAGING_SMOKE_HEALTH_PATH")"
request "read" "GET" "$(join_url "$STAGING_SMOKE_BASE_URL" "$STAGING_SMOKE_READ_PATH")"
request \
  "write" \
  "$STAGING_SMOKE_WRITE_METHOD" \
  "$(join_url "$STAGING_SMOKE_BASE_URL" "$STAGING_SMOKE_WRITE_PATH")" \
  "$STAGING_SMOKE_WRITE_BODY"

echo "[staging-smoke] passed"
