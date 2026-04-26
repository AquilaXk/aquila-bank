#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-fixture-restore.sh [--print-plan]

Environment:
  FIXTURE_MODE              verify|dump|restore, default verify
  FIXTURE_NAME              default transaction-100m-fixture
  FIXTURE_RESTORE_TRUNCATE  must be true for restore
USAGE
}

if [[ "${1:-}" == "--print-plan" ]]; then
  print_plan=true
  shift
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
else
  print_plan=false
fi
[[ "$#" -eq 0 ]] || { usage; exit 1; }

fixture_mode="${FIXTURE_MODE:-verify}"
fixture_name="${FIXTURE_NAME:-transaction-100m-fixture}"
fixture_dir="${FIXTURE_DIR:-build/fixtures}"
fixture_path="${FIXTURE_PATH:-${fixture_dir}/${fixture_name}.dump}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
container_dump_path="/tmp/${fixture_name}.dump"

case "${fixture_mode}" in
  verify|dump|restore) ;;
  *) echo "FIXTURE_MODE must be verify, dump, or restore" >&2; exit 1 ;;
esac

echo "[transaction-fixture-restore] fixture=${fixture_name}"
echo "[transaction-fixture-restore] mode=${fixture_mode}"
echo "[transaction-fixture-restore] dump=${fixture_path}"
echo "[transaction-fixture-restore] modes=verify,dump,restore"

if [[ "${print_plan}" == "true" ]]; then
  exit 0
fi

if [[ "${fixture_mode}" == "restore" && "${FIXTURE_RESTORE_TRUNCATE:-false}" != "true" ]]; then
  echo "FIXTURE_RESTORE_TRUNCATE=true is required for restore" >&2
  exit 1
fi

mkdir -p "${fixture_dir}"

if [[ "${fixture_mode}" == "verify" ]]; then
  docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 \
    -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}" \
    --no-align --tuples-only --command "
      SELECT to_regclass('public.transaction_read_model') IS NOT NULL
          AND to_regclass('public.transaction_read_model_archive') IS NOT NULL
          AND to_regclass('public.idx_transaction_read_model_account_cursor') IS NOT NULL
          AND to_regclass('public.idx_transaction_read_model_archive_account_cursor') IS NOT NULL;"
elif [[ "${fixture_mode}" == "dump" ]]; then
  docker compose "${compose_files[@]}" exec -T postgres pg_dump \
    -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}" \
    --format=custom \
    --file="${container_dump_path}" \
    --table=public.transaction_read_model \
    --table=public.transaction_read_model_archive
  docker cp "aquila-bank-postgres:${container_dump_path}" "${fixture_path}"
else
  test -s "${fixture_path}" || { echo "fixture dump not found: ${fixture_path}" >&2; exit 1; }
  docker cp "${fixture_path}" "aquila-bank-postgres:${container_dump_path}"
  docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 \
    -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}" \
    --command "TRUNCATE public.transaction_read_model, public.transaction_read_model_archive;"
  docker compose "${compose_files[@]}" exec -T postgres pg_restore \
    -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}" \
    --jobs="${FIXTURE_RESTORE_JOBS:-2}" \
    "${container_dump_path}"
fi
