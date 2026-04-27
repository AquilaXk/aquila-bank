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
grep -F "observability resources: prometheus=0.25/256m grafana=0.20/256m alertmanager=0.10/128m postgres-exporter=0.10/128m" <<<"${plan}" >/dev/null
grep -F "k6 report name: transaction-100m-check" <<<"${plan}" >/dev/null
grep -F "run id=transaction-100m-check" <<<"${plan}" >/dev/null
grep -F "run context=build/reports/k6/transaction-100m-check-run-context.env" <<<"${plan}" >/dev/null
grep -F "hot p99 threshold ms=750" <<<"${plan}" >/dev/null
grep -F "cold p99 threshold ms=1500" <<<"${plan}" >/dev/null
grep -F "hot p99.9 threshold ms=1200" <<<"${plan}" >/dev/null
grep -F "cold p99.9 threshold ms=2500" <<<"${plan}" >/dev/null
grep -F "hot max threshold ms=3000" <<<"${plan}" >/dev/null
grep -F "cold max threshold ms=5000" <<<"${plan}" >/dev/null
grep -F "overload mode=false max retry-after sleep seconds=1" <<<"${plan}" >/dev/null
grep -F "overload 429 rate threshold=0.015" <<<"${plan}" >/dev/null
grep -F "burst 429 rate threshold=0.1" <<<"${plan}" >/dev/null
grep -F "overload 503 rate threshold=0" <<<"${plan}" >/dev/null
grep -F "warmup duration=10s" <<<"${plan}" >/dev/null
grep -F "run purpose=smoke" <<<"${plan}" >/dev/null
grep -F "archive output dir=docs/performance-results/k6-smoke" <<<"${plan}" >/dev/null
grep -F "summary gate=true" <<<"${plan}" >/dev/null
grep -F "backend readiness gate=true path=/actuator/health/readiness timeout=120" <<<"${plan}" >/dev/null
grep -F "postgres health gate=true required_status=healthy" <<<"${plan}" >/dev/null
grep -F "postgres recovery gate=true stable_seconds=10" <<<"${plan}" >/dev/null
grep -F "postgres recovery noise window seconds=30" <<<"${plan}" >/dev/null
grep -F "postgres exporter stable gate=true timeout=60" <<<"${plan}" >/dev/null
grep -F "generator mode=local" <<<"${plan}" >/dev/null
grep -F "generator runner=docker compose service k6-transaction-read-100m" <<<"${plan}" >/dev/null
grep -F "observability mode=prometheus" <<<"${plan}" >/dev/null
grep -F "preflight=true" <<<"${plan}" >/dev/null
grep -F "scenario mode=constant-vus" <<<"${plan}" >/dev/null

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
grep -F "remote preflight=true timeout=30 readiness_path=/actuator/health/readiness" <<<"${remote_plan}" >/dev/null
grep -F "remote preflight image=curlimages/curl:8.11.1" <<<"${remote_plan}" >/dev/null

remote_prometheus_plan="$(
  K6_REPORT_NAME=transaction-100m-remote-prometheus-check \
  K6_OBSERVABILITY_MODE=prometheus \
  K6_GENERATOR_MODE=docker-context \
  K6_DOCKER_CONTEXT=transaction-k6-remote \
  K6_REMOTE_BASE_URL=http://192.0.2.10:8080 \
  K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.10:9090/api/v1/write \
  K6_REMOTE_WORKDIR=/srv/aquila-bank \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
)"
grep -F "k6 report name: transaction-100m-remote-prometheus-check" <<<"${remote_prometheus_plan}" >/dev/null
grep -F "remote prometheus rw=http://192.0.2.10:9090/api/v1/write" <<<"${remote_prometheus_plan}" >/dev/null
grep -F "remote prometheus preflight=enabled" <<<"${remote_prometheus_plan}" >/dev/null

