#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-existing-volume-fixture-export.sh"

echo "[transaction-100m-existing-export] shell syntax"
bash -n "${script}"

echo "[transaction-100m-existing-export] dry-run contract"
plan="$(
  FIXTURE_NAME=transaction-100m-check \
  FIXTURE_DIR=build/fixtures/check \
    "${script}" --dry-run
)"
grep -F "FIXTURE_MODE=dump" <<<"${plan}" >/dev/null
grep -F "FIXTURE_NAME=transaction-100m-check" <<<"${plan}" >/dev/null
grep -F "FIXTURE_PATH=build/fixtures/check/transaction-100m-check.dump" <<<"${plan}" >/dev/null
grep -F "tools/test/run-transaction-100m-fixture-restore.sh" <<<"${plan}" >/dev/null
grep -F "tools/test/validate-transaction-100m-fixture-artifact.sh --verify" <<<"${plan}" >/dev/null
grep -F "manifest=build/fixtures/check/transaction-100m-check.dump.manifest" <<<"${plan}" >/dev/null
grep -F "checksum=build/fixtures/check/transaction-100m-check.dump.sha256" <<<"${plan}" >/dev/null
