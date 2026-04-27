#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-fixture-restore.sh [--print-plan]

Environment:
  FIXTURE_MODE              verify|dump|restore, default verify
  FIXTURE_NAME              default transaction-100m-fixture
  FIXTURE_RECOVERY_PREFLIGHT default true
  FIXTURE_RECOVERY_WAIT_SECONDS default 180
  FIXTURE_REQUIRE_DUMP      require local dump artifact before mode, default false; restore always requires it
  FIXTURE_WRITE_MANIFEST    write manifest/checksum after dump, default true
  FIXTURE_ARTIFACT_VERIFY   verify manifest/checksum before restore, default false
  FIXTURE_VERIFY_MIN_ROWS   sampled minimum rows for verify, default 0
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
fixture_recovery_preflight="${FIXTURE_RECOVERY_PREFLIGHT:-true}"
fixture_recovery_wait_seconds="${FIXTURE_RECOVERY_WAIT_SECONDS:-180}"
fixture_require_dump="${FIXTURE_REQUIRE_DUMP:-false}"
fixture_write_manifest="${FIXTURE_WRITE_MANIFEST:-true}"
fixture_artifact_verify="${FIXTURE_ARTIFACT_VERIFY:-false}"
fixture_verify_min_rows="${FIXTURE_VERIFY_MIN_ROWS:-0}"
postgres_container_name="${FIXTURE_POSTGRES_CONTAINER_NAME:-${LOADTEST_POSTGRES_CONTAINER_NAME:-aquila-bank-postgres-loadtest}}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
container_dump_path="/tmp/${fixture_name}.dump"

case "${fixture_mode}" in
  verify|dump|restore) ;;
  *) echo "FIXTURE_MODE must be verify, dump, or restore" >&2; exit 1 ;;
esac
case "${fixture_recovery_preflight}" in
  true|false) ;;
  *) echo "FIXTURE_RECOVERY_PREFLIGHT must be true or false" >&2; exit 1 ;;
esac
if ! [[ "${fixture_recovery_wait_seconds}" =~ ^[0-9]+$ ]]; then
  echo "FIXTURE_RECOVERY_WAIT_SECONDS must be zero or a positive integer" >&2
  exit 1
fi
case "${fixture_require_dump}" in
  true|false) ;;
  *) echo "FIXTURE_REQUIRE_DUMP must be true or false" >&2; exit 1 ;;
esac
case "${fixture_write_manifest}" in
  true|false) ;;
  *) echo "FIXTURE_WRITE_MANIFEST must be true or false" >&2; exit 1 ;;
esac
case "${fixture_artifact_verify}" in
  true|false) ;;
  *) echo "FIXTURE_ARTIFACT_VERIFY must be true or false" >&2; exit 1 ;;
esac
if ! [[ "${fixture_verify_min_rows}" =~ ^[0-9]+$ ]]; then
  echo "FIXTURE_VERIFY_MIN_ROWS must be zero or a positive integer" >&2
  exit 1
fi
if [[ "${fixture_mode}" == "restore" ]]; then
  fixture_require_dump="true"
fi

echo "[transaction-fixture-restore] fixture=${fixture_name}"
echo "[transaction-fixture-restore] mode=${fixture_mode}"
echo "[transaction-fixture-restore] dump=${fixture_path}"
echo "[transaction-fixture-restore] recovery_preflight=${fixture_recovery_preflight}"
echo "[transaction-fixture-restore] recovery_wait_seconds=${fixture_recovery_wait_seconds}"
echo "[transaction-fixture-restore] postgres_container=${postgres_container_name}"
echo "[transaction-fixture-restore] require_dump=${fixture_require_dump}"
echo "[transaction-fixture-restore] write_manifest=${fixture_write_manifest}"
echo "[transaction-fixture-restore] artifact_verify=${fixture_artifact_verify}"
echo "[transaction-fixture-restore] verify_min_rows=${fixture_verify_min_rows}"
echo "[transaction-fixture-restore] modes=verify,dump,restore"

if [[ "${print_plan}" == "true" ]]; then
  exit 0
fi

if [[ "${fixture_mode}" == "restore" && "${FIXTURE_RESTORE_TRUNCATE:-false}" != "true" ]]; then
  echo "FIXTURE_RESTORE_TRUNCATE=true is required for restore" >&2
  exit 1
fi

mkdir -p "${fixture_dir}"

assert_fixture_dump_present() {
  test -s "${fixture_path}" || { echo "fixture dump not found: ${fixture_path}" >&2; exit 1; }
}

