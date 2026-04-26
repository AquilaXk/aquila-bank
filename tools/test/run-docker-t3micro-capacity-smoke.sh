#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-docker-t3micro-capacity-smoke.sh [--print-plan|--dry-run]

Environment:
  DOCKER_T3MICRO_IMAGE             Java 21 JDK image, default eclipse-temurin:21-jdk
  DOCKER_T3MICRO_CPUS              Docker CPU quota, default 2
  DOCKER_T3MICRO_MEMORY            Docker memory limit, default 1024m
  DOCKER_T3MICRO_MEMORY_SWAP       Docker memory+swap limit, default 1024m
  DOCKER_T3MICRO_PIDS_LIMIT        Docker pids limit, default 384
  DOCKER_T3MICRO_GRADLE_USER_HOME  Host Gradle cache mount, default $HOME/.gradle
  DOCKER_T3MICRO_PREPARE_TEST_CLASSES  compile test classes on host before Docker run, default true
  DOCKER_T3MICRO_ARCHIVE_RESULT     write Markdown result to docs/performance-results, default true
  DOCKER_T3MICRO_RESULT_NAME        output basename, default docker-t3micro-capacity-<timestamp>
  SOAK_REPEAT                      repeat count passed to production smoke, default 1

Examples:
  tools/test/run-docker-t3micro-capacity-smoke.sh --print-plan
  tools/test/run-docker-t3micro-capacity-smoke.sh --dry-run
  tools/test/run-docker-t3micro-capacity-smoke.sh
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
    *)
      echo "${name} must be true or false" >&2
      exit 1
      ;;
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

image="${DOCKER_T3MICRO_IMAGE:-eclipse-temurin:21-jdk}"
cpus="${DOCKER_T3MICRO_CPUS:-2}"
memory="${DOCKER_T3MICRO_MEMORY:-1024m}"
memory_swap="${DOCKER_T3MICRO_MEMORY_SWAP:-1024m}"
pids_limit="${DOCKER_T3MICRO_PIDS_LIMIT:-384}"
repeat="${SOAK_REPEAT:-1}"
db_pool_max_size="${PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE:-4}"
server_threads_max="${PRODUCTION_T3MICRO_SERVER_THREADS_MAX:-16}"
sse_max_total_sessions="${PRODUCTION_T3MICRO_SSE_MAX_TOTAL_SESSIONS:-64}"
notification_stream_max="${PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX:-4}"
prepare_test_classes="${DOCKER_T3MICRO_PREPARE_TEST_CLASSES:-true}"
archive_result="${DOCKER_T3MICRO_ARCHIVE_RESULT:-true}"
result_name="${DOCKER_T3MICRO_RESULT_NAME:-docker-t3micro-capacity-$(date +%Y-%m-%d-%H%M%S)}"

require_positive_number "DOCKER_T3MICRO_CPUS" "${cpus}"
require_memory_value "DOCKER_T3MICRO_MEMORY" "${memory}"
require_memory_value "DOCKER_T3MICRO_MEMORY_SWAP" "${memory_swap}"
require_positive_integer "DOCKER_T3MICRO_PIDS_LIMIT" "${pids_limit}"
require_positive_integer "SOAK_REPEAT" "${repeat}"
require_positive_integer "PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE" "${db_pool_max_size}"
require_positive_integer "PRODUCTION_T3MICRO_SERVER_THREADS_MAX" "${server_threads_max}"
require_positive_integer "PRODUCTION_T3MICRO_SSE_MAX_TOTAL_SESSIONS" "${sse_max_total_sessions}"
require_positive_integer "PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX" "${notification_stream_max}"
require_boolean "DOCKER_T3MICRO_PREPARE_TEST_CLASSES" "${prepare_test_classes}"
require_boolean "DOCKER_T3MICRO_ARCHIVE_RESULT" "${archive_result}"

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
host_gradle_home="${DOCKER_T3MICRO_GRADLE_USER_HOME:-${HOME}/.gradle}"
container_workdir="/workspace"
container_name="aquila-bank-docker-t3micro-capacity-${USER:-local}-$$"
container_command="tools/test/run-production-t3micro-capacity-smoke.sh"

