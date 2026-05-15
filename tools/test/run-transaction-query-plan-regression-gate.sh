#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-query-plan-regression-gate.sh [--print-plan]
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

print_plan() {
  echo "[transaction-plan-gate] fixture=baseline hot account + long-history partition-fit + concurrency SLO"
  echo "[transaction-plan-gate] filter_matrix=first_page,cursor,deep_cursor,status_cursor,direction_first,amount_first,mixed_cursor,reference_exact"
  echo "[transaction-plan-gate] mixed_cursor=status+direction+amount+cursor"
  echo "[transaction-plan-gate] expectation=no Seq Scan or Sort on account-scoped keyset plans"
  echo "[transaction-plan-gate] index_expectation=idx_transaction_read_model_account_* cursor/reference paths"
  echo "[transaction-plan-gate] latency_target=baseline p95 thresholds and concurrency failures=0 p95<=350ms max<=750ms"
  echo "[transaction-plan-gate] timeout_guard=statement_timeout=3000ms query-timeout=3s request-timeout=5000ms"
  echo "[transaction-plan-gate] gradle_task=./back/gradlew -p back queryPlanTest"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

tools/test/with-resource-lock.sh back-gradle-transaction-plan ./back/gradlew -p back queryPlanTest
