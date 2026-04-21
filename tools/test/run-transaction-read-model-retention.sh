#!/usr/bin/env bash
set -euo pipefail

echo "[transaction-retention] fixture: cutoff 이전 read model 2 rows + fresh row 1 row"
echo "[transaction-retention] expectation: expired rows move to transaction_read_model_archive; ledger_entry remains"
echo "[transaction-retention] index guard: cleanup BRIN must not replace account-scoped keyset btree plans"
echo "[transaction-retention] runtime default: TRANSACTION_READ_MODEL_CLEANUP_ENABLED=false"

tools/test/with-resource-lock.sh back-gradle-transaction-retention ./back/gradlew -p back test \
  --tests '*TransactionReadModelRetentionCleanupServiceTest' \
  --tests '*JdbcTransactionReadModelRetentionCleanupRepositoryIntegrationTest' \
  --tests '*JdbcTransactionReadRepositoryPartitionFitIntegrationTest'
