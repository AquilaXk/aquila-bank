#!/usr/bin/env bash
set -euo pipefail

reconnect_clients="${SSE_RECONNECT_CLIENTS:-3}"
reconnect_rounds="${SSE_RECONNECT_ROUNDS:-3}"
if ! [[ "${reconnect_clients}" =~ ^[1-9][0-9]*$ ]]; then
  echo "SSE_RECONNECT_CLIENTS must be a positive integer" >&2
  exit 1
fi
if ! [[ "${reconnect_rounds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "SSE_RECONNECT_ROUNDS must be a positive integer" >&2
  exit 1
fi

echo "[sse-reconnect-storm] nginx: directive smoke"
bash tools/test/check-nginx-sse-proxy.sh

echo "[sse-reconnect-storm] backend: Last-Event-ID reconnect replay clients=${reconnect_clients} rounds=${reconnect_rounds}"
tools/test/with-resource-lock.sh back-gradle-sse-reconnect-storm \
  ./back/gradlew -p back test \
  -Dsse.reconnect.client-count="${reconnect_clients}" \
  -Dsse.reconnect.rounds="${reconnect_rounds}" \
  --tests '*NotificationSseIntegrationTest.reconnectStormReplaysMissedNotificationsWithoutDuplicates'

echo "[sse-reconnect-storm] passed"
