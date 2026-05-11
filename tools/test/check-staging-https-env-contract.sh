#!/usr/bin/env bash
set -euo pipefail

workflow_path=".github/workflows/staging-deploy.yml"
deploy_readme_path="ops/deploy/oci/README.md"

for path in "${workflow_path}" "${deploy_readme_path}"; do
  if [[ ! -f "${path}" ]]; then
    echo "[staging-https-env-contract] missing file: ${path}" >&2
    exit 1
  fi
done

require_pattern() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "${pattern}" "${path}"; then
    echo "[staging-https-env-contract] ${path} missing contract: ${pattern}" >&2
    exit 1
  fi
}

require_pattern "${workflow_path}" 'NGINX_SERVER_NAME="${NGINX_SERVER_NAME:-bank.aquilaxk.site}"'
require_pattern "${workflow_path}" 'NGINX_ENABLE_HTTPS="${NGINX_ENABLE_HTTPS:-auto}"'
require_pattern "${workflow_path}" 'NGINX_SSL_CERTIFICATE_PATH="${NGINX_SSL_CERTIFICATE_PATH:-/etc/letsencrypt/live/${NGINX_SERVER_NAME}/fullchain.pem}"'
require_pattern "${workflow_path}" 'NGINX_SSL_CERTIFICATE_KEY_PATH="${NGINX_SSL_CERTIFICATE_KEY_PATH:-/etc/letsencrypt/live/${NGINX_SERVER_NAME}/privkey.pem}"'
require_pattern "${workflow_path}" 'STAGING_PUBLIC_BASE_URL="${STAGING_PUBLIC_BASE_URL:-https://${NGINX_SERVER_NAME}}"'
require_pattern "${workflow_path}" 'STAGING_PUBLIC_FRONTEND_SMOKE_ENABLED="${STAGING_PUBLIC_FRONTEND_SMOKE_ENABLED:-true}"'
require_pattern "${workflow_path}" 'PUBLIC_EDGE_BASE_URL="${STAGING_PUBLIC_BASE_URL}"'
require_pattern "${workflow_path}" 'PUBLIC_EDGE_EXPECT_HSTS=true'
require_pattern "${workflow_path}" 'tools/test/check-public-edge-bot-guard-live.sh'
require_pattern "${workflow_path}" 'PUBLIC_FRONTEND_BASE_URL="${STAGING_PUBLIC_BASE_URL}"'
require_pattern "${workflow_path}" 'PUBLIC_FRONTEND_EXPECT_HSTS=true'
require_pattern "${workflow_path}" 'tools/test/check-public-frontend-live-smoke.sh'

require_pattern "${deploy_readme_path}" 'NGINX_SERVER_NAME=bank.aquilaxk.site'
require_pattern "${deploy_readme_path}" 'NGINX_ENABLE_HTTPS=auto'
require_pattern "${deploy_readme_path}" 'NGINX_SSL_CERTIFICATE_PATH=/etc/letsencrypt/live/bank.aquilaxk.site/fullchain.pem'
require_pattern "${deploy_readme_path}" 'NGINX_SSL_CERTIFICATE_KEY_PATH=/etc/letsencrypt/live/bank.aquilaxk.site/privkey.pem'
require_pattern "${deploy_readme_path}" 'Cloudflare/WAF'

echo "[staging-https-env-contract] passed"
