#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "::error::Missing ${name}."
    exit 1
  fi
}

build_url() {
  local base="${PRODUCTION_BASE_URL%/}"
  local path="$1"
  if [[ "${path}" != /* ]]; then
    path="/${path}"
  fi
  printf '%s%s' "${base}" "${path}"
}

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local url
  url="$(build_url "${path}")"

  local args=(
    --fail-with-body
    --show-error
    --silent
    --location
    --max-time "${PRODUCTION_SMOKE_TIMEOUT_SECONDS}"
    --request "${method}"
  )

  if [ -n "${PRODUCTION_SMOKE_AUTH_HEADER_NAME:-}" ] || [ -n "${PRODUCTION_SMOKE_AUTH_HEADER_VALUE:-}" ]; then
    require_env PRODUCTION_SMOKE_AUTH_HEADER_NAME
    require_env PRODUCTION_SMOKE_AUTH_HEADER_VALUE
    args+=(--header "${PRODUCTION_SMOKE_AUTH_HEADER_NAME}: ${PRODUCTION_SMOKE_AUTH_HEADER_VALUE}")
  fi

  if [ -n "${body}" ]; then
    args+=(--header "Content-Type: application/json" --data "${body}")
  fi

  curl "${args[@]}" "${url}"
}

require_env TARGET_SHA
require_env PRODUCTION_BASE_URL
require_env PRODUCTION_SMOKE_SHA_PATH
require_env PRODUCTION_SMOKE_READ_PATH
require_env PRODUCTION_SMOKE_WRITE_PATH

PRODUCTION_SMOKE_HEALTH_PATH="${PRODUCTION_SMOKE_HEALTH_PATH:-/actuator/health}"
PRODUCTION_SMOKE_WRITE_METHOD="${PRODUCTION_SMOKE_WRITE_METHOD:-POST}"
PRODUCTION_SMOKE_WRITE_BODY="${PRODUCTION_SMOKE_WRITE_BODY:-{}}"
PRODUCTION_SMOKE_TIMEOUT_SECONDS="${PRODUCTION_SMOKE_TIMEOUT_SECONDS:-5}"

if [[ ! "${TARGET_SHA}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "::error::TARGET_SHA must be a full lowercase 40-character commit SHA."
  exit 1
fi

echo "Checking production health endpoint..."
request GET "${PRODUCTION_SMOKE_HEALTH_PATH}" >/dev/null

echo "Checking production deployed SHA..."
sha_response="$(request GET "${PRODUCTION_SMOKE_SHA_PATH}")"
# same SHA 배포만 성공 처리해 이전 버전 응답을 production 성공으로 기록하지 않는다.
if ! grep -Fq -- "${TARGET_SHA}" <<< "${sha_response}"; then
  echo "::error::Production SHA endpoint did not report TARGET_SHA ${TARGET_SHA}."
  exit 1
fi

echo "Checking production read endpoint..."
request GET "${PRODUCTION_SMOKE_READ_PATH}" >/dev/null

echo "Checking production write endpoint..."
request "${PRODUCTION_SMOKE_WRITE_METHOD}" "${PRODUCTION_SMOKE_WRITE_PATH}" "${PRODUCTION_SMOKE_WRITE_BODY}" >/dev/null

echo "Production post-deploy smoke passed."
