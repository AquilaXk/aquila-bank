#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-sse-multinode-drain-smoke.sh"

echo "[sse-multinode-drain-script] syntax: ${script}"
bash -n "${script}"

echo "[sse-multinode-drain-script] plan includes drain sequence and replay checks"
plan="$(
  SSE_MULTINODE_DRAIN_GRACE_SECONDS=7 \
  SSE_RECONNECT_DELAY_MS=4500 \
  "${script}" --print-plan
)"
grep -F "upstream remove -> nginx reload -> wait 7s -> client reconnect" <<<"${plan}" >/dev/null
grep -F "Last-Event-ID replay" <<<"${plan}" >/dev/null
grep -F "reconnect delay 4500ms" <<<"${plan}" >/dev/null
grep -F "check-nginx-sse-proxy.sh" <<<"${plan}" >/dev/null
grep -F "*NotificationSseIntegrationTest.reconnectStormReplaysMissedNotificationsWithoutDuplicates" <<<"${plan}" >/dev/null
grep -F "*NotificationSseBrokerTest" <<<"${plan}" >/dev/null

echo "[sse-multinode-drain-script] invalid numeric input fails before Gradle"
if SSE_MULTINODE_DRAIN_GRACE_SECONDS=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "SSE_MULTINODE_DRAIN_GRACE_SECONDS=0 unexpectedly succeeded" >&2
  exit 1
fi
if SSE_RECONNECT_DELAY_MS=abc "${script}" --print-plan >/dev/null 2>&1; then
  echo "SSE_RECONNECT_DELAY_MS=abc unexpectedly succeeded" >&2
  exit 1
fi
