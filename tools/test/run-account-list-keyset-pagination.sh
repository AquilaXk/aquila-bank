#!/usr/bin/env bash
set -euo pipefail

echo "[account-list-keyset] fixture: JWT user account list keyset page + 5000 membership EXPLAIN baseline"
echo "[account-list-keyset] expectation: user_id/status/account_id cursor index without membership Seq Scan"

./back/gradlew -p back test \
  --tests '*AccountListJwtSecurityIntegrationTest' \
  --tests '*JdbcAccountListRepositoryIntegrationTest'
