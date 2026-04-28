#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-ec2-local-db-capacity-env-doctor.sh [--print-plan|--print-env-template|--dry-run]

Environment:
  EC2_LOCAL_DB_CAPACITY_ENV_FILE optional local env file to source
  EC2_DIRECT_BACKEND_BASE_URL required
  EC2_NGINX_BASE_URL required
  EC2_LOADTEST_AUTH_REQUIRED default true
  EC2_LOADTEST_AUTH_TOKEN required when auth is required
  EC2_K6_HOT_ACCOUNT_ID / EC2_K6_HOT_FROM / EC2_K6_HOT_TO required
  EC2_K6_COLD_ACCOUNT_ID / EC2_K6_COLD_FROM / EC2_K6_COLD_TO required
  EC2_DOCKER_NETWORK default aquila-bank-prod
  EC2_LOCAL_DB_TUNNEL_CHECK default true
  EC2_LOCAL_DB_PROBE_IMAGE default postgres:18-alpine
  EC2_LOCAL_DB_HOST default host.docker.internal
  EC2_LOCAL_DB_PORT default 25432
  EC2_LOCAL_DB_NAME required when tunnel check is true
  EC2_LOCAL_DB_USER required when tunnel check is true
  EC2_LOCAL_DB_PASSWORD required when tunnel check is true

Examples:
  EC2_LOCAL_DB_CAPACITY_ENV_FILE=.env/ec2-local-db-capacity.env \
    tools/test/run-ec2-local-db-capacity-env-doctor.sh --print-plan
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --print-env-template)
      mode="print-env-template"
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

template="tools/test/ec2-local-db-capacity.env.example"
if [[ "${mode}" == "print-env-template" ]]; then
  cat "${template}"
  exit 0
fi

env_file="${EC2_LOCAL_DB_CAPACITY_ENV_FILE:-${CAPACITY_ENV_FILE:-}}"
if [[ -n "${env_file}" ]]; then
  if [[ ! -f "${env_file}" ]]; then
    echo "EC2_LOCAL_DB_CAPACITY_ENV_FILE not found: ${env_file}" >&2
    exit 1
  fi
  set -a
  # local-only capacity secrets/endpoints stay outside git.
  # shellcheck disable=SC1090
  source "${env_file}"
  set +a
fi

run_id="${EC2_CAPACITY_RUN_ID:-ec2-local-db-100m-$(date +%Y-%m-%d-%H%M%S)}"
direct_base_url="${EC2_DIRECT_BACKEND_BASE_URL:-}"
nginx_base_url="${EC2_NGINX_BASE_URL:-}"
auth_required="${EC2_LOADTEST_AUTH_REQUIRED:-true}"
auth_token="${EC2_LOADTEST_AUTH_TOKEN:-}"
hot_account_id="${EC2_K6_HOT_ACCOUNT_ID:-}"
hot_from="${EC2_K6_HOT_FROM:-}"
hot_to="${EC2_K6_HOT_TO:-}"
cold_account_id="${EC2_K6_COLD_ACCOUNT_ID:-}"
cold_from="${EC2_K6_COLD_FROM:-}"
cold_to="${EC2_K6_COLD_TO:-}"
docker_network="${EC2_DOCKER_NETWORK:-aquila-bank-prod}"
tunnel_check="${EC2_LOCAL_DB_TUNNEL_CHECK:-true}"
probe_image="${EC2_LOCAL_DB_PROBE_IMAGE:-postgres:18-alpine}"
db_host="${EC2_LOCAL_DB_HOST:-host.docker.internal}"
db_port="${EC2_LOCAL_DB_PORT:-25432}"
db_name="${EC2_LOCAL_DB_NAME:-}"
db_user="${EC2_LOCAL_DB_USER:-}"
db_password="${EC2_LOCAL_DB_PASSWORD:-}"

require_bool() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_non_empty() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "${name} is required" >&2
    exit 1
  fi
}

