#!/usr/bin/env bash
set -euo pipefail

echo "[transaction-archive-baseline] fixture: archive hot account 80000 rows + noise 6x4000 rows"
echo "[transaction-archive-baseline] target: first-page/cursor/status/reference keep archive account keyset indexes without Seq Scan or Sort"
echo "[transaction-archive-baseline] timeout order: statement_timeout=3000ms query-timeout=3s request-timeout=5000ms"

tools/test/with-resource-lock.sh back-gradle-transaction-archive-baseline ./back/gradlew -p back test \
  --tests '*JdbcTransactionArchiveReadRepositoryBaselineIntegrationTest'
