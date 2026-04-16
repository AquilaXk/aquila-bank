#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 7 ]]; then
  cat <<'USAGE' >&2
usage: tools/ops/internal-auth-update-user-status.sh <base_url> <bootstrap_token> <actor_subject> <request_id> <user_id> <user_status> <reason>

example:
  tools/ops/internal-auth-update-user-status.sh \
    http://localhost:8080 \
    "$SECURITY_AUTH_BOOTSTRAP_API_TOKEN" \
    ops-admin \
    auth-user-disable-20260416-001 \
    21 \
    DISABLED \
    fraud-review
USAGE
  exit 1
fi

base_url="$1"
bootstrap_token="$2"
actor_subject="$3"
request_id="$4"
user_id="$5"
user_status="$6"
reason="$7"

# 운영 추적 기준: X-Subject / X-Request-Id / reason을 항상 명시합니다.
curl --fail-with-body --silent --show-error \
  --request PUT \
  --header "Content-Type: application/json" \
  --header "X-Auth-Bootstrap-Token: ${bootstrap_token}" \
  --header "X-Subject: ${actor_subject}" \
  --header "X-Request-Id: ${request_id}" \
  --data "{\"userStatus\":\"${user_status}\",\"reason\":\"${reason}\"}" \
  "${base_url%/}/internal/api/v1/auth/users/${user_id}/status"
