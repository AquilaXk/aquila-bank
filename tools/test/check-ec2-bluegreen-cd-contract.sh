#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/ec2-bluegreen-deploy.yml"
deploy_script="ops/deploy/ec2/bluegreen-deploy.sh"
deploy_readme="ops/deploy/ec2/README.md"
terraform_dir="infra/terraform/aws/ec2-app-baseline"

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
    echo "[ec2-bluegreen-cd] missing file: $file" >&2
    exit 1
  fi
}

require_pattern() {
  local pattern="$1"
  local file="$2"
  if ! contains "$pattern" "$file"; then
    echo "[ec2-bluegreen-cd] missing pattern in $file: $pattern" >&2
    exit 1
  fi
}

echo "[ec2-bluegreen-cd] required files"
require_file "$workflow"
require_file "$deploy_script"
require_file "$deploy_readme"
require_file "back/Dockerfile"
require_file "front/Dockerfile"
require_file "${terraform_dir}/iam.tf"

echo "[ec2-bluegreen-cd] shell syntax"
bash -n "$deploy_script"
bash -n "$0"

if command -v ruby >/dev/null 2>&1; then
  ruby -e 'require "yaml"; YAML.load_file(ARGV.fetch(0))' "$workflow"
fi

echo "[ec2-bluegreen-cd] workflow contract"
workflow_patterns=(
  "workflow_run:"
  "workflow_dispatch:"
  "workflows:"
  "- Main CI"
  "docker/build-push-action@v6"
  "registry: ghcr.io"
  "AWS-RunShellScript"
  "aws ssm send-command"
  "EC2_BACKEND_ENV"
  "Mark deployment success"
  "Mark deployment failure"
  "env.EC2_PUBLIC_BASE_URL"
)
for pattern in "${workflow_patterns[@]}"; do
  require_pattern "$pattern" "$workflow"
done

echo "[ec2-bluegreen-cd] deploy script contract"
script_patterns=(
  "aquila-bank-backend-a"
  "aquila-bank-backend-b"
  "aquila-bank-front-a"
  "aquila-bank-front-b"
  "host.docker.internal:host-gateway"
  "/actuator/health"
  "location = /api/v1/notifications/stream"
  "proxy_buffering off;"
  "nginx -s reload"
  'docker rm -f "$(slot_name backend "${green}")"'
)
for pattern in "${script_patterns[@]}"; do
  require_pattern "$pattern" "$deploy_script"
done

echo "[ec2-bluegreen-cd] runtime image contract"
require_pattern "ENTRYPOINT [\"java\", \"-jar\", \"/app/app.jar\"]" "back/Dockerfile"
require_pattern "mkdir -p public" "front/Dockerfile"
require_pattern "COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./" "front/Dockerfile"
require_pattern "output: 'standalone'" "front/next.config.js"

echo "[ec2-bluegreen-cd] terraform contract"
require_pattern "AmazonSSMManagedInstanceCore" "${terraform_dir}/iam.tf"
require_pattern "iam_instance_profile" "${terraform_dir}/compute.tf"
require_pattern "http_ingress_cidr" "${terraform_dir}/security.tf"
require_pattern "from_port   = 80" "${terraform_dir}/security.tf"

echo "[ec2-bluegreen-cd] docs contract"
require_pattern "로컬 Mac Docker PostgreSQL" "$deploy_readme"
require_pattern "reverse SSH tunnel" "$deploy_readme"
require_pattern "EC2_BACKEND_ENV" "$deploy_readme"

echo "[ec2-bluegreen-cd] contract check passed"
