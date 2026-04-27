#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-fixture-artifact-scheduled-verify.sh"

echo "[transaction-100m-fixture-scheduled] shell syntax"
bash -n "${script}"

echo "[transaction-100m-fixture-scheduled] print plan"
plan="$(
  SCHEDULED_FIXTURE_NAME=transaction-100m-scheduled-check \
  SCHEDULED_FIXTURE_DIR=build/fixtures/check \
    "${script}" --print-plan
)"
grep -F "fixture=transaction-100m-scheduled-check" <<<"${plan}" >/dev/null
grep -F "dump=build/fixtures/check/transaction-100m-scheduled-check.dump" <<<"${plan}" >/dev/null
grep -F "export_enabled=true" <<<"${plan}" >/dev/null
grep -F "verify_enabled=true" <<<"${plan}" >/dev/null
grep -F "fresh_volume_restore_smoke=true" <<<"${plan}" >/dev/null
grep -F "k6_enabled=false" <<<"${plan}" >/dev/null
grep -F "steps=export,verify,fresh-volume-restore-smoke" <<<"${plan}" >/dev/null

echo "[transaction-100m-fixture-scheduled] dry run"
dry_run="$(
  SCHEDULED_FIXTURE_NAME=transaction-100m-scheduled-check \
  SCHEDULED_FIXTURE_DIR=build/fixtures/check \
    "${script}" --dry-run
)"
grep -F "tools/test/run-transaction-100m-existing-volume-fixture-export.sh" <<<"${dry_run}" >/dev/null
grep -F "tools/test/validate-transaction-100m-fixture-artifact.sh --verify" <<<"${dry_run}" >/dev/null
grep -F "tools/test/run-transaction-100m-fresh-volume-restore-k6.sh" <<<"${dry_run}" >/dev/null
grep -F "FRESH_VOLUME_K6_ENABLED=false" <<<"${dry_run}" >/dev/null

echo "[transaction-100m-fixture-scheduled] runner contract"
grep -F "SCHEDULED_FIXTURE_RESTORE_CONFIRM" "${script}" >/dev/null
grep -F "FRESH_VOLUME_CONFIRM=\"\${restore_confirm}\"" "${script}" >/dev/null
grep -F "FRESH_VOLUME_K6_ENABLED=\"\${k6_enabled}\"" "${script}" >/dev/null

echo "[transaction-100m-fixture-scheduled] invalid input fails"
if SCHEDULED_FIXTURE_EXPORT_ENABLED=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad SCHEDULED_FIXTURE_EXPORT_ENABLED unexpectedly succeeded" >&2
  exit 1
fi
if SCHEDULED_FIXTURE_K6_ENABLED=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad SCHEDULED_FIXTURE_K6_ENABLED unexpectedly succeeded" >&2
  exit 1
fi