wait_for_postgres_recovery() {
  local state running restarting oom_killed status
  local in_recovery last_error elapsed deadline
  local started_at="${SECONDS}"
  deadline=$((SECONDS + fixture_recovery_wait_seconds))
  while ((SECONDS <= deadline)); do
    if ! state="$(docker inspect "${postgres_container_name}" --format '{{.State.Running}} {{.State.Restarting}} {{.State.OOMKilled}} {{.State.Status}}' 2>/dev/null)"; then
      last_error="PostgreSQL container not found: ${postgres_container_name}"
    else
      read -r running restarting oom_killed status <<<"${state}"
      if [[ "${oom_killed}" == "true" ]]; then
        echo "PostgreSQL container has OOMKilled=true. Recreate or restore from fixture dump before retry." >&2
        exit 1
      fi
      if [[ "${running}" == "true" && "${restarting}" == "false" && "${status}" == "running" ]]; then
        if in_recovery="$(
          docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 \
            -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}" \
            --no-align --tuples-only --command "SELECT pg_is_in_recovery();" 2>&1
        )"; then
          in_recovery="$(tr -d '[:space:]' <<<"${in_recovery}")"
          if [[ "${in_recovery}" != "t" ]]; then
            elapsed=$((SECONDS - started_at))
            echo "[transaction-fixture-restore] postgres recovery closed elapsed_seconds=${elapsed}"
            return 0
          fi
          last_error="pg_is_in_recovery()=true"
        else
          last_error="${in_recovery}"
        fi
      else
        last_error="Running=${running:-unknown} Restarting=${restarting:-unknown} Status=${status:-unknown}"
      fi
    fi
    echo "[transaction-fixture-restore] waiting postgres recovery: ${last_error}" >&2
    sleep 5
  done
  echo "PostgreSQL recovery wait exceeded: seconds=${fixture_recovery_wait_seconds}" >&2
  echo "${last_error:-no final state}" >&2
  docker logs --tail 80 "${postgres_container_name}" >&2 || true
  exit 1
}

assert_postgres_recovery_safe() {
  if [[ "${fixture_recovery_preflight}" != "true" ]]; then
    echo "[transaction-fixture-restore] recovery preflight skipped"
    return 0
  fi
  wait_for_postgres_recovery
}

verify_fixture_schema() {
  docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 \
    -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}" \
    --no-align --tuples-only --command "
      SELECT to_regclass('public.transaction_read_model') IS NOT NULL
          AND to_regclass('public.transaction_read_model_archive') IS NOT NULL
          AND to_regclass('public.idx_transaction_read_model_account_cursor') IS NOT NULL
          AND to_regclass('public.idx_transaction_read_model_archive_account_cursor') IS NOT NULL;"
}

verify_fixture_rows() {
  if [[ "${fixture_verify_min_rows}" -eq 0 ]]; then
    return 0
  fi

  local sampled_rows
  sampled_rows="$(
    docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 \
      -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}" \
      --no-align --tuples-only --command "
        SELECT count(*)
        FROM (
          SELECT 1 FROM public.transaction_read_model
          UNION ALL
          SELECT 1 FROM public.transaction_read_model_archive
          LIMIT ${fixture_verify_min_rows}
        ) rows;"
  )"
  sampled_rows="$(tr -d '[:space:]' <<<"${sampled_rows}")"
  echo "[transaction-fixture-restore] transaction_read_model rows sampled=${sampled_rows} min=${fixture_verify_min_rows}"
  if ! [[ "${sampled_rows}" =~ ^[0-9]+$ ]] || ((sampled_rows < fixture_verify_min_rows)); then
    echo "transaction_read_model rows below fixture verify minimum: sampled=${sampled_rows} min=${fixture_verify_min_rows}" >&2
    exit 1
  fi
}

if [[ "${fixture_require_dump}" == "true" ]]; then
  assert_fixture_dump_present
fi

assert_postgres_recovery_safe

if [[ "${fixture_mode}" == "verify" ]]; then
  schema_ready="$(verify_fixture_schema)"
  echo "${schema_ready}"
  if [[ "$(tr -d '[:space:]' <<<"${schema_ready}")" != "t" ]]; then
    echo "transaction read fixture schema/index preflight failed" >&2
    exit 1
  fi
  verify_fixture_rows
elif [[ "${fixture_mode}" == "dump" ]]; then
  docker compose "${compose_files[@]}" exec -T postgres pg_dump \
    -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}" \
    --format=custom \
    --file="${container_dump_path}" \
    --table=public.transaction_read_model \
    --table=public.transaction_read_model_archive
  docker cp "${postgres_container_name}:${container_dump_path}" "${fixture_path}"
  assert_fixture_dump_present
  if [[ "${fixture_write_manifest}" == "true" ]]; then
    FIXTURE_NAME="${fixture_name}" \
    FIXTURE_PATH="${fixture_path}" \
      tools/test/validate-transaction-100m-fixture-artifact.sh --write-manifest
  fi
else
  assert_fixture_dump_present
  if [[ "${fixture_artifact_verify}" == "true" ]]; then
    FIXTURE_NAME="${fixture_name}" \
    FIXTURE_PATH="${fixture_path}" \
      tools/test/validate-transaction-100m-fixture-artifact.sh --verify
  fi
  docker cp "${fixture_path}" "${postgres_container_name}:${container_dump_path}"
  docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 \
    -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}" \
    --command "TRUNCATE public.transaction_read_model, public.transaction_read_model_archive;"
  docker compose "${compose_files[@]}" exec -T postgres pg_restore \
    -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}" \
    --jobs="${FIXTURE_RESTORE_JOBS:-2}" \
    "${container_dump_path}"
fi
