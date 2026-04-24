#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-t3micro-mixed-workload-soak.sh [--print-plan]

Environment:
  SOAK_REPEAT   repeat count for long-running rehearsal, default 1

Examples:
  tools/test/run-t3micro-mixed-workload-soak.sh
  SOAK_REPEAT=12 tools/test/run-t3micro-mixed-workload-soak.sh
  tools/test/run-t3micro-mixed-workload-soak.sh --print-plan
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

repeat="${SOAK_REPEAT:-1}"
if ! [[ "${repeat}" =~ ^[1-9][0-9]*$ ]]; then
  echo "SOAK_REPEAT must be a positive integer" >&2
  exit 1
fi

test_selectors=(
  "*TransactionQueryConcurrencySloIntegrationTest"
  "*TransferCommandApiIntegrationTest"
  "*NotificationSseBrokerTest"
  "*NotificationSseIntegrationTest"
)

# smoke는 이전 test result cache가 아니라 현재 budget에서의 실제 실행을 봅니다.
gradle_args=(./back/gradlew -p back cleanTest test)
for selector in "${test_selectors[@]}"; do
  gradle_args+=(--tests "${selector}")
done

echo_plan() {
  echo "[t3micro-mixed-soak] repeat=${repeat}"
  echo "[t3micro-mixed-soak] workload: transaction query concurrency + transfer write/outbox + notification fanout + SSE stream"
  echo "[t3micro-mixed-soak] t3.micro guard: DB_POOL_MAX_SIZE=4 SERVER_THREADS_MAX=16 SSE total sessions<=56"
  echo "[t3micro-mixed-soak] target: failures=0, transaction p95<=350ms max<=750ms, transfer side effects consistent, SSE duplicate/backpressure guards pass"
  echo "[t3micro-mixed-soak] selectors:"
  printf '  --tests %s\n' "${test_selectors[@]}"
}

echo_plan

if [[ "${print_plan}" == "true" ]]; then
  exit 0
fi

for ((iteration = 1; iteration <= repeat; iteration++)); do
  echo "[t3micro-mixed-soak] iteration ${iteration}/${repeat}"
  tools/test/with-resource-lock.sh back-gradle-t3micro-mixed-soak "${gradle_args[@]}"
done
