#!/usr/bin/env bash
set -euo pipefail

bootstrap_script="ops/deploy/oci/bootstrap-self-hosted-runner.sh"
doctor_script="ops/deploy/oci/check-self-hosted-runner.sh"
observability_script="tools/ops/collect-oci-a1-direct-observability.sh"
doctor_workflow=".github/workflows/oci-a1-runner-doctor.yml"
staging_workflow=".github/workflows/staging-deploy.yml"
delivery_doc="docs/delivery-flow.md"
deploy_doc="ops/deploy/oci/README.md"

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
    echo "[oci-runner-automation] missing file: $file" >&2
    exit 1
  fi
}

require_pattern() {
  local pattern="$1"
  local file="$2"
  if ! contains "$pattern" "$file"; then
    echo "[oci-runner-automation] missing pattern in $file: $pattern" >&2
    exit 1
  fi
}

reject_pattern() {
  local pattern="$1"
  local file="$2"
  if contains "$pattern" "$file"; then
    echo "[oci-runner-automation] forbidden pattern in $file: $pattern" >&2
    exit 1
  fi
}

echo "[oci-runner-automation] required files"
require_file "$bootstrap_script"
require_file "$doctor_script"
require_file "$observability_script"
require_file "$doctor_workflow"
require_file "$staging_workflow"
require_file "$delivery_doc"
require_file "$deploy_doc"

echo "[oci-runner-automation] shell syntax"
bash -n "$bootstrap_script"
bash -n "$doctor_script"
bash -n "$observability_script"
bash -n "$0"

echo "[oci-runner-automation] yaml syntax"
ruby -e 'require "yaml"; YAML.load_file(ARGV.fetch(0)); YAML.load_file(ARGV.fetch(1)); puts "ok"' \
  "$doctor_workflow" "$staging_workflow" >/dev/null

echo "[oci-runner-automation] bootstrap contract"
bootstrap_patterns=(
  "GITHUB_RUNNER_TOKEN"
  "GITHUB_REPOSITORY_SLUG"
  "RUNNER_LABELS"
  "oci-a1-staging"
  'runner_arch="arm64"'
  'actions-runner-linux-${runner_arch}'
  "svc.sh install"
  "svc.sh start"
  "AQUILA_CONFIGURE_PASSWORDLESS_SUDO"
  "/etc/sudoers.d/aquila-github-runner"
  "apt-get install"
  "postgresql-client"
)
for pattern in "${bootstrap_patterns[@]}"; do
  require_pattern "$pattern" "$bootstrap_script"
done
reject_pattern "RUNNER_TOKEN=" "$bootstrap_script"
reject_pattern "GITHUB_RUNNER_TOKEN=" "$bootstrap_script"

echo "[oci-runner-automation] doctor contract"
doctor_patterns=(
  "OCI_A1_RUNNER_CHECK_NETWORK"
  "OCI_A1_RUNNER_CHECK_GHCR"
  "base64 curl jq psql"
  "sudo -n true"
  "docker info"
  "https://github.com"
  "https://ghcr.io/v2/"
)
for pattern in "${doctor_patterns[@]}"; do
  require_pattern "$pattern" "$doctor_script"
done

echo "[oci-runner-automation] direct observability contract"
observability_patterns=(
  "OCI_A1_OBSERVABILITY_DOCKER_CONTEXTS"
  "OCI_A1_OBSERVABILITY_TIMEOUT_SECONDS"
  "OCI_A1_OBSERVABILITY_LOG_TAIL_LINES"
  "docker context inspect"
  "docker ps"
  "docker stats --no-stream"
  "docker logs"
  "sanitize_log"
  "no observable Docker contexts"
)
for pattern in "${observability_patterns[@]}"; do
  require_pattern "$pattern" "$observability_script"
done

OCI_A1_RUNNER_CHECK_NETWORK=false \
OCI_A1_RUNNER_CHECK_GHCR=false \
  "$doctor_script" --dry-run >/dev/null

echo "[oci-runner-automation] workflow contract"
workflow_patterns=(
  "name: OCI A1 Runner Doctor"
  "workflow_dispatch:"
  "runs-on: [self-hosted, oci-a1-staging]"
  "ops/deploy/oci/check-self-hosted-runner.sh"
  "tools/ops/collect-oci-a1-direct-observability.sh"
  "Upload OCI A1 direct observability artifact"
  "build/reports/oci-a1-direct-observability/"
)
for pattern in "${workflow_patterns[@]}"; do
  require_pattern "$pattern" "$doctor_workflow"
done
require_pattern "ops/deploy/oci/check-self-hosted-runner.sh" "$staging_workflow"
reject_pattern "for command_name in base64 curl jq psql" "$staging_workflow"

echo "[oci-runner-automation] docs contract"
doc_patterns=(
  "bootstrap-self-hosted-runner.sh"
  "check-self-hosted-runner.sh"
  "GITHUB_RUNNER_TOKEN"
  "oci-a1-staging"
)
for pattern in "${doc_patterns[@]}"; do
  require_pattern "$pattern" "$delivery_doc"
  require_pattern "$pattern" "$deploy_doc"
done

echo "[oci-runner-automation] contract check passed"
