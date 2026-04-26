#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/validate-production-high-traffic-config.sh"
workflow=".github/workflows/production-promotion.yml"

echo "[production-high-traffic-config] syntax: ${script}"
bash -n "${script}"

echo "[production-high-traffic-config] workflow wiring"
ruby -e "require 'yaml'; YAML.load_file('${workflow}'); puts 'ok'" >/dev/null
ruby <<'RUBY'
require 'yaml'

workflow = YAML.load_file('.github/workflows/production-promotion.yml')
steps = workflow.fetch('jobs').fetch('promote').fetch('steps')
step_names = steps.map { |step| step['name'] }
target_name = 'Run production high-traffic config gate'
target_index = step_names.index(target_name) or abort("missing step: #{target_name}")
alert_index = step_names.index('Run Alertmanager receiver secret smoke') or abort('alertmanager smoke missing')
deploy_index = step_names.index('Create production deployment') or abort('production deployment step missing')
abort('high-traffic gate must run after alertmanager smoke') unless alert_index < target_index
abort('high-traffic gate must run before production deployment') unless target_index < deploy_index

step = steps.fetch(target_index)
run = step.fetch('run')
env = step.fetch('env')
abort('gate step must call validation script') unless run.include?('tools/ops/validate-production-high-traffic-config.sh')

%w[
  SECURITY_LOGIN_THROTTLING_STORE
  SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS
  REDIS_HOST
  TRANSACTION_READ_REPLICA_ENABLED
  TRANSACTION_READ_REPLICA_URL
  OUTBOX_KAFKA_ENABLED
  OUTBOX_KAFKA_BOOTSTRAP_SERVERS
  NOTIFICATION_INBOX_CONSUMER_ENABLED
  NOTIFICATION_INBOX_CONSUMER_CONCURRENCY
  KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR
  OPS_API_ADMISSION_CONTROL_ENABLED
  OPS_T3MICRO_SATURATION_GUARD_ENABLED
  DB_POOL_MAX_SIZE
].each do |key|
  abort("missing gate env: #{key}") unless env.key?(key)
end

abort('read replica url must come from secret') unless env.fetch('TRANSACTION_READ_REPLICA_URL').include?('secrets.TRANSACTION_READ_REPLICA_URL')
abort('admission flag must come from variable') unless env.fetch('OPS_API_ADMISSION_CONTROL_ENABLED').include?('vars.OPS_API_ADMISSION_CONTROL_ENABLED')
RUBY

echo "[production-high-traffic-config] required key plan"
plan="$("${script}" --print-plan)"
grep -F "SECURITY_LOGIN_THROTTLING_STORE=redis" <<<"${plan}" >/dev/null
grep -F "TRANSACTION_READ_REPLICA_ENABLED=true" <<<"${plan}" >/dev/null
grep -F "OUTBOX_KAFKA_ENABLED=true" <<<"${plan}" >/dev/null
grep -F "NOTIFICATION_INBOX_CONSUMER_CONCURRENCY>=2" <<<"${plan}" >/dev/null
grep -F "OPS_API_ADMISSION_CONTROL_ENABLED=true" <<<"${plan}" >/dev/null
grep -F "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=8" <<<"${plan}" >/dev/null
grep -F "DB_POOL_MAX_SIZE=6" <<<"${plan}" >/dev/null

echo "[production-high-traffic-config] guard: missing env fails"
if "${script}" >/dev/null 2>&1; then
  echo "missing env unexpectedly succeeded" >&2
  exit 1
fi

