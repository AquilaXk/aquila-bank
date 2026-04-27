#!/usr/bin/env bash
set -euo pipefail

script="tools/test/cleanup-loadtest-runtime.sh"

echo "[loadtest-cleanup] shell syntax"
bash -n "${script}"

echo "[loadtest-cleanup] dry-run contract"
plan="$("${script}" --dry-run)"
grep -F "docker rm -f aquila-bank-postgres-loadtest" <<<"${plan}" >/dev/null
grep -F "docker rm -f aquila-bank-backend-loadtest" <<<"${plan}" >/dev/null
grep -F "volume cleanup skipped: set LOADTEST_CLEANUP_DELETE_VOLUME=true" <<<"${plan}" >/dev/null
if grep -F "docker volume rm" <<<"${plan}" >/dev/null; then
  echo "volume removal appeared without explicit confirm" >&2
  exit 1
fi

confirmed_plan="$(
  LOADTEST_CLEANUP_DELETE_VOLUME=true \
  LOADTEST_CLEANUP_VOLUME_NAME=aquila-bank-loadtest-postgres-data \
  LOADTEST_CLEANUP_VOLUME_CONFIRM=delete-loadtest-volume \
    "${script}" --dry-run
)"
grep -F "docker volume rm aquila-bank-loadtest-postgres-data" <<<"${confirmed_plan}" >/dev/null

if LOADTEST_CLEANUP_DELETE_VOLUME=true \
  LOADTEST_CLEANUP_VOLUME_NAME=aquila-bank-postgres-data \
  LOADTEST_CLEANUP_VOLUME_CONFIRM=delete-loadtest-volume \
    "${script}" --dry-run >/dev/null 2>&1; then
  echo "non-loadtest volume name unexpectedly accepted" >&2
  exit 1
fi
