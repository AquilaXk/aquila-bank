#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-kafka-consumer-partition-concurrency.sh [--print-plan]

Examples:
  tools/test/run-kafka-consumer-partition-concurrency.sh
  tools/test/run-kafka-consumer-partition-concurrency.sh --print-plan
USAGE
}

print_plan=false
if [[ "${1:-}" == "--print-plan" ]]; then
  print_plan=true
  shift
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -ne 0 ]]; then
  usage
  exit 1
fi

test_selectors=(
  "*NotificationInboxConsumerConfigurationTest"
  "*KafkaConsumerPartitionConcurrencyIntegrationTest"
)

echo "[kafka-consumer-partition-concurrency] fixture: 4 partitions, 48 records, fixed 30ms listener work"
echo "[kafka-consumer-partition-concurrency] comparison: concurrency=1 vs concurrency=4 on the same record shape"
echo "[kafka-consumer-partition-concurrency] target: final consumer lag=0 and concurrency=4 throughput > 1.5x concurrency=1"
echo "[kafka-consumer-partition-concurrency] t3.micro default: NOTIFICATION_INBOX_CONSUMER_CONCURRENCY=1 unless explicitly raised with topic partitions"
echo "[kafka-consumer-partition-concurrency] selectors:"
printf '  --tests %s\n' "${test_selectors[@]}"

if [[ "${print_plan}" == "true" ]]; then
  exit 0
fi

gradle_args=(./back/gradlew -p back test)
for selector in "${test_selectors[@]}"; do
  gradle_args+=(--tests "${selector}")
done

tools/test/with-resource-lock.sh back-kafka-consumer-partition-concurrency "${gradle_args[@]}"
