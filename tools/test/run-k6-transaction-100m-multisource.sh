#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-k6-transaction-100m-multisource.sh [--print-plan|--no-up] [--no-deps]

Environment:
  K6_MULTI_SOURCE_NAME                       default transaction-read-multisource-<timestamp>
  K6_MULTI_SOURCE_CONTEXTS                   required comma-separated Docker contexts, at least 2
  K6_MULTI_SOURCE_BASE_URLS                  required comma-separated backend URLs; one URL may be shared by all contexts
  K6_MULTI_SOURCE_PROMETHEUS_RW_SERVER_URLS  optional comma-separated remote-write URLs; one URL may be shared by all contexts
  K6_MULTI_SOURCE_REMOTE_WORKDIRS            optional comma-separated repo paths; one path may be shared by all contexts
  K6_MULTI_SOURCE_RUN_ID_PREFIX              default K6_MULTI_SOURCE_NAME
  K6_MULTI_SOURCE_OUTPUT_DIR                 default build/reports/k6/<name>
  K6_MULTI_SOURCE_PARALLEL                   run shards in parallel, default true

Pass-through environment such as K6_DURATION, K6_WORKLOAD_SHAPE, K6_VUS, and account/date
fixture values is forwarded to tools/test/run-k6-transaction-100m-loadtest.sh.
USAGE
}

mode="run"
child_args=()
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --no-up|--no-deps)
      child_args+=("$1")
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

name="${K6_MULTI_SOURCE_NAME:-transaction-read-multisource-$(date +%Y-%m-%d-%H%M%S)}"
contexts_csv="${K6_MULTI_SOURCE_CONTEXTS:-}"
base_urls_csv="${K6_MULTI_SOURCE_BASE_URLS:-}"
prometheus_urls_csv="${K6_MULTI_SOURCE_PROMETHEUS_RW_SERVER_URLS:-}"
workdirs_csv="${K6_MULTI_SOURCE_REMOTE_WORKDIRS:-}"
run_id_prefix="${K6_MULTI_SOURCE_RUN_ID_PREFIX:-${name}}"
output_dir="${K6_MULTI_SOURCE_OUTPUT_DIR:-build/reports/k6/${name}}"
parallel="${K6_MULTI_SOURCE_PARALLEL:-true}"
child_runner="tools/test/run-k6-transaction-100m-loadtest.sh"
default_workdir="$(pwd)"
contexts=()
base_urls=()
prometheus_urls=()
workdirs=()

split_csv() {
  local value="$1"
  local target="$2"
  local items=()
  local item
  if [[ -n "${value}" ]]; then
    IFS=',' read -r -a items <<<"${value}"
  fi
  if [[ "${#items[@]}" -gt 0 ]]; then
    for item in "${items[@]}"; do
      if [[ -z "${item}" ]]; then
        echo "${target} must not contain empty entries" >&2
        exit 1
      fi
    done
  fi
  case "${target}" in
    contexts)
      if [[ "${#items[@]}" -eq 0 ]]; then contexts=(); else contexts=("${items[@]}"); fi
      ;;
    base_urls)
      if [[ "${#items[@]}" -eq 0 ]]; then base_urls=(); else base_urls=("${items[@]}"); fi
      ;;
    prometheus_urls)
      if [[ "${#items[@]}" -eq 0 ]]; then prometheus_urls=(); else prometheus_urls=("${items[@]}"); fi
      ;;
    workdirs)
      if [[ "${#items[@]}" -eq 0 ]]; then workdirs=(); else workdirs=("${items[@]}"); fi
      ;;
    *)
      echo "unknown split target: ${target}" >&2
      exit 1
      ;;
  esac
}

validate_boolean() {
  local name="$1"
  local value="$2"
  case "${value}" in
    true|false)
      ;;
    *)
      echo "${name} must be true or false: ${value}" >&2
      exit 1
      ;;
  esac
}

validate_required_lists() {
  split_csv "${contexts_csv}" contexts
  split_csv "${base_urls_csv}" base_urls
  split_csv "${prometheus_urls_csv}" prometheus_urls
  split_csv "${workdirs_csv}" workdirs
  validate_boolean "K6_MULTI_SOURCE_PARALLEL" "${parallel}"

  if [[ "${#contexts[@]}" -lt 2 ]]; then
    echo "K6_MULTI_SOURCE_CONTEXTS requires at least 2 contexts" >&2
    exit 1
  fi
  if [[ "${#base_urls[@]}" -ne 1 && "${#base_urls[@]}" -ne "${#contexts[@]}" ]]; then
    echo "K6_MULTI_SOURCE_BASE_URLS must contain 1 URL or match context count" >&2
    exit 1
  fi
  if [[ "${#prometheus_urls[@]}" -ne 0 && "${#prometheus_urls[@]}" -ne 1 && "${#prometheus_urls[@]}" -ne "${#contexts[@]}" ]]; then
    echo "K6_MULTI_SOURCE_PROMETHEUS_RW_SERVER_URLS must contain 0, 1, or context-count URLs" >&2
    exit 1
  fi
  if [[ "${#workdirs[@]}" -ne 0 && "${#workdirs[@]}" -ne 1 && "${#workdirs[@]}" -ne "${#contexts[@]}" ]]; then
    echo "K6_MULTI_SOURCE_REMOTE_WORKDIRS must contain 0, 1, or context-count paths" >&2
    exit 1
  fi
}

