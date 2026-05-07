#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "::error::$*" >&2
  exit 1
}

print_plan() {
  cat <<'PLAN'
[production-high-traffic-config] required defensive baseline:
- SECURITY_LOGIN_THROTTLING_STORE=memory 또는 redis
- SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS=false when store=memory
- TRANSACTION_READ_REPLICA_ENABLED=false by default; true일 때 replica env required
- OUTBOX_KAFKA_ENABLED=true
- NOTIFICATION_INBOX_CONSUMER_ENABLED=true
- KAFKA_TOPIC_PROVISIONING_ENABLED=true
- KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED=true
- OPS_API_ADMISSION_CONTROL_ENABLED=true
- OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX<=3
- OPS_T3MICRO_SATURATION_GUARD_ENABLED=true
- DB_POOL_MAX_SIZE<=4
- SERVER_THREADS_MAX<=16
- NOTIFICATION_SSE_MAX_TOTAL_SESSIONS<=64
- worker batch env가 있으면 small batch upper bound를 초과할 수 없음
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

require_false() {
  local name="$1"
  require_env "$name"
  [ "$(normalize_bool "${!name}")" = "false" ] || fail "${name} must be false"
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

require_max_integer() {
  local name="$1"
  local max="$2"
  require_positive_integer "$name"
  local value="${!name}"
  [ "$value" -le "$max" ] || fail "${name} must be ${max} or less"
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

is_true() {
  local name="$1"
  [ "$(normalize_bool "${!name:-false}")" = "true" ]
}

optional_max_integer() {
  local name="$1"
  local max="$2"
  local value="${!name:-}"
  [ -z "$value" ] && return 0
  require_max_integer "$name" "$max"
}

validate_login_throttling() {
  require_env SECURITY_LOGIN_THROTTLING_STORE
  require_env SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS

  case "$(normalize_bool "${SECURITY_LOGIN_THROTTLING_STORE}")" in
    memory)
      require_false SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS
      ;;
    redis)
      require_true SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS
      require_env REDIS_HOST
      require_positive_integer REDIS_PORT
      ;;
    *)
      fail "SECURITY_LOGIN_THROTTLING_STORE must be memory or redis"
      ;;
  esac
}

validate_read_replica() {
  is_true TRANSACTION_READ_REPLICA_ENABLED || return 0

  require_env TRANSACTION_READ_REPLICA_URL
  require_env TRANSACTION_READ_REPLICA_USERNAME
  require_env TRANSACTION_READ_REPLICA_PASSWORD
  require_positive_integer TRANSACTION_READ_REPLICA_POOL_MAX_SIZE
  require_positive_integer TRANSACTION_READ_REPLICA_LAG_THRESHOLD_MS
}

validate_kafka() {
  local outbox_enabled=false
  local consumer_enabled=false
  local topic_provisioning_enabled=false
  local topic_validation_enabled=false

  require_true OUTBOX_KAFKA_ENABLED
  require_true NOTIFICATION_INBOX_CONSUMER_ENABLED
  require_true KAFKA_TOPIC_PROVISIONING_ENABLED
  require_true KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED

  outbox_enabled=true
  consumer_enabled=true
  topic_provisioning_enabled=true
  topic_validation_enabled=true

  if [ "$outbox_enabled" = "true" ]; then
    require_env OUTBOX_KAFKA_BOOTSTRAP_SERVERS
    require_env OUTBOX_KAFKA_TOPIC_DEFAULT
    require_env OUTBOX_KAFKA_TOPIC_TRANSFER_BOOKED
    require_env OUTBOX_KAFKA_TOPIC_TRANSFER_REVERSED
    require_equal OUTBOX_KAFKA_PRODUCER_ACKS all
    require_true OUTBOX_KAFKA_PRODUCER_ENABLE_IDEMPOTENCE
    require_equal OUTBOX_KAFKA_PRODUCER_MAX_IN_FLIGHT 1
  fi

  if [ "$consumer_enabled" = "true" ]; then
    require_true NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED
    require_env NOTIFICATION_INBOX_CONSUMER_BOOTSTRAP_SERVERS
    require_min_integer NOTIFICATION_INBOX_CONSUMER_CONCURRENCY 1
    require_env NOTIFICATION_INBOX_CONSUMER_TRANSFER_BOOKED_TOPIC
    require_env NOTIFICATION_INBOX_CONSUMER_TRANSFER_REVERSED_TOPIC
    require_env NOTIFICATION_INBOX_CONSUMER_DLQ_TOPIC
  fi

  if [ "$outbox_enabled" = "true" ] && [ "$consumer_enabled" = "true" ]; then
    require_same_value OUTBOX_KAFKA_BOOTSTRAP_SERVERS NOTIFICATION_INBOX_CONSUMER_BOOTSTRAP_SERVERS
  fi

  if [ "$topic_provisioning_enabled" = "true" ] || [ "$topic_validation_enabled" = "true" ]; then
    if [ "$outbox_enabled" != "true" ] && [ "$consumer_enabled" != "true" ]; then
      fail "Kafka topic validation requires OUTBOX_KAFKA_ENABLED or NOTIFICATION_INBOX_CONSUMER_ENABLED"
    fi
    require_positive_integer KAFKA_TOPIC_PROVISIONING_PARTITIONS
    require_min_integer KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR 1
    require_min_integer KAFKA_TOPIC_PROVISIONING_MIN_IN_SYNC_REPLICAS 1
  fi

  if [ "$consumer_enabled" = "true" ] && [ "$topic_provisioning_enabled" = "true" ]; then
    require_lte_integer NOTIFICATION_INBOX_CONSUMER_CONCURRENCY KAFKA_TOPIC_PROVISIONING_PARTITIONS
  fi

  if [ "$consumer_enabled" = "true" ]; then
    require_lte_integer NOTIFICATION_INBOX_CONSUMER_CONCURRENCY DB_POOL_MAX_SIZE
  fi
}

