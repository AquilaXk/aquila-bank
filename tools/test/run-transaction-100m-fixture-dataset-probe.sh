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
  FIXTURE_DATASET_DB_GATE              true|false, default true
  FIXTURE_DATASET_PROBE_DB_FALLBACK    true|false, default false
  FIXTURE_DATASET_MIN_TOTAL_ROWS       default manifest total_rows or 100000000
  FIXTURE_DATASET_MIN_HOT_ROWS         default manifest hot_rows or 1
  FIXTURE_DATASET_MIN_ARCHIVE_ROWS     default manifest archive_rows or 1
  FIXTURE_DATASET_MIN_WINDOW_ROWS      default 51

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
db_gate="${FIXTURE_DATASET_DB_GATE:-true}"
db_fallback="${FIXTURE_DATASET_PROBE_DB_FALLBACK:-false}"
min_total_rows="${FIXTURE_DATASET_MIN_TOTAL_ROWS:-}"
min_hot_rows="${FIXTURE_DATASET_MIN_HOT_ROWS:-}"
min_archive_rows="${FIXTURE_DATASET_MIN_ARCHIVE_ROWS:-}"
min_window_rows="${FIXTURE_DATASET_MIN_WINDOW_ROWS:-51}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}")

require_bool_value "FIXTURE_DATASET_DB_GATE" "${db_gate}"
require_bool_value "FIXTURE_DATASET_PROBE_DB_FALLBACK" "${db_fallback}"

require_non_negative_integer_value() {
  local key="$1"
  local value="$2"
  if [[ -n "${value}" && ! "${value}" =~ ^[0-9]+$ ]]; then
    echo "${key} must be zero or a positive integer: ${value}" >&2
    exit 1
  fi
}

require_positive_integer_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${key} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_non_negative_integer_value "FIXTURE_DATASET_MIN_TOTAL_ROWS" "${min_total_rows}"
require_non_negative_integer_value "FIXTURE_DATASET_MIN_HOT_ROWS" "${min_hot_rows}"
require_non_negative_integer_value "FIXTURE_DATASET_MIN_ARCHIVE_ROWS" "${min_archive_rows}"
require_positive_integer_value "FIXTURE_DATASET_MIN_WINDOW_ROWS" "${min_window_rows}"

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

