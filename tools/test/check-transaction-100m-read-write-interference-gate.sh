#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-100m-read-write-interference-gate.sh"
write_script="ops/k6/transfer-write-interference.js"

echo "[transaction-read-write-interference] shell syntax"
bash -n "${runner}"

echo "[transaction-read-write-interference] compose config"
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml config >/dev/null

echo "[transaction-read-write-interference] print plan"
plan="$(
  INTERFERENCE_NAME=transaction-interference-check \
  INTERFERENCE_DURATION=45s \
  INTERFERENCE_READ_VUS=8 \
  INTERFERENCE_WRITE_VUS=2 \
    "${runner}" --print-plan
)"
grep -F "name=transaction-interference-check" <<<"${plan}" >/dev/null
grep -F "duration=45s" <<<"${plan}" >/dev/null
grep -F "read_vus=8" <<<"${plan}" >/dev/null
grep -F "write_vus=2" <<<"${plan}" >/dev/null
grep -F "write_amount_minor=1" <<<"${plan}" >/dev/null
grep -F "read_hot_p95_threshold_ms=350" <<<"${plan}" >/dev/null
grep -F "read_cold_p95_threshold_ms=750" <<<"${plan}" >/dev/null
grep -F "read_overload_mode=false" <<<"${plan}" >/dev/null
grep -F "read_429_rate_threshold=0" <<<"${plan}" >/dev/null
grep -F "read_overload_429_rate_threshold=0.05" <<<"${plan}" >/dev/null
grep -F "write_429_rate_threshold=0.05" <<<"${plan}" >/dev/null
grep -F "services=postgres,kafka,aquila-bank-backend,prometheus,grafana,alertmanager,postgres-exporter" <<<"${plan}" >/dev/null
grep -F "write_fixture_enabled=true" <<<"${plan}" >/dev/null
grep -F "write_fixture_cleanup=true" <<<"${plan}" >/dev/null
grep -F "write_source_account_id=920000001" <<<"${plan}" >/dev/null
grep -F "write_target_account_id=920000002" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/k6/transaction-interference-check/read-write-interference-summary.tsv" <<<"${plan}" >/dev/null

overload_plan="$(
  INTERFERENCE_NAME=transaction-interference-overload-check \
  INTERFERENCE_READ_OVERLOAD_MODE=true \
    "${runner}" --print-plan
)"
grep -F "read_overload_mode=true" <<<"${overload_plan}" >/dev/null
grep -F "read_429_rate_threshold=0.05" <<<"${overload_plan}" >/dev/null

echo "[transaction-read-write-interference] runner contract"
grep -F "bootstrap_write_fixture" "${runner}" >/dev/null
grep -F "cleanup_write_fixture" "${runner}" >/dev/null
grep -F "OVERRIDING SYSTEM VALUE" "${runner}" >/dev/null
grep -F "INTERFERENCE_WRITE_FIXTURE_ENABLED" "${runner}" >/dev/null
grep -F "KAFKA_ADVERTISED_HOST=kafka" "${runner}" >/dev/null
grep -F "OUTBOX_KAFKA_ENABLED=true" "${runner}" >/dev/null
grep -F "NOTIFICATION_INBOX_CONSUMER_ENABLED=true" "${runner}" >/dev/null
grep -F "KAFKA_TOPIC_PROVISIONING_ENABLED=true" "${runner}" >/dev/null
grep -F 'K6_OVERLOAD_MODE="${read_overload_mode}"' "${runner}" >/dev/null
grep -F 'K6_OVERLOAD_429_RATE_THRESHOLD="${read_overload_429_rate_threshold}"' "${runner}" >/dev/null
grep -F "run-k6-transaction-100m-loadtest.sh --no-up --no-deps" "${runner}" >/dev/null
grep -F "transfer-write-interference.js" "${runner}" >/dev/null
grep -F "read-write-interference-summary.tsv" "${runner}" >/dev/null
grep -F "check_interference_thresholds" "${runner}" >/dev/null
grep -F "aquila_transaction_429_rate" "${runner}" >/dev/null
grep -F "aquila_transfer_write_429_rate" "${runner}" >/dev/null
source_require_count="$(grep -c '^[[:space:]]*require_env INTERFERENCE_WRITE_SOURCE_ACCOUNT_ID$' "${runner}")"
target_require_count="$(grep -c '^[[:space:]]*require_env INTERFERENCE_WRITE_TARGET_ACCOUNT_ID$' "${runner}")"
if [[ "${source_require_count}" -ne 1 ]]; then
  echo "INTERFERENCE_WRITE_SOURCE_ACCOUNT_ID must be required only when fixture is disabled" >&2
  exit 1