capacity_archive_plan="$(
  K6_REPORT_NAME=transaction-100m-capacity-archive-check \
  K6_RUN_PURPOSE=capacity \
  K6_OBSERVABILITY_MODE=summary-only \
  K6_GENERATOR_MODE=docker-context \
  K6_DOCKER_CONTEXT=transaction-k6-remote \
  K6_REMOTE_BASE_URL=http://192.0.2.10:8080 \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
)"
grep -F "run purpose=capacity" <<<"${capacity_archive_plan}" >/dev/null
grep -F "archive output dir=docs/performance-results/k6-capacity" <<<"${capacity_archive_plan}" >/dev/null

overload_plan="$(
  K6_REPORT_NAME=transaction-100m-overload-check \
  K6_OVERLOAD_MODE=true \
  K6_MAX_RETRY_AFTER_SLEEP_SECONDS=2 \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
)"
grep -F "k6 report name: transaction-100m-overload-check" <<<"${overload_plan}" >/dev/null
grep -F "overload mode=true max retry-after sleep seconds=2" <<<"${overload_plan}" >/dev/null
grep -F "overload 429 rate threshold=0.015" <<<"${overload_plan}" >/dev/null
grep -F "burst 429 rate threshold=0.1" <<<"${overload_plan}" >/dev/null
grep -F "overload 503 rate threshold=0" <<<"${overload_plan}" >/dev/null

burst_default_plan="$(
  K6_REPORT_NAME=transaction-100m-burst-default-check \
  K6_SCENARIO_MODE=burst \
  K6_BURST_RATE=16 \
  K6_BURST_DURATION=20s \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
)"
grep -F "scenario mode=burst" <<<"${burst_default_plan}" >/dev/null
grep -F "burst rate=16 duration=20s preAllocatedVUs=16 maxVUs=32" <<<"${burst_default_plan}" >/dev/null

burst_plan="$(
  K6_REPORT_NAME=transaction-100m-burst-check \
  K6_SCENARIO_MODE=burst \
  K6_BURST_RATE=16 \
  K6_BURST_DURATION=20s \
  K6_PRE_ALLOCATED_VUS=8 \
  K6_MAX_VUS=32 \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
)"
grep -F "scenario mode=burst" <<<"${burst_plan}" >/dev/null
grep -F "burst rate=16 duration=20s preAllocatedVUs=8 maxVUs=32" <<<"${burst_plan}" >/dev/null

burst_offhost_plan="$(
  K6_REPORT_NAME=transaction-100m-burst-offhost-check \
  K6_SCENARIO_MODE=burst \
  K6_BURST_RATE=256 \
  K6_BURST_DURATION=20s \
  K6_GENERATOR_MODE=docker-context \
  K6_DOCKER_CONTEXT=transaction-k6-remote \
  K6_REMOTE_BASE_URL=http://192.0.2.10:8080 \
  K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.10:9090/api/v1/write \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
)"
grep -F "k6 report name: transaction-100m-burst-offhost-check" <<<"${burst_offhost_plan}" >/dev/null
grep -F "generator mode=docker-context" <<<"${burst_offhost_plan}" >/dev/null
grep -F "burst rate=256 duration=20s preAllocatedVUs=512 maxVUs=1024" <<<"${burst_offhost_plan}" >/dev/null

