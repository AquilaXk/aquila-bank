#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/staging-deploy.yml"
deploy_script="ops/deploy/oci/bluegreen-deploy.sh"
database_url_resolver_script="tools/ops/resolve-oci-a1-staging-database-url.sh"
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
require_file "$database_url_resolver_script"
require_file "$fixture_principal_script"
require_file "back/Dockerfile"
require_file "front/Dockerfile"
require_file "back/src/main/resources/application-oci-a1.yml"

echo "[oci-a1-bluegreen-cd] shell syntax"
bash -n "$deploy_script"
bash -n "$database_url_resolver_script"
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
  "OCI_A1_CAPACITY_PROFILE_ENABLED"
  "Create Staging Deployment"
  "Build And Push Backend Image"
  "Build And Push Frontend Image"
  "Deploy And Verify On OCI A1"
  "Finalize Staging Deployment"
  "runs-on: [self-hosted, oci-a1-staging]"
  "Load OCI A1 staging env"
  'source "${staging_env_path}"'
  "docker/setup-qemu-action@v3"
  "platforms: arm64"
  "docker buildx build"
  "--platform linux/arm64"
  "registry: ghcr.io"
  "OCI_A1_BACKEND_ENV_B64"
  "Check OCI self-hosted runner prerequisites"
  "ops/deploy/oci/check-self-hosted-runner.sh"
  "Resolve OCI A1 staging database URL"
  "tools/ops/resolve-oci-a1-staging-database-url.sh"
  "Run OCI A1 blue-green deploy locally"
  "ops/deploy/oci/bluegreen-deploy.sh"
  "Ensure staging fixture principal"
  "tools/ops/staging-fixture-principal-bootstrap.sh"
  "Mark staging deployment success"
  "Mark staging deployment failure"
  "Run staging post-deploy smoke"
  "Run transaction replay regression gate"
  "STAGING_REPLAY_ENABLED"
  "Record staging 100m replay evidence"
  "staging-100m-replay"
)
for pattern in "${workflow_patterns[@]}"; do
  require_pattern "$pattern" "$workflow"
done

if command -v ruby >/dev/null 2>&1; then
  ruby <<'RUBY'
require "yaml"

workflow = YAML.load_file(".github/workflows/staging-deploy.yml")
jobs = workflow.fetch("jobs")
frontend_job = jobs.fetch("frontend-image")
frontend_runner_labels = Array(frontend_job.fetch("runs-on"))
unless frontend_runner_labels.include?("self-hosted") && frontend_runner_labels.include?("oci-a1-staging")
  abort("frontend image job must run on OCI A1 self-hosted ARM64 runner")
end

frontend_steps = frontend_job.fetch("steps")
frontend_step_names = frontend_steps.map { |step| step["name"] }
if frontend_steps.any? { |step| step["uses"] == "docker/setup-qemu-action@v3" }
  abort("frontend image job must not use QEMU for ARM64 image build")
end

prerequisite_index = frontend_step_names.index("Check OCI self-hosted runner prerequisites") ||
  abort("frontend image job must check OCI self-hosted runner prerequisites")
docker_config_index = frontend_step_names.index("Prepare frontend Docker config") ||
  abort("frontend image job must prepare isolated Docker config")
buildx_index = frontend_step_names.index("Set up Docker Buildx") ||
  abort("frontend image job must set up Docker Buildx")
build_index = frontend_step_names.index("Build and push frontend image") ||
  abort("frontend image job must build and push frontend image")
abort("frontend runner prerequisites must run before frontend image build") unless prerequisite_index < build_index
abort("frontend Docker config must be prepared before buildx setup") unless docker_config_index < buildx_index

docker_config_run = frontend_steps.fetch(docker_config_index).fetch("run")
abort("frontend Docker config must use RUNNER_TEMP") unless docker_config_run.include?("RUNNER_TEMP") && docker_config_run.include?("DOCKER_CONFIG")
abort("frontend Docker config must be persisted through GITHUB_ENV") unless docker_config_run.include?("GITHUB_ENV")

