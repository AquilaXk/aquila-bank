#!/usr/bin/env bash
set -euo pipefail

echo "[transaction-retention-cutoff] fixture: hot account 72000 rows over 720 days + noise 4x4000 rows"
echo "[transaction-retention-cutoff] target: short=30d hot=3000/candidate=69000, default=365d hot=36500/candidate=35500, long=730d hot=72000/candidate=0"
echo "[transaction-retention-cutoff] index guard: cutoff windows keep idx_transaction_read_model_account_cursor without Seq Scan or Sort"
echo "[transaction-retention-cutoff] runtime guard: small cleanup batch, statement_timeout=3000ms query-timeout=3s request-timeout=5000ms"

tools/test/with-resource-lock.sh back-gradle-transaction-retention-cutoff ./back/gradlew -p back test \
  --tests '*JdbcTransactionReadModelRetentionCutoffLoadIntegrationTest'
