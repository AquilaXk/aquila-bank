#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "::error::$*" >&2
  exit 1
}

print_plan() {
  cat <<'PLAN'
[production-high-traffic-config] required high-traffic baseline:
- SECURITY_LOGIN_THROTTLING_STORE=redis
- SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS=true
- TRANSACTION_READ_REPLICA_ENABLED=true
- OUTBOX_KAFKA_ENABLED=true
- NOTIFICATION_INBOX_CONSUMER_ENABLED=true
- NOTIFICATION_INBOX_CONSUMER_CONCURRENCY>=2
- NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED=true
- KAFKA_TOPIC_PROVISIONING_ENABLED=true
- KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED=true
- OPS_API_ADMISSION_CONTROL_ENABLED=true
- OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=8
- OPS_T3MICRO_SATURATION_GUARD_ENABLED=true
- DB_POOL_MAX_SIZE=6
- Redis/read replica/Kafka/admission/t3.micro required env values must be present
PLAN
}

normalize_bool() {
  local value="$1"
  printf '%s' "${value}" | tr '[:upper:]' '[:lower:]'
}

require_env() {
  local name="$1"
  local value="${!name:-}"
  [ -n "$value" ] || fail "Missing required environment variable: ${name}"
}

require_true() {
  local name="$1"
  require_env "$name"
  [ "$(normalize_bool "${!name}")" = "true" ] || fail "${name} must be true"
}

require_equal() {
  local name="$1"
  local expected="$2"
  require_env "$name"
  [ "${!name}" = "$expected" ] || fail "${name} must be ${expected}"
}

require_positive_integer() {
  local name="$1"
  require_env "$name"
  local value="${!name}"
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || fail "${name} must be a positive integer"
}

require_min_integer() {
  local name="$1"
  local min="$2"
  require_positive_integer "$name"
  local value="${!name}"
  [ "$value" -ge "$min" ] || fail "${name} must be ${min} or greater"
}

require_lte_integer() {
  local name="$1"
  local max_name="$2"
  require_positive_integer "$name"
  require_positive_integer "$max_name"
  local value="${!name}"
  local max_value="${!max_name}"
  [ "$value" -le "$max_value" ] || fail "${name} must be less than or equal to ${max_name}"
}

require_same_value() {
  local left="$1"
  local right="$2"
  require_env "$left"
  require_env "$right"
  [ "${!left}" = "${!right}" ] || fail "${left} must match ${right}"
}

validate_redis() {
  require_equal SECURITY_LOGIN_THROTTLING_STORE redis
  require_true SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS
  require_env REDIS_HOST
  require_positive_integer REDIS_PORT
}

validate_read_replica() {
  require_true TRANSACTION_READ_REPLICA_ENABLED
  require_env TRANSACTION_READ_REPLICA_URL
  require_env TRANSACTION_READ_REPLICA_USERNAME
  require_env TRANSACTION_READ_REPLICA_PASSWORD
  require_positive_integer TRANSACTION_READ_REPLICA_POOL_MAX_SIZE
  require_positive_integer TRANSACTION_READ_REPLICA_LAG_THRESHOLD_MS
}

validate_kafka() {
  require_true OUTBOX_KAFKA_ENABLED
  require_env OUTBOX_KAFKA_BOOTSTRAP_SERVERS
  require_env OUTBOX_KAFKA_TOPIC_DEFAULT
  require_env OUTBOX_KAFKA_TOPIC_TRANSFER_BOOKED
  require_env OUTBOX_KAFKA_TOPIC_TRANSFER_REVERSED
  require_equal OUTBOX_KAFKA_PRODUCER_ACKS all
  require_true OUTBOX_KAFKA_PRODUCER_ENABLE_IDEMPOTENCE
  require_equal OUTBOX_KAFKA_PRODUCER_MAX_IN_FLIGHT 1

  require_true NOTIFICATION_INBOX_CONSUMER_ENABLED
  require_true NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED
  require_env NOTIFICATION_INBOX_CONSUMER_BOOTSTRAP_SERVERS
  require_min_integer NOTIFICATION_INBOX_CONSUMER_CONCURRENCY 2
  require_env NOTIFICATION_INBOX_CONSUMER_TRANSFER_BOOKED_TOPIC
  require_env NOTIFICATION_INBOX_CONSUMER_TRANSFER_REVERSED_TOPIC
  require_env NOTIFICATION_INBOX_CONSUMER_DLQ_TOPIC
  require_same_value OUTBOX_KAFKA_BOOTSTRAP_SERVERS NOTIFICATION_INBOX_CONSUMER_BOOTSTRAP_SERVERS

  require_true KAFKA_TOPIC_PROVISIONING_ENABLED
  require_true KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED
  require_positive_integer KAFKA_TOPIC_PROVISIONING_PARTITIONS
  require_lte_integer NOTIFICATION_INBOX_CONSUMER_CONCURRENCY KAFKA_TOPIC_PROVISIONING_PARTITIONS
  require_min_integer KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR 3
  require_min_integer KAFKA_TOPIC_PROVISIONING_MIN_IN_SYNC_REPLICAS 2
}

validate_admission_and_t3micro() {
  require_true OPS_API_ADMISSION_CONTROL_ENABLED
  require_equal OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX 8
  require_positive_integer OPS_API_ADMISSION_CONTROL_ACCOUNT_READ_MAX
  require_positive_integer OPS_API_ADMISSION_CONTROL_TRANSFER_WRITE_MAX
  require_positive_integer OPS_API_ADMISSION_CONTROL_NOTIFICATION_STREAM_MAX
  require_positive_integer OPS_API_ADMISSION_CONTROL_NOTIFICATION_READ_MAX
  require_positive_integer OPS_API_ADMISSION_CONTROL_INTERNAL_OPS_MAX

  require_true OPS_T3MICRO_SATURATION_GUARD_ENABLED
  require_positive_integer OPS_T3MICRO_SATURATION_GUARD_POOL_ACTIVE_THRESHOLD_PERCENT
  require_positive_integer OPS_T3MICRO_SATURATION_GUARD_THREADS_BUSY_THRESHOLD_PERCENT
  require_positive_integer OPS_T3MICRO_SATURATION_GUARD_QUERY_TIMEOUT_THRESHOLD

  require_equal DB_POOL_MAX_SIZE 6
  require_lte_integer NOTIFICATION_INBOX_CONSUMER_CONCURRENCY DB_POOL_MAX_SIZE
  require_positive_integer SERVER_THREADS_MAX
  require_positive_integer NOTIFICATION_SSE_MAX_TOTAL_SESSIONS
}

main() {
  if [ "${1:-}" = "--print-plan" ]; then
    print_plan
    return 0
  fi

  validate_redis
  validate_read_replica
  validate_kafka
  validate_admission_and_t3micro
  echo "Production high-traffic config baseline is complete."
}

main "$@"
