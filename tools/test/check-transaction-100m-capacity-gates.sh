#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-capacity-gates.sh"

echo "[transaction-100m-capacity] shell syntax"
bash -n "${script}"

echo "[transaction-100m-capacity] print plan"
plan="$(
  CAPACITY_NAME=transaction-capacity-check \
  CAPACITY_LONG_SOAK_DURATION=30m \
  CAPACITY_K6_DOCKER_CONTEXT=capacity-k6-remote \
  CAPACITY_K6_REMOTE_BASE_URL=http://192.0.2.20:8080 \
  CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.20:9090/api/v1/write \
  CAPACITY_K6_REMOTE_WORKDIR=/srv/aquila-bank \
    "${script}" --print-plan
)"
grep -F "capacity=transaction-capacity-check" <<<"${plan}" >/dev/null
grep -F "single_host_profiles=single-host-default,single-host-high-traffic" <<<"${plan}" >/dev/null
grep -F "cpu_split_profiles=cpu-backend040-postgres060,cpu-backend100-postgres060" <<<"${plan}" >/dev/null
grep -F "long_soak_profile=long-soak-high-traffic duration=30m" <<<"${plan}" >/dev/null
grep -F "adaptive_enabled=true" <<<"${plan}" >/dev/null
grep -F "hard_thresholds=true" <<<"${plan}" >/dev/null
grep -F "hot_p95_threshold_ms=350" <<<"${plan}" >/dev/null
grep -F "cold_p95_threshold_ms=750" <<<"${plan}" >/dev/null
grep -F "strict_429_rate_threshold=0" <<<"${plan}" >/dev/null
grep -F "overload_429_rate_threshold=0.02" <<<"${plan}" >/dev/null
grep -F "backend_cpu_threshold_percent=120" <<<"${plan}" >/dev/null
grep -F "postgres_cpu_threshold_percent=90" <<<"${plan}" >/dev/null
grep -F "hikari_pending_threshold=0" <<<"${plan}" >/dev/null
grep -F "k6_generator_mode=docker-context" <<<"${plan}" >/dev/null
grep -F "allow_local_k6_generator=false" <<<"${plan}" >/dev/null
grep -F "k6_docker_context=capacity-k6-remote" <<<"${plan}" >/dev/null
grep -F "k6_remote_base_url=http://192.0.2.20:8080" <<<"${plan}" >/dev/null
grep -F "k6_remote_workdir=/srv/aquila-bank" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/k6/transaction-capacity-check/capacity-summary.tsv" <<<"${plan}" >/dev/null

echo "[transaction-100m-capacity] runner contract"
grep -F "CAPACITY_K6_GENERATOR_MODE" "${script}" >/dev/null
grep -F "CAPACITY_ALLOW_LOCAL_K6_GENERATOR" "${script}" >/dev/null
grep -F "CAPACITY_K6_DOCKER_CONTEXT is required" "${script}" >/dev/null
grep -F "K6_GENERATOR_MODE=\"\${capacity_k6_generator_mode}\"" "${script}" >/dev/null
grep -F "K6_RUN_PURPOSE=capacity" "${script}" >/dev/null
grep -F "stop_backend_before_bootjar" "${script}" >/dev/null
grep -F "docker compose \"\${compose_files[@]}\" --profile loadtest stop aquila-bank-backend" "${script}" >/dev/null
grep -F -- "up -d --force-recreate" "${script}" >/dev/null
grep -F "CAPACITY_HARD_THRESHOLD_ENABLED" "${script}" >/dev/null
grep -F "CAPACITY_HOT_P95_THRESHOLD_MS" "${script}" >/dev/null
grep -F "CAPACITY_COLD_P95_THRESHOLD_MS" "${script}" >/dev/null
grep -F "CAPACITY_STRICT_429_RATE_THRESHOLD" "${script}" >/dev/null
grep -F "CAPACITY_OVERLOAD_429_RATE_THRESHOLD" "${script}" >/dev/null
grep -F "CAPACITY_BACKEND_CPU_THRESHOLD_PERCENT" "${script}" >/dev/null
grep -F "CAPACITY_POSTGRES_CPU_THRESHOLD_PERCENT" "${script}" >/dev/null
grep -F "CAPACITY_HIKARI_PENDING_THRESHOLD" "${script}" >/dev/null
grep -F "CAPACITY_ADAPTIVE_ENABLED" "${script}" >/dev/null
grep -F "assert_adaptive_strict_guard" "${script}" >/dev/null
grep -F "strict profile uses overload mode or VU <= admission" "${script}" >/dev/null
grep -F "check_capacity_thresholds" "${script}" >/dev/null
grep -F "T3MICRO_BACKEND_CPUS" "${script}" >/dev/null
grep -F "T3MICRO_POSTGRES_CPUS" "${script}" >/dev/null
grep -F "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX" "${script}" >/dev/null
grep -F "DB_POOL_MAX_SIZE" "${script}" >/dev/null
grep -F "K6_OVERLOAD_MODE" "${script}" >/dev/null
grep -F "K6_DURATION" "${script}" >/dev/null
grep -F "docker stats --no-stream" "${script}" >/dev/null
grep -F "run-k6-transaction-100m-loadtest.sh --no-up --no-deps" "${script}" >/dev/null
grep -F "capacity-summary.tsv" "${script}" >/dev/null
grep -F "single-host" "${script}" >/dev/null
grep -F "long-soak" "${script}" >/dev/null
grep -F "metric_from_json" "${script}" >/dev/null

echo "[transaction-100m-capacity] invalid input fails"
if CAPACITY_LONG_SOAK_DURATION=0m "${script}" --print-plan >/dev/null 2>&1; then
  echo "CAPACITY_LONG_SOAK_DURATION=0m unexpectedly succeeded" >&2
  exit 1
fi
if CAPACITY_SINGLE_HOST_PROFILES=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad CAPACITY_SINGLE_HOST_PROFILES unexpectedly succeeded" >&2
  exit 1
fi
if CAPACITY_HOT_P95_THRESHOLD_MS=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad CAPACITY_HOT_P95_THRESHOLD_MS unexpectedly succeeded" >&2
  exit 1
fi
if CAPACITY_OVERLOAD_429_RATE_THRESHOLD=1.5 "${script}" --print-plan >/dev/null 2>&1; then
  echo "CAPACITY_OVERLOAD_429_RATE_THRESHOLD=1.5 unexpectedly succeeded" >&2
  exit 1
fi
if CAPACITY_SINGLE_HOST_PROFILES=bad-strict:3:8:0.40:512m:0.60:384m:4:false:1m "${script}" --print-plan >/dev/null 2>&1; then
  echo "adaptive strict profile without overload unexpectedly succeeded" >&2
  exit 1
fi
if CAPACITY_K6_GENERATOR_MODE=local "${script}" --print-plan >/dev/null 2>&1; then
  echo "local k6 generator without explicit allowance unexpectedly succeeded" >&2
  exit 1
fi
if CAPACITY_K6_GENERATOR_MODE=local CAPACITY_ALLOW_LOCAL_K6_GENERATOR=true "${script}" --print-plan >/dev/null 2>&1; then
  echo "capacity local k6 generator unexpectedly succeeded" >&2
  exit 1
fi
if CAPACITY_K6_GENERATOR_MODE=docker-context "${script}" --print-plan >/dev/null 2>&1; then
  echo "docker-context k6 generator without remote runtime unexpectedly succeeded" >&2
  exit 1
fi
