#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-fixture-dataset-probe.sh [--print-plan|--dry-run|--assert-estimate]

Environment:
  FIXTURE_NAME                         default transaction-100m-fixture
  FIXTURE_PATH                         default build/fixtures/<FIXTURE_NAME>.dump
  FIXTURE_MANIFEST_PATH                default <FIXTURE_PATH>.manifest
  FIXTURE_DATASET_ENV_PATH             default <FIXTURE_PATH>.dataset.env
  FIXTURE_DATASET_DB_REPORT_PATH       default <FIXTURE_PATH>.db-gate.env
  FIXTURE_DATASET_DB_GATE              true|false, default true
  FIXTURE_DATASET_PROBE_DB_FALLBACK    true|false, default false
  FIXTURE_DATASET_MIN_TOTAL_ROWS       default manifest total_rows or 100000000
  FIXTURE_DATASET_MIN_HOT_ROWS         default manifest hot_rows or 1
  FIXTURE_DATASET_MIN_ARCHIVE_ROWS     default manifest archive_rows or 1
  FIXTURE_DATASET_MIN_WINDOW_ROWS      default 51
  FIXTURE_DATASET_ESTIMATE_TOLERANCE_ROWS default 1000
  FIXTURE_DATASET_QUERY_STATEMENT_TIMEOUT_MS default 3000
  FIXTURE_DATASET_QUERY_LOCK_TIMEOUT_MS default 1000
  FIXTURE_DATASET_QUERY_WORK_MEM       default 2MB
  FIXTURE_DATASET_QUERY_TEMP_FILE_LIMIT default 8MB
  FIXTURE_DATASET_RUN_ID               default timestamp
  FIXTURE_DATASET_DB_FAILURE_REPORT_PATH default <FIXTURE_PATH>.db-gate-<run-id>.failure.env
  FIXTURE_DATASET_RECOVERY_ON_FAILURE  true|false, default true
  FIXTURE_DATASET_RECOVERY_WAIT_SECONDS default 90

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
    --assert-estimate)
      mode="assert-estimate"
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
db_report_path="${FIXTURE_DATASET_DB_REPORT_PATH:-${fixture_path}.db-gate.env}"
db_gate="${FIXTURE_DATASET_DB_GATE:-true}"
db_fallback="${FIXTURE_DATASET_PROBE_DB_FALLBACK:-false}"
min_total_rows="${FIXTURE_DATASET_MIN_TOTAL_ROWS:-}"
min_hot_rows="${FIXTURE_DATASET_MIN_HOT_ROWS:-}"
min_archive_rows="${FIXTURE_DATASET_MIN_ARCHIVE_ROWS:-}"
min_window_rows="${FIXTURE_DATASET_MIN_WINDOW_ROWS:-51}"
estimate_tolerance_rows="${FIXTURE_DATASET_ESTIMATE_TOLERANCE_ROWS:-1000}"
query_statement_timeout_ms="${FIXTURE_DATASET_QUERY_STATEMENT_TIMEOUT_MS:-3000}"
query_lock_timeout_ms="${FIXTURE_DATASET_QUERY_LOCK_TIMEOUT_MS:-1000}"
query_work_mem="${FIXTURE_DATASET_QUERY_WORK_MEM:-2MB}"
query_temp_file_limit="${FIXTURE_DATASET_QUERY_TEMP_FILE_LIMIT:-8MB}"
run_id="${FIXTURE_DATASET_RUN_ID:-$(date +%Y-%m-%d-%H%M%S)}"
db_failure_report_path="${FIXTURE_DATASET_DB_FAILURE_REPORT_PATH:-${fixture_path}.db-gate-${run_id}.failure.env}"
recovery_on_failure="${FIXTURE_DATASET_RECOVERY_ON_FAILURE:-true}"
recovery_wait_seconds="${FIXTURE_DATASET_RECOVERY_WAIT_SECONDS:-90}"
postgres_container_name="${FIXTURE_POSTGRES_CONTAINER_NAME:-${LOADTEST_POSTGRES_CONTAINER_NAME:-aquila-bank-postgres-loadtest}}"
assert_estimate_label="${FIXTURE_DATASET_ASSERT_LABEL:-estimate}"
assert_estimate_actual="${FIXTURE_DATASET_ASSERT_ACTUAL:-}"
assert_estimate_minimum="${FIXTURE_DATASET_ASSERT_MINIMUM:-}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql --quiet -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}")