build_run = frontend_steps.fetch(build_index).fetch("run")
abort("frontend image must remain linux/arm64") unless build_run.include?("--platform linux/arm64")
RUBY
fi

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
  "DOCKER_CONFIG"
  "mktemp -d"
  "connect_postgres_container"
  "preflight_backend_database"
  "OCI_A1_CAPACITY_PROFILE_ENABLED"
  "backend_spring_profiles_active"
  ",oci-a1"
  'SPRING_PROFILES_ACTIVE="${backend_profiles}"'
  "backend_capacity_profile_env_args"
  'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=${OCI_A1_TRANSACTION_READ_ADMISSION_MAX:-8}'
  'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_MIN=${OCI_A1_TRANSACTION_READ_ADMISSION_MIN:-6}'
  'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_MAX=${OCI_A1_TRANSACTION_READ_ADMISSION_ADAPTIVE_MAX:-12}'
  'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_LOW_SATURATION_INCREASE_EVERY_SUCCESSES=${OCI_A1_TRANSACTION_READ_ADMISSION_LOW_SATURATION_INCREASE_EVERY_SUCCESSES:-16}'
  'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_HOT_MAX=${OCI_A1_TRANSACTION_READ_HOT_ADMISSION_MAX:-${OCI_A1_TRANSACTION_READ_ADMISSION_MAX:-8}}'
  'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ARCHIVE_MAX=${OCI_A1_TRANSACTION_READ_ARCHIVE_ADMISSION_MAX:-6}'
  "POSTGRES_CONTAINER_NAME"
  "POSTGRES_LOG_TAIL_LINES"
  "POSTGRES_BOOTSTRAP_ENABLED"
  "POSTGRES_IMAGE"
  "POSTGRES_DATA_VOLUME"
  "ensure_postgres_container_for_host"
  "start_postgres_systemd_service"
  "start_postgres_docker_container"
  'com.aquilabank.service=postgres'
  'com.aquilabank.service=backend'
  'com.aquilabank.service=nginx'
  'com.aquilabank.slot=${green}'
  'com.aquilabank.runtime=oci-a1'
  "write_postgres_env_file"
  "systemctl enable --now aquila-postgres.service"
  "docker volume create"
  "bootstrap postgres container"
  "require_postgres_container_for_host"
  "diagnose_postgres_preflight"
  "backend DB host requires PostgreSQL container"
  'docker logs --tail="${POSTGRES_LOG_TAIL_LINES}"'
  "docker inspect -f"
  "aquila-postgres"
  "aquila-bank-backend-a"
  "aquila-bank-backend-b"
  "aquila-bank-front-a"
  "aquila-bank-front-b"
  "host.docker.internal:host-gateway"
  "/actuator/health"
  'proxy_set_header Host ${backend_proxy_host};'
  'proxy_set_header X-Request-Id \$request_id;'
  'proxy_set_header X-K6-Run-Id \$http_x_k6_run_id;'
  '"upstream_status":"\$upstream_status"'
  '"limit_req_status":"\$limit_req_status"'
  '"reject_source":"\$sent_http_x_aquila_reject_source"'
  '"reject_reason":"\$sent_http_x_aquila_reject_reason"'
  '"k6_run_id":"\$http_x_k6_run_id"'
  "limit_req_status 429;"
  'limit_req_zone \$binary_remote_addr zone=aquila_bank_api_per_ip:10m rate=30r/s;'
  'limit_req_zone \$binary_remote_addr zone=aquila_bank_auth_per_ip:10m rate=5r/s;'
  'limit_req_zone \$binary_remote_addr zone=aquila_bank_transaction_hot_per_ip:10m rate=${transaction_read_hot_rate_rps}r/s;'
  'limit_req_zone \$binary_remote_addr zone=aquila_bank_transaction_archive_per_ip:10m rate=${transaction_read_archive_rate_rps}r/s;'
  'limit_req_zone \$binary_remote_addr zone=aquila_bank_transfer_per_ip:10m rate=3r/s;'
  'proxy_set_header X-Forwarded-Host \$host;'
  "error_page 429 = @aquila_edge_rate_limited;"
  "location @aquila_edge_rate_limited"
  "add_header X-Aquila-Reject-Source nginx-edge always;"
  "add_header X-Aquila-Reject-Reason edge-rate-limit always;"
  'add_header Retry-After ${edge_retry_after_seconds} always;'
  'add_header X-RateLimit-Retry-After-Millis ${edge_retry_after_millis} always;'
  'add_header X-RateLimit-Retry-Jitter-Millis ${edge_retry_jitter_millis} always;'
  'add_header X-Aquila-Edge-Limit-Status \$limit_req_status always;'
  '"source":"nginx-edge"'
  "location = /api/v1/notifications/stream"
  "proxy_buffering off;"
  "location = /api/v1/auth/login"
  "location = /api/v1/auth/refresh"
  "location = /api/v1/auth/password-recovery/request"
  "limit_req zone=aquila_bank_auth_per_ip burst=10 nodelay;"
  "location = /api/v1/transactions"
  "location = /api/v1/transactions/archive"
  'limit_req zone=aquila_bank_transaction_hot_per_ip burst=${transaction_read_hot_burst} nodelay;'
  'limit_req zone=aquila_bank_transaction_archive_per_ip burst=${transaction_read_archive_burst} nodelay;'
  "location = /api/v1/transfers"
  "location ~ ^/api/v1/transfers/[^/]+/reversal$"
  "limit_req zone=aquila_bank_transfer_per_ip burst=6 nodelay;"
  "keepalive_requests 1000;"
  'backend_api_keepalive_timeout_seconds="${NGINX_BACKEND_API_KEEPALIVE_TIMEOUT_SECONDS:-2}"'
  'keepalive_timeout ${backend_api_keepalive_timeout_seconds}s;'
  "proxy_next_upstream error timeout http_502;"
  "proxy_next_upstream_tries 2;"
  "proxy_next_upstream_timeout 2s;"
  "proxy_socket_keepalive on;"
  "limit_req zone=aquila_bank_api_per_ip burst=20 delay=5;"
  "nginx -s reload"
  "ensure_nginx_config_visible"
  'docker exec "${NGINX_CONTAINER}" grep -Fq'
  "stale nginx config bind mount detected"
  'docker rm -f "${NGINX_CONTAINER}"'
  'if [[ -e "${active_config}" ]]; then'
  'cat "${next_config}" >"${active_config}"'
  'docker rm -f "$(slot_name backend "${green}")"'
)
for pattern in "${script_patterns[@]}"; do
  require_pattern "$pattern" "$deploy_script"
