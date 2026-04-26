#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-fixture-cleanup-wal-budget.sh"
runbook="docs/transaction-read-volume-corruption-runbook.md"

echo "[transaction-100m-cleanup-wal] shell syntax"
bash -n "${script}"

echo "[transaction-100m-cleanup-wal] print plan"
plan="$(
  CLEANUP_CHUNK_SIZE=1000 \
  CLEANUP_MAX_CHUNKS=3 \
  CLEANUP_WAL_MAX_BYTES=1048576 \
    "${script}" --print-plan
)"
grep -F "mode=print-plan" <<<"${plan}" >/dev/null
grep -F "confirm=delete-100m-fixture required for run" <<<"${plan}" >/dev/null
grep -F "accounts=910000001,910000002" <<<"${plan}" >/dev/null
grep -F "chunk_size=1000" <<<"${plan}" >/dev/null
grep -F "max_chunks=3" <<<"${plan}" >/dev/null
grep -F "wal_max_bytes=1048576" <<<"${plan}" >/dev/null
grep -F "tables=transaction_read_model,transaction_read_model_archive" <<<"${plan}" >/dev/null

echo "[transaction-100m-cleanup-wal] dry-run command"
dry_run="$("${script}" --dry-run)"
grep -F "FIXTURE_MODE=verify" <<<"${dry_run}" >/dev/null
grep -F "DELETE FROM public.transaction_read_model" <<<"${dry_run}" >/dev/null
grep -F "DELETE FROM public.transaction_read_model_archive" <<<"${dry_run}" >/dev/null
grep -F "pg_wal_lsn_diff" <<<"${dry_run}" >/dev/null

echo "[transaction-100m-cleanup-wal] runner contract"
grep -F "delete-100m-fixture" "${script}" >/dev/null
grep -F "CLEANUP_CHUNK_SIZE" "${script}" >/dev/null
grep -F "CLEANUP_WAL_MAX_BYTES" "${script}" >/dev/null
grep -F "FIXTURE_MODE=verify" "${script}" >/dev/null
grep -F "ctid" "${script}" >/dev/null
grep -F "pg_current_wal_lsn()" "${script}" >/dev/null
grep -F "pg_wal_lsn_diff" "${script}" >/dev/null
grep -F "WAL budget exceeded" "${script}" >/dev/null

echo "[transaction-100m-cleanup-wal] runbook contract"
test -f "${runbook}"
grep -F "복구 기준" "${runbook}" >/dev/null
grep -F "폐기 기준" "${runbook}" >/dev/null
grep -F "재생성 기준" "${runbook}" >/dev/null
grep -F "run-transaction-100m-fresh-volume-restore-k6.sh" "${runbook}" >/dev/null
grep -F "run-transaction-100m-fixture-cleanup-wal-budget.sh" "${runbook}" >/dev/null
grep -F "OOMKilled=true" "${runbook}" >/dev/null

echo "[transaction-100m-cleanup-wal] invalid input fails"
if CLEANUP_CHUNK_SIZE=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "CLEANUP_CHUNK_SIZE=0 unexpectedly succeeded" >&2
  exit 1
fi
if CLEANUP_MAX_CHUNKS=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "CLEANUP_MAX_CHUNKS=bad unexpectedly succeeded" >&2
  exit 1
fi
if CLEANUP_WAL_MAX_BYTES=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "CLEANUP_WAL_MAX_BYTES=0 unexpectedly succeeded" >&2
  exit 1
fi
if CLEANUP_CONFIRM=bad "${script}" >/dev/null 2>&1; then
  echo "CLEANUP_CONFIRM=bad unexpectedly succeeded" >&2
  exit 1
fi
