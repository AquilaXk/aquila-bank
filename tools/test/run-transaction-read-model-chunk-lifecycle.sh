#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/transaction-read-model-chunk-lifecycle.sh"

echo "[transaction-chunk-lifecycle] syntax: ${script}"
bash -n "${script}"

echo "[transaction-chunk-lifecycle] precreate: future monthly partitions for hot/archive"
precreate_sql="$("${script}" --action precreate --target both --reference-month 2026-04 --months-ahead 2 --print-sql)"
grep -F "SET lock_timeout = '1000ms';" <<<"${precreate_sql}" >/dev/null
grep -F "SET statement_timeout = '30000ms';" <<<"${precreate_sql}" >/dev/null
grep -F "reference_month DATE := DATE '2026-04-01';" <<<"${precreate_sql}" >/dev/null
grep -F "month_count INTEGER := 2;" <<<"${precreate_sql}" >/dev/null
grep -F "('transaction_read_model')" <<<"${precreate_sql}" >/dev/null
grep -F "('transaction_read_model_archive')" <<<"${precreate_sql}" >/dev/null
grep -F "CREATE TABLE IF NOT EXISTS %I PARTITION OF %s" <<<"${precreate_sql}" >/dev/null

echo "[transaction-chunk-lifecycle] retention-plan: old monthly archive candidates only"
plan_sql="$("${script}" --action retention-plan --target archive --before-month 2026-02 --print-sql)"
grep -F "transaction_read_model_archive" <<<"${plan_sql}" >/dev/null
grep -F "recommended_action" <<<"${plan_sql}" >/dev/null
grep -F "detach_archive_candidate" <<<"${plan_sql}" >/dev/null
if grep -F "transaction_read_model')" <<<"${plan_sql}" >/dev/null; then
  echo "archive retention plan must not include hot parent" >&2
  exit 1
fi

echo "[transaction-chunk-lifecycle] detach: archive partition only with explicit cutoff"
detach_sql="$("${script}" --action detach --target archive --before-month 2026-02 --print-sql)"
grep -F "CREATE SCHEMA IF NOT EXISTS transaction_read_model_detached;" <<<"${detach_sql}" >/dev/null
grep -F "ALTER TABLE public.transaction_read_model_archive DETACH PARTITION" <<<"${detach_sql}" >/dev/null
grep -F "ALTER TABLE %s SET SCHEMA transaction_read_model_detached" <<<"${detach_sql}" >/dev/null

echo "[transaction-chunk-lifecycle] drop-detached: requires confirmation outside dry-run"
drop_sql="$("${script}" --action drop-detached --before-month 2026-01 --print-sql)"
grep -F "DROP TABLE transaction_read_model_detached" <<<"${drop_sql}" >/dev/null
if "${script}" --action drop-detached --before-month 2026-01 >/dev/null 2>&1; then
  echo "drop-detached must require CONFIRM_DROP when not dry-run" >&2
  exit 1
fi

echo "[transaction-chunk-lifecycle] maintenance: partition-level analyze/vacuum"
analyze_sql="$("${script}" --action analyze --target both --print-sql)"
grep -F "ANALYZE VERBOSE" <<<"${analyze_sql}" >/dev/null
grep -F "pg_inherits" <<<"${analyze_sql}" >/dev/null

vacuum_sql="$("${script}" --action vacuum-analyze --target archive --print-sql)"
grep -F "VACUUM (ANALYZE, VERBOSE)" <<<"${vacuum_sql}" >/dev/null
grep -F "transaction_read_model_archive" <<<"${vacuum_sql}" >/dev/null
if grep -F "transaction_read_model')" <<<"${vacuum_sql}" >/dev/null; then
  echo "archive vacuum must not include hot parent" >&2
  exit 1
fi

echo "[transaction-chunk-lifecycle] guards: invalid input fails before psql"
if "${script}" --action detach --target hot --before-month 2026-02 --print-sql >/dev/null 2>&1; then
  echo "detach target hot unexpectedly succeeded" >&2
  exit 1
fi
if "${script}" --action precreate --reference-month 2026-4 --print-sql >/dev/null 2>&1; then
  echo "invalid reference month unexpectedly succeeded" >&2
  exit 1
fi
if "${script}" --action retention-plan --target archive --print-sql >/dev/null 2>&1; then
  echo "missing before-month unexpectedly succeeded" >&2
  exit 1
fi
