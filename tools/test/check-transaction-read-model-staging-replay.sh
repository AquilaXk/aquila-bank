#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/transaction-read-model-staging-replay.sh"
workflow=".github/workflows/transaction-read-model-staging-replay.yml"

require_pattern() {
  local path="$1"
  local pattern="$2"
  if ! grep -F "$pattern" "$path" >/dev/null; then
    echo "[transaction-read-model-staging-replay] ${path} missing contract: ${pattern}" >&2
    exit 1
  fi
}

ruby -e "require 'yaml'; YAML.load_file('${workflow}'); puts 'ok'" >/dev/null

require_pattern "$script" 'STAGING_PUBLIC_API_BASE_URL="${STAGING_PUBLIC_API_BASE_URL:-}"'
require_pattern "$script" 'STAGING_PUBLIC_BASE_URL="${STAGING_PUBLIC_BASE_URL:-}"'
require_pattern "$script" 'STAGING_REPLAY_BASE_URL="${STAGING_REPLAY_BASE_URL:-}"'
require_pattern "$script" 'STAGING_BASE_URL="${STAGING_REPLAY_BASE_URL:-${STAGING_PUBLIC_BASE_URL:-${STAGING_PUBLIC_API_BASE_URL:-${STAGING_BASE_URL:-}}}}"'
require_pattern "$script" 'canonicalize_staging_base_url()'
require_pattern "$script" 'STAGING_BASE_URL="https://${STAGING_BASE_URL#http://}"'
require_pattern "$script" 'case "${STAGING_BASE_URL}" in'
require_pattern "$script" 'http://localhost|http://localhost:*|http://127.*|http://[[]::1[]]*)'
require_pattern "$script" 'canonicalize_staging_base_url'

require_pattern "$workflow" 'STAGING_REPLAY_BASE_URL="${STAGING_REPLAY_BASE_URL:-${STAGING_PUBLIC_BASE_URL:-${STAGING_PUBLIC_API_BASE_URL:-${STAGING_BASE_URL:-}}}}"'
require_pattern "$workflow" 'NGINX_SERVER_NAME="${NGINX_SERVER_NAME:-bank.aquilaxk.site}"'
require_pattern "$workflow" 'STAGING_PUBLIC_BASE_URL="${STAGING_PUBLIC_BASE_URL:-https://${NGINX_SERVER_NAME}}"'
require_pattern "$workflow" 'STAGING_PUBLIC_API_BASE_URL="${STAGING_PUBLIC_API_BASE_URL:-${STAGING_PUBLIC_BASE_URL}}"'
require_pattern "$workflow" 'STAGING_REPLAY_BASE_URL'
require_pattern "$workflow" 'STAGING_PUBLIC_API_BASE_URL'
require_pattern "$workflow" 'STAGING_PUBLIC_BASE_URL'

echo "[transaction-read-model-staging-replay] passed"