echo "[k6-transaction-100m] k6 script contract"
grep -F "experimental-prometheus-rw" compose.loadtest.yml >/dev/null
grep -F "K6_PROMETHEUS_RW_SERVER_URL" compose.loadtest.yml >/dev/null
grep -F 'K6_PROMETHEUS_RW_TREND_STATS: "p(50),p(90),p(95),p(99),p(99.9),min,max,avg"' compose.loadtest.yml >/dev/null
grep -F -- "--no-collector.stat_bgwriter" compose.loadtest.yml >/dev/null
grep -F -- "--config.file=/etc/postgres_exporter/postgres_exporter.yml" compose.loadtest.yml >/dev/null
grep -F "./ops/prometheus/postgres-exporter/postgres_exporter.yml:/etc/postgres_exporter/postgres_exporter.yml:ro" compose.loadtest.yml >/dev/null
grep -F "auth_modules:" ops/prometheus/postgres-exporter/postgres_exporter.yml >/dev/null
grep -F "max_wal_size" compose.loadtest.yml >/dev/null
grep -F "checkpoint_timeout" compose.loadtest.yml >/dev/null
grep -F "assert_k6_preflight" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "assert_postgres_recovery_preflight" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "wait_for_postgres_exporter_stability" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "pg_is_in_recovery()" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "postgres health status must be healthy" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "pg_up" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "idx_transaction_read_model_account_cursor" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "OOMKilled" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "stop_backend_before_bootjar" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "docker compose \"\${compose_files[@]}\" --profile loadtest stop aquila-bank-backend" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F -- "--force-recreate aquila-bank-backend" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "aquila_transaction_hot_first_ms" ops/k6/transaction-read-100m.js >/dev/null
grep -F "aquila_transaction_cold_cursor_ms" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_HOT_P99_THRESHOLD_MS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_COLD_P99_THRESHOLD_MS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_HOT_P999_THRESHOLD_MS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_COLD_P999_THRESHOLD_MS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_HOT_MAX_THRESHOLD_MS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_COLD_MAX_THRESHOLD_MS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "p(99)<" ops/k6/transaction-read-100m.js >/dev/null
grep -F "p(99.9)<" ops/k6/transaction-read-100m.js >/dev/null
grep -F "max<" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_OVERLOAD_MODE" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_RUN_ID" ops/k6/transaction-read-100m.js >/dev/null
grep -F "run_id: runId" ops/k6/transaction-read-100m.js >/dev/null
grep -F "AQUILA_K6_SCENARIO_MODE" ops/k6/transaction-read-100m.js >/dev/null
grep -F "AQUILA_K6_VUS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "AQUILA_K6_DURATION" ops/k6/transaction-read-100m.js >/dev/null
grep -F "AQUILA_K6_SCENARIO_MODE" compose.loadtest.yml >/dev/null
grep -F "AQUILA_K6_VUS" compose.loadtest.yml >/dev/null
grep -F "AQUILA_K6_DURATION" compose.loadtest.yml >/dev/null
grep -F -- "-e AQUILA_K6_SCENARIO_MODE=\"\${K6_SCENARIO_MODE}\"" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F -- "-e AQUILA_K6_VUS=\"\${K6_VUS}\"" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F -- "-e AQUILA_K6_DURATION=\"\${K6_DURATION:-1m}\"" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
if grep -F "const vus = Number(__ENV.K6_VUS" ops/k6/transaction-read-100m.js >/dev/null; then
  echo "transaction-read-100m.js still reads k6 runtime-reserved K6_VUS" >&2
  exit 1
fi
if grep -F "const duration = __ENV.K6_DURATION" ops/k6/transaction-read-100m.js >/dev/null; then
  echo "transaction-read-100m.js still reads k6 runtime-reserved K6_DURATION" >&2
  exit 1
fi
if grep -F -- "-e K6_VUS=\"\${K6_VUS}\"" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null; then
  echo "runner still passes k6 runtime-reserved K6_VUS into the k6 container" >&2
  exit 1
fi
if grep -F -- "-e K6_DURATION=\"\${K6_DURATION:-1m}\"" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null; then
  echo "runner still passes k6 runtime-reserved K6_DURATION into the k6 container" >&2
  exit 1
