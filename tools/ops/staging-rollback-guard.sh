#!/usr/bin/env bash
set -euo pipefail

STAGING_ROLLBACK_WEBHOOK_URL="${STAGING_ROLLBACK_WEBHOOK_URL:-}"
STAGING_ROLLBACK_TOKEN="${STAGING_ROLLBACK_TOKEN:-}"
DEPLOY_SHA="${DEPLOY_SHA:-}"
DEPLOY_LOG_URL="${DEPLOY_LOG_URL:-}"
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-}"
DEPLOYMENT_ID="${DEPLOYMENT_ID:-}"

if [[ -z "$STAGING_ROLLBACK_WEBHOOK_URL" && -z "$STAGING_ROLLBACK_TOKEN" ]]; then
  echo "[staging-rollback] rollback hook is not configured; skipping."
  exit 0
fi

if [[ -z "$STAGING_ROLLBACK_WEBHOOK_URL" || -z "$STAGING_ROLLBACK_TOKEN" ]]; then
  echo "[staging-rollback] rollback hook is partially configured; skipping." >&2
  exit 0
fi

if [[ -z "$DEPLOY_SHA" || -z "$GITHUB_REPOSITORY" ]]; then
  echo "[staging-rollback] DEPLOY_SHA and GITHUB_REPOSITORY are required." >&2
  exit 1
fi

payload="$(
  jq -n \
    --arg sha "$DEPLOY_SHA" \
    --arg repository "$GITHUB_REPOSITORY" \
    --arg run_url "$DEPLOY_LOG_URL" \
    --arg deployment_id "$DEPLOYMENT_ID" \
    '{
      sha: $sha,
      repository: $repository,
      environment: "staging",
      runUrl: $run_url,
      deploymentId: $deployment_id,
      reason: "post-deploy-smoke-failed"
    }'
)"

echo "[staging-rollback] dispatching rollback guard for ${DEPLOY_SHA}"
curl --fail-with-body --show-error --silent \
  --request POST \
  --header "Authorization: Bearer ${STAGING_ROLLBACK_TOKEN}" \
  --header "Content-Type: application/json" \
  --data "$payload" \
  "$STAGING_ROLLBACK_WEBHOOK_URL" >/dev/null

echo "[staging-rollback] dispatched"
