#!/usr/bin/env bash
set -euo pipefail

echo "[transaction-100m-local-runner] shell syntax"
bash -n tools/test/seed-transaction-read-model-100m.sh
bash -n tools/test/run-transaction-read-model-100m-k6-local.sh
bash -n tools/test/prepare-transaction-read-model-100m-fixture.sh

echo "[transaction-100m-local-runner] compose config"
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml config >/dev/null

echo "[transaction-100m-local-runner] seed plan"
plan="$(
  SEED_TOTAL_ROWS=1000 \
  SEED_HOT_ROWS=600 \
  SEED_TRUNCATE=true \
    tools/test/seed-transaction-read-model-100m.sh --print-plan
)"
grep -F "total_rows=1000" <<<"${plan}" >/dev/null
grep -F "hot_rows=600 archive_rows=400 batch_size=250000" <<<"${plan}" >/dev/null
grep -F "truncate=true" <<<"${plan}" >/dev/null
grep -F "index-strategy=required" <<<"${plan}" >/dev/null
grep -F "conflict-mode=fail" <<<"${plan}" >/dev/null
grep -F "local read-path seed only" <<<"${plan}" >/dev/null

idempotent_plan="$(
  SEED_TOTAL_ROWS=1000 \
  SEED_HOT_ROWS=600 \
  SEED_BATCH_SIZE=200 \
  SEED_TRUNCATE=false \
  SEED_CONFLICT_MODE=ignore \
    tools/test/seed-transaction-read-model-100m.sh --print-plan
)"
grep -F "hot_rows=600 archive_rows=400 batch_size=200" <<<"${idempotent_plan}" >/dev/null
grep -F "truncate=false" <<<"${idempotent_plan}" >/dev/null
grep -F "conflict-mode=ignore" <<<"${idempotent_plan}" >/dev/null

echo "[transaction-100m-local-runner] seed script contract"
grep -F "TRIGGER ALL" tools/test/seed-transaction-read-model-100m.sh >/dev/null
grep -F "SEED_INDEX_STRATEGY" tools/test/seed-transaction-read-model-100m.sh >/dev/null
grep -F "SEED_CONFLICT_MODE" tools/test/seed-transaction-read-model-100m.sh >/dev/null
grep -F "ensure_required_indexes" tools/test/seed-transaction-read-model-100m.sh >/dev/null
grep -F "drop_optional_secondary_indexes" tools/test/seed-transaction-read-model-100m.sh >/dev/null
grep -F "conflict_clause" tools/test/seed-transaction-read-model-100m.sh >/dev/null
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
if SEED_CONFLICT_MODE=bad tools/test/seed-transaction-read-model-100m.sh --print-plan >/dev/null 2>&1; then
  echo "SEED_CONFLICT_MODE=bad unexpectedly succeeded" >&2
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
grep -F "seed_batch_size=250000" <<<"${wrapper_plan}" >/dev/null
grep -F "seed_conflict_mode=fail" <<<"${wrapper_plan}" >/dev/null
grep -F "flyway preflight=latest local migration" <<<"${wrapper_plan}" >/dev/null
grep -F "k6 report=transaction-100m-check" <<<"${wrapper_plan}" >/dev/null
grep -F "backend env DB_USERNAME=postgres DB_NAME=aquila_bank" <<<"${wrapper_plan}" >/dev/null
grep -F "Prometheus http://localhost:9090, Grafana http://localhost:3001" <<<"${wrapper_plan}" >/dev/null

no_deps_plan="$(
  SEED_TOTAL_ROWS=1000 \
  K6_REPORT_NAME=transaction-100m-check \
    tools/test/run-transaction-read-model-100m-k6-local.sh --print-plan --no-deps
)"
grep -F "dependencies=no-deps" <<<"${no_deps_plan}" >/dev/null

echo "[transaction-100m-local-runner] wrapper contract"
grep -F -- "--no-deps" tools/test/run-transaction-read-model-100m-k6-local.sh >/dev/null
grep -F -- "--force-recreate aquila-bank-backend" tools/test/run-transaction-read-model-100m-k6-local.sh >/dev/null
grep -F "validate_backend_env" tools/test/run-transaction-read-model-100m-k6-local.sh >/dev/null
grep -F "psql output" tools/test/run-transaction-read-model-100m-k6-local.sh >/dev/null
grep -F "assert_flyway_latest" tools/test/run-transaction-read-model-100m-k6-local.sh >/dev/null
grep -F "flyway latest applied" tools/test/run-transaction-read-model-100m-k6-local.sh >/dev/null
grep -F "assert_k6_preflight" tools/test/run-transaction-read-model-100m-k6-local.sh >/dev/null
grep -F "run-k6-transaction-100m-loadtest.sh --no-up" tools/test/run-transaction-read-model-100m-k6-local.sh >/dev/null
grep -F "seed-transaction-read-model-100m.sh" tools/test/run-transaction-read-model-100m-k6-local.sh >/dev/null

echo "[transaction-100m-local-runner] fixture prepare plan"
fixture_plan="$(
  SEED_TOTAL_ROWS=1000 \
  K6_REPORT_NAME=transaction-100m-check \
    tools/test/prepare-transaction-read-model-100m-fixture.sh --print-plan
)"
grep -F "mode=print-plan" <<<"${fixture_plan}" >/dev/null
grep -F "fixture phase only" <<<"${fixture_plan}" >/dev/null
grep -F "seed_total_rows=1000" <<<"${fixture_plan}" >/dev/null
grep -F "seed_batch_size=250000" <<<"${fixture_plan}" >/dev/null
grep -F "seed_conflict_mode=fail" <<<"${fixture_plan}" >/dev/null
grep -F "observability=off" <<<"${fixture_plan}" >/dev/null
grep -F "read phase: tools/test/run-transaction-read-model-100m-k6-local.sh --k6-only" <<<"${fixture_plan}" >/dev/null

echo "[transaction-100m-local-runner] fixture prepare contract"
grep -F "fixture phase only" tools/test/prepare-transaction-read-model-100m-fixture.sh >/dev/null
grep -F "run-transaction-read-model-100m-k6-local.sh --k6-only" tools/test/prepare-transaction-read-model-100m-fixture.sh >/dev/null
grep -F "SEED_TOTAL_ROWS" tools/test/prepare-transaction-read-model-100m-fixture.sh >/dev/null
grep -F "SEED_BATCH_SIZE" tools/test/prepare-transaction-read-model-100m-fixture.sh >/dev/null
grep -F "assert_flyway_latest" tools/test/prepare-transaction-read-model-100m-fixture.sh >/dev/null
