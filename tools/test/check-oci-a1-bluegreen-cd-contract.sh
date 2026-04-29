#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/staging-deploy.yml"
deploy_script="ops/deploy/oci/bluegreen-deploy.sh"
fixture_principal_script="tools/ops/staging-fixture-principal-bootstrap.sh"
delivery_doc="docs/delivery-flow.md"
production_doc="docs/production-promotion.md"
terraform_dir="infra/terraform/oci/always-free-a1-flex"

contains() {
  local pattern="$1"
  local file="$2"
  if command -v rg >/dev/null 2>&1; then
    rg -F --quiet -- "$pattern" "$file"
    return
  fi
  grep -Fq -- "$pattern" "$file"
}

require_file() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "[oci-a1-bluegreen-cd] missing file: $file" >&2
    exit 1
  fi
}

require_pattern() {
  local pattern="$1"
  local file="$2"
  if ! contains "$pattern" "$file"; then
    echo "[oci-a1-bluegreen-cd] missing pattern in $file: $pattern" >&2
    exit 1
  fi
}

reject_pattern() {
  local pattern="$1"
  local file="$2"
  if contains "$pattern" "$file"; then
    echo "[oci-a1-bluegreen-cd] forbidden scattered env pattern in $file: $pattern" >&2
    exit 1
  fi
}

reject_workflow_pattern() {
  local pattern="$1"
  local matches
  if command -v rg >/dev/null 2>&1; then
    matches="$(rg -n --glob '*.yml' --glob '*.yaml' -e "$pattern" .github/workflows || true)"
  else
    matches="$(grep -RInE --include='*.yml' --include='*.yaml' -- "$pattern" .github/workflows || true)"
  fi
  if [[ -n "$matches" ]]; then
    echo "[oci-a1-bluegreen-cd] forbidden non-OCI CD pattern in workflows: $pattern" >&2
    printf '%s\n' "$matches" >&2
    exit 1
  fi
}

echo "[oci-a1-bluegreen-cd] required files"
require_file "$workflow"
require_file "$deploy_script"
require_file "$fixture_principal_script"
require_file "$delivery_doc"
require_file "$production_doc"
require_file "back/Dockerfile"
require_file "front/Dockerfile"

echo "[oci-a1-bluegreen-cd] shell syntax"
bash -n "$deploy_script"
bash -n "$fixture_principal_script"
bash -n "$0"

if command -v ruby >/dev/null 2>&1; then
  ruby -e 'require "yaml"; YAML.load_file(ARGV.fetch(0))' "$workflow"
fi

echo "[oci-a1-bluegreen-cd] workflow contract"
workflow_patterns=(
  "workflow_run:"
  "- Main CI"
  "DEPLOY_TARGET_RUNTIME: oci-a1"
  "OCI_A1_STAGING_ENV"
  "Create Staging Deployment"
  "Build And Push Images"
  "Deploy And Verify On OCI A1"
  "Finalize Staging Deployment"
  "runs-on: [self-hosted, oci-a1-staging]"
  "Load OCI A1 staging env"
  'source "${staging_env_path}"'
  "docker buildx build"
  "registry: ghcr.io"
  "OCI_A1_BACKEND_ENV_B64"
  "Check OCI self-hosted runner prerequisites"
  "ops/deploy/oci/check-self-hosted-runner.sh"
  "Run OCI A1 blue-green deploy locally"
  "ops/deploy/oci/bluegreen-deploy.sh"
  "Ensure staging fixture principal"
  "tools/ops/staging-fixture-principal-bootstrap.sh"
  "Mark staging deployment success"
  "Mark staging deployment failure"
  "Run staging post-deploy smoke"
  "Run transaction replay regression gate"
)
for pattern in "${workflow_patterns[@]}"; do
  require_pattern "$pattern" "$workflow"
done

scattered_patterns=(
  "vars.OCI_A1_"
  "vars.STAGING_"
  "secrets.OCI_A1_SSH_"
  "secrets.OCI_A1_BACKEND_ENV"
  "secrets.OCI_A1_FRONTEND_ENV"
  "secrets.STAGING_"
  "secrets.ALERTMANAGER_"
)
for pattern in "${scattered_patterns[@]}"; do
  reject_pattern "$pattern" "$workflow"
done
reject_pattern "OCI_A1_SSH_" "$workflow"

echo "[oci-a1-bluegreen-cd] OCI-only workflow guard"
reject_workflow_pattern "aws-actions/configure-aws-credentials"
reject_workflow_pattern "AWS_ACCESS_KEY_ID"
reject_workflow_pattern "AWS_SECRET_ACCESS_KEY"
reject_workflow_pattern "aws ssm"
reject_workflow_pattern "AWS-RunShellScript"
reject_workflow_pattern "ssh-keyscan"
reject_workflow_pattern "appleboy/ssh-action"
reject_workflow_pattern "appleboy/scp-action"

echo "[oci-a1-bluegreen-cd] deploy script contract"
script_patterns=(
  "apt-get update"
  "docker.io"
  "aquila-bank-backend-a"
  "aquila-bank-backend-b"
  "aquila-bank-front-a"
  "aquila-bank-front-b"
  "host.docker.internal:host-gateway"
  "/actuator/health"
  'proxy_set_header Host ${backend_proxy_host};'
  'proxy_set_header X-Forwarded-Host \$host;'
  "location = /api/v1/notifications/stream"
  "proxy_buffering off;"
  "nginx -s reload"
  'docker rm -f "$(slot_name backend "${green}")"'
)
for pattern in "${script_patterns[@]}"; do
  require_pattern "$pattern" "$deploy_script"
done

echo "[oci-a1-bluegreen-cd] fixture principal contract"
fixture_principal_patterns=(
  "STAGING_REPLAY_USER_ID"
  "OVERRIDING SYSTEM VALUE"
  "INSERT INTO bank_account"
  "INSERT INTO bank_user"
  "INSERT INTO user_account_membership"
  "ON CONFLICT (user_id, account_id)"
  "pg_get_serial_sequence('bank_user', 'id')"
)
for pattern in "${fixture_principal_patterns[@]}"; do
  require_pattern "$pattern" "$fixture_principal_script"
done

echo "[oci-a1-bluegreen-cd] terraform contract"
require_pattern "http_ingress_cidr" "${terraform_dir}/variables.tf"
require_pattern "HTTP ingress for OCI A1 staging" "${terraform_dir}/network.tf"
require_pattern "min = 80" "${terraform_dir}/network.tf"

echo "[oci-a1-bluegreen-cd] docs contract"
doc_patterns=(
  "OCI_A1_STAGING_ENV"
  "OCI self-hosted runner"
  "oci-a1-staging"
  "OCI_A1_BACKEND_ENV_B64"
  "STAGING_OCI_A1_DATABASE_URL"
  "STAGING_REPLAY_USER_ID"
  "STAGING_BASE_URL"
)
for pattern in "${doc_patterns[@]}"; do
  require_pattern "$pattern" "$delivery_doc"
done
require_pattern "self-hosted runner" "ops/deploy/oci/README.md"
reject_pattern "OCI_A1_SSH_" "$delivery_doc"
reject_pattern "ssh-keyscan" "$delivery_doc"
reject_pattern "OCI_A1_SSH_" "ops/deploy/oci/README.md"
reject_pattern "ssh-keyscan" "ops/deploy/oci/README.md"
require_pattern "same SHA" "$production_doc"

echo "[oci-a1-bluegreen-cd] contract check passed"
