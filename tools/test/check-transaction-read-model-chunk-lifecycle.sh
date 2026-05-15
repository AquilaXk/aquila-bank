#!/usr/bin/env bash
set -euo pipefail

runner="tools/ops/transaction-read-model-chunk-lifecycle.sh"

require_contains() {
  local content="$1"
  local expected="$2"
  if ! grep -F "${expected}" <<<"${content}" >/dev/null; then
    echo "missing expected chunk lifecycle contract text: ${expected}" >&2
    exit 1
  fi
}

require_not_contains() {
  local content="$1"
  local unexpected="$2"
  if grep -F "${unexpected}" <<<"${content}" >/dev/null; then
    echo "unexpected chunk lifecycle contract text: ${unexpected}" >&2
    exit 1
  fi
}

echo "[transaction-read-model-chunk-lifecycle] shell syntax"
bash -n "${runner}"

echo "[transaction-read-model-chunk-lifecycle] precreate SQL contract"
precreate_sql="$(
  "${runner}" \
    --action precreate \
    --target both \
    --reference-month 2026-05 \
    --months-ahead 3 \
    --lock-timeout-ms 1000 \
    --statement-timeout-ms 30000 \
    --print-sql
)"
require_contains "${precreate_sql}" "SET lock_timeout = '1000ms';"
require_contains "${precreate_sql}" "SET statement_timeout = '30000ms';"
require_contains "${precreate_sql}" "reference_month DATE := DATE '2026-05-01';"
require_contains "${precreate_sql}" "month_count INTEGER := 3;"
require_contains "${precreate_sql}" "('transaction_read_model')"
require_contains "${precreate_sql}" "('transaction_read_model_archive')"
require_contains "${precreate_sql}" "CREATE TABLE IF NOT EXISTS %I PARTITION OF %s"
require_contains "${precreate_sql}" "parent_regclass := to_regclass('public.' || parent_name);"

echo "[transaction-read-model-chunk-lifecycle] analyze SQL contract"
analyze_sql="$(
  "${runner}" \
    --action analyze \
    --target both \
    --reference-month 2026-05 \
    --months-ahead 1 \
    --print-sql
)"
require_contains "${analyze_sql}" "ANALYZE VERBOSE %s;"
require_contains "${analyze_sql}" "FROM target_parent target"
require_contains "${analyze_sql}" "child.relname <> parent.relname || '_default'"
require_contains "${analyze_sql}" "\\gexec"
require_not_contains "${analyze_sql}" "VACUUM (ANALYZE, VERBOSE)"

echo "[transaction-read-model-chunk-lifecycle] vacuum analyze SQL contract"
vacuum_sql="$(
  "${runner}" \
    --action vacuum-analyze \
    --target archive \
    --reference-month 2026-05 \
    --months-ahead 1 \
    --statement-timeout-ms 120000 \
    --print-sql
)"
require_contains "${vacuum_sql}" "SET statement_timeout = '120000ms';"
require_contains "${vacuum_sql}" "('transaction_read_model_archive')"
require_contains "${vacuum_sql}" "VACUUM (ANALYZE, VERBOSE) %s;"
require_contains "${vacuum_sql}" "\\gexec"
require_not_contains "${vacuum_sql}" "('transaction_read_model')"

echo "[transaction-read-model-chunk-lifecycle] retention plan SQL contract"
retention_sql="$(
  "${runner}" \
    --action retention-plan \
    --target both \
    --before-month 2026-03 \
    --print-sql
)"
require_contains "${retention_sql}" "recommended_action"
require_contains "${retention_sql}" "detach_archive_candidate"
require_contains "${retention_sql}" "run_row_retention_before_hot_detach"
require_contains "${retention_sql}" "WHERE month_end <= DATE '2026-03-01'"

echo "[transaction-read-model-chunk-lifecycle] passed"
