#!/usr/bin/env bash
set -euo pipefail

echo "[notification-channel-outbox-claim-baseline] fixture: due 20000 rows + future 4000 rows + sent 4000 rows"
echo "[notification-channel-outbox-claim-baseline] expectation: claim candidates use idx_notification_channel_outbox_due_claim without channel outbox Seq Scan or Sort"
echo "[notification-channel-outbox-claim-baseline] path: PENDING/FAILED + available_at <= now + ORDER BY available_at,id + small LIMIT + SKIP LOCKED"

tools/test/with-resource-lock.sh back-gradle-notification-channel-outbox-claim-baseline ./back/gradlew -p back test \
  --tests '*JdbcNotificationChannelOutboxClaimExplainBaselineIntegrationTest'