item_for_shard() {
  local index="$1"
  local kind="$2"
  case "${kind}" in
    base_url)
      if [[ "${#base_urls[@]}" -eq 1 ]]; then
        echo "${base_urls[0]}"
      else
        echo "${base_urls[${index}]}"
      fi
      ;;
    prometheus_url)
      if [[ "${#prometheus_urls[@]}" -eq 0 ]]; then
        echo "disabled"
      elif [[ "${#prometheus_urls[@]}" -eq 1 ]]; then
        echo "${prometheus_urls[0]}"
      else
        echo "${prometheus_urls[${index}]}"
      fi
      ;;
    workdir)
      if [[ "${#workdirs[@]}" -eq 0 ]]; then
        echo "${default_workdir}"
      elif [[ "${#workdirs[@]}" -eq 1 ]]; then
        echo "${workdirs[0]}"
      else
        echo "${workdirs[${index}]}"
      fi
      ;;
    *)
      echo "unknown shard item kind: ${kind}" >&2
      exit 1
      ;;
  esac
}

print_plan() {
  local i
  local shard_number
  local run_id
  local report_name
  validate_required_lists
  echo "[k6-transaction-100m-multisource] name=${name}"
  echo "[k6-transaction-100m-multisource] source boundary=multi-source-real-ip"
  echo "[k6-transaction-100m-multisource] shard_count=${#contexts[@]}"
  echo "[k6-transaction-100m-multisource] parallel=${parallel}"
  echo "[k6-transaction-100m-multisource] child runner=${child_runner}"
  echo "[k6-transaction-100m-multisource] child generator mode=docker-context"
  echo "[k6-transaction-100m-multisource] shared_env K6_WORKLOAD_SHAPE=${K6_WORKLOAD_SHAPE:-fixed-order} K6_DURATION=${K6_DURATION:-1m}"
  for i in "${!contexts[@]}"; do
    shard_number="$((i + 1))"
    run_id="${run_id_prefix}-shard-${shard_number}"
    report_name="${run_id}"
    echo "[k6-transaction-100m-multisource] shard=${shard_number} context=${contexts[${i}]} base_url=$(item_for_shard "${i}" base_url) prometheus_rw=$(item_for_shard "${i}" prometheus_url) workdir=$(item_for_shard "${i}" workdir) run_id=${run_id} report_name=${report_name}"
  done
  echo "[k6-transaction-100m-multisource] output_dir=${output_dir}"
}

run_shard() {
  local index="$1"
  local shard_number="$((index + 1))"
  local run_id="${run_id_prefix}-shard-${shard_number}"
  local report_name="${run_id}"
  local prometheus_url
  prometheus_url="$(item_for_shard "${index}" prometheus_url)"

  if [[ "${prometheus_url}" == "disabled" ]]; then
    K6_REPORT_NAME="${report_name}" \
    K6_RUN_ID="${run_id}" \
    K6_GENERATOR_MODE=docker-context \
    K6_DOCKER_CONTEXT="${contexts[${index}]}" \
    K6_REMOTE_BASE_URL="$(item_for_shard "${index}" base_url)" \
    K6_REMOTE_WORKDIR="$(item_for_shard "${index}" workdir)" \
    K6_OBSERVABILITY_MODE="${K6_OBSERVABILITY_MODE:-summary-only}" \
      "${child_runner}" "${child_args[@]}"
  else
    K6_REPORT_NAME="${report_name}" \
    K6_RUN_ID="${run_id}" \
    K6_GENERATOR_MODE=docker-context \
    K6_DOCKER_CONTEXT="${contexts[${index}]}" \
    K6_REMOTE_BASE_URL="$(item_for_shard "${index}" base_url)" \
    K6_REMOTE_PROMETHEUS_RW_SERVER_URL="${prometheus_url}" \
    K6_REMOTE_WORKDIR="$(item_for_shard "${index}" workdir)" \
      "${child_runner}" "${child_args[@]}"
  fi
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

mkdir -p "${output_dir}"
status=0
if [[ "${parallel}" == "true" ]]; then
  pids=()
  for i in "${!contexts[@]}"; do
    shard_number="$((i + 1))"
    run_shard "${i}" >"${output_dir}/shard-${shard_number}.log" 2>&1 &
    pids+=("$!")
  done
  for pid in "${pids[@]}"; do
    if ! wait "${pid}"; then
      status=1
    fi
  done
else
  for i in "${!contexts[@]}"; do
    shard_number="$((i + 1))"
    if ! run_shard "${i}" >"${output_dir}/shard-${shard_number}.log" 2>&1; then
      status=1
    fi
  done
fi

if [[ "${status}" -ne 0 ]]; then
  echo "k6 transaction 100m multi-source run failed: ${output_dir}" >&2
fi
exit "${status}"
