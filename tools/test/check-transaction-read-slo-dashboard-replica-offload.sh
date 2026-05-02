#!/usr/bin/env bash
set -euo pipefail

dashboard="ops/prometheus/grafana/aquila-bank-overview.json"
validator="tools/ops/validate-prometheus-assets.sh"
runner="tools/test/run-transaction-read-replica-100m-offload.sh"
compose_file="compose.loadtest.yml"

echo "[transaction-read-slo-replica] prometheus asset validator"
"${validator}"

echo "[transaction-read-slo-replica] dashboard contract"
jq -e '
  any(.panels[]?; .title == "Transaction Read Accepted P95 SLO")
  and any(.panels[]?; .title == "Transaction Read Rejected Ratio")
  and any(.panels[]?; .title == "Transaction Read Inflight and 429")
  and any(.panels[]?.targets[]?.expr?; contains("aquila_transaction_query_latency_seconds_bucket{outcome=\"success\""))
  and any(.panels[]?.targets[]?.expr?; contains("aquila_api_admission_requests_total{group=~\"transaction-read-(hot|archive)\",outcome=\"rejected\""))
  and any(.panels[]?.targets[]?.expr?; contains("aquila_api_admission_inflight{group=~\"transaction-read-(hot|archive)\""))
  and any(.panels[]?.targets[]?.expr?; contains("aquila_transaction_429_rate"))
' "${dashboard}" >/dev/null

echo "[transaction-read-slo-replica] runner syntax"
bash -n "${runner}"

echo "[transaction-read-slo-replica] runner plan"
plan="$(
  REPLICA_OFFLOAD_NAME=transaction-replica-check \
  K6_REPORT_NAME=transaction-replica-check-k6 \
    "${runner}" --print-plan --no-deps
)"
grep -F "name=transaction-replica-check" <<<"${plan}" >/dev/null
grep -F "k6 report=transaction-replica-check-k6" <<<"${plan}" >/dev/null
grep -F "t3 saturation guard=false" <<<"${plan}" >/dev/null
grep -F "route_metric=aquila_transaction_read_replica_route_decisions_total{query_shape=\"archive\",route=\"replica\",reason=\"replica_healthy\"}" <<<"${plan}" >/dev/null
grep -F "k6 runner=tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/k6/transaction-replica-check/replica-offload-summary.tsv" <<<"${plan}" >/dev/null

echo "[transaction-read-slo-replica] compose replica env"
grep -F "TRANSACTION_READ_REPLICA_ENABLED" "${compose_file}" >/dev/null
grep -F "TRANSACTION_READ_REPLICA_URL" "${compose_file}" >/dev/null
grep -F "TRANSACTION_READ_REPLICA_POOL_MAX_SIZE" "${compose_file}" >/dev/null

echo "[transaction-read-slo-replica] runner contract"
grep -F "TRANSACTION_READ_REPLICA_ENABLED=true" "${runner}" >/dev/null
grep -F "TRANSACTION_READ_REPLICA_URL" "${runner}" >/dev/null
grep -F "aquila_transaction_read_replica_lag_ms" "${runner}" >/dev/null
grep -F "wait_for_route_delta" "${runner}" >/dev/null
grep -F "REPLICA_OFFLOAD_ALLOW_NO_ROUTE_DELTA" "${runner}" >/dev/null
grep -F "OPS_T3MICRO_SATURATION_GUARD_ENABLED" "${runner}" >/dev/null

echo "[transaction-read-slo-replica] invalid input fails"
if REPLICA_OFFLOAD_ROUTE_WAIT_SECONDS=0 "${runner}" --print-plan >/dev/null 2>&1; then
  echo "invalid route wait unexpectedly succeeded" >&2
  exit 1
fi
if REPLICA_OFFLOAD_ALLOW_NO_ROUTE_DELTA=maybe "${runner}" --print-plan >/dev/null 2>&1; then
  echo "invalid allow flag unexpectedly succeeded" >&2
  exit 1
fi
if REPLICA_OFFLOAD_T3_GUARD_ENABLED=maybe "${runner}" --print-plan >/dev/null 2>&1; then
  echo "invalid t3 guard flag unexpectedly succeeded" >&2
  exit 1
fi
if "${runner}" --no-up --no-deps >/dev/null 2>&1; then
  echo "missing replica env unexpectedly succeeded" >&2
  exit 1
fi
