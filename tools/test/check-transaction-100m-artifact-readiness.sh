#!/usr/bin/env bash
set -euo pipefail

validator="tools/test/validate-transaction-100m-fixture-artifact.sh"
runner="tools/test/run-transaction-100m-artifact-ready-k6.sh"
fresh_runner="tools/test/run-transaction-100m-fresh-volume-restore-k6.sh"

echo "[transaction-100m-artifact] shell syntax"
bash -n "${validator}"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

dump_path="${temp_dir}/transaction-100m-check.dump"
manifest_path="${dump_path}.manifest"
checksum_path="${dump_path}.sha256"
printf "sample-dump\n" >"${dump_path}"
checksum="$(shasum -a 256 "${dump_path}" | awk '{print $1}')"
printf "%s  %s\n" "${checksum}" "$(basename "${dump_path}")" >"${checksum_path}"
{
  echo "fixture_name=transaction-100m-check"
  echo "dump_path=${dump_path}"
  echo "sha256=${checksum}"
  echo "flyway_version=999"
  echo "total_rows=1000"
  echo "hot_rows=600"
  echo "archive_rows=400"
  echo "generated_at=2026-04-27T00:00:00Z"
} >"${manifest_path}"

echo "[transaction-100m-artifact] validator plan"
plan="$(
  FIXTURE_NAME=transaction-100m-check \
  FIXTURE_PATH="${dump_path}" \
  FIXTURE_MANIFEST_PATH="${manifest_path}" \
  FIXTURE_CHECKSUM_PATH="${checksum_path}" \
  FIXTURE_ARTIFACT_MIN_ROWS=1000 \
  FIXTURE_EXPECT_FLYWAY_VERSION=999 \
    "${validator}" --print-plan
)"
grep -F "mode=print-plan" <<<"${plan}" >/dev/null
grep -F "manifest=${manifest_path}" <<<"${plan}" >/dev/null
grep -F "checksum=${checksum_path}" <<<"${plan}" >/dev/null
grep -F "min_rows=1000" <<<"${plan}" >/dev/null

echo "[transaction-100m-artifact] validator verify"
FIXTURE_NAME=transaction-100m-check \
FIXTURE_PATH="${dump_path}" \
FIXTURE_MANIFEST_PATH="${manifest_path}" \
FIXTURE_CHECKSUM_PATH="${checksum_path}" \
FIXTURE_ARTIFACT_MIN_ROWS=1000 \
FIXTURE_EXPECT_FLYWAY_VERSION=999 \
  "${validator}" --verify >/dev/null

echo "[transaction-100m-artifact] validator catches stale checksum"
printf "changed\n" >"${dump_path}"
if FIXTURE_NAME=transaction-100m-check \
  FIXTURE_PATH="${dump_path}" \
  FIXTURE_MANIFEST_PATH="${manifest_path}" \
  FIXTURE_CHECKSUM_PATH="${checksum_path}" \
  FIXTURE_ARTIFACT_MIN_ROWS=1000 \
  FIXTURE_EXPECT_FLYWAY_VERSION=999 \
    "${validator}" --verify >/dev/null 2>&1; then
  echo "stale checksum unexpectedly passed" >&2
  exit 1
fi

echo "[transaction-100m-artifact] artifact-ready runner plan"
k6_plan="$(
  FIXTURE_NAME=transaction-100m-check \
  FIXTURE_PATH="${dump_path}" \
  K6_REPORT_NAME=transaction-100m-artifact-ready-check \
    "${runner}" --print-plan
)"
grep -F "artifact_gate=tools/test/validate-transaction-100m-fixture-artifact.sh --verify" <<<"${k6_plan}" >/dev/null
grep -F "db_gate_artifact=${dump_path}.db-gate.env" <<<"${k6_plan}" >/dev/null
grep -F "dataset_source=existing-artifact-or-probe" <<<"${k6_plan}" >/dev/null
grep -F "k6_no_deps=true" <<<"${k6_plan}" >/dev/null

echo "[transaction-100m-artifact] fresh-volume dry-run preflight order"
dry_run="$(
  FIXTURE_NAME=transaction-100m-check \
  FIXTURE_PATH="${dump_path}" \
  K6_REPORT_NAME=transaction-100m-fresh-check \
    "${fresh_runner}" --dry-run
)"
preflight_line="$(grep -n "validate-transaction-100m-fixture-artifact.sh --verify" <<<"${dry_run}" | head -1 | cut -d: -f1)"
volume_line="$(grep -n "docker volume rm" <<<"${dry_run}" | head -1 | cut -d: -f1)"
if [[ -z "${preflight_line}" || -z "${volume_line}" || "${preflight_line}" -ge "${volume_line}" ]]; then
  echo "artifact preflight must be planned before docker volume rm" >&2
  exit 1
fi
grep -F "db gate artifact: ${dump_path}.db-gate.env" <<<"${dry_run}" >/dev/null
grep -F "source existing dataset env when db gate artifact passed" <<<"${dry_run}" >/dev/null

echo "[transaction-100m-artifact] wrapper artifact reuse contract"
grep -F "dataset_artifacts_ready" "${runner}" >/dev/null
grep -F "load_dataset_env_or_probe" "${runner}" >/dev/null
grep -F "FIXTURE_DATASET_DB_GATE_STATUS=passed" "${runner}" >/dev/null
grep -F "dataset_artifacts_ready" "${fresh_runner}" >/dev/null
grep -F "load_dataset_env_or_probe" "${fresh_runner}" >/dev/null
grep -F "FIXTURE_DATASET_DB_GATE_STATUS=passed" "${fresh_runner}" >/dev/null

echo "[transaction-100m-artifact] invalid input fails"
if FRESH_VOLUME_DUMP_MISSING_MODE=bad "${fresh_runner}" --print-plan >/dev/null 2>&1; then
  echo "FRESH_VOLUME_DUMP_MISSING_MODE=bad unexpectedly succeeded" >&2
  exit 1
fi
