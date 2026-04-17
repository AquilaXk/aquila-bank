#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  cat <<'USAGE' >&2
usage: tools/ops/outbox-recover-stale-sending.sh <base_url> <ops_token>

example:
  tools/ops/outbox-recover-stale-sending.sh \
    http://localhost:8080 \
    "$OUTBOX_OPS_TOKEN"
USAGE
  exit 1
fi

base_url="$1"
ops_token="$2"

# stale recovery 는 직접 publish 가 아니라 stale SENDING row 를 PENDING 으로 재진입시킵니다.
curl --fail-with-body --silent --show-error \
  --request POST \
  --header "X-Outbox-Ops-Token: ${ops_token}" \
  "${base_url%/}/internal/api/v1/outbox/recovery/stale-sending"
