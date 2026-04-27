#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-fresh-volume-restore-k6.sh"

echo "[transaction-100m-fresh-volume] shell syntax"
bash -n "${script}"

echo "[transaction-100m-fresh-volume] compose config"
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml config >/dev/null

echo "[transaction-100m-fresh-volume] print plan"
plan="$(
  FRESH_VOLUME_NAME=aquila-bank-postgres-data \
  FIXTURE_NAME=transaction-100m-check \
  K6_REPORT_NAME=transaction-100m-fresh-check \
  FRESH_VOLUME_RESTORE_VERIFY_MIN_ROWS=1000 \
    "${script}" --print-plan
)"
grep -F "mode=print-plan" <<<"${plan}" >/dev/null
grep -F "volume=aquila-bank-postgres-data" <<<"${plan}" >/dev/null
grep -F "confirm=erase-postgres-volume required for run" <<<"${plan}" >/dev/null
grep -F "fixture=transaction-100m-check" <<<"${plan}" >/dev/null
grep -F "artifact_preflight=true" <<<"${plan}" >/dev/null
grep -F "dump_missing_mode=fail-only" <<<"${plan}" >/dev/null
grep -F "restore_verify_min_rows=1000" <<<"${plan}" >/dev/null
grep -F "restore_runner=tools/test/run-transaction-100m-fixture-restore.sh" <<<"${plan}" >/dev/null
grep -F "artifact_gate=tools/test/validate-transaction-100m-fixture-artifact.sh --verify" <<<"${plan}" >/dev/null
grep -F "k6_runner=tools/test/run-k6-transaction-100m-loadtest.sh --no-up" <<<"${plan}" >/dev/null
grep -F "k6_enabled=true" <<<"${plan}" >/dev/null
grep -F "hot account=910000001" <<<"${plan}" >/dev/null
grep -F "cold account=910000002" <<<"${plan}" >/dev/null

echo "[transaction-100m-fresh-volume] dry-run command"
dry_run="$(
  FRESH_VOLUME_NAME=aquila-bank-postgres-data \
  FIXTURE_NAME=transaction-100m-check \
  K6_REPORT_NAME=transaction-100m-fresh-check \
    "${script}" --dry-run
)"
preflight_line="$(grep -n "validate-transaction-100m-fixture-artifact.sh --verify" <<<"${dry_run}" | head -1 | cut -d: -f1)"
volume_line="$(grep -n "docker volume rm aquila-bank-postgres-data" <<<"${dry_run}" | head -1 | cut -d: -f1)"
if [[ -z "${preflight_line}" || -z "${volume_line}" || "${preflight_line}" -ge "${volume_line}" ]]; then
  echo "artifact preflight must be planned before docker volume rm" >&2
  exit 1
fi
grep -F "docker volume rm aquila-bank-postgres-data" <<<"${dry_run}" >/dev/null
grep -F "FIXTURE_MODE=restore" <<<"${dry_run}" >/dev/null
grep -F "FIXTURE_RESTORE_TRUNCATE=true" <<<"${dry_run}" >/dev/null
grep -F "run-k6-transaction-100m-loadtest.sh --no-up" <<<"${dry_run}" >/dev/null

seed_fallback_dry_run="$(
  FRESH_VOLUME_DUMP_MISSING_MODE=seed-only \
  FRESH_VOLUME_NAME=aquila-bank-postgres-data \
  FIXTURE_NAME=transaction-100m-check \
  K6_REPORT_NAME=transaction-100m-fresh-check \
    "${script}" --dry-run
)"
grep -F "if fixture dump is absent: tools/test/prepare-transaction-read-model-100m-fixture.sh" <<<"${seed_fallback_dry_run}" >/dev/null

echo "[transaction-100m-fresh-volume] runner contract"
grep -F "erase-postgres-volume" "${script}" >/dev/null
grep -F "preflight_fixture_artifact" "${script}" >/dev/null
grep -F "FRESH_VOLUME_DUMP_MISSING_MODE" "${script}" >/dev/null
grep -F "seed-only fallback" "${script}" >/dev/null
grep -F "docker volume rm" "${script}" >/dev/null
grep -F "wait_for_schema" "${script}" >/dev/null
grep -F "assert_flyway_latest" "${script}" >/dev/null
grep -F "FIXTURE_MODE=restore" "${script}" >/dev/null
grep -F "FIXTURE_RESTORE_TRUNCATE=true" "${script}" >/dev/null
grep -F "FIXTURE_VERIFY_MIN_ROWS" "${script}" >/dev/null
grep -F "run-k6-transaction-100m-loadtest.sh --no-up" "${script}" >/dev/null

echo "[transaction-100m-fresh-volume] invalid input fails"
if FRESH_VOLUME_K6_ENABLED=maybe "${script}" --print-plan >/dev/null 2>&1; then
  echo "FRESH_VOLUME_K6_ENABLED=maybe unexpectedly succeeded" >&2
  exit 1
fi
if FRESH_VOLUME_RESTORE_VERIFY_MIN_ROWS=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "FRESH_VOLUME_RESTORE_VERIFY_MIN_ROWS=bad unexpectedly succeeded" >&2
  exit 1
fi
if FRESH_VOLUME_DUMP_MISSING_MODE=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "FRESH_VOLUME_DUMP_MISSING_MODE=bad unexpectedly succeeded" >&2
  exit 1
fi
if FRESH_VOLUME_CONFIRM=bad "${script}" >/dev/null 2>&1; then
  echo "FRESH_VOLUME_CONFIRM=bad unexpectedly succeeded" >&2
  exit 1
fi
