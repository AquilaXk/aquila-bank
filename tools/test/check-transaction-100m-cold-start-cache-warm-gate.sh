#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-cold-start-cache-warm-gate.sh"

echo "[transaction-100m-cold-warm] shell syntax"
bash -n "${script}"

echo "[transaction-100m-cold-warm] print plan"
plan="$(
  COLD_WARM_NAME=transaction-cold-warm-check \
  COLD_WARM_COLD_START_DURATION=45s \
  COLD_WARM_WARMUP_DURATION=60s \
  COLD_WARM_WARM_READ_DURATION=45s \
    "${script}" --print-plan
)"
grep -F "name=transaction-cold-warm-check" <<<"${plan}" >/dev/null
grep -F "force_recreate=true" <<<"${plan}" >/dev/null
grep -F "cold_start_duration=45s" <<<"${plan}" >/dev/null
grep -F "warmup_duration=60s" <<<"${plan}" >/dev/null
grep -F "warm_read_duration=45s" <<<"${plan}" >/dev/null
grep -F "vus=8" <<<"${plan}" >/dev/null
grep -F "limit=50" <<<"${plan}" >/dev/null
grep -F "hard_thresholds=true" <<<"${plan}" >/dev/null
grep -F "cold_start_p95_threshold_ms=1000" <<<"${plan}" >/dev/null
grep -F "warm_hot_p95_threshold_ms=350" <<<"${plan}" >/dev/null
grep -F "warm_cold_p95_threshold_ms=750" <<<"${plan}" >/dev/null
grep -F "transaction_429_rate_threshold=0" <<<"${plan}" >/dev/null
grep -F "transaction_503_rate_threshold=0" <<<"${plan}" >/dev/null
grep -F "runner=tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/k6/transaction-cold-warm-check/cold-warm-summary.tsv" <<<"${plan}" >/dev/null
grep -F "report=build/reports/k6/transaction-cold-warm-check/cold-warm-cache-state-slo.md" <<<"${plan}" >/dev/null

echo "[transaction-100m-cold-warm] runner contract"
grep -F "COLD_WARM_FORCE_RECREATE" "${script}" >/dev/null
grep -F "COLD_WARM_COLD_START_DURATION" "${script}" >/dev/null
grep -F "COLD_WARM_WARMUP_DURATION" "${script}" >/dev/null
grep -F "COLD_WARM_WARM_READ_DURATION" "${script}" >/dev/null
grep -F "COLD_WARM_COLD_START_P95_THRESHOLD_MS" "${script}" >/dev/null
grep -F "COLD_WARM_WARM_HOT_P95_THRESHOLD_MS" "${script}" >/dev/null
grep -F "COLD_WARM_WARM_COLD_P95_THRESHOLD_MS" "${script}" >/dev/null
grep -F "COLD_WARM_429_RATE_THRESHOLD" "${script}" >/dev/null
grep -F "docker compose" "${script}" >/dev/null
grep -F -- "--force-recreate" "${script}" >/dev/null
grep -F "wait_for_backend_readiness" "${script}" >/dev/null
grep -F "wait_for_prometheus_readiness" "${script}" >/dev/null
grep -F "run-k6-transaction-100m-loadtest.sh --no-up --no-deps" "${script}" >/dev/null
grep -F "cold-start" "${script}" >/dev/null
grep -F "warm-read" "${script}" >/dev/null
grep -F "cold-warm-summary.tsv" "${script}" >/dev/null
grep -F "cold-warm-cache-state-slo.md" "${script}" >/dev/null
grep -F "cache_state" "${script}" >/dev/null
grep -F "first_p95_ms" "${script}" >/dev/null
grep -F "deep_p95_ms" "${script}" >/dev/null
grep -F "aquila_transaction_503_rate" "${script}" >/dev/null
grep -F "aquila_transaction_503_count" "${script}" >/dev/null
grep -F "write_report" "${script}" >/dev/null
grep -F "check_phase_thresholds" "${script}" >/dev/null

echo "[transaction-100m-cold-warm] invalid input fails"
if COLD_WARM_COLD_START_DURATION=0m "${script}" --print-plan >/dev/null 2>&1; then
  echo "COLD_WARM_COLD_START_DURATION=0m unexpectedly succeeded" >&2
  exit 1
fi
if COLD_WARM_VUS=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "COLD_WARM_VUS=0 unexpectedly succeeded" >&2
  exit 1
fi
if COLD_WARM_429_RATE_THRESHOLD=1.5 "${script}" --print-plan >/dev/null 2>&1; then
  echo "COLD_WARM_429_RATE_THRESHOLD=1.5 unexpectedly succeeded" >&2
  exit 1
fi
if COLD_WARM_FORCE_RECREATE=maybe "${script}" --print-plan >/dev/null 2>&1; then
  echo "COLD_WARM_FORCE_RECREATE=maybe unexpectedly succeeded" >&2
  exit 1
fi
