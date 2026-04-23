#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/transaction-read-model-planner-stats-freshness-guard.sh"

echo "[transaction-planner-stats-guard] syntax: ${script}"
bash -n "${script}"

echo "[transaction-planner-stats-guard] dry-run: SQL exposes freshness signals and ANALYZE guidance"
sql="$("${script}" --print-sql)"
grep -F "n_mod_since_analyze" <<<"${sql}" >/dev/null
grep -F "last_autoanalyze" <<<"${sql}" >/dev/null
grep -F "stale_analyze_age" <<<"${sql}" >/dev/null
grep -F "stale_modified_ratio" <<<"${sql}" >/dev/null
grep -F "format('ANALYZE VERBOSE public.%I;', table_name)" <<<"${sql}" >/dev/null

echo "[transaction-planner-stats-guard] guard: invalid threshold fails before querying"
if STATS_MAX_AGE_HOURS=0 "${script}" --print-sql >/dev/null 2>&1; then
  echo "invalid STATS_MAX_AGE_HOURS unexpectedly succeeded" >&2
  exit 1
fi
if STATS_MAX_MODIFIED_RATIO=ratio "${script}" --print-sql >/dev/null 2>&1; then
  echo "invalid STATS_MAX_MODIFIED_RATIO unexpectedly succeeded" >&2
  exit 1
fi
