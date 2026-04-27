#!/usr/bin/env bash
set -euo pipefail

echo "[loadtest-readiness-recovery] shell syntax"
bash -n tools/test/run-transaction-100m-fixture-restore.sh
bash -n tools/test/run-transaction-100m-fresh-volume-restore-k6.sh
bash -n tools/test/run-outbox-provider-backlog-local-gate.sh
bash -n tools/test/run-k6-transaction-100m-loadtest.sh

echo "[loadtest-readiness-recovery] actuator permit contract"
grep -F '"/actuator/health/**"' back/src/main/java/com/aquilabank/global/security/SecurityConfiguration.java >/dev/null
grep -F "keepsReadinessUpWhenOutboxThresholdIsExceeded" back/src/test/java/com/aquilabank/global/web/notification/OutboxOpsApiIntegrationTest.java >/dev/null

echo "[loadtest-readiness-recovery] postgres recovery wait contract"
grep -F "FIXTURE_RECOVERY_WAIT_SECONDS" tools/test/run-transaction-100m-fixture-restore.sh >/dev/null
grep -F "wait_for_postgres_recovery" tools/test/run-transaction-100m-fixture-restore.sh >/dev/null
grep -F "PostgreSQL recovery wait exceeded" tools/test/run-transaction-100m-fixture-restore.sh >/dev/null

plan="$(tools/test/run-transaction-100m-fixture-restore.sh --print-plan)"
grep -F "recovery_wait_seconds=180" <<<"${plan}" >/dev/null
grep -F "postgres_container=aquila-bank-postgres-loadtest" <<<"${plan}" >/dev/null

local_plan="$(tools/test/run-outbox-provider-backlog-local-gate.sh --print-plan)"
grep -F "base_url=http://localhost:18080" <<<"${local_plan}" >/dev/null
grep -F "readiness_path=/actuator/health/readiness" <<<"${local_plan}" >/dev/null

k6_plan="$(
  K6_REPORT_NAME=readiness-check \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
)"
grep -F "backend readiness gate=true path=/actuator/health/readiness timeout=120" <<<"${k6_plan}" >/dev/null
grep -F "wait_for_backend_readiness" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