require_bool_value "FIXTURE_DATASET_DB_GATE" "${db_gate}"
require_bool_value "FIXTURE_DATASET_PROBE_DB_FALLBACK" "${db_fallback}"
require_bool_value "FIXTURE_DATASET_RECOVERY_ON_FAILURE" "${recovery_on_failure}"

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
require_non_negative_integer_value "FIXTURE_DATASET_ESTIMATE_TOLERANCE_ROWS" "${estimate_tolerance_rows}"
require_positive_integer_value "FIXTURE_DATASET_QUERY_STATEMENT_TIMEOUT_MS" "${query_statement_timeout_ms}"
require_positive_integer_value "FIXTURE_DATASET_QUERY_LOCK_TIMEOUT_MS" "${query_lock_timeout_ms}"
require_non_negative_integer_value "FIXTURE_DATASET_RECOVERY_WAIT_SECONDS" "${recovery_wait_seconds}"
require_postgres_memory_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*(kB|KB|MB|GB)$ ]]; then
    echo "${key} must use PostgreSQL memory units such as 2048kB or 2MB: ${value}" >&2
    exit 1
  fi
}

require_postgres_memory_value "FIXTURE_DATASET_QUERY_WORK_MEM" "${query_work_mem}"
require_postgres_memory_value "FIXTURE_DATASET_QUERY_TEMP_FILE_LIMIT" "${query_temp_file_limit}"

sanitize_report_value() {
  tr '\n\r' '  ' | sed -E 's/[[:space:]]+/ /g; s/^ //; s/ $//'
}

manifest_value() {
  local key="$1"
  if [[ ! -s "${manifest_path}" ]]; then
    return 0
  fi
  awk -F '=' -v key="${key}" '$1 == key {print $2}' "${manifest_path}" | tail -1
}

dataset_env_value() {
  local key="$1"
  if [[ ! -s "${dataset_env_path}" ]]; then
    return 0
  fi
  awk -F '=' -v key="${key}" '$1 == key {print $2}' "${dataset_env_path}" | tail -1
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
  value="${!env_key:-}"
  if [[ -n "${value}" ]]; then
    echo "${value}"
    return 0
  fi
  dataset_env_value "${env_key}"
}

