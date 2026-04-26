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
  OUTBOX_KAFKA_ENABLED
  NOTIFICATION_INBOX_CONSUMER_ENABLED
  KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED
  OPS_API_ADMISSION_CONTROL_ENABLED
  OPS_T3MICRO_SATURATION_GUARD_ENABLED
  DB_POOL_MAX_SIZE
  SERVER_THREADS_MAX
  NOTIFICATION_SSE_MAX_TOTAL_SESSIONS
].each do |key|
  abort("missing gate env: #{key}") unless env.key?(key)
end

abort('admission flag must come from variable') unless env.fetch('OPS_API_ADMISSION_CONTROL_ENABLED').include?('vars.OPS_API_ADMISSION_CONTROL_ENABLED')
RUBY

echo "[production-high-traffic-config] required key plan"
plan="$("${script}" --print-plan)"
grep -F "SECURITY_LOGIN_THROTTLING_STORE=memory 또는 redis" <<<"${plan}" >/dev/null
grep -F "TRANSACTION_READ_REPLICA_ENABLED=false by default" <<<"${plan}" >/dev/null
grep -F "OUTBOX_KAFKA_ENABLED=false by default" <<<"${plan}" >/dev/null
grep -F "OPS_API_ADMISSION_CONTROL_ENABLED=true" <<<"${plan}" >/dev/null
grep -F "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX<=3" <<<"${plan}" >/dev/null
grep -F "DB_POOL_MAX_SIZE<=4" <<<"${plan}" >/dev/null
grep -F "NOTIFICATION_SSE_MAX_TOTAL_SESSIONS<=64" <<<"${plan}" >/dev/null

echo "[production-high-traffic-config] guard: missing env fails"
if "${script}" >/dev/null 2>&1; then
  echo "missing env unexpectedly succeeded" >&2
  exit 1
fi

valid_gate_env=(
  SECURITY_LOGIN_THROTTLING_STORE=memory
  SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS=false
  TRANSACTION_READ_REPLICA_ENABLED=false
  OUTBOX_KAFKA_ENABLED=false
  NOTIFICATION_INBOX_CONSUMER_ENABLED=false
  KAFKA_TOPIC_PROVISIONING_ENABLED=false
  KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED=false
  OPS_API_ADMISSION_CONTROL_ENABLED=true
  OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=3
  OPS_API_ADMISSION_CONTROL_ACCOUNT_READ_MAX=4
  OPS_API_ADMISSION_CONTROL_TRANSFER_WRITE_MAX=2
  OPS_API_ADMISSION_CONTROL_NOTIFICATION_STREAM_MAX=4
  OPS_API_ADMISSION_CONTROL_NOTIFICATION_READ_MAX=4
  OPS_API_ADMISSION_CONTROL_INTERNAL_OPS_MAX=2
  OPS_T3MICRO_SATURATION_GUARD_ENABLED=true
  OPS_T3MICRO_SATURATION_GUARD_POOL_ACTIVE_THRESHOLD_PERCENT=90
  OPS_T3MICRO_SATURATION_GUARD_THREADS_BUSY_THRESHOLD_PERCENT=90
  OPS_T3MICRO_SATURATION_GUARD_QUERY_TIMEOUT_THRESHOLD=1
  DB_POOL_MAX_SIZE=4
  SERVER_THREADS_MAX=16
  NOTIFICATION_SSE_MAX_TOTAL_SESSIONS=64
  OUTBOX_POLLER_BATCH_SIZE=20
  NOTIFICATION_CHANNEL_PROVIDER_WORKER_BATCH_SIZE=10
)

echo "[production-high-traffic-config] valid defensive baseline passes"
env "${valid_gate_env[@]}" "${script}" >/dev/null

echo "[production-high-traffic-config] guard: transaction read admission above defensive cap fails"
if env "${valid_gate_env[@]}" OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=4 "${script}" >/dev/null 2>&1; then
  echo "transaction read admission above defensive cap unexpectedly succeeded" >&2
  exit 1
