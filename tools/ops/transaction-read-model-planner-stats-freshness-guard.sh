#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/ops/transaction-read-model-planner-stats-freshness-guard.sh [--print-sql]

Environment:
  DATABASE_URL   optional PostgreSQL URL. If set, it is passed directly to psql.
  DB_HOST        fallback host, default localhost
  DB_PORT        fallback port, default 5432
  DB_NAME        fallback database, default aquila_bank
  DB_USERNAME    fallback user, default postgres
  DB_PASSWORD    fallback password, mapped to PGPASSWORD when PGPASSWORD is unset

Threshold overrides:
  STATS_MAX_AGE_HOURS        default 24
  STATS_MAX_MODIFIED_RATIO   default 0.05
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

stats_max_age_hours="${STATS_MAX_AGE_HOURS:-24}"
stats_max_modified_ratio="${STATS_MAX_MODIFIED_RATIO:-0.05}"

validate_positive_integer() {
  local name="$1"
  local value="$2"
  [[ "${value}" =~ ^[1-9][0-9]*$ ]] || {
    echo "${name} must be a positive integer" >&2
    exit 1
  }
}

validate_ratio() {
  local name="$1"
  local value="$2"
  [[ "${value}" =~ ^([0-9]+([.][0-9]+)?|[.][0-9]+)$ ]] || {
    echo "${name} must be a decimal ratio between 0 and 1" >&2
    exit 1
  }
  awk -v value="${value}" 'BEGIN { exit !(value > 0 && value < 1) }' || {
    echo "${name} must be greater than 0 and less than 1" >&2
    exit 1
  }
}

validate_positive_integer "STATS_MAX_AGE_HOURS" "${stats_max_age_hours}"
validate_ratio "STATS_MAX_MODIFIED_RATIO" "${stats_max_modified_ratio}"

read -r -d '' sql <<'SQL' || true
WITH target_table(table_name) AS (
    VALUES
        ('transaction_read_model'),
        ('transaction_read_model_archive')
),
relation_data AS (
    SELECT
        t.table_name,
        c.oid AS relid,
        GREATEST(c.reltuples, 0)::bigint AS row_estimate,
        s.n_live_tup,
        s.n_mod_since_analyze,
        s.last_analyze,
        s.last_autoanalyze,
        s.analyze_count,
        s.autoanalyze_count
    FROM target_table t
    LEFT JOIN (
        SELECT c.oid,
               c.relname
        FROM pg_class c
        JOIN pg_namespace n
          ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
    ) c
      ON c.relname = t.table_name
    LEFT JOIN pg_stat_user_tables s
      ON s.relid = c.oid
),
calculated AS (
    SELECT
        table_name,
        relid,
        COALESCE(row_estimate, 0) AS row_estimate,
        COALESCE(n_live_tup, 0) AS live_tuple_count,
        COALESCE(n_mod_since_analyze, 0) AS modified_tuple_count,
        CASE
            WHEN COALESCE(row_estimate, 0) <= 0 THEN
                CASE
                    WHEN COALESCE(n_mod_since_analyze, 0) > 0 THEN 1::numeric
                    ELSE 0::numeric
                END
            ELSE ROUND(
                COALESCE(n_mod_since_analyze, 0)::numeric / COALESCE(row_estimate, 0),
                4
            )
        END AS modified_ratio,
        CASE
            WHEN last_analyze IS NULL AND last_autoanalyze IS NULL THEN NULL
            ELSE GREATEST(
                COALESCE(last_analyze, '-infinity'::timestamp with time zone),
                COALESCE(last_autoanalyze, '-infinity'::timestamp with time zone)
            )
        END AS last_analyze_at,
        COALESCE(analyze_count, 0) AS analyze_count,
        COALESCE(autoanalyze_count, 0) AS autoanalyze_count,
        format('ANALYZE VERBOSE public.%I;', table_name) AS analyze_command
    FROM relation_data
)
SELECT
    table_name,
    CASE
        WHEN relid IS NULL THEN 'missing_table'
        WHEN last_analyze_at IS NULL THEN 'missing_analyze'
        WHEN ROUND(EXTRACT(EPOCH FROM (now() - last_analyze_at)) / 3600, 1) >= :'stats_max_age_hours'::numeric
            THEN 'stale_analyze_age'
        WHEN modified_ratio >= :'stats_max_modified_ratio'::numeric
            THEN 'stale_modified_ratio'
        ELSE 'ok'
    END AS status,
    row_estimate,
    live_tuple_count,
    modified_tuple_count,
    modified_ratio,
    last_analyze_at,
    CASE
        WHEN last_analyze_at IS NULL THEN NULL
        ELSE ROUND(EXTRACT(EPOCH FROM (now() - last_analyze_at)) / 3600, 1)
    END AS analyze_age_hours,
    analyze_count,
    autoanalyze_count,
    analyze_command
FROM calculated
ORDER BY table_name;
SQL

if [[ "${1:-}" == "--print-sql" ]]; then
  printf '%s\n' "${sql}"
  exit 0
fi

if [[ $# -ne 0 ]]; then
  usage
  exit 1
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required. Install PostgreSQL client tools or use --print-sql." >&2
  exit 1
fi

echo "[transaction-planner-stats-guard] target tables: transaction_read_model, transaction_read_model_archive"
echo "[transaction-planner-stats-guard] max analyze age hours: ${stats_max_age_hours}"
echo "[transaction-planner-stats-guard] max modified ratio: ${stats_max_modified_ratio}"

result_file="$(mktemp)"
trap 'rm -f "${result_file}"' EXIT

psql_args=(--no-psqlrc --set ON_ERROR_STOP=1 --pset pager=off --csv)
psql_vars=(
  --set "stats_max_age_hours=${stats_max_age_hours}"
  --set "stats_max_modified_ratio=${stats_max_modified_ratio}"
)

if [[ -n "${DATABASE_URL:-}" ]]; then
  psql "${psql_args[@]}" "${psql_vars[@]}" "${DATABASE_URL}" --command "${sql}" >"${result_file}"
else
  export PGHOST="${PGHOST:-${DB_HOST:-localhost}}"
  export PGPORT="${PGPORT:-${DB_PORT:-5432}}"
  export PGDATABASE="${PGDATABASE:-${DB_NAME:-aquila_bank}}"
  export PGUSER="${PGUSER:-${DB_USERNAME:-postgres}}"
  if [[ -z "${PGPASSWORD:-}" && -n "${DB_PASSWORD:-}" ]]; then
    export PGPASSWORD="${DB_PASSWORD}"
  fi
  psql "${psql_args[@]}" "${psql_vars[@]}" --command "${sql}" >"${result_file}"
fi

cat "${result_file}"

failure_count="$(
  awk -F, 'NR > 1 && $2 != "ok" { count += 1 } END { print count + 0 }' "${result_file}"
)"

if [[ "${failure_count}" != "0" ]]; then
  echo "::error::Planner stats freshness guard detected stale stats. Run ANALYZE on reported tables before replay." >&2
  awk -F, '
    NR > 1 && $2 != "ok" {
      printf "::error::%s status=%s analyzeAgeHours=%s modifiedRatio=%s guidance=%s\n", $1, $2, $8, $6, $11 > "/dev/stderr"
    }
  ' "${result_file}"
  exit 1
fi

echo "::notice::Planner stats freshness guard passed." >&2
