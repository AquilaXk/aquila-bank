#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/transaction-read-model-vacuum-analyze.sh"

echo "[transaction-vacuum-automation] syntax: ${script}"
bash -n "${script}"

echo "[transaction-vacuum-automation] dry-run: both tables VACUUM (ANALYZE) with bounded timeout"
sql="$("${script}" --target both --mode vacuum-analyze --lock-timeout-ms 1000 --statement-timeout-ms 30000 --print-sql)"
grep -F "SET lock_timeout = '1000ms';" <<<"${sql}" >/dev/null
grep -F "SET statement_timeout = '30000ms';" <<<"${sql}" >/dev/null
grep -F "VACUUM (ANALYZE, VERBOSE) public.transaction_read_model;" <<<"${sql}" >/dev/null
grep -F "VACUUM (ANALYZE, VERBOSE) public.transaction_read_model_archive;" <<<"${sql}" >/dev/null

echo "[transaction-vacuum-automation] dry-run: archive ANALYZE only"
archive_sql="$("${script}" --target archive --mode analyze --print-sql)"
grep -F "ANALYZE VERBOSE public.transaction_read_model_archive;" <<<"${archive_sql}" >/dev/null
if grep -F "transaction_read_model;" <<<"${archive_sql}" >/dev/null; then
  echo "archive target must not include hot transaction_read_model" >&2
  exit 1
fi

echo "[transaction-vacuum-automation] guard: invalid target/mode fail before psql"
if "${script}" --target ledger_entry --print-sql >/dev/null 2>&1; then
  echo "invalid target unexpectedly succeeded" >&2
  exit 1
fi
if "${script}" --mode reindex --print-sql >/dev/null 2>&1; then
  echo "invalid mode unexpectedly succeeded" >&2
  exit 1
fi
