#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/ops/transaction-read-model-chunk-lifecycle.sh [options]

Options:
  --action <precreate|retention-plan|detach|drop-detached|analyze|vacuum-analyze>
  --target <hot|archive|both>              default both, detach supports archive only
  --reference-month <YYYY-MM>              default current UTC month, precreate start month
  --months-ahead <count>                   default 4, number of months to precreate
  --before-month <YYYY-MM>                 required for retention-plan/detach/drop-detached
  --lock-timeout-ms <milliseconds>         default 1000
  --statement-timeout-ms <milliseconds>    default 30000
  --print-sql                             print SQL and exit
  --dry-run                               same as --print-sql
  -h, --help                              show this help

Environment:
  CONFIRM_DROP   must be `drop-detached-transaction-read-model` for drop-detached execution
  DATABASE_URL   optional PostgreSQL URL. If set, it is passed directly to psql.
  DB_HOST        fallback host, default localhost
  DB_PORT        fallback port, default 5432
  DB_NAME        fallback database, default aquila_bank
  DB_USERNAME    fallback user, default postgres
  DB_PASSWORD    fallback password, mapped to PGPASSWORD when PGPASSWORD is unset
USAGE
}

action=""
target="both"
reference_month="$(date -u +%Y-%m)"
months_ahead="${MONTHS_AHEAD:-4}"
before_month=""
lock_timeout_ms="${LOCK_TIMEOUT_MS:-1000}"
statement_timeout_ms="${STATEMENT_TIMEOUT_MS:-30000}"
print_sql=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --action)
      [[ $# -ge 2 ]] || {
        usage
        exit 1
      }
      action="$2"
      shift 2
      ;;
    --target)
      [[ $# -ge 2 ]] || {
        usage
        exit 1
      }
      target="$2"
      shift 2
      ;;
    --reference-month)
      [[ $# -ge 2 ]] || {
        usage
        exit 1
      }
      reference_month="$2"
      shift 2
      ;;
    --months-ahead)
      [[ $# -ge 2 ]] || {
        usage
        exit 1
      }
      months_ahead="$2"
      shift 2
      ;;
    --before-month)
      [[ $# -ge 2 ]] || {
        usage
        exit 1
      }
      before_month="$2"
      shift 2
      ;;
    --lock-timeout-ms)
      [[ $# -ge 2 ]] || {
        usage
        exit 1
      }
      lock_timeout_ms="$2"
      shift 2
      ;;
    --statement-timeout-ms)
      [[ $# -ge 2 ]] || {
        usage
        exit 1
      }
      statement_timeout_ms="$2"
      shift 2
      ;;
    --print-sql | --dry-run)
      print_sql=true
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
done

fail() {
  echo "$*" >&2
  exit 1
}

validate_positive_integer() {
  local name="$1"
  local value="$2"
  [[ "${value}" =~ ^[1-9][0-9]*$ ]] || fail "${name} must be a positive integer"
}

validate_month() {
  local name="$1"
  local value="$2"
  [[ "${value}" =~ ^[0-9]{4}-(0[1-9]|1[0-2])$ ]] || fail "${name} must use YYYY-MM"
}

case "${action}" in
  precreate | retention-plan | detach | drop-detached | analyze | vacuum-analyze) ;;
  "")
    fail "--action is required"
    ;;
  *)
    fail "invalid action: ${action}"
    ;;
esac

case "${target}" in
  hot | archive | both) ;;
  *)
    fail "invalid target: ${target}"
    ;;
esac

validate_month "reference-month" "${reference_month}"
validate_positive_integer "months-ahead" "${months_ahead}"
validate_positive_integer "lock-timeout-ms" "${lock_timeout_ms}"
validate_positive_integer "statement-timeout-ms" "${statement_timeout_ms}"

case "${action}" in
  retention-plan | detach | drop-detached)
    [[ -n "${before_month}" ]] || fail "--before-month is required for ${action}"
    validate_month "before-month" "${before_month}"
    ;;
esac

if [[ "${action}" == "detach" && "${target}" != "archive" ]]; then
  fail "detach only supports --target archive; hot chunks must be emptied by row retention first"
fi

if [[ "${action}" == "drop-detached" && "${target}" == "hot" ]]; then
  fail "drop-detached only supports archive detached chunks"
fi

if [[ "${action}" == "drop-detached" && "${print_sql}" != "true" ]]; then
  [[ "${CONFIRM_DROP:-}" == "drop-detached-transaction-read-model" ]] \
    || fail "set CONFIRM_DROP=drop-detached-transaction-read-model to execute drop-detached"
fi

target_parent_values() {
  case "${target}" in
    hot)
      printf "('transaction_read_model')"
      ;;
    archive)
      printf "('transaction_read_model_archive')"
      ;;
    both)
      printf "('transaction_read_model'),\n        ('transaction_read_model_archive')"
      ;;
  esac
}

