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
NGINX_BACKEND_PROXY_HOST="${NGINX_BACKEND_PROXY_HOST:-aquila-bank-backend}"
NGINX_EDGE_RETRY_AFTER_SECONDS="${NGINX_EDGE_RETRY_AFTER_SECONDS:-1}"
NGINX_EDGE_RETRY_AFTER_MILLIS="${NGINX_EDGE_RETRY_AFTER_MILLIS:-150}"
NGINX_EDGE_RETRY_JITTER_MILLIS="${NGINX_EDGE_RETRY_JITTER_MILLIS:-100}"
NGINX_REAL_IP_HEADER="${NGINX_REAL_IP_HEADER:-X-Forwarded-For}"
NGINX_REAL_IP_TRUSTED_PROXIES="${NGINX_REAL_IP_TRUSTED_PROXIES:-${OCI_A1_NGINX_REAL_IP_TRUSTED_PROXIES:-10.60.0.0/16}}"
NGINX_BACKEND_API_KEEPALIVE_TIMEOUT_SECONDS="${NGINX_BACKEND_API_KEEPALIVE_TIMEOUT_SECONDS:-2}"
NGINX_TRANSACTION_READ_BUDGET_PROFILE="${NGINX_TRANSACTION_READ_BUDGET_PROFILE:-${OCI_A1_TRANSACTION_READ_BUDGET_PROFILE:-burst64}}"

case "${NGINX_TRANSACTION_READ_BUDGET_PROFILE}" in
  burst64)
    transaction_read_profile_hot_rate_rps=160
    transaction_read_profile_archive_rate_rps=160
    transaction_read_profile_hot_burst=20
    transaction_read_profile_archive_burst=20
    transaction_read_profile_hot_delay=0
    transaction_read_profile_archive_delay=0
    ;;
  balanced)
    transaction_read_profile_hot_rate_rps=96
    transaction_read_profile_archive_rate_rps=96
    transaction_read_profile_hot_burst=12
    transaction_read_profile_archive_burst=12
    transaction_read_profile_hot_delay=1
    transaction_read_profile_archive_delay=1
    ;;
  fail-fast)
    transaction_read_profile_hot_rate_rps=96
    transaction_read_profile_archive_rate_rps=96
    transaction_read_profile_hot_burst=12
    transaction_read_profile_archive_burst=12
    transaction_read_profile_hot_delay=0
    transaction_read_profile_archive_delay=0
    ;;
  *)
    echo "[nginx-render] NGINX_TRANSACTION_READ_BUDGET_PROFILE must be burst64, balanced, or fail-fast: ${NGINX_TRANSACTION_READ_BUDGET_PROFILE}" >&2
    exit 1
    ;;
esac

NGINX_TRANSACTION_READ_HOT_RATE_RPS="${NGINX_TRANSACTION_READ_HOT_RATE_RPS:-${transaction_read_profile_hot_rate_rps}}"
NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS="${NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS:-${transaction_read_profile_archive_rate_rps}}"
NGINX_TRANSACTION_READ_HOT_BURST="${NGINX_TRANSACTION_READ_HOT_BURST:-${transaction_read_profile_hot_burst}}"
NGINX_TRANSACTION_READ_ARCHIVE_BURST="${NGINX_TRANSACTION_READ_ARCHIVE_BURST:-${transaction_read_profile_archive_burst}}"
NGINX_TRANSACTION_READ_HOT_DELAY="${NGINX_TRANSACTION_READ_HOT_DELAY:-${transaction_read_profile_hot_delay}}"
NGINX_TRANSACTION_READ_ARCHIVE_DELAY="${NGINX_TRANSACTION_READ_ARCHIVE_DELAY:-${transaction_read_profile_archive_delay}}"

require_non_negative_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "[nginx-render] ${name} must be a non-negative integer: ${value}" >&2
    exit 1
  fi
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "[nginx-render] ${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_non_negative_integer "NGINX_EDGE_RETRY_AFTER_SECONDS" "${NGINX_EDGE_RETRY_AFTER_SECONDS}"
require_non_negative_integer "NGINX_EDGE_RETRY_AFTER_MILLIS" "${NGINX_EDGE_RETRY_AFTER_MILLIS}"
require_non_negative_integer "NGINX_EDGE_RETRY_JITTER_MILLIS" "${NGINX_EDGE_RETRY_JITTER_MILLIS}"
require_positive_integer "NGINX_BACKEND_API_KEEPALIVE_TIMEOUT_SECONDS" "${NGINX_BACKEND_API_KEEPALIVE_TIMEOUT_SECONDS}"
require_positive_integer "NGINX_TRANSACTION_READ_HOT_RATE_RPS" "${NGINX_TRANSACTION_READ_HOT_RATE_RPS}"
require_positive_integer "NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS" "${NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS}"
require_positive_integer "NGINX_TRANSACTION_READ_HOT_BURST" "${NGINX_TRANSACTION_READ_HOT_BURST}"
require_positive_integer "NGINX_TRANSACTION_READ_ARCHIVE_BURST" "${NGINX_TRANSACTION_READ_ARCHIVE_BURST}"
require_non_negative_integer "NGINX_TRANSACTION_READ_HOT_DELAY" "${NGINX_TRANSACTION_READ_HOT_DELAY}"
require_non_negative_integer "NGINX_TRANSACTION_READ_ARCHIVE_DELAY" "${NGINX_TRANSACTION_READ_ARCHIVE_DELAY}"

case "${NGINX_REAL_IP_HEADER}" in
  X-Forwarded-For|X-Real-IP) ;;
  *)
    echo "[nginx-render] NGINX_REAL_IP_HEADER must be X-Forwarded-For or X-Real-IP: ${NGINX_REAL_IP_HEADER}" >&2
    exit 1
    ;;
