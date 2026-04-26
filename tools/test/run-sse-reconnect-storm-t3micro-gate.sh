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
  SSE_T3MICRO_RESULT_NAME       output basename, default sse-reconnect-t3micro-<timestamp>

Examples:
  tools/test/run-sse-reconnect-storm-t3micro-gate.sh --print-plan
  tools/test/run-sse-reconnect-storm-t3micro-gate.sh --dry-run
  tools/test/run-sse-reconnect-storm-t3micro-gate.sh
USAGE
}

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
result_name="${SSE_T3MICRO_RESULT_NAME:-sse-reconnect-t3micro-$(date +%Y-%m-%d-%H%M%S)}"

require_positive_number "SSE_T3MICRO_CPUS" "${cpus}"
require_memory_value "SSE_T3MICRO_MEMORY" "${memory}"
require_memory_value "SSE_T3MICRO_MEMORY_SWAP" "${memory_swap}"
require_positive_integer "SSE_T3MICRO_PIDS_LIMIT" "${pids_limit}"
require_boolean "SSE_T3MICRO_ARCHIVE_RESULT" "${archive_result}"

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
host_gradle_home="${SSE_T3MICRO_GRADLE_USER_HOME:-${HOME}/.gradle}"
container_workdir="/workspace"
container_name="aquila-bank-sse-reconnect-t3micro-${USER:-local}-$$"
container_command="tools/test/run-sse-reconnect-storm-smoke.sh"

print_plan() {
  echo "[sse-reconnect-t3micro] source=${container_command}"
  echo "[sse-reconnect-t3micro] image=${image}"
  echo "[sse-reconnect-t3micro] docker limit: cpus=${cpus} memory=${memory} memory-swap=${memory_swap} pids-limit=${pids_limit}"
  echo "[sse-reconnect-t3micro] archive-result=${archive_result}"
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
  -e "JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS:--XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=40}"
  -e "GRADLE_OPTS=${GRADLE_OPTS:--Dorg.gradle.jvmargs=-Xmx512m -Dorg.gradle.daemon=false -Dorg.gradle.parallel=false}"
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

mkdir -p "${host_gradle_home}"
set +e
docker "${docker_args[@]}" "${image}" bash -lc "${container_command}"
status=$?
set -e
archive_sse_result "${status}"
exit "${status}"