require_url() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^https?://[^[:space:]]+$ ]]; then
    echo "${name} must be an http(s) URL: ${value}" >&2
    exit 1
  fi
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

mask() {
  local value="$1"
  if [[ -z "${value}" ]]; then
    printf 'missing'
  else
    printf 'set'
  fi
}

require_bool "EC2_LOADTEST_AUTH_REQUIRED" "${auth_required}"
require_bool "EC2_LOCAL_DB_TUNNEL_CHECK" "${tunnel_check}"
require_url "EC2_DIRECT_BACKEND_BASE_URL" "${direct_base_url}"
require_url "EC2_NGINX_BASE_URL" "${nginx_base_url}"
require_non_empty "EC2_K6_HOT_ACCOUNT_ID" "${hot_account_id}"
require_non_empty "EC2_K6_HOT_FROM" "${hot_from}"
require_non_empty "EC2_K6_HOT_TO" "${hot_to}"
require_non_empty "EC2_K6_COLD_ACCOUNT_ID" "${cold_account_id}"
require_non_empty "EC2_K6_COLD_FROM" "${cold_from}"
require_non_empty "EC2_K6_COLD_TO" "${cold_to}"
if [[ "${auth_required}" == "true" ]]; then
  require_non_empty "EC2_LOADTEST_AUTH_TOKEN" "${auth_token}"
fi
if [[ "${tunnel_check}" == "true" ]]; then
  require_positive_integer "EC2_LOCAL_DB_PORT" "${db_port}"
  require_non_empty "EC2_DOCKER_NETWORK" "${docker_network}"
  require_non_empty "EC2_LOCAL_DB_NAME" "${db_name}"
  require_non_empty "EC2_LOCAL_DB_USER" "${db_user}"
  require_non_empty "EC2_LOCAL_DB_PASSWORD" "${db_password}"
fi

print_plan() {
  echo "[ec2-local-db-doctor] mode=${mode}"
  echo "[ec2-local-db-doctor] env_file=${env_file:-missing}"
  echo "[ec2-local-db-doctor] run_id=${run_id}"
  echo "[ec2-local-db-doctor] direct_base_url=${direct_base_url}"
  echo "[ec2-local-db-doctor] nginx_base_url=${nginx_base_url}"
  echo "[ec2-local-db-doctor] auth_required=${auth_required} auth_token=$(mask "${auth_token}")"
  echo "[ec2-local-db-doctor] hot=${hot_account_id} ${hot_from}..${hot_to}"
  echo "[ec2-local-db-doctor] cold=${cold_account_id} ${cold_from}..${cold_to}"
  echo "[ec2-local-db-doctor] tunnel_check=${tunnel_check}"
  echo "[ec2-local-db-doctor] docker_network=${docker_network}"
  echo "[ec2-local-db-doctor] db=${db_user:-missing}@${db_host}:${db_port}/${db_name:-missing} password=$(mask "${db_password}")"
  echo "[ec2-local-db-doctor] probe_image=${probe_image}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  echo "[ec2-local-db-doctor] dry-run passed"
  exit 0
fi

if [[ "${tunnel_check}" != "true" ]]; then
  echo "[ec2-local-db-doctor] tunnel check skipped"
  exit 0
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required for container-to-host-gateway tunnel probe" >&2
  exit 1
fi

docker network inspect "${docker_network}" >/dev/null
docker run --rm \
  --network "${docker_network}" \
  --add-host host.docker.internal:host-gateway \
  -e PGPASSWORD="${db_password}" \
  -e PGSSLMODE=disable \
  "${probe_image}" \
  psql -v ON_ERROR_STOP=1 \
    -h "${db_host}" \
    -p "${db_port}" \
    -U "${db_user}" \
    -d "${db_name}" \
    --command "BEGIN READ ONLY; SELECT 1 AS ec2_local_db_tunnel_probe; ROLLBACK;" >/dev/null

echo "[ec2-local-db-doctor] tunnel probe passed"
