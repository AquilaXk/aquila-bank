#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -lt 1 || "$#" -gt 2 ]]; then
  echo "usage: $0 <output-path> [env-file]" >&2
  exit 1
fi

output_path="$1"
env_file="${2:-}"
template_path="ops/nginx/nginx.conf"

if [[ ! -f "${template_path}" ]]; then
  echo "[nginx-render] missing template: ${template_path}" >&2
  exit 1
fi

if [[ -n "${env_file}" ]]; then
  if [[ ! -f "${env_file}" ]]; then
    echo "[nginx-render] missing env file: ${env_file}" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "${env_file}"
  set +a
fi

required_vars=(
  NGINX_SERVER_NAME
  NGINX_SSL_CERTIFICATE_PATH
  NGINX_SSL_CERTIFICATE_KEY_PATH
  NGINX_FRONTEND_SERVER
  NGINX_BACKEND_API_SERVERS
)

for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "[nginx-render] missing required env: ${var_name}" >&2
    exit 1
  fi
done

NGINX_BACKEND_SSE_SERVERS="${NGINX_BACKEND_SSE_SERVERS:-${NGINX_BACKEND_API_SERVERS}}"

render_server_lines() {
  local servers_csv="$1"
  local lines=""
  IFS=',' read -r -a servers <<<"${servers_csv}"
  for raw_server in "${servers[@]}"; do
    local server
    server="$(printf '%s' "${raw_server}" | xargs)"
    if [[ -z "${server}" ]]; then
      continue
    fi
    lines="${lines}    server ${server} max_fails=3 fail_timeout=5s;\n"
  done
  if [[ -z "${lines}" ]]; then
    echo "[nginx-render] upstream server list must not be empty" >&2
    exit 1
  fi
  printf '%b' "${lines}"
}

NGINX_FRONTEND_SERVER_LINES="    server ${NGINX_FRONTEND_SERVER};"
NGINX_BACKEND_API_SERVER_LINES="$(render_server_lines "${NGINX_BACKEND_API_SERVERS}")"
NGINX_BACKEND_SSE_SERVER_LINES="$(render_server_lines "${NGINX_BACKEND_SSE_SERVERS}")"

rendered="$(cat "${template_path}")"
rendered="${rendered//'${NGINX_SERVER_NAME}'/${NGINX_SERVER_NAME}}"
rendered="${rendered//'${NGINX_SSL_CERTIFICATE_PATH}'/${NGINX_SSL_CERTIFICATE_PATH}}"
rendered="${rendered//'${NGINX_SSL_CERTIFICATE_KEY_PATH}'/${NGINX_SSL_CERTIFICATE_KEY_PATH}}"
rendered="${rendered//'${NGINX_FRONTEND_SERVER_LINES}'/${NGINX_FRONTEND_SERVER_LINES}}"
rendered="${rendered//'${NGINX_BACKEND_API_SERVER_LINES}'/${NGINX_BACKEND_API_SERVER_LINES}}"
rendered="${rendered//'${NGINX_BACKEND_SSE_SERVER_LINES}'/${NGINX_BACKEND_SSE_SERVER_LINES}}"

mkdir -p "$(dirname "${output_path}")"
printf '%s\n' "${rendered}" > "${output_path}"
echo "[nginx-render] rendered ${output_path}"
