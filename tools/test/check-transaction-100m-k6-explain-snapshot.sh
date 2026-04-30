#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-k6-explain-snapshot.sh"

echo "[transaction-100m-k6-explain] shell syntax"
bash -n "${script}"

echo "[transaction-100m-k6-explain] dry-run contract"
plan="$(
  K6_REPORT_NAME=transaction-100m-explain-check \
  K6_HOT_ACCOUNT_ID=910000001 \
  K6_HOT_FROM=2026-04-01T00:00:00Z \
  K6_HOT_TO=2026-04-30T00:00:00Z \
  K6_HOT_DEEP_CURSOR_BOOKED_AT=2026-04-15T00:00:00Z \
  K6_HOT_DEEP_CURSOR_ID=9223372036854775807 \
  K6_COLD_ACCOUNT_ID=910000002 \
  K6_COLD_FROM=2026-01-01T00:00:00Z \
  K6_COLD_TO=2026-01-31T00:00:00Z \
  K6_COLD_DEEP_CURSOR_BOOKED_AT=2026-01-15T00:00:00Z \
  K6_COLD_DEEP_CURSOR_ID=9223372036854775807 \
    "${script}" --dry-run
)"
grep -F "hot-first=build/reports/k6/transaction-100m-explain-check-explain/manual-hot-first.txt" <<<"${plan}" >/dev/null
grep -F "hot-cursor=build/reports/k6/transaction-100m-explain-check-explain/manual-hot-cursor.txt" <<<"${plan}" >/dev/null
grep -F "hot-deep-cursor=build/reports/k6/transaction-100m-explain-check-explain/manual-hot-deep-cursor.txt" <<<"${plan}" >/dev/null
grep -F "cold-first=build/reports/k6/transaction-100m-explain-check-explain/manual-cold-first.txt" <<<"${plan}" >/dev/null
grep -F "cold-cursor=build/reports/k6/transaction-100m-explain-check-explain/manual-cold-cursor.txt" <<<"${plan}" >/dev/null
grep -F "cold-deep-cursor=build/reports/k6/transaction-100m-explain-check-explain/manual-cold-deep-cursor.txt" <<<"${plan}" >/dev/null

sql="$(
  K6_HOT_ACCOUNT_ID=910000001 \
  K6_HOT_FROM=2026-04-01T00:00:00Z \
  K6_HOT_TO=2026-04-30T00:00:00Z \
  K6_HOT_DEEP_CURSOR_BOOKED_AT=2026-04-15T00:00:00Z \
  K6_HOT_DEEP_CURSOR_ID=9223372036854775807 \
  K6_COLD_ACCOUNT_ID=910000002 \
  K6_COLD_FROM=2026-01-01T00:00:00Z \
  K6_COLD_TO=2026-01-31T00:00:00Z \
  K6_COLD_DEEP_CURSOR_BOOKED_AT=2026-01-15T00:00:00Z \
  K6_COLD_DEEP_CURSOR_ID=9223372036854775807 \
    "${script}" --print-sql
)"
grep -F "EXPLAIN (FORMAT TEXT)" <<<"${sql}" >/dev/null
grep -F "FROM public.transaction_read_model" <<<"${sql}" >/dev/null
grep -F "FROM public.transaction_read_model_archive" <<<"${sql}" >/dev/null
grep -F "(booked_at, id) <" <<<"${sql}" >/dev/null
grep -F "booked_at <= '2026-04-15T00:00:00Z'::timestamptz" <<<"${sql}" >/dev/null
grep -F "booked_at <= '2026-01-15T00:00:00Z'::timestamptz" <<<"${sql}" >/dev/null
grep -F ">\"\${output}\"" "${script}" >/dev/null
if grep -F -- "--output \"\${output}\"" "${script}" >/dev/null; then
  echo "psql --output writes inside the postgres container, not host artifact path" >&2
  exit 1
fi

k6_plan="$(tools/test/run-k6-transaction-100m-loadtest.sh --print-plan)"
grep -F "explain_snapshot=true" <<<"${k6_plan}" >/dev/null
grep -F "run_explain_snapshot pre" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "run_explain_snapshot post" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
