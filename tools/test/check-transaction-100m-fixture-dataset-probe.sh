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
grep -F "db_fallback=false" <<<"${plan}" >/dev/null

echo "[transaction-100m-dataset-probe] manifest probe writes k6 env"
FIXTURE_PATH="${dump_path}" \
FIXTURE_MANIFEST_PATH="${manifest_path}" \
FIXTURE_DATASET_ENV_PATH="${env_path}" \
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
    "${script}" >/dev/null 2>&1; then
  echo "missing manifest metadata unexpectedly passed without explicit DB fallback" >&2
  exit 1
fi

echo "[transaction-100m-dataset-probe] wrapper contract"
grep -F "run-transaction-100m-fixture-dataset-probe.sh" tools/test/run-transaction-100m-artifact-ready-k6.sh >/dev/null
grep -F "run-transaction-100m-fixture-dataset-probe.sh" tools/test/run-transaction-100m-fresh-volume-restore-k6.sh >/dev/null
