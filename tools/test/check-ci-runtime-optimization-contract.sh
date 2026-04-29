#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

backend_ci="${ROOT_DIR}/.github/workflows/backend-ci.yml"
frontend_ci="${ROOT_DIR}/.github/workflows/frontend-ci.yml"
main_ci="${ROOT_DIR}/.github/workflows/main-ci.yml"
workflow_contract_ci="${ROOT_DIR}/.github/workflows/workflow-contract-ci.yml"
query_plan_gate="${ROOT_DIR}/tools/test/run-transaction-query-plan-regression-gate.sh"

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

require_file "${backend_ci}"
require_file "${frontend_ci}"
require_file "${main_ci}"
require_file "${workflow_contract_ci}"
require_file "${query_plan_gate}"

if (( failures == 0 )); then
  require_contains "${backend_ci}" "Detect backend CI scope"
  require_contains "${backend_ci}" "query_plan_required"
  require_contains "${backend_ci}" "./gradlew ciFastCheck"
  require_contains "${backend_ci}" "./gradlew queryPlanTest"
  require_contains "${backend_ci}" "steps.scope.outputs.query_plan_required == 'true'"
  require_not_contains "${backend_ci}" '      - ".github/workflows/**"'

  require_not_contains "${frontend_ci}" '      - ".github/workflows/**"'

  require_contains "${workflow_contract_ci}" "CI Workflow Contract"
  require_contains "${workflow_contract_ci}" "tools/test/check-ci-runtime-optimization-contract.sh"

  require_contains "${main_ci}" "./gradlew check jacocoFullTestReport"
  require_not_contains "${main_ci}" "tools/test/run-transaction-query-plan-regression-gate.sh"

  require_contains "${query_plan_gate}" "./back/gradlew -p back queryPlanTest"
fi

if (( failures > 0 )); then
  printf '[ci-runtime-contract] failed: %d contract violation(s)\n' "${failures}" >&2
  exit 1
fi

printf '[ci-runtime-contract] ok\n'
