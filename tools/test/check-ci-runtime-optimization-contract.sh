#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

backend_ci="${ROOT_DIR}/.github/workflows/backend-ci.yml"
frontend_ci="${ROOT_DIR}/.github/workflows/frontend-ci.yml"
main_ci="${ROOT_DIR}/.github/workflows/main-ci.yml"
staging_deploy="${ROOT_DIR}/.github/workflows/staging-deploy.yml"
backend_runtime_dockerfile="${ROOT_DIR}/back/Dockerfile.runtime"
backend_dockerignore="${ROOT_DIR}/back/.dockerignore"
workflow_contract_ci="${ROOT_DIR}/.github/workflows/workflow-contract-ci.yml"
workflow_dir="${ROOT_DIR}/.github/workflows"
query_plan_gate="${ROOT_DIR}/tools/test/run-transaction-query-plan-regression-gate.sh"
flyway_migration_gate="${ROOT_DIR}/tools/test/check-flyway-backward-compatible-migrations.sh"

failures=0

require_file() {
  local path="$1"
  if [[ ! -f "${path}" ]]; then
    printf '[ci-runtime-contract] missing file: %s\n' "${path#${ROOT_DIR}/}" >&2
    failures=$((failures + 1))
  fi
}

require_contains() {
  local path="$1"
  local needle="$2"
  if ! grep -Fq -- "${needle}" "${path}"; then
    printf '[ci-runtime-contract] %s must contain: %s\n' "${path#${ROOT_DIR}/}" "${needle}" >&2
    failures=$((failures + 1))
  fi
}

require_not_contains() {
  local path="$1"
  local needle="$2"
  if grep -Fq -- "${needle}" "${path}"; then
    printf '[ci-runtime-contract] %s must not contain: %s\n' "${path#${ROOT_DIR}/}" "${needle}" >&2
    failures=$((failures + 1))
  fi
}

require_workflows_contains() {
  local needle="$1"
  if ! grep -R -Fq -- "${needle}" "${workflow_dir}"; then
    printf '[ci-runtime-contract] workflows must contain: %s\n' "${needle}" >&2
    failures=$((failures + 1))
  fi
}

require_workflows_not_contains() {
  local needle="$1"
  if grep -R -Fq -- "${needle}" "${workflow_dir}"; then
    printf '[ci-runtime-contract] workflows must not contain: %s\n' "${needle}" >&2
    failures=$((failures + 1))
  fi
}

require_file "${backend_ci}"
require_file "${frontend_ci}"
require_file "${main_ci}"
require_file "${staging_deploy}"
require_file "${backend_runtime_dockerfile}"
require_file "${backend_dockerignore}"
require_file "${workflow_contract_ci}"
require_file "${query_plan_gate}"
require_file "${flyway_migration_gate}"

if (( failures == 0 )); then
  require_contains "${backend_ci}" "Detect backend CI scope"
  require_contains "${backend_ci}" "Run Flyway backward-compatible migration gate"
  require_contains "${backend_ci}" "tools/test/check-flyway-backward-compatible-migrations.sh"
  require_contains "${backend_ci}" "query_plan_required"
  require_contains "${backend_ci}" "./gradlew ciFastCheck"
  require_contains "${backend_ci}" "./gradlew queryPlanTest"
  require_contains "${backend_ci}" "steps.scope.outputs.query_plan_required == 'true'"
  require_not_contains "${backend_ci}" '      - ".github/workflows/**"'

  require_not_contains "${frontend_ci}" '      - ".github/workflows/**"'

  require_contains "${workflow_contract_ci}" "CI Workflow Contract"
  require_contains "${workflow_contract_ci}" "tools/test/check-ci-runtime-optimization-contract.sh"
  require_contains "${workflow_contract_ci}" "tools/test/check-flyway-backward-compatible-migrations.sh"

  require_contains "${main_ci}" "./gradlew check jacocoFullTestReport"
  require_not_contains "${main_ci}" "tools/test/run-transaction-query-plan-regression-gate.sh"

  require_contains "${query_plan_gate}" "./back/gradlew -p back queryPlanTest"
  require_contains "${flyway_migration_gate}" "flyway:allow-breaking-change"
  require_contains "${flyway_migration_gate}" "DROP TABLE"
  require_contains "${flyway_migration_gate}" "ALTER TABLE accounts DROP COLUMN legacy_code"
  require_contains "${flyway_migration_gate}" "--self-test"

  require_contains "${staging_deploy}" "cancel-in-progress: true"
  require_contains "${staging_deploy}" "backend-image:"
  require_contains "${staging_deploy}" "frontend-image:"
  require_contains "${staging_deploy}" "validate-latest-before-deploy:"
  require_contains "${staging_deploy}" "Validate latest main before deploy"
  require_contains "${staging_deploy}" "Set up backend Java 21"
  require_contains "${staging_deploy}" "Set up backend Gradle"
  require_contains "${staging_deploy}" "./gradlew bootJar --no-daemon --stacktrace"
  require_contains "${staging_deploy}" "--file ./back/Dockerfile.runtime"
  require_contains "${staging_deploy}" 'BACKEND_IMAGE: ${{ needs.backend-image.outputs.backend_image }}'
  require_contains "${staging_deploy}" 'FRONTEND_IMAGE: ${{ needs.frontend-image.outputs.frontend_image }}'
  require_contains "${staging_deploy}" "Mark stale staging deployment inactive"

  require_contains "${backend_runtime_dockerfile}" "FROM eclipse-temurin:21-jre"
  require_contains "${backend_runtime_dockerfile}" 'ARG JAR_FILE=build/libs/*.jar'
  require_contains "${backend_runtime_dockerfile}" 'COPY ${JAR_FILE} /app/app.jar'
  require_contains "${backend_dockerignore}" "!build/libs/*.jar"

  require_workflows_not_contains "actions/checkout@v4"
  require_workflows_not_contains "actions/upload-artifact@v4"
  require_workflows_not_contains "actions/setup-node@v4"
  require_workflows_contains "actions/checkout@v5"
  require_workflows_contains "actions/upload-artifact@v7"
  require_workflows_contains "actions/setup-node@v6"
fi

if (( failures > 0 )); then
  printf '[ci-runtime-contract] failed: %d contract violation(s)\n' "${failures}" >&2
  exit 1
fi

printf '[ci-runtime-contract] ok\n'
