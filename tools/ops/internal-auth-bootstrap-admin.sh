#!/usr/bin/env bash
set -euo pipefail

dry_run=false

usage() {
  cat <<'USAGE' >&2
usage: tools/ops/internal-auth-bootstrap-admin.sh [--dry-run]

Required env:
  AQUILA_TEMP_ADMIN_LOGIN_ID       temporary admin login id
  AQUILA_TEMP_ADMIN_PASSWORD       temporary admin password, never printed
  AQUILA_TEMP_ADMIN_DISPLAY_NAME   temporary admin display name
  AUTH_BOOTSTRAP_SERVICE_TOKEN     service token with AUTH_BOOTSTRAP scope

Optional env:
  AQUILA_OPS_BASE_URL              default: http://localhost:8080
  INTERNAL_API_BASE_URL            fallback base url when AQUILA_OPS_BASE_URL is unset
  AQUILA_TEMP_ADMIN_REQUEST_ID     default: temp-admin-bootstrap-<UTC timestamp>
  AQUILA_TEMP_ADMIN_TTL_DAYS       default: 1
  AQUILA_TEMP_ADMIN_ACCOUNT_ID     when set, upsert membership after user bootstrap
  AQUILA_TEMP_ADMIN_MEMBERSHIP_ROLE    default: OWNER
  AQUILA_TEMP_ADMIN_MEMBERSHIP_STATUS  default: ACTIVE
  AUTH_ADMIN_SERVICE_TOKEN         printed as revoke-guide token placeholder only

Examples:
  tools/ops/internal-auth-bootstrap-admin.sh --dry-run

  AQUILA_OPS_BASE_URL=http://localhost:8080 \
  AUTH_BOOTSTRAP_SERVICE_TOKEN="$AUTH_BOOTSTRAP_SERVICE_TOKEN" \
  AQUILA_TEMP_ADMIN_LOGIN_ID="$AQUILA_TEMP_ADMIN_LOGIN_ID" \
  AQUILA_TEMP_ADMIN_PASSWORD="$AQUILA_TEMP_ADMIN_PASSWORD" \
  AQUILA_TEMP_ADMIN_DISPLAY_NAME="$AQUILA_TEMP_ADMIN_DISPLAY_NAME" \
  AQUILA_TEMP_ADMIN_ACCOUNT_ID=1001 \
  tools/ops/internal-auth-bootstrap-admin.sh
USAGE
}

