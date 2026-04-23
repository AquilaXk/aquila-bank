#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "::error::Missing ${name}."
    exit 1
  fi
}

verify_full_sha() {
  local name="$1"
  local value="$2"
  if [[ ! "${value}" =~ ^[0-9a-f]{40}$ ]]; then
    echo "::error::${name} must be a full lowercase 40-character commit SHA."
    exit 1
  fi
}

has_successful_staging_deployment() {
  local sha="$1"
  local deployment_ids
  deployment_ids="$(
    gh api --method GET "repos/${GITHUB_REPOSITORY}/deployments" \
      -f environment=staging \
      -f sha="${sha}" \
      --paginate \
      --jq ".[].id"
  )"

  if [ -z "${deployment_ids}" ]; then
    return 1
  fi

  local deployment_id
  while IFS= read -r deployment_id; do
    if [ -z "${deployment_id}" ]; then
      continue
    fi

    local state
    state="$(
      gh api --method GET "repos/${GITHUB_REPOSITORY}/deployments/${deployment_id}/statuses" \
        --jq ".[0].state // \"\""
    )"
    if [ "${state}" = "success" ]; then
      return 0
    fi
  done <<< "${deployment_ids}"

  return 1
}

if [ -z "${PRODUCTION_ROLLBACK_WEBHOOK_URL:-}" ] &&
  [ -z "${PRODUCTION_ROLLBACK_TOKEN:-}" ] &&
  [ -z "${PRODUCTION_ROLLBACK_TARGET_SHA:-}" ]; then
  echo "::notice::Production rollback hook is not configured. Workflow failure status remains the rollback guard."
  exit 0
fi

require_env PRODUCTION_ROLLBACK_WEBHOOK_URL
require_env PRODUCTION_ROLLBACK_TOKEN
require_env PRODUCTION_ROLLBACK_TARGET_SHA
require_env TARGET_SHA
require_env GITHUB_REPOSITORY
require_env PROMOTION_LOG_URL
require_env GH_TOKEN

verify_full_sha TARGET_SHA "${TARGET_SHA}"
verify_full_sha PRODUCTION_ROLLBACK_TARGET_SHA "${PRODUCTION_ROLLBACK_TARGET_SHA}"

if [ "${PRODUCTION_ROLLBACK_TARGET_SHA}" = "${TARGET_SHA}" ]; then
  echo "::error::Rollback target must differ from failed production TARGET_SHA."
  exit 1
fi

# rollback target은 staging success SHA로 제한해 검증되지 않은 commit 재승격을 막는다.
if ! has_successful_staging_deployment "${PRODUCTION_ROLLBACK_TARGET_SHA}"; then
  echo "::error::Rollback target ${PRODUCTION_ROLLBACK_TARGET_SHA} has no successful staging deployment."
  exit 1
fi

payload="$(
  jq -n \
    --arg failed_sha "${TARGET_SHA}" \
    --arg rollback_sha "${PRODUCTION_ROLLBACK_TARGET_SHA}" \
    --arg repository "${GITHUB_REPOSITORY}" \
    --arg run_url "${PROMOTION_LOG_URL}" \
    --arg deployment_id "${DEPLOYMENT_ID:-}" \
    '{
      failedSha: $failed_sha,
      rollbackSha: $rollback_sha,
      repository: $repository,
      environment: "production",
      runUrl: $run_url,
      deploymentId: $deployment_id,
      reason: "production-post-deploy-smoke-failed"
    }'
)"

curl --fail-with-body --show-error --silent \
  --request POST \
  --header "Authorization: Bearer ${PRODUCTION_ROLLBACK_TOKEN}" \
  --header "Content-Type: application/json" \
  --data "${payload}" \
  "${PRODUCTION_ROLLBACK_WEBHOOK_URL}"

echo "Production rollback guard dispatched."