fi
grep -F "constant-arrival-rate" ops/k6/transaction-read-100m.js >/dev/null
grep -F "burst_admission" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_OVERLOAD_429_RATE_THRESHOLD" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_BURST_429_RATE_THRESHOLD" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_OVERLOAD_503_RATE_THRESHOLD" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_MAX_RETRY_AFTER_SLEEP_SECONDS" ops/k6/transaction-read-100m.js >/dev/null
grep -F "aquila_transaction_429_rate" ops/k6/transaction-read-100m.js >/dev/null
grep -F "aquila_transaction_503_rate" ops/k6/transaction-read-100m.js >/dev/null
grep -F "aquila_transaction_503_count" ops/k6/transaction-read-100m.js >/dev/null
grep -F "AQUILA_K6_WARMUP_DURATION" ops/k6/transaction-read-100m.js >/dev/null
grep -F "transaction_read_100m_warmup" ops/k6/transaction-read-100m.js >/dev/null
grep -F "exec.scenario.name" ops/k6/transaction-read-100m.js >/dev/null
grep -F 'rate<${effectiveOverload429RateThreshold}' ops/k6/transaction-read-100m.js >/dev/null
grep -F 'rate<=${overload503RateThreshold}' ops/k6/transaction-read-100m.js >/dev/null
grep -F "count<1" ops/k6/transaction-read-100m.js >/dev/null
grep -F "summaryTrendStats" ops/k6/transaction-read-100m.js >/dev/null
grep -F '"p(99)"' ops/k6/transaction-read-100m.js >/dev/null
grep -F '"p(99.9)"' ops/k6/transaction-read-100m.js >/dev/null
grep -F "Retry-After" ops/k6/transaction-read-100m.js >/dev/null
grep -F "K6_OVERLOAD_MODE" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_SCENARIO_MODE" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "require_scenario_mode" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_HOT_P99_THRESHOLD_MS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_COLD_P99_THRESHOLD_MS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_HOT_P999_THRESHOLD_MS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_COLD_P999_THRESHOLD_MS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_HOT_MAX_THRESHOLD_MS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_COLD_MAX_THRESHOLD_MS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_OVERLOAD_429_RATE_THRESHOLD" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_BURST_429_RATE_THRESHOLD" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_OVERLOAD_503_RATE_THRESHOLD" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_GENERATOR_MODE" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_OBSERVABILITY_MODE" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_BACKEND_READINESS_GATE" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_RUN_ID" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "write_run_context" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "RECOVERY_NOISE_WINDOW_SECONDS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "wait_for_backend_readiness" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "LOADTEST_PROMETHEUS_CPUS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "LOADTEST_GRAFANA_MEMORY" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "LOADTEST_ALERTMANAGER_CPUS" compose.loadtest.yml >/dev/null
grep -F "LOADTEST_POSTGRES_EXPORTER_MEMORY" compose.loadtest.yml >/dev/null
grep -F "require_observability_mode" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "experimental-prometheus-rw" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "without prometheus remote-write" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_DOCKER_CONTEXT" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_REMOTE_BASE_URL" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_REMOTE_PROMETHEUS_RW_SERVER_URL" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_REMOTE_PREFLIGHT" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_REMOTE_READINESS_PATH" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "assert_remote_k6_preflight" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "docker --context \"\${K6_DOCKER_CONTEXT}\" info" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "remote prometheus remote-write preflight" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_REMOTE_WORKDIR" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "K6_RUN_PURPOSE" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "PERFORMANCE_RESULT_PURPOSE=\"\${K6_RUN_PURPOSE}\"" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "PERFORMANCE_RESULT_OUTPUT_DIR=\"\${archive_output_dir}\"" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "resultPurpose" tools/test/archive-k6-transaction-100m-result.sh >/dev/null
grep -F "reportClass" tools/test/archive-k6-transaction-100m-result.sh >/dev/null
grep -F "assert_k6_summary_gate" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "interrupted_iterations" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "dropped_iterations" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "Insufficient VUs" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "handleSummary" ops/k6/transaction-read-100m.js >/dev/null
grep -F "observabilityNote" ops/k6/transaction-read-100m.js >/dev/null
grep -F "Prometheus remote write와 summary 파일을 함께 남깁니다" ops/k6/transaction-read-100m.js >/dev/null
grep -F '/reports/${reportName}-summary.md' ops/k6/transaction-read-100m.js >/dev/null

