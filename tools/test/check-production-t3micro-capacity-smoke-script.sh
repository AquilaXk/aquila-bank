#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-production-t3micro-capacity-smoke.sh"

echo "[production-t3micro-capacity-script] syntax: ${script}"
bash -n "${script}"

echo "[production-t3micro-capacity-script] plan includes production budget and mixed workload source"
plan="$(
  SOAK_REPEAT=2 \
  PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE=4 \
  PRODUCTION_T3MICRO_SERVER_THREADS_MAX=16 \
  PRODUCTION_T3MICRO_SSE_MAX_TOTAL_SESSIONS=64 \
  PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX=4 \
  "${script}" --print-plan
)"
grep -F "repeat=2" <<<"${plan}" >/dev/null
grep -F "source=tools/test/run-t3micro-mixed-workload-soak.sh" <<<"${plan}" >/dev/null
grep -F "DB_POOL_MAX_SIZE=4" <<<"${plan}" >/dev/null
grep -F "SERVER_THREADS_MAX=16" <<<"${plan}" >/dev/null
grep -F "NOTIFICATION_SSE_MAX_TOTAL_SESSIONS=64" <<<"${plan}" >/dev/null
grep -F "OPS_API_ADMISSION_CONTROL_NOTIFICATION_STREAM_MAX=4" <<<"${plan}" >/dev/null
grep -F "*TransactionQueryConcurrencySloIntegrationTest" <<<"${plan}" >/dev/null
grep -F "*NotificationSseIntegrationTest" <<<"${plan}" >/dev/null

echo "[production-t3micro-capacity-script] invalid budget fails before Gradle"
if PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE=0 unexpectedly succeeded" >&2
  exit 1
fi
if PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX=abc "${script}" --print-plan >/dev/null 2>&1; then
  echo "PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX=abc unexpectedly succeeded" >&2
  exit 1
fi