manifest_integer_or_default() {
  local key="$1"
  local fallback="$2"
  local value
  value="$(manifest_value "${key}")"
  if [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${value}"
    return 0
  fi
  echo "${fallback}"
}

db_scalar() {
  local sql="$1"
  "${psql_base[@]}" --no-align --tuples-only --command "${sql}" | tr -d '[:space:]'
}

table_estimate() {
  local table="$1"
  db_scalar "
    WITH leaf AS (
      SELECT relid
      FROM pg_partition_tree('public.${table}'::regclass)
      WHERE isleaf
    )
    SELECT COALESCE(SUM(GREATEST(item.reltuples, 0))::bigint, 0)
    FROM leaf
    JOIN pg_class item ON item.oid = leaf.relid;
  "
}

window_count() {
  local table="$1"
  local account_id="$2"
  local from="$3"
  local to="$4"
  db_scalar "
    SELECT count(*)
    FROM public.${table}
    WHERE account_id = '${account_id}'
      AND booked_at >= '${from}'::timestamptz
      AND booked_at < '${to}'::timestamptz;
  "
}

partition_name_for_month() {
  local table="$1"
  local timestamp="$2"
  local month
  month="$(sed -E 's/^([0-9]{4})-([0-9]{2}).*$/\1\2/' <<<"${timestamp}")"
  if ! [[ "${month}" =~ ^[0-9]{6}$ ]]; then
    echo "invalid timestamp for monthly partition check: ${timestamp}" >&2
    exit 1
  fi
  echo "${table}_y${month}"
}

assert_partition_exists() {
  local table="$1"
  local timestamp="$2"
  local partition exists
  partition="$(partition_name_for_month "${table}" "${timestamp}")"
  exists="$(db_scalar "SELECT to_regclass('public.${partition}') IS NOT NULL;")"
  if [[ "${exists}" != "t" ]]; then
    echo "dataset probe monthly partition is missing: ${partition}" >&2
    exit 1
  fi
}

assert_integer_at_least() {
  local label="$1"
  local actual="$2"
  local minimum="$3"
  if ! [[ "${actual}" =~ ^[0-9]+$ ]]; then
    echo "dataset probe ${label} returned invalid count: ${actual}" >&2
    exit 1
  fi
  if ((actual < minimum)); then
    echo "dataset probe ${label} below minimum: actual=${actual} min=${minimum}" >&2
    exit 1
  fi
}

assert_dataset_db_gate() {
  local hot_account_id="$1"
  local hot_from="$2"
  local hot_to="$3"
  local cold_account_id="$4"
  local cold_from="$5"
  local cold_to="$6"
  if [[ "${db_gate}" != "true" ]]; then
    echo "[transaction-100m-dataset-probe] db gate skipped"
    return 0
  fi

  local total_min hot_min archive_min
  local hot_estimate archive_estimate total_estimate hot_count cold_count
  total_min="${min_total_rows:-$(manifest_integer_or_default total_rows 100000000)}"
  hot_min="${min_hot_rows:-$(manifest_integer_or_default hot_rows 1)}"
  archive_min="${min_archive_rows:-$(manifest_integer_or_default archive_rows 1)}"

  # parent reltuples는 partition leaf 합산 estimate만 사용해 1억 row full count를 피합니다.
  hot_estimate="$(table_estimate transaction_read_model)"
  archive_estimate="$(table_estimate transaction_read_model_archive)"
  assert_integer_at_least hot_estimate "${hot_estimate}" 0
  assert_integer_at_least archive_estimate "${archive_estimate}" 0
  total_estimate=$((hot_estimate + archive_estimate))
  assert_integer_at_least hot_estimate "${hot_estimate}" "${hot_min}"
  assert_integer_at_least archive_estimate "${archive_estimate}" "${archive_min}"
  assert_integer_at_least total_estimate "${total_estimate}" "${total_min}"

  hot_count="$(window_count transaction_read_model "${hot_account_id}" "${hot_from}" "${hot_to}")"
  cold_count="$(window_count transaction_read_model_archive "${cold_account_id}" "${cold_from}" "${cold_to}")"
  assert_integer_at_least hot_window_count "${hot_count}" "${min_window_rows}"
  assert_integer_at_least cold_window_count "${cold_count}" "${min_window_rows}"
  assert_partition_exists transaction_read_model "${hot_from}"
  assert_partition_exists transaction_read_model_archive "${cold_from}"

  echo "[transaction-100m-dataset-probe] db gate hot_estimate=${hot_estimate} archive_estimate=${archive_estimate} hot_window_count=${hot_count} cold_window_count=${cold_count}"
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
  echo "[transaction-100m-dataset-probe] db_gate=${db_gate}"
  echo "[transaction-100m-dataset-probe] db_fallback=${db_fallback}"
  echo "[transaction-100m-dataset-probe] min_total_rows=${min_total_rows:-manifest-total_rows-or-100000000}"
  echo "[transaction-100m-dataset-probe] min_hot_rows=${min_hot_rows:-manifest-hot_rows-or-1}"
  echo "[transaction-100m-dataset-probe] min_archive_rows=${min_archive_rows:-manifest-archive_rows-or-1}"
  echo "[transaction-100m-dataset-probe] min_window_rows=${min_window_rows}"
  echo "[transaction-100m-dataset-probe] db_gate_checks=estimate,window-count,monthly-partition"
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
  assert_dataset_db_gate "${hot_account_id}" "${hot_from}" "${hot_to}" "${cold_account_id}" "${cold_from}" "${cold_to}"
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
