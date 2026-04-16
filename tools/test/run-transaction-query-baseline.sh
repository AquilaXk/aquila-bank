#!/usr/bin/env bash
set -euo pipefail

./back/gradlew -p back test \
  --tests '*JdbcTransactionReadRepositoryBaselineIntegrationTest' \
  --tests '*TransactionDatasourceStatementTimeoutIntegrationTest' \
  --tests '*TransactionJdbcQueryTimeoutIntegrationTest'