validate_admission_and_t3micro() {
  require_true OPS_API_ADMISSION_CONTROL_ENABLED
  require_max_integer OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX 3
  require_max_integer OPS_API_ADMISSION_CONTROL_ACCOUNT_READ_MAX 4
  require_max_integer OPS_API_ADMISSION_CONTROL_TRANSFER_WRITE_MAX 2
  require_max_integer OPS_API_ADMISSION_CONTROL_NOTIFICATION_STREAM_MAX 4
  require_max_integer OPS_API_ADMISSION_CONTROL_NOTIFICATION_READ_MAX 4
  require_max_integer OPS_API_ADMISSION_CONTROL_INTERNAL_OPS_MAX 2

  require_true OPS_T3MICRO_SATURATION_GUARD_ENABLED
  require_positive_integer OPS_T3MICRO_SATURATION_GUARD_POOL_ACTIVE_THRESHOLD_PERCENT
  require_positive_integer OPS_T3MICRO_SATURATION_GUARD_THREADS_BUSY_THRESHOLD_PERCENT
  require_positive_integer OPS_T3MICRO_SATURATION_GUARD_QUERY_TIMEOUT_THRESHOLD

  require_max_integer DB_POOL_MAX_SIZE 4
  require_max_integer SERVER_THREADS_MAX 16
  require_max_integer NOTIFICATION_SSE_MAX_TOTAL_SESSIONS 64

  optional_max_integer OUTBOX_POLLER_BATCH_SIZE 20
  optional_max_integer OUTBOX_CLEANUP_BATCH_SIZE 500
  optional_max_integer AUTH_PASSWORD_RECOVERY_DELIVERY_WORKER_BATCH_SIZE 10
  optional_max_integer AUTH_REFRESH_TOKEN_SESSION_CLEANUP_BATCH_SIZE 500
  optional_max_integer AUTH_PASSWORD_RECOVERY_TOKEN_CLEANUP_BATCH_SIZE 500
  optional_max_integer LEDGER_COMMAND_IDEMPOTENCY_CLEANUP_BATCH_SIZE 500
  optional_max_integer LEDGER_COMMAND_IDEMPOTENCY_OPS_RECOVERY_BATCH_SIZE 100
  optional_max_integer LEDGER_SNAPSHOT_RECONCILIATION_BATCH_SIZE 100
  optional_max_integer TRANSACTION_READ_MODEL_CLEANUP_BATCH_SIZE 500
  optional_max_integer NOTIFICATION_CHANNEL_PROVIDER_WORKER_BATCH_SIZE 10
  optional_max_integer NOTIFICATION_CHANNEL_PROVIDER_CLEANUP_BATCH_SIZE 200
  optional_max_integer NOTIFICATION_INBOX_CLEANUP_BATCH_SIZE 500
}

main() {
  if [ "${1:-}" = "--print-plan" ]; then
    print_plan
    return 0
  fi

  validate_login_throttling
  validate_read_replica
  validate_admission_and_t3micro
  validate_kafka
  echo "Production high-traffic config baseline is complete."
}

main "$@"
