#!/usr/bin/env bash
set -euo pipefail

echo "[transaction-plan-gate] fixture: baseline hot account + long-history partition-fit + concurrency SLO"
echo "[transaction-plan-gate] expectation: account-scoped keyset plans keep idx_transaction_read_model_account_* without Seq Scan or Sort"
echo "[transaction-plan-gate] latency target: baseline p95 thresholds and concurrency failures=0 p95<=350ms max<=750ms"
echo "[transaction-plan-gate] timeout guard: statement_timeout=3000ms query-timeout=3s request-timeout=5000ms"

tools/test/with-resource-lock.sh back-gradle-transaction-plan ./back/gradlew -p back test \
  --tests '*JdbcTransactionReadRepositoryBaselineIntegrationTest' \
  --tests '*TransactionDatasourceStatementTimeoutIntegrationTest' \
  --tests '*TransactionJdbcQueryTimeoutIntegrationTest' \
  --tests '*JdbcTransactionReadRepositoryPartitionFitIntegrationTest' \
  --tests '*TransactionQueryConcurrencySloIntegrationTest'
