#!/usr/bin/env bash
set -euo pipefail

echo "[transaction-concurrency-slo] fixture: hot account 80000 rows + noise 6x4000 rows"
echo "[transaction-concurrency-slo] workload: 16 concurrent repository fetches across first/cursor/status/direction/amount/reference query shapes"
echo "[transaction-concurrency-slo] target: failures=0 p95<=350ms max<=750ms"
echo "[transaction-concurrency-slo] pool guard: default DB_POOL_MAX_SIZE=4, SERVER_THREADS_MAX=16"

tools/test/with-resource-lock.sh back-gradle-transaction-concurrency ./back/gradlew -p back test \
  --tests '*TransactionQueryConcurrencySloIntegrationTest'
