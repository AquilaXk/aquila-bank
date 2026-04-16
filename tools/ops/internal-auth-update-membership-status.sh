#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 9 ]]; then
  cat <<'USAGE' >&2
usage: tools/ops/internal-auth-update-membership-status.sh <base_url> <bootstrap_token> <actor_subject> <request_id> <user_id> <account_id> <membership_status> <reason_code> <reason_detail>

example:
  tools/ops/internal-auth-update-membership-status.sh \
    http://localhost:8080 \
    "$SECURITY_AUTH_BOOTSTRAP_API_TOKEN" \
    ops-admin \
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
bootstrap_token="$2"
actor_subject="$3"
request_id="$4"
user_id="$5"
account_id="$6"
membership_status="$7"
reason_code="$8"
reason_detail="$9"

# 운영 추적 기준: membership 회수도 user 상태 변경과 같은 reasonCode/reasonDetail 구조를 강제합니다.
curl --fail-with-body --silent --show-error \
  --request PUT \
  --header "Content-Type: application/json" \
  --header "X-Auth-Bootstrap-Token: ${bootstrap_token}" \
  --header "X-Subject: ${actor_subject}" \
  --header "X-Request-Id: ${request_id}" \
  --data "{\"membershipStatus\":\"${membership_status}\",\"reasonCode\":\"${reason_code}\",\"reasonDetail\":\"${reason_detail}\"}" \
  "${base_url%/}/internal/api/v1/auth/users/${user_id}/memberships/${account_id}/status"
