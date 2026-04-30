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
grep -F "k6_run_id=transaction-read-hotpath-check-k6" <<<"${plan}" >/dev/null
grep -F "backend_timer=aquila_transaction_read_http_stage_seconds" <<<"${plan}" >/dev/null
grep -F "backend_timer_labels=endpoint,stage,outcome" <<<"${plan}" >/dev/null
grep -F "method_timer_stage=authorization,usecase,response_mapping,total" <<<"${plan}" >/dev/null
grep -F "jfr_dump_timeout_seconds=60" <<<"${plan}" >/dev/null
grep -F "backend_health_url=http://localhost:8080/actuator/health" <<<"${plan}" >/dev/null
grep -F "artifact=build/reports/profiling/transaction-read-hotpath-check/transaction-read-hotpath-check.jfr" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/profiling/transaction-read-hotpath-check/transaction-read-hotpath-check-summary.md" <<<"${plan}" >/dev/null

burst_plan="$(
  PROFILE_NAME=transaction-read-burst-hot-first-check \
  PROFILE_SPIKE_PROFILE=burst-hot-first \
  PROFILE_K6_DOCKER_CONTEXT=profile-k6-remote \
  PROFILE_K6_REMOTE_BASE_URL=http://192.0.2.40:8080 \
  PROFILE_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.40:9090/api/v1/write \
  PROFILE_K6_REMOTE_WORKDIR=/srv/aquila-bank \
  K6_BURST_RATE=256 \
  K6_BURST_DURATION=20s \
  K6_DURATION=15s \
  K6_VUS=8 \
    "${script}" --print-plan
)"
grep -F "profile=transaction-read-burst-hot-first-check" <<<"${burst_plan}" >/dev/null
grep -F "spike_profile=burst-hot-first" <<<"${burst_plan}" >/dev/null
grep -F "k6 scenario=burst" <<<"${burst_plan}" >/dev/null
grep -F "k6 burst rate=256 duration=20s" <<<"${burst_plan}" >/dev/null
grep -F "k6_overload_mode=true" <<<"${burst_plan}" >/dev/null
grep -F "k6_burst_429_threshold=0.1" <<<"${burst_plan}" >/dev/null
grep -F "hot_first_spike_metric=aquila_transaction_hot_first_ms" <<<"${burst_plan}" >/dev/null
grep -F "spike_metrics=build/reports/profiling/transaction-read-burst-hot-first-check/transaction-read-burst-hot-first-check-hot-first-spike.txt" <<<"${burst_plan}" >/dev/null

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
grep -F "PROFILE_SPIKE_PROFILE" "${script}" >/dev/null
grep -F "PROFILE_K6_GENERATOR_MODE" "${script}" >/dev/null
grep -F "K6_SCENARIO_MODE" "${script}" >/dev/null
grep -F "K6_BURST_RATE" "${script}" >/dev/null
grep -F "K6_BURST_DURATION" "${script}" >/dev/null
grep -F "K6_OVERLOAD_MODE" "${script}" >/dev/null
grep -F -- "--no-deps" "${script}" >/dev/null
grep -F "jfr summary" "${script}" >/dev/null
grep -F "view --width 160 hot-methods" "${script}" >/dev/null
grep -F "view --width 160 allocation-by-class" "${script}" >/dev/null
grep -F "hot_first_spike_metrics" "${script}" >/dev/null
grep -F "aquila_transaction_hot_first_ms" "${script}" >/dev/null
grep -F "aquila.transaction.read.http.stage" "${script}" >/dev/null
grep -F "backend method timer" "${script}" >/dev/null
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
if PROFILE_SPIKE_PROFILE=unknown "${script}" --print-plan >/dev/null 2>&1; then
  echo "unknown spike profile unexpectedly succeeded" >&2
  exit 1
fi
if PROFILE_SPIKE_PROFILE=burst-hot-first PROFILE_K6_SCENARIO_MODE=constant-vus \
    PROFILE_K6_DOCKER_CONTEXT=profile-k6-remote \
    PROFILE_K6_REMOTE_BASE_URL=http://192.0.2.40:8080 \
    PROFILE_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.40:9090/api/v1/write \
    "${script}" --print-plan >/dev/null 2>&1; then
  echo "burst hot-first profile without burst scenario unexpectedly succeeded" >&2
  exit 1
fi
if PROFILE_SPIKE_PROFILE=burst-hot-first K6_BURST_RATE=0 \
    PROFILE_K6_DOCKER_CONTEXT=profile-k6-remote \
    PROFILE_K6_REMOTE_BASE_URL=http://192.0.2.40:8080 \
    PROFILE_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.40:9090/api/v1/write \
    "${script}" --print-plan >/dev/null 2>&1; then
  echo "K6_BURST_RATE=0 unexpectedly succeeded" >&2
  exit 1
fi
