#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-sse-multinode-drain-smoke.sh [--print-plan]

Environment:
  SSE_MULTINODE_DRAIN_GRACE_SECONDS  default 5
  SSE_RECONNECT_DELAY_MS             default 3000

Examples:
  tools/test/run-sse-multinode-drain-smoke.sh
  SSE_MULTINODE_DRAIN_GRACE_SECONDS=8 tools/test/run-sse-multinode-drain-smoke.sh
  tools/test/run-sse-multinode-drain-smoke.sh --print-plan
USAGE
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer" >&2
    exit 1
  fi
}

print_plan=false
if [[ "${1:-}" == "--print-plan" ]]; then
  print_plan=true
  shift
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -ne 0 ]]; then
  usage
  exit 1
fi

drain_grace_seconds="${SSE_MULTINODE_DRAIN_GRACE_SECONDS:-5}"
reconnect_delay_ms="${SSE_RECONNECT_DELAY_MS:-3000}"

require_positive_integer "SSE_MULTINODE_DRAIN_GRACE_SECONDS" "${drain_grace_seconds}"
require_positive_integer "SSE_RECONNECT_DELAY_MS" "${reconnect_delay_ms}"

echo "[sse-multinode-drain] plan: upstream remove -> nginx reload -> wait ${drain_grace_seconds}s -> client reconnect"
echo "[sse-multinode-drain] reconnect: Last-Event-ID replay with reconnect delay ${reconnect_delay_ms}ms"
echo "[sse-multinode-drain] checks: nginx directive drift + reconnect storm replay + broker fanout fault"
echo "[sse-multinode-drain] selectors:"
echo "  bash tools/test/check-nginx-sse-proxy.sh"
echo "  --tests *NotificationSseIntegrationTest.reconnectStormReplaysMissedNotificationsWithoutDuplicates"
echo "  --tests *NotificationSseBrokerTest"

if [[ "${print_plan}" == "true" ]]; then
  exit 0
fi

echo "[sse-multinode-drain] nginx: directive smoke"
bash tools/test/check-nginx-sse-proxy.sh

echo "[sse-multinode-drain] backend: reconnect replay + broker fanout fault"
tools/test/with-resource-lock.sh back-gradle-sse-multinode-drain \
  ./back/gradlew -p back test \
  --tests '*NotificationSseIntegrationTest.reconnectStormReplaysMissedNotificationsWithoutDuplicates' \
  --tests '*NotificationSseBrokerTest'

echo "[sse-multinode-drain] passed"
