#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  cat <<'USAGE' >&2
usage: tools/ops/notification-get-consumer-summary.sh <base_url> <service_token>

example:
  tools/ops/notification-get-consumer-summary.sh \
    http://localhost:8080 \
    "$OUTBOX_OPS_SERVICE_TOKEN"
USAGE
  exit 1
fi

base_url="$1"
service_token="$2"

# consumer lag 와 DLQ count 는 같은 summary 에서 먼저 확인해 triage 축을 고정합니다.
curl --fail-with-body --silent --show-error \
  --get \
  --header "Authorization: Bearer ${service_token}" \
  "${base_url%/}/internal/api/v1/outbox/notification/summary"
