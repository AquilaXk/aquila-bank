#!/usr/bin/env bash
set -euo pipefail

echo "[bootstrap-bulk-import] fixture: 2 accounts + 1 user + 2 memberships"
echo "[bootstrap-bulk-import] expectation: bounded internal bulk import and request-level rollback"

./back/gradlew -p back test \
  --tests '*BootstrapBulkImportApiIntegrationTest'
