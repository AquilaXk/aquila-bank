#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-fixture-dataset-probe.sh"

echo "[transaction-100m-dataset-probe] shell syntax"
bash -n "${script}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

dump_path="${temp_dir}/transaction-100m-check.dump"
manifest_path="${dump_path}.manifest"
env_path="${temp_dir}/transaction-100m-check.dataset.env"
printf "sample-dump\n" >"${dump_path}"
{
  echo "fixture_name=transaction-100m-check"
  echo "dump_path=${dump_path}"
  echo "sha256=dummy"
  echo "flyway_version=999"
  echo "total_rows=1000"
  echo "hot_rows=600"
  echo "archive_rows=400"
  echo "hot_account_id=910000001"
  echo "hot_from=2026-04-01T00:00:00Z"
  echo "hot_to=2026-04-30T00:00:00Z"
  echo "cold_account_id=910000002"
  echo "cold_from=2026-01-01T00:00:00Z"
  echo "cold_to=2026-01-31T00:00:00Z"
  echo "generated_at=2026-04-27T00:00:00Z"
} >"${manifest_path}"

echo "[transaction-100m-dataset-probe] print plan"
plan="$(
  FIXTURE_PATH="${dump_path}" \
  FIXTURE_MANIFEST_PATH="${manifest_path}" \
  FIXTURE_DATASET_ENV_PATH="${env_path}" \
    "${script}" --print-plan
)"
grep -F "mode=print-plan" <<<"${plan}" >/dev/null
grep -F "manifest=${manifest_path}" <<<"${plan}" >/dev/null
grep -F "dataset_env=${env_path}" <<<"${plan}" >/dev/null
grep -F "db_gate=true" <<<"${plan}" >/dev/null
grep -F "db_fallback=false" <<<"${plan}" >/dev/null
grep -F "min_window_rows=51" <<<"${plan}" >/dev/null
grep -F "estimate_tolerance_rows=1000" <<<"${plan}" >/dev/null
grep -F "db_report=${dump_path}.db-gate.env" <<<"${plan}" >/dev/null
grep -F "query_statement_timeout_ms=3000" <<<"${plan}" >/dev/null
grep -F "query_lock_timeout_ms=1000" <<<"${plan}" >/dev/null
grep -F "query_work_mem=2MB" <<<"${plan}" >/dev/null
grep -F "query_temp_file_limit=8MB" <<<"${plan}" >/dev/null
grep -F "query_read_only=true" <<<"${plan}" >/dev/null
grep -F "window_probe_mode=index-only-bounded" <<<"${plan}" >/dev/null
grep -F "window_probe_limit=51" <<<"${plan}" >/dev/null
grep -F "db_gate_checks=partition-estimate-tolerance,index-only-bounded-window-probe,monthly-partition" <<<"${plan}" >/dev/null

echo "[transaction-100m-dataset-probe] estimate tolerance contract"
FIXTURE_DATASET_ASSERT_LABEL=total_estimate \
FIXTURE_DATASET_ASSERT_ACTUAL=99999884 \
FIXTURE_DATASET_ASSERT_MINIMUM=100000000 \
FIXTURE_DATASET_ESTIMATE_TOLERANCE_ROWS=1000 \
  "${script}" --assert-estimate >/dev/null
if FIXTURE_DATASET_ASSERT_LABEL=total_estimate \
  FIXTURE_DATASET_ASSERT_ACTUAL=99900000 \
  FIXTURE_DATASET_ASSERT_MINIMUM=100000000 \
  FIXTURE_DATASET_ESTIMATE_TOLERANCE_ROWS=1000 \
    "${script}" --assert-estimate >/dev/null 2>&1; then
  echo "estimate outside tolerance unexpectedly passed" >&2
  exit 1
fi

echo "[transaction-100m-dataset-probe] manifest probe writes k6 env"
FIXTURE_PATH="${dump_path}" \
FIXTURE_MANIFEST_PATH="${manifest_path}" \
FIXTURE_DATASET_ENV_PATH="${env_path}" \
FIXTURE_DATASET_DB_GATE=false \
  "${script}" >/dev/null
grep -F "K6_HOT_ACCOUNT_ID=910000001" "${env_path}" >/dev/null
grep -F "K6_HOT_FROM=2026-04-01T00:00:00Z" "${env_path}" >/dev/null
grep -F "K6_HOT_TO=2026-04-30T00:00:00Z" "${env_path}" >/dev/null
grep -F "K6_COLD_ACCOUNT_ID=910000002" "${env_path}" >/dev/null
grep -F "K6_COLD_FROM=2026-01-01T00:00:00Z" "${env_path}" >/dev/null
grep -F "K6_COLD_TO=2026-01-31T00:00:00Z" "${env_path}" >/dev/null

echo "[transaction-100m-dataset-probe] missing metadata fails without db fallback"
sed -i.bak '/hot_account_id=/d' "${manifest_path}"
if FIXTURE_PATH="${dump_path}" \
  FIXTURE_MANIFEST_PATH="${manifest_path}" \
  FIXTURE_DATASET_ENV_PATH="${env_path}" \
  FIXTURE_DATASET_DB_GATE=false \
    "${script}" >/dev/null 2>&1; then
  echo "missing manifest metadata unexpectedly passed without explicit DB fallback" >&2
  exit 1
fi

echo "[transaction-100m-dataset-probe] wrapper contract"
grep -F "assert_dataset_db_gate" "${script}" >/dev/null
grep -F "partition_name_for_month" "${script}" >/dev/null
grep -F "assert_partition_exists transaction_read_model" "${script}" >/dev/null
grep -F "assert_partition_exists transaction_read_model_archive" "${script}" >/dev/null
grep -F "assert_estimate_at_least" "${script}" >/dev/null
grep -F "index_only_window_probe" "${script}" >/dev/null
grep -F "ORDER BY booked_at DESC, id DESC" "${script}" >/dev/null
grep -F "LIMIT \${min_window_rows}" "${script}" >/dev/null
grep -F "write_db_gate_report" "${script}" >/dev/null
grep -F "BEGIN READ ONLY" "${script}" >/dev/null
grep -F "SET LOCAL statement_timeout" "${script}" >/dev/null
grep -F "SET LOCAL lock_timeout" "${script}" >/dev/null
grep -F "SET LOCAL work_mem" "${script}" >/dev/null
grep -F "SET LOCAL temp_file_limit" "${script}" >/dev/null
grep -F "FIXTURE_DATASET_MIN_WINDOW_ROWS" "${script}" >/dev/null
if grep -F "bounded-window-count" "${script}" >/dev/null; then
  echo "dataset probe still reports bounded-window-count instead of index-only bounded probe" >&2
  exit 1
fi
grep -F "run-transaction-100m-fixture-dataset-probe.sh" tools/test/run-transaction-100m-artifact-ready-k6.sh >/dev/null
grep -F "run-transaction-100m-fixture-dataset-probe.sh" tools/test/run-transaction-100m-fresh-volume-restore-k6.sh >/dev/null

echo "[transaction-100m-dataset-probe] invalid input fails"
if FIXTURE_DATASET_QUERY_TEMP_FILE_LIMIT=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "FIXTURE_DATASET_QUERY_TEMP_FILE_LIMIT=bad unexpectedly succeeded" >&2
  exit 1
fi