echo "[k6-transaction-100m] summary hard gate"
temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
summary_ok="${temp_dir}/summary-ok.json"
summary_zero="${temp_dir}/summary-zero.json"
summary_interrupted="${temp_dir}/summary-interrupted.json"
summary_dropped="${temp_dir}/summary-dropped.json"
empty_log="${temp_dir}/empty.log"
insufficient_log="${temp_dir}/insufficient.log"
cat >"${summary_ok}" <<'JSON'
{"metrics":{"iterations":{"values":{"count":3}},"checks":{"values":{"rate":1,"passes":12,"fails":0}},"interrupted_iterations":{"values":{"count":0}},"dropped_iterations":{"values":{"count":0}}}}
JSON
cat >"${summary_zero}" <<'JSON'
{"metrics":{"iterations":{"values":{"count":0}},"checks":{"values":{"count":0}},"interrupted_iterations":{"values":{"count":0}},"dropped_iterations":{"values":{"count":0}}}}
JSON
cat >"${summary_interrupted}" <<'JSON'
{"metrics":{"iterations":{"values":{"count":3}},"checks":{"values":{"count":12}},"interrupted_iterations":{"values":{"count":1}},"dropped_iterations":{"values":{"count":0}}}}
JSON
cat >"${summary_dropped}" <<'JSON'
{"metrics":{"iterations":{"values":{"count":3}},"checks":{"values":{"count":12}},"interrupted_iterations":{"values":{"count":0}},"dropped_iterations":{"values":{"count":1}}}}
JSON
: >"${empty_log}"
echo 'level=warning msg="Insufficient VUs, reached 8 active VUs and cannot initialize more"' >"${insufficient_log}"
tools/test/run-k6-transaction-100m-loadtest.sh --assert-summary "${summary_ok}" "${empty_log}" >/dev/null
if tools/test/run-k6-transaction-100m-loadtest.sh --assert-summary "${summary_zero}" "${empty_log}" >/dev/null 2>&1; then
  echo "zero iteration/check summary unexpectedly succeeded" >&2
  exit 1
fi
if tools/test/run-k6-transaction-100m-loadtest.sh --assert-summary "${summary_interrupted}" "${empty_log}" >/dev/null 2>&1; then
  echo "interrupted iteration summary unexpectedly succeeded" >&2
  exit 1
fi
if tools/test/run-k6-transaction-100m-loadtest.sh --assert-summary "${summary_dropped}" "${empty_log}" >/dev/null 2>&1; then
  echo "dropped iteration summary unexpectedly succeeded" >&2
  exit 1
fi
if tools/test/run-k6-transaction-100m-loadtest.sh --assert-summary "${summary_ok}" "${insufficient_log}" >/dev/null 2>&1; then
  echo "Insufficient VUs log unexpectedly succeeded" >&2
  exit 1
fi

echo "[k6-transaction-100m] invalid input fails"
if K6_OVERLOAD_429_RATE_THRESHOLD=1.5 tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_OVERLOAD_429_RATE_THRESHOLD=1.5 unexpectedly succeeded" >&2
  exit 1
fi
if K6_BURST_429_RATE_THRESHOLD=1.5 tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_BURST_429_RATE_THRESHOLD=1.5 unexpectedly succeeded" >&2
  exit 1
fi
if K6_OVERLOAD_503_RATE_THRESHOLD=1.5 tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_OVERLOAD_503_RATE_THRESHOLD=1.5 unexpectedly succeeded" >&2
  exit 1
fi
if K6_HOT_P99_THRESHOLD_MS=0 tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_HOT_P99_THRESHOLD_MS=0 unexpectedly succeeded" >&2
  exit 1
fi
if K6_HOT_P999_THRESHOLD_MS=0 tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_HOT_P999_THRESHOLD_MS=0 unexpectedly succeeded" >&2
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
if K6_SCENARIO_MODE=unknown tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_SCENARIO_MODE=unknown unexpectedly succeeded" >&2
  exit 1
fi
if K6_WARMUP_DURATION=bad tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_WARMUP_DURATION=bad unexpectedly succeeded" >&2
  exit 1
fi
if K6_POSTGRES_HEALTH_GATE=bad tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_POSTGRES_HEALTH_GATE=bad unexpectedly succeeded" >&2
  exit 1
