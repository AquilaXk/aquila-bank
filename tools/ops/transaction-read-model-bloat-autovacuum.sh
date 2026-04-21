#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/ops/transaction-read-model-bloat-autovacuum.sh [--print-sql]

Environment:
  DATABASE_URL   optional PostgreSQL URL. If set, it is passed directly to psql.
  DB_HOST        fallback host, default localhost
  DB_PORT        fallback port, default 5432
  DB_NAME        fallback database, default aquila_bank
  DB_USERNAME    fallback user, default postgres
  DB_PASSWORD    fallback password, mapped to PGPASSWORD when PGPASSWORD is unset

Threshold overrides:
  DEAD_TUPLE_WARN_RATIO       default 0.10
  DEAD_TUPLE_CRITICAL_RATIO   default 0.20
  VACUUM_WARN_HOURS           default 24
  VACUUM_CRITICAL_HOURS       default 72
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

dead_tuple_warn_ratio="${DEAD_TUPLE_WARN_RATIO:-0.10}"
dead_tuple_critical_ratio="${DEAD_TUPLE_CRITICAL_RATIO:-0.20}"
vacuum_warn_hours="${VACUUM_WARN_HOURS:-24}"
vacuum_critical_hours="${VACUUM_CRITICAL_HOURS:-72}"

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
        s.n_live_tup,
        s.n_dead_tup,
        s.last_vacuum,
        s.last_autovacuum,
        s.last_analyze,
        s.last_autoanalyze,
        s.vacuum_count,
        s.autovacuum_count,
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
        COALESCE(n_live_tup, 0) AS live_tuple_count,
        COALESCE(n_dead_tup, 0) AS dead_tuple_count,
        CASE
            WHEN COALESCE(n_live_tup, 0) + COALESCE(n_dead_tup, 0) = 0 THEN 0::numeric
            ELSE ROUND(
                COALESCE(n_dead_tup, 0)::numeric
                / (COALESCE(n_live_tup, 0) + COALESCE(n_dead_tup, 0)),
                4
            )
        END AS dead_tuple_ratio,
        GREATEST(last_vacuum, last_autovacuum) AS last_vacuum_at,
        GREATEST(last_analyze, last_autoanalyze) AS last_analyze_at,
        vacuum_count,
        autovacuum_count,
        analyze_count,
        autoanalyze_count,
        CASE
            WHEN GREATEST(last_vacuum, last_autovacuum) IS NULL THEN NULL
            ELSE ROUND(
                EXTRACT(EPOCH FROM (now() - GREATEST(last_vacuum, last_autovacuum))) / 3600,
                1
            )
        END AS hours_since_vacuum,
        CASE WHEN relid IS NULL THEN NULL ELSE pg_size_pretty(pg_relation_size(relid)) END
            AS table_size,
        CASE WHEN relid IS NULL THEN NULL ELSE pg_size_pretty(pg_indexes_size(relid)) END
            AS index_size,
        CASE WHEN relid IS NULL THEN NULL ELSE pg_size_pretty(pg_total_relation_size(relid)) END
            AS total_size
    FROM relation_data
)
SELECT
    table_name,
    CASE
        WHEN relid IS NULL THEN 'missing'
        WHEN dead_tuple_ratio >= :'dead_tuple_critical_ratio'::numeric THEN 'critical_dead_tuple_ratio'
        WHEN hours_since_vacuum >= :'vacuum_critical_hours'::numeric THEN 'critical_vacuum_age'
        WHEN dead_tuple_ratio >= :'dead_tuple_warn_ratio'::numeric THEN 'warning_dead_tuple_ratio'
        WHEN hours_since_vacuum >= :'vacuum_warn_hours'::numeric THEN 'warning_vacuum_age'
        ELSE 'ok'
    END AS status,
    live_tuple_count,
    dead_tuple_count,
    dead_tuple_ratio,
    last_vacuum_at,
    last_analyze_at,
    hours_since_vacuum,
    vacuum_count,
    autovacuum_count,
    analyze_count,
    autoanalyze_count,
    table_size,
    index_size,
    total_size
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

echo "[transaction-read-model-bloat] target tables: transaction_read_model, transaction_read_model_archive"
echo "[transaction-read-model-bloat] dead tuple ratio: warn>=${dead_tuple_warn_ratio} critical>=${dead_tuple_critical_ratio}"
echo "[transaction-read-model-bloat] vacuum age hours: warn>=${vacuum_warn_hours} critical>=${vacuum_critical_hours}"

psql_args=(--no-psqlrc --set ON_ERROR_STOP=1 --pset pager=off)
psql_vars=(
  --set "dead_tuple_warn_ratio=${dead_tuple_warn_ratio}"
  --set "dead_tuple_critical_ratio=${dead_tuple_critical_ratio}"
  --set "vacuum_warn_hours=${vacuum_warn_hours}"
  --set "vacuum_critical_hours=${vacuum_critical_hours}"
)

if [[ -n "${DATABASE_URL:-}" ]]; then
  psql "${psql_args[@]}" "${psql_vars[@]}" "${DATABASE_URL}" --command "${sql}"
else
  export PGHOST="${PGHOST:-${DB_HOST:-localhost}}"
  export PGPORT="${PGPORT:-${DB_PORT:-5432}}"
  export PGDATABASE="${PGDATABASE:-${DB_NAME:-aquila_bank}}"
  export PGUSER="${PGUSER:-${DB_USERNAME:-postgres}}"
  if [[ -z "${PGPASSWORD:-}" && -n "${DB_PASSWORD:-}" ]]; then
    export PGPASSWORD="${DB_PASSWORD}"
  fi
  psql "${psql_args[@]}" "${psql_vars[@]}" --command "${sql}"
fi
