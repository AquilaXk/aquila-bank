#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-fixture-dataset-probe.sh [--print-plan|--dry-run]

Environment:
  FIXTURE_NAME                         default transaction-100m-fixture
  FIXTURE_PATH                         default build/fixtures/<FIXTURE_NAME>.dump
  FIXTURE_MANIFEST_PATH                default <FIXTURE_PATH>.manifest
  FIXTURE_DATASET_ENV_PATH             default <FIXTURE_PATH>.dataset.env
  FIXTURE_DATASET_PROBE_DB_FALLBACK    true|false, default false

Manifest keys:
  hot_account_id hot_from hot_to cold_account_id cold_from cold_to
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --dry-run)
      mode="dry-run"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

require_bool_value() {
  local key="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${key} must be true or false: ${value}" >&2
    exit 1
  fi
}

fixture_name="${FIXTURE_NAME:-transaction-100m-fixture}"
fixture_dir="${FIXTURE_DIR:-build/fixtures}"
fixture_path="${FIXTURE_PATH:-${fixture_dir}/${fixture_name}.dump}"
manifest_path="${FIXTURE_MANIFEST_PATH:-${fixture_path}.manifest}"
dataset_env_path="${FIXTURE_DATASET_ENV_PATH:-${fixture_path}.dataset.env}"
db_fallback="${FIXTURE_DATASET_PROBE_DB_FALLBACK:-false}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}")

require_bool_value "FIXTURE_DATASET_PROBE_DB_FALLBACK" "${db_fallback}"

manifest_value() {
  local key="$1"
  if [[ ! -s "${manifest_path}" ]]; then
    return 0
  fi
  awk -F '=' -v key="${key}" '$1 == key {print $2}' "${manifest_path}" | tail -1
}

require_value() {
  local key="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "dataset probe ${key} is missing" >&2
    exit 1
  fi
}

resolve_manifest_or_env() {
  local manifest_key="$1"
  local env_key="$2"
  local value
  value="$(manifest_value "${manifest_key}")"
  if [[ -n "${value}" ]]; then
    echo "${value}"
    return 0
  fi
  echo "${!env_key:-}"
}

probe_from_db() {
  local table="$1"
  local account_id="$2"
  local from_to
  from_to="$(
    "${psql_base[@]}" --no-align --tuples-only --field-separator='|' --command "
      SELECT MIN(booked_at), MAX(booked_at)
      FROM public.${table}
      WHERE account_id = '${account_id}';
    "
  )"
  tr -d '[:space:]' <<<"${from_to}"
}

write_dataset_env() {
  local hot_account_id="$1"
  local hot_from="$2"
  local hot_to="$3"
  local cold_account_id="$4"
  local cold_from="$5"
  local cold_to="$6"
  mkdir -p "$(dirname "${dataset_env_path}")"
  {
    echo "K6_HOT_ACCOUNT_ID=${hot_account_id}"
    echo "K6_HOT_FROM=${hot_from}"
    echo "K6_HOT_TO=${hot_to}"
    echo "K6_COLD_ACCOUNT_ID=${cold_account_id}"
    echo "K6_COLD_FROM=${cold_from}"
    echo "K6_COLD_TO=${cold_to}"
  } >"${dataset_env_path}"
  echo "[transaction-100m-dataset-probe] dataset env written=${dataset_env_path}"
}

print_plan() {
  echo "[transaction-100m-dataset-probe] mode=${mode}"
  echo "[transaction-100m-dataset-probe] fixture=${fixture_name}"
  echo "[transaction-100m-dataset-probe] dump=${fixture_path}"
  echo "[transaction-100m-dataset-probe] manifest=${manifest_path}"
  echo "[transaction-100m-dataset-probe] dataset_env=${dataset_env_path}"
  echo "[transaction-100m-dataset-probe] db_fallback=${db_fallback}"
  echo "[transaction-100m-dataset-probe] source_order=manifest,env,db-fallback"
}

run_probe() {
  local hot_account_id hot_from hot_to cold_account_id cold_from cold_to
  hot_account_id="$(resolve_manifest_or_env hot_account_id K6_HOT_ACCOUNT_ID)"
  hot_from="$(resolve_manifest_or_env hot_from K6_HOT_FROM)"
  hot_to="$(resolve_manifest_or_env hot_to K6_HOT_TO)"
  cold_account_id="$(resolve_manifest_or_env cold_account_id K6_COLD_ACCOUNT_ID)"
  cold_from="$(resolve_manifest_or_env cold_from K6_COLD_FROM)"
  cold_to="$(resolve_manifest_or_env cold_to K6_COLD_TO)"

  if [[ "${db_fallback}" == "true" ]]; then
    if [[ -n "${hot_account_id}" && ( -z "${hot_from}" || -z "${hot_to}" ) ]]; then
      IFS='|' read -r hot_from hot_to <<<"$(probe_from_db transaction_read_model "${hot_account_id}")"
    fi
    if [[ -n "${cold_account_id}" && ( -z "${cold_from}" || -z "${cold_to}" ) ]]; then
      IFS='|' read -r cold_from cold_to <<<"$(probe_from_db transaction_read_model_archive "${cold_account_id}")"
    fi
  fi

  require_value hot_account_id "${hot_account_id}"
  require_value hot_from "${hot_from}"
  require_value hot_to "${hot_to}"
  require_value cold_account_id "${cold_account_id}"
  require_value cold_from "${cold_from}"
  require_value cold_to "${cold_to}"
  write_dataset_env "${hot_account_id}" "${hot_from}" "${hot_to}" "${cold_account_id}" "${cold_from}" "${cold_to}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  echo "write ${dataset_env_path} from manifest/env metadata"
  if [[ "${db_fallback}" == "true" ]]; then
    echo "db fallback enabled for missing windows"
  fi
  exit 0
fi

run_probe
