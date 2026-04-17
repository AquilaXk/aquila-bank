#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  cat <<'USAGE' >&2
usage: tools/ops/outbox-find-failed-events.sh <base_url> <service_token> <limit>

example:
  tools/ops/outbox-find-failed-events.sh \
    http://localhost:8080 \
    "$OUTBOX_OPS_SERVICE_TOKEN" \
    20
USAGE
  exit 1
fi

base_url="$1"
service_token="$2"
limit="$3"

# failed list 는 dispatch 순서 기준으로 잘라서 보도록 limit query 를 항상 고정합니다.
curl --fail-with-body --silent --show-error \
  --get \
  --header "Authorization: Bearer ${service_token}" \
  --data-urlencode "limit=${limit}" \
  "${base_url%/}/internal/api/v1/outbox/failed-events"