esac

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

render_real_ip_trusted_proxy_lines() {
  local proxies_csv="$1"
  local lines=""
  local raw_proxy proxy

  if [[ -z "${proxies_csv}" || "${proxies_csv}" == "none" ]]; then
    printf '  # NGINX_REAL_IP_TRUSTED_PROXIES disabled: limiter key stays on TCP peer address.\n'
    return
  fi

  IFS=',' read -r -a proxies <<<"${proxies_csv}"
  for raw_proxy in "${proxies[@]}"; do
    proxy="$(printf '%s' "${raw_proxy}" | xargs)"
    if [[ -z "${proxy}" ]]; then
      continue
    fi
    if ! [[ "${proxy}" =~ ^[0-9A-Fa-f:.\/]+$ ]]; then
      echo "[nginx-render] NGINX_REAL_IP_TRUSTED_PROXIES contains an invalid IP/CIDR: ${proxy}" >&2
      exit 1
    fi
    lines="${lines}  set_real_ip_from ${proxy};\n"
  done

  if [[ -z "${lines}" ]]; then
    printf '  # NGINX_REAL_IP_TRUSTED_PROXIES empty: limiter key stays on TCP peer address.\n'
    return
  fi

  printf '%b' "${lines}"
}

render_limit_req_mode() {
  local delay="$1"
  if [[ "${delay}" == "0" ]]; then
    printf 'nodelay'
    return
  fi
  printf 'delay=%s' "${delay}"
}

NGINX_FRONTEND_SERVER_LINES="    server ${NGINX_FRONTEND_SERVER};"
NGINX_BACKEND_API_SERVER_LINES="$(render_server_lines "${NGINX_BACKEND_API_SERVERS}")"
NGINX_BACKEND_SSE_SERVER_LINES="$(render_server_lines "${NGINX_BACKEND_SSE_SERVERS}")"
NGINX_REAL_IP_TRUSTED_PROXY_LINES="$(render_real_ip_trusted_proxy_lines "${NGINX_REAL_IP_TRUSTED_PROXIES}")"
NGINX_TRANSACTION_READ_HOT_LIMIT_MODE="$(render_limit_req_mode "${NGINX_TRANSACTION_READ_HOT_DELAY}")"
NGINX_TRANSACTION_READ_ARCHIVE_LIMIT_MODE="$(render_limit_req_mode "${NGINX_TRANSACTION_READ_ARCHIVE_DELAY}")"

rendered="$(cat "${template_path}")"
rendered="${rendered//'${NGINX_SERVER_NAME}'/${NGINX_SERVER_NAME}}"
rendered="${rendered//'${NGINX_SSL_CERTIFICATE_PATH}'/${NGINX_SSL_CERTIFICATE_PATH}}"
rendered="${rendered//'${NGINX_SSL_CERTIFICATE_KEY_PATH}'/${NGINX_SSL_CERTIFICATE_KEY_PATH}}"
rendered="${rendered//'${NGINX_FRONTEND_SERVER_LINES}'/${NGINX_FRONTEND_SERVER_LINES}}"
rendered="${rendered//'${NGINX_BACKEND_API_SERVER_LINES}'/${NGINX_BACKEND_API_SERVER_LINES}}"
rendered="${rendered//'${NGINX_BACKEND_SSE_SERVER_LINES}'/${NGINX_BACKEND_SSE_SERVER_LINES}}"
rendered="${rendered//'${NGINX_BACKEND_PROXY_HOST}'/${NGINX_BACKEND_PROXY_HOST}}"
rendered="${rendered//'${NGINX_EDGE_RETRY_AFTER_SECONDS}'/${NGINX_EDGE_RETRY_AFTER_SECONDS}}"
rendered="${rendered//'${NGINX_EDGE_RETRY_AFTER_MILLIS}'/${NGINX_EDGE_RETRY_AFTER_MILLIS}}"
rendered="${rendered//'${NGINX_EDGE_RETRY_JITTER_MILLIS}'/${NGINX_EDGE_RETRY_JITTER_MILLIS}}"
rendered="${rendered//'${NGINX_REAL_IP_HEADER}'/${NGINX_REAL_IP_HEADER}}"
rendered="${rendered//'${NGINX_REAL_IP_TRUSTED_PROXY_LINES}'/${NGINX_REAL_IP_TRUSTED_PROXY_LINES}}"
rendered="${rendered//'${NGINX_BACKEND_API_KEEPALIVE_TIMEOUT_SECONDS}'/${NGINX_BACKEND_API_KEEPALIVE_TIMEOUT_SECONDS}}"
rendered="${rendered//'${NGINX_TRANSACTION_READ_HOT_RATE_RPS}'/${NGINX_TRANSACTION_READ_HOT_RATE_RPS}}"
rendered="${rendered//'${NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS}'/${NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS}}"
rendered="${rendered//'${NGINX_TRANSACTION_READ_HOT_BURST}'/${NGINX_TRANSACTION_READ_HOT_BURST}}"
rendered="${rendered//'${NGINX_TRANSACTION_READ_ARCHIVE_BURST}'/${NGINX_TRANSACTION_READ_ARCHIVE_BURST}}"
rendered="${rendered//'${NGINX_TRANSACTION_READ_HOT_LIMIT_MODE}'/${NGINX_TRANSACTION_READ_HOT_LIMIT_MODE}}"
rendered="${rendered//'${NGINX_TRANSACTION_READ_ARCHIVE_LIMIT_MODE}'/${NGINX_TRANSACTION_READ_ARCHIVE_LIMIT_MODE}}"

mkdir -p "$(dirname "${output_path}")"
printf '%s\n' "${rendered}" > "${output_path}"
echo "[nginx-render] rendered ${output_path}"
