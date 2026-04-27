#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-sse-reconnect-storm-t3micro-gate.sh [--print-plan|--dry-run]

Environment:
  SSE_T3MICRO_IMAGE             Java 21 JDK image, default eclipse-temurin:21-jdk
  SSE_T3MICRO_CPUS              Docker CPU quota, default 2
  SSE_T3MICRO_MEMORY            Docker memory limit, default 1024m
  SSE_T3MICRO_MEMORY_SWAP       Docker memory+swap limit, default 1024m
  SSE_T3MICRO_PIDS_LIMIT        Docker pids limit, default 384
  SSE_T3MICRO_GRADLE_USER_HOME  Host Gradle cache mount, default $HOME/.gradle
  SSE_T3MICRO_ARCHIVE_RESULT    write Markdown result to docs/performance-results, default true
  SSE_T3MICRO_TELEMETRY_ENABLED sample docker stats and GC log, default true
  SSE_T3MICRO_TELEMETRY_INTERVAL_SECONDS default 2
  SSE_T3MICRO_RESULT_NAME       output basename, default sse-reconnect-t3micro-<timestamp>
  SSE_RECONNECT_CLIENTS         concurrent reconnect clients passed to smoke, default 3
  SSE_RECONNECT_ROUNDS          reconnect rounds passed to smoke, default 3

Examples:
  tools/test/run-sse-reconnect-storm-t3micro-gate.sh --print-plan
  tools/test/run-sse-reconnect-storm-t3micro-gate.sh --dry-run
  tools/test/run-sse-reconnect-storm-t3micro-gate.sh
USAGE
}

source "tools/test/t3micro-cgroup-telemetry-lib.sh"

require_positive_number() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*([.][0-9]+)?$ ]]; then
    echo "${name} must be a positive number" >&2
    exit 1
  fi
}

require_memory_value() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*[kKmMgG]?$ ]]; then
    echo "${name} must be a positive Docker memory value" >&2
    exit 1
  fi
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer" >&2
    exit 1
  fi
}

require_boolean() {
  local name="$1"
  local value="$2"
  case "${value}" in
    true | false) ;;
    *) echo "${name} must be true or false" >&2; exit 1 ;;
  esac
}

mode="run"
if [[ "${1:-}" == "--print-plan" ]]; then
  mode="print-plan"
  shift