fi
if [[ "${target_require_count}" -ne 1 ]]; then
  echo "INTERFERENCE_WRITE_TARGET_ACCOUNT_ID must be required only when fixture is disabled" >&2
  exit 1
fi
if grep -F "cleanup_write_fixture >/dev/null 2>&1 || true" "${runner}" >/dev/null; then
  echo "cleanup_write_fixture must not suppress cleanup failures silently" >&2
  exit 1
fi
grep -F "cleanup failed; fixture rows may remain" "${runner}" >/dev/null
grep -F "interference_fixture_account_ids" "${runner}" >/dev/null
grep -F "interference_fixture_transaction_references" "${runner}" >/dev/null
grep -F "payload->>'transactionReference'" "${runner}" >/dev/null
grep -F "idempotency_key LIKE 'interference-\${write_target_account_id}-%'" "${runner}" >/dev/null

echo "[transaction-read-write-interference] k6 write contract"
grep -F "K6_WRITE_SOURCE_ACCOUNT_ID" "${write_script}" >/dev/null
grep -F "K6_WRITE_TARGET_ACCOUNT_ID" "${write_script}" >/dev/null
grep -F "Idempotency-Key" "${write_script}" >/dev/null
grep -F "/api/v1/transfers" "${write_script}" >/dev/null
grep -F "aquila_transfer_write_429_rate" "${write_script}" >/dev/null
grep -F "aquila_transfer_write_duration_ms" "${write_script}" >/dev/null
grep -F "Retry-After" "${write_script}" >/dev/null

echo "[transaction-read-write-interference] compose kafka override"
grep -F 'image: ${KAFKA_IMAGE:-bitnamilegacy/kafka:4.0.0-debian-12-r10}' compose.yml >/dev/null
if sed -n '/^  kafka:/,/^  [a-zA-Z0-9_-]*:/p' compose.yml | grep -F "profiles:" >/dev/null; then
  echo "Kafka service must be always-on in local compose" >&2
  exit 1
fi
grep -F 'KAFKA_CFG_ADVERTISED_LISTENERS: PLAINTEXT://${KAFKA_ADVERTISED_HOST:-localhost}:${KAFKA_PORT:-9092}' compose.yml >/dev/null
grep -F 'OUTBOX_KAFKA_ENABLED: ${OUTBOX_KAFKA_ENABLED:-true}' compose.loadtest.yml >/dev/null
grep -F 'OUTBOX_KAFKA_BOOTSTRAP_SERVERS: ${OUTBOX_KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}' compose.loadtest.yml >/dev/null
grep -F 'NOTIFICATION_INBOX_CONSUMER_ENABLED: ${NOTIFICATION_INBOX_CONSUMER_ENABLED:-true}' compose.loadtest.yml >/dev/null
grep -F 'NOTIFICATION_INBOX_CONSUMER_BOOTSTRAP_SERVERS: ${NOTIFICATION_INBOX_CONSUMER_BOOTSTRAP_SERVERS:-kafka:9092}' compose.loadtest.yml >/dev/null
grep -F 'KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED: ${KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED:-true}' compose.loadtest.yml >/dev/null

echo "[transaction-read-write-interference] invalid input fails"
if INTERFERENCE_DURATION=0m "${runner}" --print-plan >/dev/null 2>&1; then
  echo "INTERFERENCE_DURATION=0m unexpectedly succeeded" >&2
  exit 1
fi
if INTERFERENCE_WRITE_VUS=0 "${runner}" --print-plan >/dev/null 2>&1; then
  echo "INTERFERENCE_WRITE_VUS=0 unexpectedly succeeded" >&2
  exit 1
fi
if INTERFERENCE_WRITE_429_RATE_THRESHOLD=1.5 "${runner}" --print-plan >/dev/null 2>&1; then
  echo "INTERFERENCE_WRITE_429_RATE_THRESHOLD=1.5 unexpectedly succeeded" >&2
  exit 1
fi
if INTERFERENCE_READ_OVERLOAD_MODE=maybe "${runner}" --print-plan >/dev/null 2>&1; then
  echo "INTERFERENCE_READ_OVERLOAD_MODE=maybe unexpectedly succeeded" >&2
  exit 1
fi
if INTERFERENCE_READ_OVERLOAD_429_RATE_THRESHOLD=1.5 "${runner}" --print-plan >/dev/null 2>&1; then
  echo "INTERFERENCE_READ_OVERLOAD_429_RATE_THRESHOLD=1.5 unexpectedly succeeded" >&2
  exit 1
fi
