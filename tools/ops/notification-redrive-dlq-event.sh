#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
  cat <<'USAGE' >&2
usage: tools/ops/notification-redrive-dlq-event.sh <base_url> <service_token> <partition> <offset>

example:
  tools/ops/notification-redrive-dlq-event.sh \
    http://localhost:8080 \
    "$OUTBOX_OPS_SERVICE_TOKEN" \
    0 \
    12
USAGE
  exit 1
fi

base_url="$1"
service_token="$2"
partition="$3"
offset="$4"

# preview 좌표만 받아 서버가 DLQ record 를 직접 읽고 original topic 으로 재발행하게 합니다.
curl --fail-with-body --silent --show-error \
  --request POST \
  --header "Authorization: Bearer ${service_token}" \
  --header "Content-Type: application/json" \
  --data "{\"partition\":${partition},\"offset\":${offset}}" \
  "${base_url%/}/internal/api/v1/outbox/notification/dlq-events/redrive"