valid_gate_env=(
  SECURITY_LOGIN_THROTTLING_STORE=redis
  SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS=true
  REDIS_HOST=redis.production.internal
  REDIS_PORT=6379
  TRANSACTION_READ_REPLICA_ENABLED=true
  TRANSACTION_READ_REPLICA_URL=jdbc:postgresql://replica/aquila_bank
  TRANSACTION_READ_REPLICA_USERNAME=replica_user
  TRANSACTION_READ_REPLICA_PASSWORD=replica_password
  TRANSACTION_READ_REPLICA_POOL_MAX_SIZE=2
  TRANSACTION_READ_REPLICA_LAG_THRESHOLD_MS=3000
  OUTBOX_KAFKA_ENABLED=true
  OUTBOX_KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
  OUTBOX_KAFKA_TOPIC_DEFAULT=bank.notification.outbox.v1
  OUTBOX_KAFKA_TOPIC_TRANSFER_BOOKED=bank.transfer.booked.v1
  OUTBOX_KAFKA_TOPIC_TRANSFER_REVERSED=bank.transfer.reversed.v1
  OUTBOX_KAFKA_PRODUCER_ACKS=all
  OUTBOX_KAFKA_PRODUCER_ENABLE_IDEMPOTENCE=true
  OUTBOX_KAFKA_PRODUCER_MAX_IN_FLIGHT=1
  NOTIFICATION_INBOX_CONSUMER_ENABLED=true
  NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED=true
  NOTIFICATION_INBOX_CONSUMER_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
  NOTIFICATION_INBOX_CONSUMER_CONCURRENCY=4
  NOTIFICATION_INBOX_CONSUMER_TRANSFER_BOOKED_TOPIC=bank.transfer.booked.v1
  NOTIFICATION_INBOX_CONSUMER_TRANSFER_REVERSED_TOPIC=bank.transfer.reversed.v1
  NOTIFICATION_INBOX_CONSUMER_DLQ_TOPIC=bank.notification.inbox.dlq.v1
  KAFKA_TOPIC_PROVISIONING_ENABLED=true
  KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED=true
  KAFKA_TOPIC_PROVISIONING_PARTITIONS=6
  KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR=3
  KAFKA_TOPIC_PROVISIONING_MIN_IN_SYNC_REPLICAS=2
  OPS_API_ADMISSION_CONTROL_ENABLED=true
  OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=8
  OPS_API_ADMISSION_CONTROL_ACCOUNT_READ_MAX=4
  OPS_API_ADMISSION_CONTROL_TRANSFER_WRITE_MAX=2
  OPS_API_ADMISSION_CONTROL_NOTIFICATION_STREAM_MAX=4
  OPS_API_ADMISSION_CONTROL_NOTIFICATION_READ_MAX=4
  OPS_API_ADMISSION_CONTROL_INTERNAL_OPS_MAX=2
  OPS_T3MICRO_SATURATION_GUARD_ENABLED=true
  OPS_T3MICRO_SATURATION_GUARD_POOL_ACTIVE_THRESHOLD_PERCENT=90
  OPS_T3MICRO_SATURATION_GUARD_THREADS_BUSY_THRESHOLD_PERCENT=90
  OPS_T3MICRO_SATURATION_GUARD_QUERY_TIMEOUT_THRESHOLD=1
  DB_POOL_MAX_SIZE=6
  SERVER_THREADS_MAX=16
  NOTIFICATION_SSE_MAX_TOTAL_SESSIONS=64
)

echo "[production-high-traffic-config] valid production baseline passes"
env "${valid_gate_env[@]}" "${script}" >/dev/null

echo "[production-high-traffic-config] guard: t3.micro transaction read default fails"
if env "${valid_gate_env[@]}" OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=3 "${script}" >/dev/null 2>&1; then
  echo "t3.micro transaction read admission default unexpectedly succeeded" >&2
  exit 1
fi

echo "[production-high-traffic-config] guard: t3.micro DB pool default fails"
if env "${valid_gate_env[@]}" DB_POOL_MAX_SIZE=4 "${script}" >/dev/null 2>&1; then
  echo "t3.micro DB pool default unexpectedly succeeded" >&2
  exit 1
fi

echo "[production-high-traffic-config] guard: Redis memory fallback fails"
if env "${valid_gate_env[@]}" SECURITY_LOGIN_THROTTLING_STORE=memory "${script}" >/dev/null 2>&1; then
  echo "memory fallback unexpectedly succeeded" >&2
  exit 1
fi

echo "[production-high-traffic-config] guard: unsafe Kafka replication fails"
if env "${valid_gate_env[@]}" KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR=1 "${script}" >/dev/null 2>&1; then
  echo "unsafe Kafka replication unexpectedly succeeded" >&2
  exit 1
fi

echo "[production-high-traffic-config] guard: notification consumer default concurrency fails"
if env "${valid_gate_env[@]}" NOTIFICATION_INBOX_CONSUMER_CONCURRENCY=1 "${script}" >/dev/null 2>&1; then
  echo "default notification consumer concurrency unexpectedly succeeded" >&2
  exit 1
fi

echo "[production-high-traffic-config] guard: consumer concurrency above partitions fails"
if env "${valid_gate_env[@]}" NOTIFICATION_INBOX_CONSUMER_CONCURRENCY=7 "${script}" >/dev/null 2>&1; then
  echo "consumer concurrency above partitions unexpectedly succeeded" >&2
  exit 1
fi

echo "[production-high-traffic-config] guard: consumer concurrency above DB pool fails"
if env "${valid_gate_env[@]}" KAFKA_TOPIC_PROVISIONING_PARTITIONS=8 NOTIFICATION_INBOX_CONSUMER_CONCURRENCY=7 "${script}" >/dev/null 2>&1; then
  echo "consumer concurrency above DB pool unexpectedly succeeded" >&2
  exit 1
fi
