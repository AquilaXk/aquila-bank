#!/usr/bin/env bash
set -euo pipefail

deploy_path="ops/deploy/oci/bluegreen-deploy.sh"

if [[ ! -f "${deploy_path}" ]]; then
  echo "[bluegreen-nginx-reload-fallback] missing deploy script: ${deploy_path}" >&2
  exit 1
fi

bash -n "${deploy_path}"

require_pattern() {
  local pattern="$1"
  if ! grep -Fq -- "${pattern}" "${deploy_path}"; then
    echo "[bluegreen-nginx-reload-fallback] missing deploy contract: ${pattern}" >&2
    exit 1
  fi
}

require_pattern "reload_nginx_or_recreate()"
require_pattern "nginx reload failed; recreate nginx container with active config"
require_pattern "docker rm -f \"\${NGINX_CONTAINER}\""
require_pattern "ensure_nginx_container"
require_pattern "nginx reload failed after previous config restore; recreate nginx container with previous config"
require_pattern "reload_nginx_or_recreate \"\${green}\""
require_pattern "reload_nginx_or_recreate \"\${green}\" \"rollback\""

echo "[bluegreen-nginx-reload-fallback] passed"