sql_header() {
  printf "SET lock_timeout = '%sms';\n" "${lock_timeout_ms}"
  printf "SET statement_timeout = '%sms';\n" "${statement_timeout_ms}"
}

build_precreate_sql() {
  local parents
  parents="$(target_parent_values)"
  cat <<SQL
$(sql_header)
DO \$\$
DECLARE
    reference_month DATE := DATE '${reference_month}-01';
    month_count INTEGER := ${months_ahead};
    month_offset INTEGER;
    month_start DATE;
    parent_name TEXT;
    parent_regclass REGCLASS;
    partition_name TEXT;
BEGIN
    FOR parent_name IN
        SELECT value
        FROM (VALUES
        ${parents}
        ) AS target(value)
    LOOP
        parent_regclass := to_regclass('public.' || parent_name);
        IF parent_regclass IS NULL THEN
            RAISE EXCEPTION 'missing transaction read model parent table: %', parent_name;
        END IF;

        FOR month_offset IN 0..(month_count - 1) LOOP
            month_start := reference_month + make_interval(months => month_offset);
            partition_name := parent_name || '_y' || to_char(month_start, 'YYYYMM');
            -- 다음 달 partition은 적재 전에 생성해 t3.micro에서 사후 대형 index build를 피합니다.
            EXECUTE format(
                'CREATE TABLE IF NOT EXISTS %I PARTITION OF %s FOR VALUES FROM (%L) TO (%L)',
                partition_name,
                parent_regclass,
                month_start::timestamptz,
                (month_start + INTERVAL '1 month')::timestamptz
            );
        END LOOP;
    END LOOP;
END \$\$;
SQL
}

build_retention_plan_sql() {
  local parents
  parents="$(target_parent_values)"
  cat <<SQL
$(sql_header)
WITH target_parent(parent_name) AS (
    VALUES
        ${parents}
),
partition_data AS (
    SELECT
        parent.relname AS parent_name,
        child.relname AS partition_name,
        ns.nspname AS partition_schema,
        to_date(substring(child.relname FROM '_y([0-9]{6})$'), 'YYYYMM') AS month_start,
        to_date(substring(child.relname FROM '_y([0-9]{6})$'), 'YYYYMM') + INTERVAL '1 month'
            AS month_end,
        GREATEST(child.reltuples, 0)::bigint AS row_estimate,
        pg_size_pretty(pg_total_relation_size(child.oid)) AS total_size
    FROM target_parent target
    JOIN pg_class parent
      ON parent.relname = target.parent_name
    JOIN pg_namespace parent_ns
      ON parent_ns.oid = parent.relnamespace
     AND parent_ns.nspname = 'public'
    JOIN pg_inherits inherits
      ON inherits.inhparent = parent.oid
    JOIN pg_class child
      ON child.oid = inherits.inhrelid
    JOIN pg_namespace ns
      ON ns.oid = child.relnamespace
    WHERE child.relname <> parent.relname || '_default'
      AND child.relname ~ '_y[0-9]{6}$'
)
SELECT
    parent_name,
    partition_schema,
    partition_name,
    month_start,
    month_end,
    row_estimate,
    total_size,
    CASE
        WHEN parent_name = 'transaction_read_model_archive'
             AND month_end <= DATE '${before_month}-01' THEN 'detach_archive_candidate'
        WHEN parent_name = 'transaction_read_model'
             AND month_end <= DATE '${before_month}-01' THEN 'run_row_retention_before_hot_detach'
        ELSE 'keep'
    END AS recommended_action
FROM partition_data
WHERE month_end <= DATE '${before_month}-01'
ORDER BY parent_name, month_start;
SQL
}

build_detach_sql() {
  cat <<SQL
$(sql_header)
CREATE SCHEMA IF NOT EXISTS transaction_read_model_detached;
DO \$\$
DECLARE
    before_month DATE := DATE '${before_month}-01';
    item RECORD;
BEGIN
    FOR item IN
        SELECT
            child.oid AS child_oid,
            child.relname AS partition_name,
            to_date(substring(child.relname FROM '_y([0-9]{6})$'), 'YYYYMM') + INTERVAL '1 month'
                AS month_end
        FROM pg_inherits inherits
        JOIN pg_class parent
          ON parent.oid = inherits.inhparent
        JOIN pg_namespace parent_ns
          ON parent_ns.oid = parent.relnamespace
         AND parent_ns.nspname = 'public'
        JOIN pg_class child
          ON child.oid = inherits.inhrelid
        JOIN pg_namespace child_ns
          ON child_ns.oid = child.relnamespace
         AND child_ns.nspname = 'public'
        WHERE parent.relname = 'transaction_read_model_archive'
          AND child.relname ~ '^transaction_read_model_archive_y[0-9]{6}$'
          AND to_date(substring(child.relname FROM '_y([0-9]{6})$'), 'YYYYMM') + INTERVAL '1 month'
              <= before_month
        ORDER BY child.relname
    LOOP
        -- archive partition만 detach합니다. hot partition은 row retention으로 먼저 비워야 합니다.
        EXECUTE format('ALTER TABLE public.transaction_read_model_archive DETACH PARTITION %s', item.child_oid::regclass);
        EXECUTE format('ALTER TABLE %s SET SCHEMA transaction_read_model_detached', item.child_oid::regclass);
    END LOOP;
END \$\$;
SQL
}

