#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-read-hotpath-profile.sh"

echo "[transaction-read-hotpath-profile] shell syntax"
bash -n "${script}"

echo "[transaction-read-hotpath-profile] print plan"
plan="$(
  PROFILE_NAME=transaction-read-hotpath-check \
  PROFILE_DURATION=20s \
  PROFILE_K6_DOCKER_CONTEXT=profile-k6-remote \
  PROFILE_K6_REMOTE_BASE_URL=http://192.0.2.40:8080 \
  PROFILE_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.40:9090/api/v1/write \
  PROFILE_K6_REMOTE_WORKDIR=/srv/aquila-bank \
  K6_DURATION=15s \
  K6_VUS=8 \
    "${script}" --print-plan
)"
grep -F "profile=transaction-read-hotpath-check" <<<"${plan}" >/dev/null
grep -F "profiler=jfr" <<<"${plan}" >/dev/null
grep -F "k6 vus=8 duration=15s" <<<"${plan}" >/dev/null
grep -F "jfr duration=20s" <<<"${plan}" >/dev/null
grep -F "k6_generator_mode=docker-context" <<<"${plan}" >/dev/null
grep -F "k6_docker_context=profile-k6-remote" <<<"${plan}" >/dev/null
grep -F "jfr_dump_timeout_seconds=60" <<<"${plan}" >/dev/null
grep -F "backend_health_url=http://localhost:8080/actuator/health" <<<"${plan}" >/dev/null
grep -F "artifact=build/reports/profiling/transaction-read-hotpath-check/transaction-read-hotpath-check.jfr" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/profiling/transaction-read-hotpath-check/transaction-read-hotpath-check-summary.md" <<<"${plan}" >/dev/null

echo "[transaction-read-hotpath-profile] runner contract"
grep -F "StartFlightRecording" "${script}" >/dev/null
grep -F "wait_for_backend_readiness" "${script}" >/dev/null
grep -F "wait_for_jfr_dump" "${script}" >/dev/null
grep -F "stop_backend_before_bootjar" "${script}" >/dev/null
grep -F "docker compose \"\${compose_files[@]}\" --profile loadtest stop aquila-bank-backend" "${script}" >/dev/null
grep -F -- "--force-recreate aquila-bank-backend" "${script}" >/dev/null
grep -F "LOADTEST_BACKEND_JAVA_TOOL_OPTIONS" "${script}" >/dev/null
grep -F "docker cp" "${script}" >/dev/null
grep -F "docker compose" "${script}" >/dev/null
grep -F "run-k6-transaction-100m-loadtest.sh --no-up --no-deps" "${script}" >/dev/null
grep -F "K6_RUN_PURPOSE=profile" "${script}" >/dev/null
grep -F "PROFILE_K6_GENERATOR_MODE" "${script}" >/dev/null
grep -F -- "--no-deps" "${script}" >/dev/null
grep -F "jfr summary" "${script}" >/dev/null
grep -F "view --width 160 hot-methods" "${script}" >/dev/null
grep -F "view --width 160 allocation-by-class" "${script}" >/dev/null
grep -F '\`build/reports/profiling\`' "${script}" >/dev/null
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
if PROFILE_K6_GENERATOR_MODE=local "${script}" --print-plan >/dev/null 2>&1; then
  echo "local k6 generator for profile unexpectedly succeeded" >&2
  exit 1
fi
if PROFILE_K6_GENERATOR_MODE=docker-context "${script}" --print-plan >/dev/null 2>&1; then
  echo "docker-context profile without remote runtime unexpectedly succeeded" >&2
  exit 1
fi
