#!/usr/bin/env bash
set -euo pipefail

echo "[transaction-100m-local-runner] shell syntax"
bash -n tools/test/seed-transaction-read-model-100m.sh

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
