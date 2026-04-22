#!/usr/bin/env bash
set -euo pipefail

echo "[password-recovery-throttling] fixture: memory throttling threshold 2 per IP / 1s window"
echo "[password-recovery-throttling] expectation: recovery request burst returns 429 before token write"

./back/gradlew -p back test \
  --tests '*LoginThrottlingIntegrationTest'
