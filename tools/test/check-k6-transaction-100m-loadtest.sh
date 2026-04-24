#!/usr/bin/env bash
set -euo pipefail

echo "[k6-transaction-100m] shell syntax"
bash -n tools/test/run-k6-transaction-100m-loadtest.sh
bash -n tools/test/archive-k6-transaction-100m-result.sh

echo "[k6-transaction-100m] compose config"
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml config >/dev/null

echo "[k6-transaction-100m] runner plan"
plan="$(
  K6_REPORT_NAME=transaction-100m-check \
  K6_VUS=4 \
  K6_LIMIT=50 \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
)"
grep -F "backend: aquila-bank-backend:8080 with t3.micro budget" <<<"${plan}" >/dev/null
grep -F "observability: prometheus:9090 grafana:3000 alertmanager:9093 postgres-exporter:9187" <<<"${plan}" >/dev/null
grep -F "k6 report name: transaction-100m-check" <<<"${plan}" >/dev/null
grep -F "preflight=true" <<<"${plan}" >/dev/null

echo "[k6-transaction-100m] k6 script contract"
grep -F "experimental-prometheus-rw" compose.loadtest.yml >/dev/null
grep -F "K6_PROMETHEUS_RW_SERVER_URL" compose.loadtest.yml >/dev/null
grep -F -- "--no-collector.stat_bgwriter" compose.loadtest.yml >/dev/null
grep -F "max_wal_size" compose.loadtest.yml >/dev/null
grep -F "checkpoint_timeout" compose.loadtest.yml >/dev/null
grep -F "assert_k6_preflight" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "idx_transaction_read_model_account_cursor" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "OOMKilled" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "aquila_transaction_hot_first_ms" ops/k6/transaction-read-100m.js >/dev/null
grep -F "aquila_transaction_cold_cursor_ms" ops/k6/transaction-read-100m.js >/dev/null
grep -F "handleSummary" ops/k6/transaction-read-100m.js >/dev/null
grep -F '/reports/${reportName}-summary.md' ops/k6/transaction-read-100m.js >/dev/null

echo "[k6-transaction-100m] archive script"
temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
echo "# sample" >"${temp_dir}/transaction-100m-summary.md"
echo "{}" >"${temp_dir}/transaction-100m-summary.json"
output="$(
  PERFORMANCE_RESULT_NAME=transaction-100m-check-result \
    tools/test/archive-k6-transaction-100m-result.sh \
    "${temp_dir}/transaction-100m-summary.md" \
    "${temp_dir}/transaction-100m-summary.json"
)"
test "${output}" = "docs/performance-results/transaction-100m-check-result.md"
grep -F "sourceMarkdown: ${temp_dir}/transaction-100m-summary.md" "${output}" >/dev/null
rm -f "${output}"

echo "[k6-transaction-100m] optional k6 inspect"
if docker image inspect grafana/k6:0.54.0 >/dev/null 2>&1; then
  docker run --rm -v "$(pwd)/ops/k6:/scripts:ro" grafana/k6:0.54.0 inspect /scripts/transaction-read-100m.js >/dev/null
else
  echo "[k6-transaction-100m] grafana/k6:0.54.0 image not found; skipping inspect"
fi
