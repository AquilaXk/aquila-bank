#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 7 ]]; then
  cat <<'USAGE' >&2
usage: tools/ops/internal-auth-update-user-status.sh <base_url> <service_token> <request_id> <user_id> <user_status> <reason_code> <reason_detail>

example:
  tools/ops/internal-auth-update-user-status.sh \
    http://localhost:8080 \
    "$AUTH_ADMIN_SERVICE_TOKEN" \
    auth-user-disable-20260416-001 \
    21 \
    DISABLED \
    FRAUD_REVIEW \
    fraud-review
USAGE
  exit 1
fi

base_url="$1"
service_token="$2"
request_id="$3"
user_id="$4"
user_status="$5"
reason_code="$6"
reason_detail="$7"

# 운영 추적 기준: token sub / X-Request-Id / reasonCode / reasonDetail을 항상 명시합니다.
curl --fail-with-body --silent --show-error \
  --request PUT \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${service_token}" \
  --header "X-Request-Id: ${request_id}" \
  --data "{\"userStatus\":\"${user_status}\",\"reasonCode\":\"${reason_code}\",\"reasonDetail\":\"${reason_detail}\"}" \
  "${base_url%/}/internal/api/v1/auth/users/${user_id}/status"
