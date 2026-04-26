#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-capacity-gates.sh"

echo "[transaction-100m-capacity] shell syntax"
bash -n "${script}"

echo "[transaction-100m-capacity] print plan"
plan="$(
  CAPACITY_NAME=transaction-capacity-check \
  CAPACITY_LONG_SOAK_DURATION=30m \
    "${script}" --print-plan
)"
grep -F "capacity=transaction-capacity-check" <<<"${plan}" >/dev/null
grep -F "single_host_profiles=single-host-default,single-host-high-traffic" <<<"${plan}" >/dev/null
grep -F "cpu_split_profiles=cpu-backend040-postgres060,cpu-backend100-postgres060" <<<"${plan}" >/dev/null
grep -F "long_soak_profile=long-soak-high-traffic duration=30m" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/k6/transaction-capacity-check/capacity-summary.tsv" <<<"${plan}" >/dev/null

echo "[transaction-100m-capacity] runner contract"
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
