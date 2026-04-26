#!/usr/bin/env bash
set -euo pipefail

weighted_runner="tools/test/run-k6-transaction-100m-weighted-loadtest.sh"
compression_runner="tools/test/run-transaction-read-compression-benchmark.sh"
mapper_runner="tools/test/run-transaction-read-jdbc-mapper-allocation.sh"
fixture_runner="tools/test/run-transaction-100m-fixture-restore.sh"
weighted_script="ops/k6/transaction-read-100m-weighted.js"

echo "[transaction-100m-workload-profile] shell syntax"
bash -n "${weighted_runner}"
bash -n "${compression_runner}"
bash -n "${mapper_runner}"
bash -n "${fixture_runner}"

echo "[transaction-100m-workload-profile] weighted plan"
weighted_plan="$(
  K6_REPORT_NAME=transaction-weighted-check \
  K6_WEIGHT_HOT_FIRST=45 \
  K6_WEIGHT_HOT_CURSOR=25 \
  K6_WEIGHT_COLD_FIRST=15 \
  K6_WEIGHT_COLD_CURSOR=10 \
  K6_WEIGHT_DETAIL=5 \
    "${weighted_runner}" --print-plan --no-deps
)"
grep -F "weighted report name: transaction-weighted-check" <<<"${weighted_plan}" >/dev/null
grep -F "weights hot_first=45 hot_cursor=25 cold_first=15 cold_cursor=10 detail=5" <<<"${weighted_plan}" >/dev/null
grep -F "script=/scripts/transaction-read-100m-weighted.js" <<<"${weighted_plan}" >/dev/null

echo "[transaction-100m-workload-profile] compression plan"
compression_plan="$(
  COMPRESSION_BENCHMARK_NAME=transaction-compression-check \
  COMPRESSION_PROFILES=off:false:0,on-2kb:true:2048 \
    "${compression_runner}" --print-plan
)"
grep -F "benchmark=transaction-compression-check" <<<"${compression_plan}" >/dev/null
grep -F "profiles=off,on-2kb" <<<"${compression_plan}" >/dev/null
grep -F "admission max=8" <<<"${compression_plan}" >/dev/null
grep -F "backend_health_url=http://localhost:8080/actuator/health" <<<"${compression_plan}" >/dev/null
grep -F "summary=build/reports/k6/transaction-compression-check/compression-summary.tsv" <<<"${compression_plan}" >/dev/null

echo "[transaction-100m-workload-profile] mapper plan"
mapper_plan="$(
  MAPPER_PROFILE_NAME=transaction-mapper-check \
    "${mapper_runner}" --print-plan
)"
grep -F "profile=transaction-mapper-check" <<<"${mapper_plan}" >/dev/null
grep -F "target=JdbcTransactionReadRepository,JDBC RowMapper,OffsetDateTime,enum" <<<"${mapper_plan}" >/dev/null
grep -F "run-transaction-read-hotpath-profile.sh" <<<"${mapper_plan}" >/dev/null

echo "[transaction-100m-workload-profile] fixture plan"
fixture_plan="$(
  FIXTURE_NAME=transaction-fixture-check \
    "${fixture_runner}" --print-plan
)"
grep -F "fixture=transaction-fixture-check" <<<"${fixture_plan}" >/dev/null
grep -F "dump=build/fixtures/transaction-fixture-check.dump" <<<"${fixture_plan}" >/dev/null
grep -F "modes=verify,dump,restore" <<<"${fixture_plan}" >/dev/null

echo "[transaction-100m-workload-profile] script contract"
grep -F "K6_WEIGHT_DETAIL" "${weighted_script}" >/dev/null
grep -F "transaction_detail_ms" "${weighted_script}" >/dev/null
grep -F "transactionReference" "${weighted_script}" >/dev/null
grep -F "randomWeightedShape" "${weighted_script}" >/dev/null
grep -F "SERVER_COMPRESSION_ENABLED" "${compression_runner}" >/dev/null
grep -F "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX" "${compression_runner}" >/dev/null
grep -F "wait_for_backend_readiness" "${compression_runner}" >/dev/null
grep -F "server.compression.min-response-size" "${compression_runner}" >/dev/null
grep -F "allocation-by-site" "${mapper_runner}" >/dev/null
grep -F "pg_dump" "${fixture_runner}" >/dev/null
grep -F "pg_restore" "${fixture_runner}" >/dev/null

echo "[transaction-100m-workload-profile] invalid input fails"
if COMPRESSION_PROFILES=bad "${compression_runner}" --print-plan >/dev/null 2>&1; then
  echo "bad COMPRESSION_PROFILES unexpectedly succeeded" >&2
  exit 1
fi
if FIXTURE_MODE=restore FIXTURE_RESTORE_TRUNCATE=false "${fixture_runner}" >/dev/null 2>&1; then
  echo "restore without explicit truncate unexpectedly succeeded" >&2
  exit 1
fi
