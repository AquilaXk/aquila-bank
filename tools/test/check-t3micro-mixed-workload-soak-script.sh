#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-t3micro-mixed-workload-soak.sh"

echo "[t3micro-mixed-soak-script] syntax: ${script}"
bash -n "${script}"

echo "[t3micro-mixed-soak-script] plan includes transaction, transfer, notification, SSE selectors"
plan="$(SOAK_REPEAT=2 "${script}" --print-plan)"
grep -F "repeat=2" <<<"${plan}" >/dev/null
grep -F "DB_POOL_MAX_SIZE=4" <<<"${plan}" >/dev/null
grep -F "SERVER_THREADS_MAX=16" <<<"${plan}" >/dev/null
grep -F "*TransactionQueryConcurrencySloIntegrationTest" <<<"${plan}" >/dev/null
grep -F "*TransferCommandApiIntegrationTest" <<<"${plan}" >/dev/null
grep -F "*NotificationSseBrokerTest" <<<"${plan}" >/dev/null
grep -F "*NotificationSseIntegrationTest" <<<"${plan}" >/dev/null

echo "[t3micro-mixed-soak-script] smoke reruns test task"
grep -F "cleanTest test" "${script}" >/dev/null

echo "[t3micro-mixed-soak-script] guard: invalid repeat fails before Gradle"
if SOAK_REPEAT=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "SOAK_REPEAT=0 unexpectedly succeeded" >&2
  exit 1
fi
if SOAK_REPEAT=abc "${script}" --print-plan >/dev/null 2>&1; then
  echo "SOAK_REPEAT=abc unexpectedly succeeded" >&2
  exit 1
fi
