#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  cat <<'USAGE' >&2
usage: tools/ops/internal-auth-find-status-change-audit.sh <base_url> <service_token> <request_id>

example:
  tools/ops/internal-auth-find-status-change-audit.sh \
    http://localhost:8080 \
    "$AUTH_ADMIN_SERVICE_TOKEN" \
    auth-user-disable-20260416-001
USAGE
  exit 1
fi

base_url="$1"
service_token="$2"
request_id="$3"

# requestId exact lookup은 query로 고정해 운영자가 같은 incident key를 그대로 재현하게 합니다.
curl --fail-with-body --silent --show-error \
  --get \
  --header "Authorization: Bearer ${service_token}" \
  --data-urlencode "requestId=${request_id}" \
  "${base_url%/}/internal/api/v1/auth/status-change-audits/by-request-id"
