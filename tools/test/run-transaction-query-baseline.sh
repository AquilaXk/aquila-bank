#!/usr/bin/env bash
set -euo pipefail

echo "[transaction-baseline] fixture: hot account 80000 rows + noise 6x4000 rows"
echo "[transaction-baseline] target p95: first-page<=120ms cursor-page<=150ms status-page<=150ms"
echo "[transaction-baseline] timeout order: statement_timeout=3000ms query-timeout=3s request-timeout=5000ms"

./back/gradlew -p back test \
  --tests '*JdbcTransactionReadRepositoryBaselineIntegrationTest' \
  --tests '*TransactionDatasourceStatementTimeoutIntegrationTest' \
  --tests '*TransactionJdbcQueryTimeoutIntegrationTest'
