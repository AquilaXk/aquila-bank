#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/ops/transaction-read-model-vacuum-analyze.sh [options]

Options:
  --target <hot|archive|both>          default both
  --mode <vacuum-analyze|analyze>      default vacuum-analyze
  --lock-timeout-ms <milliseconds>     default 1000
  --statement-timeout-ms <milliseconds> default 30000
  --print-sql                         print SQL and exit
  --dry-run                           same as --print-sql
  -h, --help                          show this help

Environment:
  DATABASE_URL   optional PostgreSQL URL. If set, it is passed directly to psql.
  DB_HOST        fallback host, default localhost
  DB_PORT        fallback port, default 5432
  DB_NAME        fallback database, default aquila_bank
  DB_USERNAME    fallback user, default postgres
  DB_PASSWORD    fallback password, mapped to PGPASSWORD when PGPASSWORD is unset
USAGE
}

target="both"
mode="vacuum-analyze"
lock_timeout_ms="${LOCK_TIMEOUT_MS:-1000}"
statement_timeout_ms="${STATEMENT_TIMEOUT_MS:-30000}"
print_sql=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target)
      if [[ $# -lt 2 ]]; then
        usage
        exit 1
      fi
      target="$2"
      shift 2
      ;;
    --mode)
      if [[ $# -lt 2 ]]; then
        usage
        exit 1
      fi
      mode="$2"
      shift 2
      ;;
    --lock-timeout-ms)
      if [[ $# -lt 2 ]]; then
        usage
        exit 1
      fi
      lock_timeout_ms="$2"
      shift 2
      ;;
    --statement-timeout-ms)
      if [[ $# -lt 2 ]]; then
        usage
        exit 1
      fi
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

case "${target}" in
  hot)
    tables=(transaction_read_model)
    ;;
  archive)
    tables=(transaction_read_model_archive)
    ;;
  both)
    tables=(transaction_read_model transaction_read_model_archive)
    ;;
  *)
    echo "invalid target: ${target}" >&2
    usage
    exit 1
    ;;
esac

case "${mode}" in
  vacuum-analyze | analyze)
    ;;
  *)
    echo "invalid mode: ${mode}" >&2
    usage
    exit 1
    ;;
esac

if ! [[ "${lock_timeout_ms}" =~ ^[1-9][0-9]*$ ]]; then
  echo "lock timeout must be a positive integer millisecond value" >&2
  exit 1
fi

if ! [[ "${statement_timeout_ms}" =~ ^[1-9][0-9]*$ ]]; then
  echo "statement timeout must be a positive integer millisecond value" >&2
  exit 1
fi

commands=(
  "SET lock_timeout = '${lock_timeout_ms}ms';"
  "SET statement_timeout = '${statement_timeout_ms}ms';"
)

for table in "${tables[@]}"; do
  if [[ "${mode}" == "vacuum-analyze" ]]; then
    commands+=("VACUUM (ANALYZE, VERBOSE) public.${table};")
  else
    commands+=("ANALYZE VERBOSE public.${table};")
  fi
done

if [[ "${print_sql}" == "true" ]]; then
  printf '%s\n' "${commands[@]}"
  exit 0
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required. Install PostgreSQL client tools or use --print-sql." >&2
  exit 1
fi

echo "[transaction-vacuum-analyze] target=${target} mode=${mode}"
echo "[transaction-vacuum-analyze] lock_timeout=${lock_timeout_ms}ms statement_timeout=${statement_timeout_ms}ms"
echo "[transaction-vacuum-analyze] tables=${tables[*]}"

psql_args=(--no-psqlrc --set ON_ERROR_STOP=1 --pset pager=off)
psql_commands=()
for command in "${commands[@]}"; do
  psql_commands+=(--command "${command}")
done

if [[ -n "${DATABASE_URL:-}" ]]; then
  psql "${psql_args[@]}" "${psql_commands[@]}" "${DATABASE_URL}"
else
  export PGHOST="${PGHOST:-${DB_HOST:-localhost}}"
  export PGPORT="${PGPORT:-${DB_PORT:-5432}}"
  export PGDATABASE="${PGDATABASE:-${DB_NAME:-aquila_bank}}"
  export PGUSER="${PGUSER:-${DB_USERNAME:-postgres}}"
  if [[ -z "${PGPASSWORD:-}" && -n "${DB_PASSWORD:-}" ]]; then
    export PGPASSWORD="${DB_PASSWORD}"
  fi
  psql "${psql_args[@]}" "${psql_commands[@]}"
fi