probe_from_db() {
  local table="$1"
  local account_id="$2"
  local from_to
  from_to="$(db_tuple "
      SELECT MIN(booked_at), MAX(booked_at)
      FROM public.${table}
      WHERE account_id = '${account_id}';
    " "db_fallback_window:${table}")"
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

guarded_sql() {
  local sql="$1"
  printf "BEGIN READ ONLY;\nSET LOCAL statement_timeout = '%sms';\nSET LOCAL lock_timeout = '%sms';\nSET LOCAL work_mem = '%s';\nSET LOCAL temp_file_limit = '%s';\n%s\nCOMMIT;\n" \
    "${query_statement_timeout_ms}" \
    "${query_lock_timeout_ms}" \
    "${query_work_mem}" \
    "${query_temp_file_limit}" \
    "${sql}"
}

db_scalar() {
  local sql="$1"
  local stage="${2:-db_scalar}"
  local output
  local status
  set +e
  output="$("${psql_base[@]}" --no-align --tuples-only --command "$(guarded_sql "${sql}")" 2>&1)"
  status=$?
  set -e
  if ((status != 0)); then
    handle_db_gate_query_failure "${stage}" "${status}" "${output}"
  fi
  awk 'NF {line=$0} END {gsub(/[[:space:]]/, "", line); print line}' <<<"${output}"
}

db_tuple() {
  local sql="$1"
  local stage="${2:-db_tuple}"
  local output
  local status
  set +e
  output="$("${psql_base[@]}" --no-align --tuples-only --field-separator='|' --command "$(guarded_sql "${sql}")" 2>&1)"
  status=$?
  set -e
  if ((status != 0)); then
    handle_db_gate_query_failure "${stage}" "${status}" "${output}"
  fi
  awk 'NF {line=$0} END {gsub(/[[:space:]]/, "", line); print line}' <<<"${output}"
}

write_db_gate_failure_report() {
  local stage="$1"
  local status="$2"
  local message="$3"
  mkdir -p "$(dirname "${db_failure_report_path}")"
  {
    echo "FIXTURE_DATASET_DB_GATE_STATUS=failed"
    echo "FIXTURE_DATASET_RUN_ID=${run_id}"
    echo "FIXTURE_DATASET_DB_FAILURE_STAGE=${stage}"
    echo "FIXTURE_DATASET_DB_FAILURE_EXIT_STATUS=${status}"
    echo "FIXTURE_DATASET_DB_FAILURE_MESSAGE=$(sanitize_report_value <<<"${message}")"
    echo "FIXTURE_DATASET_DB_REPORT_PATH=${db_report_path}"
    echo "FIXTURE_DATASET_DB_FAILURE_REPORT_PATH=${db_failure_report_path}"
    echo "FIXTURE_DATASET_QUERY_STATEMENT_TIMEOUT_MS=${query_statement_timeout_ms}"
    echo "FIXTURE_DATASET_QUERY_LOCK_TIMEOUT_MS=${query_lock_timeout_ms}"
    echo "FIXTURE_DATASET_QUERY_WORK_MEM=${query_work_mem}"
    echo "FIXTURE_DATASET_QUERY_TEMP_FILE_LIMIT=${query_temp_file_limit}"
    echo "FIXTURE_DATASET_RECOVERY_ON_FAILURE=${recovery_on_failure}"
    echo "FIXTURE_DATASET_RECOVERY_WAIT_SECONDS=${recovery_wait_seconds}"
  } >"${db_failure_report_path}"
  echo "[transaction-100m-dataset-probe] db gate failure report written=${db_failure_report_path}" >&2
}

wait_for_postgres_recovery_after_failure() {
  if [[ "${recovery_on_failure}" != "true" ]]; then
    echo "[transaction-100m-dataset-probe] recovery wait after failure skipped" >&2
    return 0
  fi

  local deadline=$((SECONDS + recovery_wait_seconds))
  local state running restarting oom_killed status in_recovery last_error
  while ((SECONDS <= deadline)); do
    if ! state="$(docker inspect "${postgres_container_name}" --format '{{.State.Running}} {{.State.Restarting}} {{.State.OOMKilled}} {{.State.Status}}' 2>/dev/null)"; then
      last_error="PostgreSQL container not found: ${postgres_container_name}"
    else
      read -r running restarting oom_killed status <<<"${state}"
      if [[ "${oom_killed}" == "true" ]]; then
        echo "[transaction-100m-dataset-probe] PostgreSQL OOMKilled=true after DB gate failure" >&2
        return 1
      fi
      if [[ "${running}" == "true" && "${restarting}" == "false" && "${status}" == "running" ]]; then
        if in_recovery="$("${psql_base[@]}" --no-align --tuples-only --command "SELECT pg_is_in_recovery();" 2>/dev/null)"; then
          in_recovery="$(tr -d '[:space:]' <<<"${in_recovery}")"
          if [[ "${in_recovery}" != "t" ]]; then
            echo "[transaction-100m-dataset-probe] postgres recovery closed after DB gate failure" >&2
            return 0
          fi
          last_error="pg_is_in_recovery()=true"
        else
          last_error="pg_is_in_recovery query failed"
        fi
      else
        last_error="Running=${running:-unknown} Restarting=${restarting:-unknown} Status=${status:-unknown}"
      fi
    fi
    echo "[transaction-100m-dataset-probe] waiting postgres recovery after DB gate failure: ${last_error}" >&2
    sleep 5
  done
  echo "[transaction-100m-dataset-probe] PostgreSQL recovery wait exceeded after DB gate failure: seconds=${recovery_wait_seconds}" >&2
  return 1
}

handle_db_gate_query_failure() {
  local stage="$1"
  local status="$2"
  local output="$3"
  write_db_gate_failure_report "${stage}" "${status}" "${output}"
  wait_for_postgres_recovery_after_failure || true
  echo "dataset probe DB gate query failed: stage=${stage} status=${status} failure_report=${db_failure_report_path}" >&2
  exit 1
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
  " "table_estimate:${table}"
}

index_only_window_probe() {
  local table="$1"
  local account_id="$2"
  local from="$3"
  local to="$4"
  # account cursor index 순서로 필요한 최소 sample까지만 읽어 full window count를 피합니다.
  db_scalar "
    SELECT count(*)
    FROM (
      SELECT 1
      FROM public.${table}
      WHERE account_id = '${account_id}'
        AND booked_at >= '${from}'::timestamptz
        AND booked_at < '${to}'::timestamptz
      ORDER BY booked_at DESC, id DESC
      LIMIT ${min_window_rows}
    ) sample;
  " "index_only_window_probe:${table}"
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
  exists="$(db_scalar "SELECT to_regclass('public.${partition}') IS NOT NULL;" "partition_exists:${partition}")"
  if [[ "${exists}" != "t" ]]; then
    echo "dataset probe monthly partition is missing: ${partition}" >&2
    exit 1
  fi
  echo "${partition}"
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

assert_estimate_at_least() {
  local label="$1"
  local actual="$2"
  local minimum="$3"
  local tolerance="${estimate_tolerance_rows}"
  local allowed_min
  if ! [[ "${actual}" =~ ^[0-9]+$ ]]; then
    echo "dataset probe ${label} returned invalid estimate: ${actual}" >&2
    exit 1
  fi
  if ((minimum <= tolerance)); then
    tolerance=0
  fi
  allowed_min=$((minimum - tolerance))
  if ((allowed_min < 0)); then
    allowed_min=0
  fi
  if ((actual < allowed_min)); then
    echo "dataset probe ${label} below estimate tolerance: actual=${actual} min=${minimum} tolerance_rows=${tolerance} allowed_min=${allowed_min}" >&2
    exit 1
  fi
  if ((actual < minimum)); then
    echo "[transaction-100m-dataset-probe] ${label} accepted within estimate tolerance: actual=${actual} min=${minimum} tolerance_rows=${tolerance}"
  fi
}

write_db_gate_report() {
  local hot_min="$1"
  local archive_min="$2"
  local total_min="$3"
  local hot_estimate="$4"
  local archive_estimate="$5"
  local total_estimate="$6"
  local hot_count="$7"
  local cold_count="$8"
  local hot_partition="$9"
  local cold_partition="${10}"
  mkdir -p "$(dirname "${db_report_path}")"
  {
    echo "FIXTURE_DATASET_DB_GATE=passed"
    echo "FIXTURE_DATASET_DB_GATE_STATUS=passed"
    echo "FIXTURE_DATASET_RUN_ID=${run_id}"
    echo "FIXTURE_DATASET_TOTAL_MIN=${total_min}"
    echo "FIXTURE_DATASET_HOT_MIN=${hot_min}"
    echo "FIXTURE_DATASET_ARCHIVE_MIN=${archive_min}"
    echo "FIXTURE_DATASET_TOTAL_ESTIMATE=${total_estimate}"
    echo "FIXTURE_DATASET_HOT_ESTIMATE=${hot_estimate}"
    echo "FIXTURE_DATASET_ARCHIVE_ESTIMATE=${archive_estimate}"
    echo "FIXTURE_DATASET_ESTIMATE_TOLERANCE_ROWS=${estimate_tolerance_rows}"
    echo "FIXTURE_DATASET_HOT_WINDOW_COUNT=${hot_count}"
    echo "FIXTURE_DATASET_COLD_WINDOW_COUNT=${cold_count}"
    echo "FIXTURE_DATASET_MIN_WINDOW_ROWS=${min_window_rows}"
    echo "FIXTURE_DATASET_HOT_PARTITION=${hot_partition}"
    echo "FIXTURE_DATASET_COLD_PARTITION=${cold_partition}"
    echo "FIXTURE_DATASET_QUERY_STATEMENT_TIMEOUT_MS=${query_statement_timeout_ms}"
    echo "FIXTURE_DATASET_QUERY_LOCK_TIMEOUT_MS=${query_lock_timeout_ms}"
    echo "FIXTURE_DATASET_QUERY_WORK_MEM=${query_work_mem}"
    echo "FIXTURE_DATASET_QUERY_TEMP_FILE_LIMIT=${query_temp_file_limit}"
    echo "FIXTURE_DATASET_QUERY_READ_ONLY=true"
    echo "FIXTURE_DATASET_WINDOW_PROBE_MODE=index-only-bounded"
    echo "FIXTURE_DATASET_WINDOW_PROBE_LIMIT=${min_window_rows}"
  } >"${db_report_path}"
  echo "[transaction-100m-dataset-probe] db gate report written=${db_report_path}"
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
  local hot_estimate archive_estimate total_estimate hot_count cold_count hot_partition cold_partition
  total_min="${min_total_rows:-$(manifest_integer_or_default total_rows 100000000)}"
  hot_min="${min_hot_rows:-$(manifest_integer_or_default hot_rows 1)}"
  archive_min="${min_archive_rows:-$(manifest_integer_or_default archive_rows 1)}"

  # parent reltuples는 partition leaf 합산 estimate만 사용해 1억 row full count를 피합니다.
  hot_estimate="$(table_estimate transaction_read_model)"
  archive_estimate="$(table_estimate transaction_read_model_archive)"
  assert_integer_at_least hot_estimate "${hot_estimate}" 0
  assert_integer_at_least archive_estimate "${archive_estimate}" 0
  total_estimate=$((hot_estimate + archive_estimate))
  assert_estimate_at_least hot_estimate "${hot_estimate}" "${hot_min}"
  assert_estimate_at_least archive_estimate "${archive_estimate}" "${archive_min}"
  assert_estimate_at_least total_estimate "${total_estimate}" "${total_min}"

  hot_count="$(index_only_window_probe transaction_read_model "${hot_account_id}" "${hot_from}" "${hot_to}")"
  cold_count="$(index_only_window_probe transaction_read_model_archive "${cold_account_id}" "${cold_from}" "${cold_to}")"
  assert_integer_at_least hot_window_count "${hot_count}" "${min_window_rows}"
  assert_integer_at_least cold_window_count "${cold_count}" "${min_window_rows}"
  hot_partition="$(assert_partition_exists transaction_read_model "${hot_from}")"
  cold_partition="$(assert_partition_exists transaction_read_model_archive "${cold_from}")"
  write_db_gate_report "${hot_min}" "${archive_min}" "${total_min}" "${hot_estimate}" "${archive_estimate}" "${total_estimate}" "${hot_count}" "${cold_count}" "${hot_partition}" "${cold_partition}"

  echo "[transaction-100m-dataset-probe] db gate hot_estimate=${hot_estimate} archive_estimate=${archive_estimate} total_estimate=${total_estimate} hot_window_count=${hot_count} cold_window_count=${cold_count}"
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
  echo "[transaction-100m-dataset-probe] db_report=${db_report_path}"
  echo "[transaction-100m-dataset-probe] db_failure_report=${db_failure_report_path}"
  echo "[transaction-100m-dataset-probe] db_gate=${db_gate}"
  echo "[transaction-100m-dataset-probe] db_fallback=${db_fallback}"
  echo "[transaction-100m-dataset-probe] run_id=${run_id}"
  echo "[transaction-100m-dataset-probe] min_total_rows=${min_total_rows:-manifest-total_rows-or-100000000}"
  echo "[transaction-100m-dataset-probe] min_hot_rows=${min_hot_rows:-manifest-hot_rows-or-1}"
  echo "[transaction-100m-dataset-probe] min_archive_rows=${min_archive_rows:-manifest-archive_rows-or-1}"
  echo "[transaction-100m-dataset-probe] min_window_rows=${min_window_rows}"
  echo "[transaction-100m-dataset-probe] estimate_tolerance_rows=${estimate_tolerance_rows}"
  echo "[transaction-100m-dataset-probe] query_statement_timeout_ms=${query_statement_timeout_ms}"
  echo "[transaction-100m-dataset-probe] query_lock_timeout_ms=${query_lock_timeout_ms}"
  echo "[transaction-100m-dataset-probe] query_work_mem=${query_work_mem}"
  echo "[transaction-100m-dataset-probe] query_temp_file_limit=${query_temp_file_limit}"
  echo "[transaction-100m-dataset-probe] query_read_only=true"
  echo "[transaction-100m-dataset-probe] recovery_on_failure=${recovery_on_failure}"
  echo "[transaction-100m-dataset-probe] recovery_wait_seconds=${recovery_wait_seconds}"
  echo "[transaction-100m-dataset-probe] window_probe_mode=index-only-bounded"
  echo "[transaction-100m-dataset-probe] window_probe_limit=${min_window_rows}"
  echo "[transaction-100m-dataset-probe] db_gate_checks=partition-estimate-tolerance,index-only-bounded-window-probe,monthly-partition"
  echo "[transaction-100m-dataset-probe] source_order=manifest,env,dataset-env,db-fallback"
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
if [[ "${mode}" == "assert-estimate" ]]; then
  require_value FIXTURE_DATASET_ASSERT_ACTUAL "${assert_estimate_actual}"
  require_value FIXTURE_DATASET_ASSERT_MINIMUM "${assert_estimate_minimum}"
  assert_estimate_at_least "${assert_estimate_label}" "${assert_estimate_actual}" "${assert_estimate_minimum}"
  exit 0
fi

run_probe