elif [[ "${1:-}" == "--dry-run" ]]; then
  mode="dry-run"
  shift
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
if [[ $# -ne 0 ]]; then
  usage
  exit 1
fi

image="${SSE_T3MICRO_IMAGE:-eclipse-temurin:21-jdk}"
cpus="${SSE_T3MICRO_CPUS:-2}"
memory="${SSE_T3MICRO_MEMORY:-1024m}"
memory_swap="${SSE_T3MICRO_MEMORY_SWAP:-1024m}"
pids_limit="${SSE_T3MICRO_PIDS_LIMIT:-384}"
archive_result="${SSE_T3MICRO_ARCHIVE_RESULT:-true}"
telemetry_enabled="${SSE_T3MICRO_TELEMETRY_ENABLED:-true}"
telemetry_interval_seconds="${SSE_T3MICRO_TELEMETRY_INTERVAL_SECONDS:-2}"
result_name="${SSE_T3MICRO_RESULT_NAME:-sse-reconnect-t3micro-$(date +%Y-%m-%d-%H%M%S)}"
reconnect_clients="${SSE_RECONNECT_CLIENTS:-3}"
reconnect_rounds="${SSE_RECONNECT_ROUNDS:-3}"

require_positive_number "SSE_T3MICRO_CPUS" "${cpus}"
require_memory_value "SSE_T3MICRO_MEMORY" "${memory}"
require_memory_value "SSE_T3MICRO_MEMORY_SWAP" "${memory_swap}"
require_positive_integer "SSE_T3MICRO_PIDS_LIMIT" "${pids_limit}"
require_boolean "SSE_T3MICRO_ARCHIVE_RESULT" "${archive_result}"
require_boolean "SSE_T3MICRO_TELEMETRY_ENABLED" "${telemetry_enabled}"
require_positive_integer "SSE_T3MICRO_TELEMETRY_INTERVAL_SECONDS" "${telemetry_interval_seconds}"
require_positive_integer "SSE_RECONNECT_CLIENTS" "${reconnect_clients}"
require_positive_integer "SSE_RECONNECT_ROUNDS" "${reconnect_rounds}"

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
host_gradle_home="${SSE_T3MICRO_GRADLE_USER_HOME:-${HOME}/.gradle}"
container_workdir="/workspace"
container_name="aquila-bank-sse-reconnect-t3micro-${USER:-local}-$$"
container_command="tools/test/run-sse-reconnect-storm-smoke.sh"
telemetry_dir="build/reports/t3micro"
stats_path="${telemetry_dir}/${result_name}-docker-stats.tsv"
gc_log_path="${telemetry_dir}/${result_name}-gc.log"
telemetry_summary_path="${telemetry_dir}/${result_name}-telemetry.env"
telemetry_marker_path="${telemetry_dir}/${result_name}-telemetry.running"
base_java_tool_options="${JAVA_TOOL_OPTIONS:--XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=40}"
java_tool_options="${base_java_tool_options}"
if [[ "${telemetry_enabled}" == "true" ]]; then
  java_tool_options="$(t3micro_gc_java_tool_options "${base_java_tool_options}" "${container_workdir}/${gc_log_path}")"
fi

print_plan() {
  echo "[sse-reconnect-t3micro] source=${container_command}"
  echo "[sse-reconnect-t3micro] image=${image}"
  echo "[sse-reconnect-t3micro] docker limit: cpus=${cpus} memory=${memory} memory-swap=${memory_swap} pids-limit=${pids_limit}"
  echo "[sse-reconnect-t3micro] archive-result=${archive_result}"
  echo "[sse-reconnect-t3micro] telemetry-enabled=${telemetry_enabled}"
  echo "[sse-reconnect-t3micro] telemetry-interval-seconds=${telemetry_interval_seconds}"
  echo "[sse-reconnect-t3micro] telemetry-stats=${stats_path}"
  echo "[sse-reconnect-t3micro] telemetry-gc-log=${gc_log_path}"
  echo "[sse-reconnect-t3micro] reconnect-clients=${reconnect_clients}"
  echo "[sse-reconnect-t3micro] reconnect-rounds=${reconnect_rounds}"
  echo "[sse-reconnect-t3micro] gradle cache=${host_gradle_home}"
}

docker_args=(
  run
  --rm
  --name "${container_name}"
  --cpus "${cpus}"
  --memory "${memory}"
  --memory-swap "${memory_swap}"
  --pids-limit "${pids_limit}"
  --workdir "${container_workdir}"
  --user "$(id -u):$(id -g)"
  -e "HOME=/tmp"
  -e "GRADLE_USER_HOME=/tmp/.gradle"
  -e "JAVA_TOOL_OPTIONS=${java_tool_options}"
  -e "GRADLE_OPTS=${GRADLE_OPTS:--Dorg.gradle.jvmargs=-Xmx512m -Dorg.gradle.daemon=false -Dorg.gradle.parallel=false}"
  -e "SSE_RECONNECT_CLIENTS=${reconnect_clients}"
  -e "SSE_RECONNECT_ROUNDS=${reconnect_rounds}"
  -v "${repo_root}:${container_workdir}"
  -v "${host_gradle_home}:/tmp/.gradle"
)

print_command() {
  printf '[sse-reconnect-t3micro] docker command: docker'
  printf ' %q' "${docker_args[@]}" "${image}" bash -lc "${container_command}"
  printf '\n'
}

archive_sse_result() {
  local status="$1"
  if [[ "${archive_result}" != "true" ]]; then
    return 0
  fi
  local output_dir="docs/performance-results"
  local output_path="${output_dir}/${result_name}.md"
  mkdir -p "${output_dir}"
  {
    echo "# ${result_name}"
    echo
    echo "## SSE Reconnect Storm t3.micro Gate"
    echo
    echo "- archivedAt: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "- status: ${status}"
    echo "- source: ${container_command}"
    echo "- cpus: ${cpus}"
    echo "- memory: ${memory}"
    echo "- memorySwap: ${memory_swap}"
    echo "- pidsLimit: ${pids_limit}"
    echo "- reconnectClients: ${reconnect_clients}"
    echo "- reconnectRounds: ${reconnect_rounds}"
    if [[ -f "${telemetry_summary_path}" ]]; then
      echo "- telemetryStats: ${stats_path}"
      echo "- telemetrySummary: ${telemetry_summary_path}"
      while IFS= read -r line; do
        echo "- ${line}"
      done <"${telemetry_summary_path}"
    else
      echo "- telemetryStats: ${stats_path}"
      echo "- telemetrySummary: unavailable"
    fi
  } >"${output_path}"
  echo "[sse-reconnect-t3micro] archived=${output_path}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
print_command
if [[ "${mode}" == "dry-run" ]]; then
  exit 0
fi

mkdir -p "${host_gradle_home}" "${telemetry_dir}"
set +e
telemetry_pid=""
if [[ "${telemetry_enabled}" == "true" ]]; then
  t3micro_start_container_telemetry "${container_name}" "${stats_path}" "${telemetry_marker_path}" "${telemetry_interval_seconds}"
  telemetry_pid="${T3MICRO_TELEMETRY_PID}"
fi
docker "${docker_args[@]}" "${image}" bash -lc "${container_command}"
status=$?
if [[ "${telemetry_enabled}" == "true" ]]; then
  t3micro_stop_container_telemetry "${telemetry_marker_path}" "${telemetry_pid}"
  t3micro_write_peak_summary "${stats_path}" "${gc_log_path}" "${telemetry_summary_path}"
fi
set -e
archive_sse_result "${status}"
exit "${status}"