fail() {
  echo "::error::$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

require_env() {
  local name="$1"
  local value="${!name:-}"
  [ -n "${value}" ] || fail "Missing required environment variable: ${name}"
}

require_max_length() {
  local name="$1"
  local value="${!name:-}"
  local max="$2"
  if ((${#value} > max)); then
    fail "${name} must be ${max} characters or less"
  fi
}

require_positive_integer_if_set() {
  local name="$1"
  local value="${!name:-}"
  [ -z "${value}" ] || [[ "${value}" =~ ^[1-9][0-9]*$ ]] || fail "${name} must be a positive integer"
}

require_enum() {
  local name="$1"
  local value="${!name:-}"
  shift
  local item
  for item in "$@"; do
    [[ "${value}" == "${item}" ]] && return 0
  done
  fail "${name} must be one of: $*"
}

json_payload() {
  python3 - <<'PY'
import json
import os

print(
    json.dumps(
        {
            "loginId": os.environ["AQUILA_TEMP_ADMIN_LOGIN_ID"],
            "password": os.environ["AQUILA_TEMP_ADMIN_PASSWORD"],
            "displayName": os.environ["AQUILA_TEMP_ADMIN_DISPLAY_NAME"],
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
)
PY
}

redacted_json_payload() {
  python3 - <<'PY'
import json
import os

print(
    json.dumps(
        {
            "loginId": os.environ["AQUILA_TEMP_ADMIN_LOGIN_ID"],
            "password": "<redacted>",
            "displayName": os.environ["AQUILA_TEMP_ADMIN_DISPLAY_NAME"],
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
)
PY
}

json_membership_payload() {
  python3 - <<'PY'
import json
import os

print(
    json.dumps(
        {
            "membershipRole": os.environ["AQUILA_TEMP_ADMIN_MEMBERSHIP_ROLE"],
            "membershipStatus": os.environ["AQUILA_TEMP_ADMIN_MEMBERSHIP_STATUS"],
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
)
PY
}

extract_user_id() {
  python3 - <<'PY'
import json
import sys

try:
    payload = json.load(sys.stdin)
except json.JSONDecodeError as exc:
    raise SystemExit(f"bootstrap response is not JSON: {exc}") from exc

user_id = payload.get("userId")
if not isinstance(user_id, int) or user_id <= 0:
    raise SystemExit("bootstrap response userId must be a positive integer")
print(user_id)
PY
}

print_revoke_guide() {
  local user_id="$1"
  local account_id="$2"
  local request_id="$3"
  local base_url="$4"

  cat <<GUIDE

[revoke-guide]
# 만료 기준: ${AQUILA_TEMP_ADMIN_TTL_DAYS} day(s). 만료 시점 또는 incident 종료 즉시 회수합니다.
tools/ops/internal-auth-update-user-status.sh \\
  "${base_url}" \\
  "\${AUTH_ADMIN_SERVICE_TOKEN}" \\
  "${request_id}-user-revoke" \\
  "${user_id}" \\
  DISABLED \\
  OPS_MANUAL \\
  temp-admin-expired
GUIDE

  if [[ -n "${account_id}" ]]; then
    cat <<GUIDE

tools/ops/internal-auth-update-membership-status.sh \\
  "${base_url}" \\
  "\${AUTH_ADMIN_SERVICE_TOKEN}" \\
  "${request_id}-membership-revoke" \\
  "${user_id}" \\
  "${account_id}" \\
  REVOKED \\
  OPS_MANUAL \\
  temp-admin-expired
GUIDE
  fi

  cat <<GUIDE

tools/ops/internal-auth-find-status-change-audit.sh \\
  "${base_url}" \\
  "\${AUTH_ADMIN_SERVICE_TOKEN}" \\
  "${request_id}-user-revoke"
GUIDE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      dry_run=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      fail "Unknown argument: $1"
      ;;
  esac
done

require_command curl
require_command python3

AQUILA_OPS_BASE_URL="${AQUILA_OPS_BASE_URL:-${INTERNAL_API_BASE_URL:-http://localhost:8080}}"
AQUILA_TEMP_ADMIN_REQUEST_ID="${AQUILA_TEMP_ADMIN_REQUEST_ID:-temp-admin-bootstrap-$(date -u +%Y%m%d%H%M%S)}"
AQUILA_TEMP_ADMIN_TTL_DAYS="${AQUILA_TEMP_ADMIN_TTL_DAYS:-1}"
AQUILA_TEMP_ADMIN_ACCOUNT_ID="${AQUILA_TEMP_ADMIN_ACCOUNT_ID:-}"
AQUILA_TEMP_ADMIN_MEMBERSHIP_ROLE="${AQUILA_TEMP_ADMIN_MEMBERSHIP_ROLE:-OWNER}"
AQUILA_TEMP_ADMIN_MEMBERSHIP_STATUS="${AQUILA_TEMP_ADMIN_MEMBERSHIP_STATUS:-ACTIVE}"
AUTH_BOOTSTRAP_SERVICE_TOKEN="${AUTH_BOOTSTRAP_SERVICE_TOKEN:-${AQUILA_AUTH_BOOTSTRAP_SERVICE_TOKEN:-}}"

export AQUILA_TEMP_ADMIN_LOGIN_ID
export AQUILA_TEMP_ADMIN_PASSWORD
export AQUILA_TEMP_ADMIN_DISPLAY_NAME
export AQUILA_TEMP_ADMIN_MEMBERSHIP_ROLE
export AQUILA_TEMP_ADMIN_MEMBERSHIP_STATUS

require_env AQUILA_TEMP_ADMIN_LOGIN_ID
require_env AQUILA_TEMP_ADMIN_PASSWORD
require_env AQUILA_TEMP_ADMIN_DISPLAY_NAME
require_env AUTH_BOOTSTRAP_SERVICE_TOKEN
require_max_length AQUILA_TEMP_ADMIN_LOGIN_ID 80
require_max_length AQUILA_TEMP_ADMIN_PASSWORD 120
require_max_length AQUILA_TEMP_ADMIN_DISPLAY_NAME 80
require_positive_integer_if_set AQUILA_TEMP_ADMIN_ACCOUNT_ID
require_positive_integer_if_set AQUILA_TEMP_ADMIN_TTL_DAYS
require_enum AQUILA_TEMP_ADMIN_MEMBERSHIP_ROLE OWNER MEMBER VIEWER
require_enum AQUILA_TEMP_ADMIN_MEMBERSHIP_STATUS ACTIVE REVOKED

base_url="${AQUILA_OPS_BASE_URL%/}"
bootstrap_url="${base_url}/internal/api/v1/auth/users/bootstrap"
bootstrap_payload="$(json_payload)"
redacted_payload="$(redacted_json_payload)"

# secret은 curl header/body로만 전달하고 로그에는 redacted payload만 남깁니다.
if [[ "${dry_run}" == "true" ]]; then
  cat <<DRYRUN
[dry-run] POST ${bootstrap_url}
[dry-run] X-Request-Id: ${AQUILA_TEMP_ADMIN_REQUEST_ID}
[dry-run] body: ${redacted_payload}
DRYRUN
  if [[ -n "${AQUILA_TEMP_ADMIN_ACCOUNT_ID}" ]]; then
    cat <<DRYRUN
[dry-run] PUT ${base_url}/internal/api/v1/auth/users/<user_id_from_response>/memberships/${AQUILA_TEMP_ADMIN_ACCOUNT_ID}
[dry-run] body: $(json_membership_payload)
DRYRUN
  fi
  print_revoke_guide "<user_id_from_response>" "${AQUILA_TEMP_ADMIN_ACCOUNT_ID}" "${AQUILA_TEMP_ADMIN_REQUEST_ID}" "${base_url}"
  exit 0
fi

bootstrap_response="$(
  curl --fail-with-body --silent --show-error \
    --request POST \
    --header "Content-Type: application/json" \
    --header "Authorization: Bearer ${AUTH_BOOTSTRAP_SERVICE_TOKEN}" \
    --header "X-Request-Id: ${AQUILA_TEMP_ADMIN_REQUEST_ID}" \
    --data "${bootstrap_payload}" \
    "${bootstrap_url}"
)"
user_id="$(printf '%s' "${bootstrap_response}" | extract_user_id)"

echo "${bootstrap_response}"

if [[ -n "${AQUILA_TEMP_ADMIN_ACCOUNT_ID}" ]]; then
  membership_response="$(
    curl --fail-with-body --silent --show-error \
      --request PUT \
      --header "Content-Type: application/json" \
      --header "Authorization: Bearer ${AUTH_BOOTSTRAP_SERVICE_TOKEN}" \
      --header "X-Request-Id: ${AQUILA_TEMP_ADMIN_REQUEST_ID}-membership" \
      --data "$(json_membership_payload)" \
      "${base_url}/internal/api/v1/auth/users/${user_id}/memberships/${AQUILA_TEMP_ADMIN_ACCOUNT_ID}"
  )"
  echo "${membership_response}"
fi

print_revoke_guide "${user_id}" "${AQUILA_TEMP_ADMIN_ACCOUNT_ID}" "${AQUILA_TEMP_ADMIN_REQUEST_ID}" "${base_url}"
