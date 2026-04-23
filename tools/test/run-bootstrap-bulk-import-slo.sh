#!/usr/bin/env bash
set -euo pipefail

echo "[bootstrap-bulk-import-slo] normal: bounded large bulk import finishes within SLO"
echo "[bootstrap-bulk-import-slo] rollback: duplicate login failure rolls back within SLO"

./back/gradlew -p back test \
  --tests '*BootstrapBulkImportApiIntegrationTest.importsLargeBootstrapFixtureWithinSlo' \
  --tests '*BootstrapBulkImportApiIntegrationTest.rollsBackLargeBootstrapFixtureWithinSloWhenAnyItemFails'
