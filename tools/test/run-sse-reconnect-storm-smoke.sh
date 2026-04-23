#!/usr/bin/env bash
set -euo pipefail

echo "[sse-reconnect-storm] nginx: directive smoke"
bash tools/test/check-nginx-sse-proxy.sh

echo "[sse-reconnect-storm] backend: Last-Event-ID reconnect replay"
tools/test/with-resource-lock.sh back-gradle-sse-reconnect-storm \
  ./back/gradlew -p back test \
  --tests '*NotificationSseIntegrationTest.reconnectStormReplaysMissedNotificationsWithoutDuplicates'

echo "[sse-reconnect-storm] passed"
