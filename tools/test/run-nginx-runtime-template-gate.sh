#!/usr/bin/env bash
set -euo pipefail

env_file="${1:-ops/nginx/runtime.env.example}"
strict_mode="${NGINX_RUNTIME_GATE_STRICT:-false}"
tmp_dir="$(mktemp -d)"
rendered_config="${tmp_dir}/nginx.conf"
skip_nginx_check="false"

cleanup() {
  rm -rf "${tmp_dir}"
}
trap cleanup EXIT

contains_pattern() {
  local pattern="$1"
  if command -v rg >/dev/null 2>&1; then
    rg -F --quiet -- "$pattern" "${rendered_config}"
    return
  fi
  grep -Fq -- "$pattern" "${rendered_config}"
}

prepare_nginx_test_config() {
  local access_log_path="${tmp_dir}/logs/access.log"
  local error_log_path="${tmp_dir}/logs/error.log"
  local patched_config="${rendered_config}.patched"
  local http_listen_port="18080"
  local https_listen_port="18443"

  if ! awk \
    -v access_log_path="${access_log_path}" \
    -v error_log_path="${error_log_path}" \
    -v http_listen_port="${http_listen_port}" \
    -v https_listen_port="${https_listen_port}" '
      /^pid / && !main_log_injected {
        print
        print "error_log " error_log_path " notice;"
        main_log_injected = 1
        next
      }
      /^http \{/ && !http_log_injected {
        print
        print "  access_log " access_log_path ";"
        http_log_injected = 1
        next
      }
      /^[[:space:]]*access_log \/var\/log\/nginx\/access\.log aquila_bank_upstream;$/ && !formatted_access_log_rewritten {
        print "  access_log " access_log_path " aquila_bank_upstream;"
        formatted_access_log_rewritten = 1
        next
      }
      /^[[:space:]]*listen 80 default_server;$/ && !http_listen_rewritten {
        print "    listen " http_listen_port " default_server;"
        http_listen_rewritten = 1
        next
      }
      /^[[:space:]]*listen 443 ssl http2;$/ && !https_listen_rewritten {
        print "    listen " https_listen_port " ssl http2;"
        https_listen_rewritten = 1
        next
      }
      { print }
      END {
        if (!main_log_injected || !http_log_injected || !formatted_access_log_rewritten || !http_listen_rewritten || !https_listen_rewritten) {
          exit 1
        }
      }
    ' "${rendered_config}" > "${patched_config}"; then
    echo "[nginx-runtime-gate] failed to inject tmp log path, rewrite formatted access log, or high listen port into rendered config" >&2
    exit 1
  fi

  mv "${patched_config}" "${rendered_config}"
}

if [[ ! -f "${env_file}" ]]; then
  echo "[nginx-runtime-gate] missing env file: ${env_file}" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${env_file}"
set +a

if [[ -z "${NGINX_SERVER_NAME:-}" ]]; then
  echo "[nginx-runtime-gate] missing NGINX_SERVER_NAME in ${env_file}" >&2
  exit 1
fi

if [[ ! -f "${NGINX_SSL_CERTIFICATE_PATH:-}" || ! -f "${NGINX_SSL_CERTIFICATE_KEY_PATH:-}" ]]; then
  if command -v openssl >/dev/null 2>&1; then
    export NGINX_SSL_CERTIFICATE_PATH="${tmp_dir}/fullchain.pem"
    export NGINX_SSL_CERTIFICATE_KEY_PATH="${tmp_dir}/privkey.pem"
    openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
      -keyout "${NGINX_SSL_CERTIFICATE_KEY_PATH}" \
      -out "${NGINX_SSL_CERTIFICATE_PATH}" \
      -subj "/CN=${NGINX_SERVER_NAME}" >/dev/null 2>&1
  elif [[ "${strict_mode}" == "true" ]]; then
    echo "[nginx-runtime-gate] openssl is required to synthesize fixture certs" >&2
    exit 1
  else
    skip_nginx_check="true"
  fi
fi

bash tools/ops/render-nginx-runtime-config.sh "${rendered_config}"
mkdir -p "${tmp_dir}/logs"
prepare_nginx_test_config

if contains_pattern '${'; then
  echo "[nginx-runtime-gate] unresolved template placeholder remains in rendered config" >&2
  exit 1
fi

if ! contains_pattern "real_ip_header X-Forwarded-For;" ||
  ! contains_pattern "real_ip_recursive on;" ||
  ! contains_pattern "set_real_ip_from 10.60.0.0/16;"; then
  echo "[nginx-runtime-gate] rendered config must include OCI trusted proxy real IP defaults" >&2
  exit 1
fi

if ! contains_pattern "limit_req zone=aquila_bank_transaction_hot_per_ip burst=12 delay=1;" ||
  ! contains_pattern "limit_req zone=aquila_bank_transaction_archive_per_ip burst=12 delay=1;"; then
  echo "[nginx-runtime-gate] rendered config must use transaction-read small delay queue defaults" >&2
  exit 1
fi

if ! command -v nginx >/dev/null 2>&1; then
  if [[ "${strict_mode}" == "true" ]]; then
    echo "[nginx-runtime-gate] nginx binary is required in strict mode" >&2
    exit 1
  fi
  echo "[nginx-runtime-gate] rendered config check passed (nginx binary not found, nginx -t skipped)"
  exit 0
fi

if [[ "${skip_nginx_check}" == "true" || ! -f "${NGINX_SSL_CERTIFICATE_PATH}" || ! -f "${NGINX_SSL_CERTIFICATE_KEY_PATH}" ]]; then
  if [[ "${strict_mode}" == "true" ]]; then
    echo "[nginx-runtime-gate] runtime cert files are required in strict mode" >&2
    exit 1
  fi
  echo "[nginx-runtime-gate] rendered config check passed (runtime cert fixture unavailable, nginx -t skipped)"
  exit 0
fi

nginx -t -p "${tmp_dir}" -c "${rendered_config}" >/dev/null
echo "[nginx-runtime-gate] nginx -t passed"