print_plan() {
  echo "[docker-t3micro-capacity] source=tools/test/run-production-t3micro-capacity-smoke.sh"
  echo "[docker-t3micro-capacity] image=${image}"
  echo "[docker-t3micro-capacity] docker limit: cpus=${cpus} memory=${memory} memory-swap=${memory_swap} pids-limit=${pids_limit}"
  echo "[docker-t3micro-capacity] prepare-test-classes=${prepare_test_classes}"
  echo "[docker-t3micro-capacity] archive-result=${archive_result}"
  echo "[docker-t3micro-capacity] gradle cache=${host_gradle_home}"
  echo "[docker-t3micro-capacity] caveat: Docker cgroup은 CPU credit, EBS latency, 실제 AWS network를 재현하지 않습니다."
  SOAK_REPEAT="${repeat}" \
  PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE="${db_pool_max_size}" \
  PRODUCTION_T3MICRO_SERVER_THREADS_MAX="${server_threads_max}" \
  PRODUCTION_T3MICRO_SSE_MAX_TOTAL_SESSIONS="${sse_max_total_sessions}" \
  PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX="${notification_stream_max}" \
    tools/test/run-production-t3micro-capacity-smoke.sh --print-plan
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
  -e "SOAK_REPEAT=${repeat}"
  -e "PRODUCTION_T3MICRO_DB_POOL_MAX_SIZE=${db_pool_max_size}"
  -e "PRODUCTION_T3MICRO_SERVER_THREADS_MAX=${server_threads_max}"
  -e "PRODUCTION_T3MICRO_SSE_MAX_TOTAL_SESSIONS=${sse_max_total_sessions}"
  -e "PRODUCTION_T3MICRO_NOTIFICATION_STREAM_MAX=${notification_stream_max}"
  -v "${repo_root}:${container_workdir}"
  -v "${host_gradle_home}:/tmp/.gradle"
)

print_command() {
  printf '[docker-t3micro-capacity] docker command: docker'
  printf ' %q' "${docker_args[@]}" "${image}" bash -lc "${container_command}"
  printf '\n'
}

archive_capacity_result() {
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
    echo "## Docker t3.micro Capacity Smoke"
    echo
    echo "- archivedAt: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "- capacity smoke status: ${status}"
    echo "- source: tools/test/run-docker-t3micro-capacity-smoke.sh"
    echo "- image: ${image}"
    echo "- cpus: ${cpus}"
    echo "- memory: ${memory}"
    echo "- memorySwap: ${memory_swap}"
    echo "- pidsLimit: ${pids_limit}"
    echo "- repeat: ${repeat}"
    echo "- dbPoolMaxSize: ${db_pool_max_size}"
    echo "- serverThreadsMax: ${server_threads_max}"
    echo "- sseMaxTotalSessions: ${sse_max_total_sessions}"
    echo "- notificationStreamMax: ${notification_stream_max}"
    echo
    echo "## Notes"
    echo
    echo "- Docker cgroup은 CPU credit, EBS latency, 실제 AWS network를 재현하지 않습니다."
    echo "- 실패한 실행도 status와 budget 값을 남겨 다음 재실행 기준으로 사용합니다."
  } >"${output_path}"
  echo "[docker-t3micro-capacity] archived=${output_path}"
}

print_plan

if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

print_command

if [[ "${mode}" == "dry-run" ]]; then
  exit 0
fi

command -v docker >/dev/null 2>&1 || {
  echo "docker command is required" >&2
  exit 1
}

mkdir -p "${host_gradle_home}"

if [[ "${prepare_test_classes}" == "true" ]]; then
  echo "[docker-t3micro-capacity] preparing testClasses on host before cgroup smoke"
  # Gradle compile 비용은 앱 런타임 부하가 아니므로 Docker 1GiB 판정에서 분리합니다.
  tools/test/with-resource-lock.sh back-gradle-docker-t3micro-testclasses ./back/gradlew -p back testClasses
fi

set +e
docker "${docker_args[@]}" "${image}" bash -lc "${container_command}"
status=$?
set -e
archive_capacity_result "${status}"
exit "${status}"