fi

echo "[production-high-traffic-config] guard: DB pool above defensive cap fails"
if env "${valid_gate_env[@]}" DB_POOL_MAX_SIZE=5 "${script}" >/dev/null 2>&1; then
  echo "DB pool above defensive cap unexpectedly succeeded" >&2
  exit 1
fi

echo "[production-high-traffic-config] guard: SSE cap above defensive cap fails"
if env "${valid_gate_env[@]}" NOTIFICATION_SSE_MAX_TOTAL_SESSIONS=65 "${script}" >/dev/null 2>&1; then
  echo "SSE cap above defensive cap unexpectedly succeeded" >&2
  exit 1
fi

echo "[production-high-traffic-config] guard: worker batch above defensive cap fails"
if env "${valid_gate_env[@]}" OUTBOX_POLLER_BATCH_SIZE=21 "${script}" >/dev/null 2>&1; then
  echo "worker batch above defensive cap unexpectedly succeeded" >&2
  exit 1
fi

echo "[production-high-traffic-config] optional: Redis throttling passes when explicitly configured"
env \
  "${valid_gate_env[@]}" \
  SECURITY_LOGIN_THROTTLING_STORE=redis \
  SECURITY_LOGIN_THROTTLING_REQUIRE_REDIS=true \
  REDIS_HOST=redis.production.internal \
  REDIS_PORT=6379 \
  "${script}" >/dev/null

echo "[production-high-traffic-config] guard: read replica enabled without env fails"
if env "${valid_gate_env[@]}" TRANSACTION_READ_REPLICA_ENABLED=true "${script}" >/dev/null 2>&1; then
  echo "read replica without env unexpectedly succeeded" >&2
  exit 1
fi

echo "[production-high-traffic-config] guard: Kafka enabled without env fails"
if env "${valid_gate_env[@]}" OUTBOX_KAFKA_ENABLED=true "${script}" >/dev/null 2>&1; then
  echo "Kafka without env unexpectedly succeeded" >&2
  exit 1
fi

kafka_gate_env=(
  "${valid_gate_env[@]}"
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
  NOTIFICATION_INBOX_CONSUMER_CONCURRENCY=1
  NOTIFICATION_INBOX_CONSUMER_TRANSFER_BOOKED_TOPIC=bank.transfer.booked.v1
  NOTIFICATION_INBOX_CONSUMER_TRANSFER_REVERSED_TOPIC=bank.transfer.reversed.v1
  NOTIFICATION_INBOX_CONSUMER_DLQ_TOPIC=bank.notification.inbox.dlq.v1
  KAFKA_TOPIC_PROVISIONING_ENABLED=true
  KAFKA_TOPIC_STARTUP_VALIDATION_ENABLED=true
  KAFKA_TOPIC_PROVISIONING_PARTITIONS=3
  KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR=3
  KAFKA_TOPIC_PROVISIONING_MIN_IN_SYNC_REPLICAS=2
)

echo "[production-high-traffic-config] optional: Kafka baseline passes when explicitly configured"
env "${kafka_gate_env[@]}" "${script}" >/dev/null

echo "[production-high-traffic-config] guard: unsafe Kafka replication fails when Kafka is enabled"
if env "${kafka_gate_env[@]}" KAFKA_TOPIC_PROVISIONING_REPLICATION_FACTOR=1 "${script}" >/dev/null 2>&1; then
  echo "unsafe Kafka replication unexpectedly succeeded" >&2
  exit 1
fi

echo "[production-high-traffic-config] guard: consumer concurrency above DB pool fails when Kafka is enabled"
if env "${kafka_gate_env[@]}" KAFKA_TOPIC_PROVISIONING_PARTITIONS=8 NOTIFICATION_INBOX_CONSUMER_CONCURRENCY=5 "${script}" >/dev/null 2>&1; then
  echo "consumer concurrency above DB pool unexpectedly succeeded" >&2
  exit 1
fi
