#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  cat <<'USAGE' >&2
usage: tools/ops/notification-find-dlq-events.sh <base_url> <ops_token> <limit>

example:
  tools/ops/notification-find-dlq-events.sh \
    http://localhost:8080 \
    "$OUTBOX_OPS_TOKEN" \
    20
USAGE
  exit 1
fi

base_url="$1"
ops_token="$2"
limit="$3"

# DLQ preview 는 poison message 최근 항목만 bounded query 로 확인해 복구 범위를 넓히지 않습니다.
curl --fail-with-body --silent --show-error \
  --get \
  --header "X-Outbox-Ops-Token: ${ops_token}" \
  --data-urlencode "limit=${limit}" \
  "${base_url%/}/internal/api/v1/outbox/notification/dlq-events"