build_drop_detached_sql() {
  cat <<SQL
$(sql_header)
DO \$\$
DECLARE
    before_month DATE := DATE '${before_month}-01';
    item RECORD;
BEGIN
    FOR item IN
        SELECT
            child.relname AS partition_name,
            to_date(substring(child.relname FROM '_y([0-9]{6})$'), 'YYYYMM') + INTERVAL '1 month'
                AS month_end
        FROM pg_class child
        JOIN pg_namespace ns
          ON ns.oid = child.relnamespace
         AND ns.nspname = 'transaction_read_model_detached'
        WHERE child.relkind = 'r'
          AND child.relname ~ '^transaction_read_model_archive_y[0-9]{6}$'
          AND to_date(substring(child.relname FROM '_y([0-9]{6})$'), 'YYYYMM') + INTERVAL '1 month'
              <= before_month
        ORDER BY child.relname
    LOOP
        EXECUTE format('DROP TABLE transaction_read_model_detached.%I', item.partition_name);
    END LOOP;
END \$\$;
SQL
}

build_maintenance_sql() {
  local parents command
  parents="$(target_parent_values)"
  if [[ "${action}" == "analyze" ]]; then
    command="ANALYZE VERBOSE"
  else
    command="VACUUM (ANALYZE, VERBOSE)"
  fi
  cat <<SQL
$(sql_header)
WITH target_parent(parent_name) AS (
    VALUES
        ${parents}
),
partition_data AS (
    SELECT child.oid::regclass AS partition_regclass
    FROM target_parent target
    JOIN pg_class parent
      ON parent.relname = target.parent_name
    JOIN pg_namespace parent_ns
      ON parent_ns.oid = parent.relnamespace
     AND parent_ns.nspname = 'public'
    JOIN pg_inherits inherits
      ON inherits.inhparent = parent.oid
    JOIN pg_class child
      ON child.oid = inherits.inhrelid
    WHERE child.relname <> parent.relname || '_default'
      AND child.relname ~ '_y[0-9]{6}$'
)
SELECT format('${command} %s;', partition_regclass)
FROM partition_data
ORDER BY partition_regclass::text
\\gexec
SQL
}

case "${action}" in
  precreate)
    sql="$(build_precreate_sql)"
    ;;
  retention-plan)
    sql="$(build_retention_plan_sql)"
    ;;
  detach)
    sql="$(build_detach_sql)"
    ;;
  drop-detached)
    sql="$(build_drop_detached_sql)"
    ;;
  analyze | vacuum-analyze)
    sql="$(build_maintenance_sql)"
    ;;
esac

if [[ "${print_sql}" == "true" ]]; then
  printf '%s\n' "${sql}"
  exit 0
fi

if ! command -v psql >/dev/null 2>&1; then
  fail "psql is required. Install PostgreSQL client tools or use --print-sql."
fi

echo "[transaction-chunk-lifecycle] action=${action} target=${target}"
echo "[transaction-chunk-lifecycle] lock_timeout=${lock_timeout_ms}ms statement_timeout=${statement_timeout_ms}ms"

psql_args=(--no-psqlrc --set ON_ERROR_STOP=1 --pset pager=off)

if [[ -n "${DATABASE_URL:-}" ]]; then
  printf '%s\n' "${sql}" | psql "${psql_args[@]}" "${DATABASE_URL}"
else
  export PGHOST="${PGHOST:-${DB_HOST:-localhost}}"
  export PGPORT="${PGPORT:-${DB_PORT:-5432}}"
  export PGDATABASE="${PGDATABASE:-${DB_NAME:-aquila_bank}}"
  export PGUSER="${PGUSER:-${DB_USERNAME:-postgres}}"
  if [[ -z "${PGPASSWORD:-}" && -n "${DB_PASSWORD:-}" ]]; then
    export PGPASSWORD="${DB_PASSWORD}"
  fi
  printf '%s\n' "${sql}" | psql "${psql_args[@]}"
fi
