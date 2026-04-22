#!/usr/bin/env bash
set -euo pipefail

echo "[notification-user-inbox-baseline] fixture: target user 3x5000 rows + revoked 5000 rows + noise 24x5000 rows"
echo "[notification-user-inbox-baseline] expectation: user first/cursor page uses idx_notification_inbox_account_visible_cursor without notification_inbox Seq Scan"
echo "[notification-user-inbox-baseline] path: JWT user visibility = active membership + active user + per-user hidden state"

./back/gradlew -p back test \
  --tests '*JdbcNotificationInboxRepositoryUserExplainBaselineIntegrationTest'
