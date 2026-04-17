#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 8 ]]; then
  cat <<'USAGE' >&2
usage: tools/ops/internal-auth-update-membership-status.sh <base_url> <service_token> <request_id> <user_id> <account_id> <membership_status> <reason_code> <reason_detail>

example:
  tools/ops/internal-auth-update-membership-status.sh \
    http://localhost:8080 \
    "$AUTH_ADMIN_SERVICE_TOKEN" \
    auth-membership-revoke-20260416-001 \
    21 \
    1001 \
    REVOKED \
    OPS_MANUAL \
    manual-revoke
USAGE
  exit 1
fi

base_url="$1"
service_token="$2"
request_id="$3"
user_id="$4"
account_id="$5"
membership_status="$6"
reason_code="$7"
reason_detail="$8"

# 운영 추적 기준: membership 회수도 token sub와 같은 reasonCode/reasonDetail 구조를 강제합니다.
curl --fail-with-body --silent --show-error \
  --request PUT \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${service_token}" \
  --header "X-Request-Id: ${request_id}" \
  --data "{\"membershipStatus\":\"${membership_status}\",\"reasonCode\":\"${reason_code}\",\"reasonDetail\":\"${reason_detail}\"}" \
  "${base_url%/}/internal/api/v1/auth/users/${user_id}/memberships/${account_id}/status"
