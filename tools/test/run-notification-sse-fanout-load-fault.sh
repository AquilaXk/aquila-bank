#!/usr/bin/env bash
set -euo pipefail

echo "[notification-sse-fanout] fixture: account 2 sessions + user 2 sessions + 25 live events"
echo "[notification-sse-fanout] fault: one injected emitter send failure must not block healthy sessions"
echo "[notification-sse-fanout] backpressure: replay pending overflow drops only the slow session"

./back/gradlew -p back test \
  --tests '*NotificationSseBrokerTest'
