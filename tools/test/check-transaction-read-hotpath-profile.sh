#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-read-hotpath-profile.sh"

echo "[transaction-read-hotpath-profile] shell syntax"
bash -n "${script}"

echo "[transaction-read-hotpath-profile] print plan"
plan="$(
  PROFILE_NAME=transaction-read-hotpath-check \
  PROFILE_DURATION=20s \
  K6_DURATION=15s \
  K6_VUS=8 \
    "${script}" --print-plan
)"
grep -F "profile=transaction-read-hotpath-check" <<<"${plan}" >/dev/null
grep -F "profiler=jfr" <<<"${plan}" >/dev/null
grep -F "k6 vus=8 duration=15s" <<<"${plan}" >/dev/null
grep -F "jfr duration=20s" <<<"${plan}" >/dev/null
grep -F "backend_health_url=http://localhost:8080/actuator/health" <<<"${plan}" >/dev/null
grep -F "artifact=build/reports/profiling/transaction-read-hotpath-check/transaction-read-hotpath-check.jfr" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/profiling/transaction-read-hotpath-check/transaction-read-hotpath-check-summary.md" <<<"${plan}" >/dev/null

echo "[transaction-read-hotpath-profile] runner contract"
grep -F "StartFlightRecording" "${script}" >/dev/null
grep -F "wait_for_backend_readiness" "${script}" >/dev/null
grep -F "LOADTEST_BACKEND_JAVA_TOOL_OPTIONS" "${script}" >/dev/null
grep -F "docker cp" "${script}" >/dev/null
grep -F "docker compose" "${script}" >/dev/null
grep -F "k6-transaction-read-100m" "${script}" >/dev/null
grep -F -- "--no-deps" "${script}" >/dev/null
grep -F "jfr summary" "${script}" >/dev/null
grep -F "view --width 160 hot-methods" "${script}" >/dev/null
grep -F "view --width 160 allocation-by-class" "${script}" >/dev/null
grep -F "artifact missing or empty" "${script}" >/dev/null

echo "[transaction-read-hotpath-profile] invalid input fails"
if PROFILE_DURATION=0s "${script}" --print-plan >/dev/null 2>&1; then
  echo "PROFILE_DURATION=0s unexpectedly succeeded" >&2
  exit 1
fi
if K6_VUS=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "K6_VUS=0 unexpectedly succeeded" >&2
  exit 1
fi
