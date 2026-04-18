#!/usr/bin/env bash
set -euo pipefail

echo "[transaction-partition-fit] fixture: recent-window=80000 rows history-window=12x12000 rows on one hot account"
echo "[transaction-partition-fit] expectation: recent/history/status queries keep idx_transaction_read_model_account_* path without Seq Scan or Sort"
echo "[transaction-partition-fit] current decision: 31일 account-scoped query contract 기준 partition/archive 즉시 도입 불필요"
echo "[transaction-partition-fit] recheck triggers: >31일 조회, account scope 완화, recent-window p95 회귀, autovacuum/bloat/backup pressure"

./back/gradlew -p back test \
  --tests '*JdbcTransactionReadRepositoryPartitionFitIntegrationTest'
