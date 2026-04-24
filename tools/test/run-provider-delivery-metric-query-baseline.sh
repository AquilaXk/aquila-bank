#!/usr/bin/env bash
set -euo pipefail

echo "[provider-delivery-metric-query-baseline] fixture: notification/password-recovery delivery status 24000 rows + backlog 8000 rows"
echo "[provider-delivery-metric-query-baseline] expectation: metric status/skip/backlog counts use bounded partial indexes without Seq Scan or Sort"
echo "[provider-delivery-metric-query-baseline] path: /actuator/prometheus provider delivery gauges"

tools/test/with-resource-lock.sh back-gradle-provider-delivery-metric-query-baseline ./back/gradlew -p back test \
  --tests '*ProviderDeliveryMetricQueryBaselineIntegrationTest'
