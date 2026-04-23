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

if rg -F --quiet '${' "${rendered_config}"; then
  echo "[nginx-runtime-gate] unresolved template placeholder remains in rendered config" >&2
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

mkdir -p "${tmp_dir}/logs"
nginx -t -p "${tmp_dir}" -c "${rendered_config}" >/dev/null
echo "[nginx-runtime-gate] nginx -t passed"