done
reject_pattern "--platform linux/amd64,linux/arm64" "$workflow"

echo "[oci-a1-bluegreen-cd] database URL resolver contract"
database_url_resolver_patterns=(
  "OCI_A1_BACKEND_ENV_B64"
  "SPRING_DATASOURCE_URL"
  "SPRING_DATASOURCE_USERNAME"
  "SPRING_DATASOURCE_PASSWORD"
  "POSTGRES_HOST_BIND"
  "postgresql://%s:%s@%s:%s/%s"
)
for pattern in "${database_url_resolver_patterns[@]}"; do
  require_pattern "$pattern" "$database_url_resolver_script"
done

echo "[oci-a1-bluegreen-cd] fixture principal contract"
fixture_principal_patterns=(
  "STAGING_REPLAY_USER_ID"
  "<<'SQL'"
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
reject_pattern "--command" "$fixture_principal_script"

echo "[oci-a1-bluegreen-cd] terraform contract"
require_pattern "http_ingress_cidr" "${terraform_dir}/variables.tf"
require_pattern "HTTP ingress for OCI A1 staging" "${terraform_dir}/network.tf"
require_pattern "min = 80" "${terraform_dir}/network.tf"

echo "[oci-a1-bluegreen-cd] docs contract"
if [[ -f "$delivery_doc" ]]; then
  doc_patterns=(
    "OCI_A1_STAGING_ENV"
    "OCI self-hosted runner"
    "oci-a1-staging"
    "linux/arm64"
    "OCI_A1_BACKEND_ENV_B64"
    "STAGING_OCI_A1_DATABASE_URL"
    "STAGING_REPLAY_USER_ID"
    "STAGING_BASE_URL"
  )
  for pattern in "${doc_patterns[@]}"; do
    require_pattern "$pattern" "$delivery_doc"
  done
  reject_pattern "OCI_A1_SSH_" "$delivery_doc"
  reject_pattern "ssh-keyscan" "$delivery_doc"
else
  echo "[oci-a1-bluegreen-cd] skip local delivery doc contract: $delivery_doc"
fi
require_pattern "self-hosted runner" "ops/deploy/oci/README.md"
require_pattern "Verify staging 100m replay evidence success" ".github/workflows/production-promotion.yml"
require_pattern "staging-100m-replay" ".github/workflows/production-promotion.yml"
reject_pattern "OCI_A1_SSH_" "ops/deploy/oci/README.md"
reject_pattern "ssh-keyscan" "ops/deploy/oci/README.md"
if [[ -f "$production_doc" ]]; then
  require_pattern "same SHA" "$production_doc"
else
  echo "[oci-a1-bluegreen-cd] skip local production doc contract: $production_doc"
fi

echo "[oci-a1-bluegreen-cd] contract check passed"
