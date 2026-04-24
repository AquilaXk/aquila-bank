#!/usr/bin/env bash
set -euo pipefail

echo "[transaction-100m-local-runner] shell syntax"
bash -n tools/test/seed-transaction-read-model-100m.sh
bash -n tools/test/run-transaction-read-model-100m-k6-local.sh

echo "[transaction-100m-local-runner] compose config"
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml config >/dev/null

echo "[transaction-100m-local-runner] seed plan"
plan="$(
  SEED_TOTAL_ROWS=1000 \
  SEED_HOT_ROWS=600 \
  SEED_BATCH_SIZE=200 \
  SEED_TRUNCATE=true \
    tools/test/seed-transaction-read-model-100m.sh --print-plan
)"
grep -F "total_rows=1000" <<<"${plan}" >/dev/null
grep -F "hot_rows=600 archive_rows=400 batch_size=200" <<<"${plan}" >/dev/null
grep -F "truncate=true" <<<"${plan}" >/dev/null
grep -F "local read-path seed only" <<<"${plan}" >/dev/null

echo "[transaction-100m-local-runner] seed script contract"
grep -F "TRIGGER ALL" tools/test/seed-transaction-read-model-100m.sh >/dev/null
grep -F "CREATE INDEX IF NOT EXISTS idx_transaction_read_model_account_cursor" tools/test/seed-transaction-read-model-100m.sh >/dev/null
grep -F "generate_series" tools/test/seed-transaction-read-model-100m.sh >/dev/null
grep -F "ANALYZE transaction_read_model" tools/test/seed-transaction-read-model-100m.sh >/dev/null

echo "[transaction-100m-local-runner] invalid seed input fails"
if SEED_TOTAL_ROWS=0 tools/test/seed-transaction-read-model-100m.sh --print-plan >/dev/null 2>&1; then
  echo "SEED_TOTAL_ROWS=0 unexpectedly succeeded" >&2
  exit 1
fi
if SEED_TOTAL_ROWS=100 SEED_HOT_ROWS=100 tools/test/seed-transaction-read-model-100m.sh --print-plan >/dev/null 2>&1; then
  echo "SEED_HOT_ROWS=SEED_TOTAL_ROWS unexpectedly succeeded" >&2
  exit 1
fi

echo "[transaction-100m-local-runner] wrapper plan"
wrapper_plan="$(
  SEED_TOTAL_ROWS=1000 \
  K6_REPORT_NAME=transaction-100m-check \
    tools/test/run-transaction-read-model-100m-k6-local.sh --print-plan
)"
grep -F "mode=print-plan" <<<"${wrapper_plan}" >/dev/null
grep -F "seed_total_rows=1000" <<<"${wrapper_plan}" >/dev/null
grep -F "k6 report=transaction-100m-check" <<<"${wrapper_plan}" >/dev/null
grep -F "Prometheus http://localhost:9090, Grafana http://localhost:3001" <<<"${wrapper_plan}" >/dev/null

echo "[transaction-100m-local-runner] wrapper contract"
grep -F "run-k6-transaction-100m-loadtest.sh --no-up" tools/test/run-transaction-read-model-100m-k6-local.sh >/dev/null
grep -F "seed-transaction-read-model-100m.sh" tools/test/run-transaction-read-model-100m-k6-local.sh >/dev/null
