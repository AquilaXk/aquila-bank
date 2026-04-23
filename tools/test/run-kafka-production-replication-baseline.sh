#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-kafka-production-replication-baseline.sh [--print-plan]

Examples:
  tools/test/run-kafka-production-replication-baseline.sh
  tools/test/run-kafka-production-replication-baseline.sh --print-plan
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
  "*KafkaTopicAdministrationConfigurationTest"
  "*KafkaTopicRuntimeValidatorTest"
)

echo "[kafka-production-replication-baseline] local baseline: replicationFactor=1, minInSyncReplicas=1"
echo "[kafka-production-replication-baseline] production baseline: replicationFactor=3, minInSyncReplicas=2"
echo "[kafka-production-replication-baseline] producer contract: acks=all + idempotence=true"
echo "[kafka-production-replication-baseline] target: startup fail-fast when broker count or topic replication/min ISR drift from baseline"
echo "[kafka-production-replication-baseline] selectors:"
printf '  --tests %s\n' "${test_selectors[@]}"

if [[ "${print_plan}" == "true" ]]; then
  exit 0
fi

gradle_args=(./back/gradlew -p back test)
for selector in "${test_selectors[@]}"; do
  gradle_args+=(--tests "${selector}")
done

tools/test/with-resource-lock.sh back-kafka-production-replication-baseline "${gradle_args[@]}"
