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
grep -F "hot p99 threshold ms=750" <<<"${plan}" >/dev/null
grep -F "cold p99 threshold ms=1500" <<<"${plan}" >/dev/null
grep -F "hot max threshold ms=3000" <<<"${plan}" >/dev/null
grep -F "cold max threshold ms=5000" <<<"${plan}" >/dev/null
grep -F "overload mode=false max retry-after sleep seconds=1" <<<"${plan}" >/dev/null
grep -F "overload 429 rate threshold=0.05" <<<"${plan}" >/dev/null
grep -F "generator mode=local" <<<"${plan}" >/dev/null
grep -F "generator runner=docker compose service k6-transaction-read-100m" <<<"${plan}" >/dev/null
grep -F "observability mode=prometheus" <<<"${plan}" >/dev/null
grep -F "preflight=true" <<<"${plan}" >/dev/null

summary_only_plan="$(
  K6_REPORT_NAME=transaction-100m-summary-only-check \
  K6_OBSERVABILITY_MODE=summary-only \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
)"
grep -F "k6 report name: transaction-100m-summary-only-check" <<<"${summary_only_plan}" >/dev/null
grep -F "observability mode=summary-only" <<<"${summary_only_plan}" >/dev/null
grep -F "generator runner=docker compose service k6-transaction-read-100m without prometheus remote-write" <<<"${summary_only_plan}" >/dev/null

remote_plan="$(
  K6_REPORT_NAME=transaction-100m-remote-check \
  K6_OBSERVABILITY_MODE=summary-only \
  K6_GENERATOR_MODE=docker-context \
  K6_DOCKER_CONTEXT=transaction-k6-remote \
  K6_REMOTE_BASE_URL=http://192.0.2.10:8080 \
  K6_REMOTE_WORKDIR=/srv/aquila-bank \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
)"
grep -F "k6 report name: transaction-100m-remote-check" <<<"${remote_plan}" >/dev/null
grep -F "generator mode=docker-context" <<<"${remote_plan}" >/dev/null
grep -F "generator runner=docker --context transaction-k6-remote run grafana/k6:0.54.0" <<<"${remote_plan}" >/dev/null
grep -F "remote base url=http://192.0.2.10:8080" <<<"${remote_plan}" >/dev/null
grep -F "remote prometheus rw=disabled" <<<"${remote_plan}" >/dev/null
grep -F "remote workdir=/srv/aquila-bank" <<<"${remote_plan}" >/dev/null

overload_plan="$(
  K6_REPORT_NAME=transaction-100m-overload-check \
  K6_OVERLOAD_MODE=true \
  K6_MAX_RETRY_AFTER_SLEEP_SECONDS=2 \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
)"
grep -F "k6 report name: transaction-100m-overload-check" <<<"${overload_plan}" >/dev/null
grep -F "overload mode=true max retry-after sleep seconds=2" <<<"${overload_plan}" >/dev/null
grep -F "overload 429 rate threshold=0.05" <<<"${overload_plan}" >/dev/null

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
grep -F "K6_HOT_P99_THRESHOLD_MS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_COLD_P99_THRESHOLD_MS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_HOT_MAX_THRESHOLD_MS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_COLD_MAX_THRESHOLD_MS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "p(99)<" ops/k6/transaction-read-100m.js >/dev/null
grep -F "max<" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_OVERLOAD_MODE" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_OVERLOAD_429_RATE_THRESHOLD" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_MAX_RETRY_AFTER_SLEEP_SECONDS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "aquila_transaction_429_rate" ops/k6/transaction-read-100m.js >/dev/null
grep -F 'rate<${overload429RateThreshold}' ops/k6/transaction-read-100m.js >/dev/null
grep -F "Retry-After" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_OVERLOAD_MODE" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_HOT_P99_THRESHOLD_MS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_COLD_P99_THRESHOLD_MS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_HOT_MAX_THRESHOLD_MS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_COLD_MAX_THRESHOLD_MS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_OVERLOAD_429_RATE_THRESHOLD" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_GENERATOR_MODE" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_OBSERVABILITY_MODE" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "require_observability_mode" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "experimental-prometheus-rw" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "without prometheus remote-write" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_DOCKER_CONTEXT" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_REMOTE_BASE_URL" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_REMOTE_PROMETHEUS_RW_SERVER_URL" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_REMOTE_WORKDIR" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "handleSummary" ops/k6/transaction-read-100m.js >/dev/null
grep -F '/reports/${reportName}-summary.md' ops/k6/transaction-read-100m.js >/dev/null

echo "[k6-transaction-100m] invalid input fails"
if K6_OVERLOAD_429_RATE_THRESHOLD=1.5 tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_OVERLOAD_429_RATE_THRESHOLD=1.5 unexpectedly succeeded" >&2
  exit 1
fi
if K6_HOT_P99_THRESHOLD_MS=0 tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_HOT_P99_THRESHOLD_MS=0 unexpectedly succeeded" >&2
  exit 1
fi
if K6_GENERATOR_MODE=unknown tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_GENERATOR_MODE=unknown unexpectedly succeeded" >&2
  exit 1
fi
if K6_OBSERVABILITY_MODE=bad tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_OBSERVABILITY_MODE=bad unexpectedly succeeded" >&2
  exit 1
fi
if K6_OBSERVABILITY_MODE=prometheus K6_GENERATOR_MODE=docker-context tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "prometheus docker-context without remote prometheus unexpectedly succeeded" >&2
  exit 1
fi

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
