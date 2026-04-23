#!/usr/bin/env bash
set -euo pipefail

echo "[notification-provider-delivery] fixture: email accepted + sms delayed response on local webhook server"
echo "[notification-provider-delivery] runtime budget: worker batch=2 connect-timeout=500ms read-timeout=50ms"
echo "[notification-provider-delivery] expectation: email sent, sms timeout -> FAILED with nextAttemptAt=base+5s"

./back/gradlew -p back test \
  --tests '*NotificationChannelProviderDeliverySmokeTest'