fi
if K6_POSTGRES_RECOVERY_STABLE_SECONDS=bad tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_POSTGRES_RECOVERY_STABLE_SECONDS=bad unexpectedly succeeded" >&2
  exit 1
fi
if K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS=bad tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS=bad unexpectedly succeeded" >&2
  exit 1
fi
if K6_POSTGRES_EXPORTER_STABLE_GATE=bad tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_POSTGRES_EXPORTER_STABLE_GATE=bad unexpectedly succeeded" >&2
  exit 1
fi
if K6_POSTGRES_EXPORTER_STABLE_TIMEOUT_SECONDS=0 tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_POSTGRES_EXPORTER_STABLE_TIMEOUT_SECONDS=0 unexpectedly succeeded" >&2
  exit 1
fi
if K6_REMOTE_PREFLIGHT=bad tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_REMOTE_PREFLIGHT=bad unexpectedly succeeded" >&2
  exit 1
fi
if K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS=0 tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_REMOTE_PREFLIGHT_TIMEOUT_SECONDS=0 unexpectedly succeeded" >&2
  exit 1
fi
if K6_RUN_PURPOSE=unknown tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "K6_RUN_PURPOSE=unknown unexpectedly succeeded" >&2
  exit 1
fi
if K6_RUN_PURPOSE=capacity K6_GENERATOR_MODE=local tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "capacity purpose local generator unexpectedly succeeded" >&2
  exit 1
fi
if K6_OBSERVABILITY_MODE=prometheus K6_GENERATOR_MODE=docker-context tools/test/run-k6-transaction-100m-loadtest.sh --print-plan >/dev/null 2>&1; then
  echo "prometheus docker-context without remote prometheus unexpectedly succeeded" >&2
  exit 1
fi

echo "[k6-transaction-100m] archive script"
echo "# sample" >"${temp_dir}/transaction-100m-summary.md"
echo "{}" >"${temp_dir}/transaction-100m-summary.json"
output="$(
  PERFORMANCE_RESULT_OUTPUT_DIR="${temp_dir}/k6-smoke" \
  PERFORMANCE_RESULT_PURPOSE=smoke \
  PERFORMANCE_RESULT_NAME=transaction-100m-check-result \
    tools/test/archive-k6-transaction-100m-result.sh \
    "${temp_dir}/transaction-100m-summary.md" \
    "${temp_dir}/transaction-100m-summary.json"
)"
test "${output}" = "${temp_dir}/k6-smoke/transaction-100m-check-result.md"
grep -F "resultPurpose: smoke" "${output}" >/dev/null
grep -F "reportClass: transaction-100m-smoke" "${output}" >/dev/null
grep -F "sourceMarkdown: ${temp_dir}/transaction-100m-summary.md" "${output}" >/dev/null
rm -f "${output}"

capacity_output="$(
  PERFORMANCE_RESULT_OUTPUT_DIR="${temp_dir}/k6-capacity" \
  PERFORMANCE_RESULT_PURPOSE=capacity \
  PERFORMANCE_RESULT_NAME=transaction-100m-capacity-check-result \
    tools/test/archive-k6-transaction-100m-result.sh \
    "${temp_dir}/transaction-100m-summary.md" \
    "${temp_dir}/transaction-100m-summary.json"
)"
test "${capacity_output}" = "${temp_dir}/k6-capacity/transaction-100m-capacity-check-result.md"
grep -F "resultPurpose: capacity" "${capacity_output}" >/dev/null
grep -F "reportClass: transaction-100m-capacity" "${capacity_output}" >/dev/null
rm -f "${capacity_output}"

echo "[k6-transaction-100m] optional k6 inspect"
if docker image inspect grafana/k6:0.54.0 >/dev/null 2>&1; then
  docker run --rm -v "$(pwd)/ops/k6:/scripts:ro" grafana/k6:0.54.0 inspect /scripts/transaction-read-100m.js >/dev/null
else
  echo "[k6-transaction-100m] grafana/k6:0.54.0 image not found; skipping inspect"
fi
