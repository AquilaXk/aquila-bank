#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-page-limit-sensitivity.sh [--print-plan]

Required runtime environment for actual runs:
  K6_HOT_ACCOUNT_ID
  K6_HOT_FROM
  K6_HOT_TO
  K6_COLD_ACCOUNT_ID
  K6_COLD_FROM
  K6_COLD_TO

Optional environment:
  PAGE_LIMIT_SENSITIVITY_NAME       default transaction-read-page-limit-<timestamp>
  PAGE_LIMIT_SENSITIVITY_LIMITS     default 20,50,100,200
  PAGE_LIMIT_SENSITIVITY_DURATION   default 1m
  PAGE_LIMIT_SENSITIVITY_VUS        default 8
  PAGE_LIMIT_SENSITIVITY_PREFLIGHT  default true
  PAGE_LIMIT_SENSITIVITY_CONTINUE_ON_FAILURE default false
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
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

benchmark_name="${PAGE_LIMIT_SENSITIVITY_NAME:-transaction-read-page-limit-$(date +%Y-%m-%d-%H%M%S)}"
limits_csv="${PAGE_LIMIT_SENSITIVITY_LIMITS:-20,50,100,200}"
duration="${PAGE_LIMIT_SENSITIVITY_DURATION:-1m}"
vus="${PAGE_LIMIT_SENSITIVITY_VUS:-8}"
preflight="${PAGE_LIMIT_SENSITIVITY_PREFLIGHT:-true}"
continue_on_failure="${PAGE_LIMIT_SENSITIVITY_CONTINUE_ON_FAILURE:-false}"
runner="tools/test/run-k6-transaction-100m-loadtest.sh"
report_dir="build/reports/k6/${benchmark_name}"
summary_tsv="${report_dir}/page-limit-sensitivity-summary.tsv"

require_bool_value() {
  local key="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${key} must be true or false: ${value}" >&2
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

require_duration_value() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*(s|m|h)$ ]]; then
    echo "${key} must use a positive duration such as 60s, 30m, or 1h: ${value}" >&2
    exit 1
  fi
}

validate_limits() {
  if [[ -z "${limits_csv}" ]]; then
    echo "PAGE_LIMIT_SENSITIVITY_LIMITS must not be empty" >&2
    exit 1
  fi
  IFS=',' read -r -a limit_values <<<"${limits_csv}"
  local value
  for value in "${limit_values[@]}"; do
    require_positive_integer_value "PAGE_LIMIT_SENSITIVITY_LIMITS" "${value}"
  done
}

require_bool_value "PAGE_LIMIT_SENSITIVITY_PREFLIGHT" "${preflight}"
require_bool_value "PAGE_LIMIT_SENSITIVITY_CONTINUE_ON_FAILURE" "${continue_on_failure}"
require_positive_integer_value "PAGE_LIMIT_SENSITIVITY_VUS" "${vus}"
require_duration_value "PAGE_LIMIT_SENSITIVITY_DURATION" "${duration}"
validate_limits

print_plan() {
  echo "[transaction-read-page-limit-sensitivity] benchmark=${benchmark_name}"
  echo "[transaction-read-page-limit-sensitivity] limits=${limits_csv}"
  echo "[transaction-read-page-limit-sensitivity] duration=${duration}"
  echo "[transaction-read-page-limit-sensitivity] vus=${vus}"
  echo "[transaction-read-page-limit-sensitivity] preflight=${preflight}"
  echo "[transaction-read-page-limit-sensitivity] continue_on_failure=${continue_on_failure}"
  echo "[transaction-read-page-limit-sensitivity] runner=${runner}"
  echo "[transaction-read-page-limit-sensitivity] summary=${summary_tsv}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

mkdir -p "${report_dir}" build/reports/k6

metric_from_json() {
  local json_path="$1"
  local metric="$2"
  local value_name="$3"
  if [[ ! -f "${json_path}" ]] || ! command -v jq >/dev/null 2>&1; then
    echo "n/a"
    return 0
  fi
  jq -r --arg metric "${metric}" --arg value_name "${value_name}" \
    '.metrics[$metric].values[$value_name] // "n/a"' "${json_path}"
}

write_header() {
  printf "limit\tstatus\thttp_failed_rate\thttp_reqs\ttransaction_429_rate\thot_first_p95_ms\thot_first_p99_ms\thot_first_max_ms\thot_cursor_p95_ms\thot_cursor_p99_ms\thot_cursor_max_ms\tcold_first_p95_ms\tcold_first_p99_ms\tcold_first_max_ms\tcold_cursor_p95_ms\tcold_cursor_p99_ms\tcold_cursor_max_ms\tlog_path\tsummary_json\n" >"${summary_tsv}"
}

append_summary() {
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" "$@" >>"${summary_tsv}"
}

run_limit() {
  local limit="$1"
  local first_run="$2"
  local report_name="${benchmark_name}-limit-${limit}"
  local log_path="${report_dir}/${report_name}.log"
  local summary_json="build/reports/k6/${report_name}-summary.json"
  local args=()
  local status

  # 첫 run에서 runtime을 올리고 이후에는 같은 fixture/runtime에서 limit만 바꿉니다.
  if [[ "${first_run}" != "true" ]]; then
    args+=(--no-up --no-deps)
  fi

  set +e
  K6_REPORT_NAME="${report_name}" \
  K6_LIMIT="${limit}" \
  K6_VUS="${vus}" \
  K6_DURATION="${duration}" \
  K6_PREFLIGHT="${preflight}" \
  K6_ARCHIVE_RESULTS=false \
    "${runner}" "${args[@]}" >"${log_path}" 2>&1
  status=$?
  set -e

  append_summary \
    "${limit}" "${status}" \
    "$(metric_from_json "${summary_json}" "http_req_failed" "rate")" \
    "$(metric_from_json "${summary_json}" "http_reqs" "count")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_429_rate" "rate")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_hot_first_ms" "p(95)")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_hot_first_ms" "p(99)")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_hot_first_ms" "max")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_hot_cursor_ms" "p(95)")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_hot_cursor_ms" "p(99)")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_hot_cursor_ms" "max")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_cold_first_ms" "p(95)")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_cold_first_ms" "p(99)")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_cold_first_ms" "max")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_cold_cursor_ms" "p(95)")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_cold_cursor_ms" "p(99)")" \
    "$(metric_from_json "${summary_json}" "aquila_transaction_cold_cursor_ms" "max")" \
    "${log_path}" "${summary_json}"

  if [[ "${status}" -ne 0 && "${continue_on_failure}" != "true" ]]; then
    echo "[transaction-read-page-limit-sensitivity] limit=${limit} failed; see ${log_path}" >&2
    exit "${status}"
  fi
}

write_header
IFS=',' read -r -a limit_values <<<"${limits_csv}"
first_run=true
for limit in "${limit_values[@]}"; do
  run_limit "${limit}" "${first_run}"
  first_run=false
done

echo "[transaction-read-page-limit-sensitivity] summary=${summary_tsv}"
