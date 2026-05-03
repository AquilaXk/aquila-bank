#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/offhost-100m-capacity-prerequisite.yml"

echo "[offhost-capacity-prerequisite] workflow exists"
test -f "${workflow}"

echo "[offhost-capacity-prerequisite] workflow contract"
grep -F "name: off-host 100m capacity prerequisite" "${workflow}" >/dev/null
grep -F "workflow_dispatch:" "${workflow}" >/dev/null
grep -F "pull_request:" "${workflow}" >/dev/null
grep -F "off-host capacity prerequisite contract" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'pull_request'" "${workflow}" >/dev/null
grep -F "tools/test/check-offhost-capacity-prerequisite-required-gate.sh" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'workflow_dispatch'" "${workflow}" >/dev/null
grep -F "environment:" "${workflow}" >/dev/null
grep -F "name: staging" "${workflow}" >/dev/null
grep -F 'OCI_A1_STAGING_ENV: ${{ secrets.OCI_A1_STAGING_ENV }}' "${workflow}" >/dev/null
grep -F "Load off-host capacity env" "${workflow}" >/dev/null
grep -F "CAPACITY_K6_DOCKER_CONTEXT_VAR" "${workflow}" >/dev/null
grep -F 'CAPACITY_REMOTE_PREFLIGHT="true"' "${workflow}" >/dev/null
grep -F "Run off-host remote preflight" "${workflow}" >/dev/null
grep -F "tools/test/run-offhost-capacity-env-doctor.sh" "${workflow}" >/dev/null
grep -F "offhost-capacity-preflight.log" "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-100m-capacity-gates.sh --print-plan" "${workflow}" >/dev/null
grep -F "capacity-prerequisite-plan.log" "${workflow}" >/dev/null
grep -F "actions/upload-artifact@" "${workflow}" >/dev/null
grep -F "offhost-capacity-prerequisite" "${workflow}" >/dev/null
grep -F "build/reports/k6/ci-offhost-100m-capacity-prerequisite/" "${workflow}" >/dev/null
if grep -F 'CAPACITY_K6_DOCKER_CONTEXT: ${{ inputs.docker_context || vars.CAPACITY_K6_DOCKER_CONTEXT }}' "${workflow}" >/dev/null; then
  echo "off-host workflow must not rely only on dispatch inputs and repository vars" >&2
  exit 1
fi

echo "[offhost-capacity-prerequisite] local fail-fast contract"
temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
prereq="${temp_dir}/capacity-prerequisite-failure.env"
if CAPACITY_NAME=ci-offhost-prereq-check \
  CAPACITY_PREREQUISITE_FAILURE_REPORT_PATH="${prereq}" \
  CAPACITY_K6_GENERATOR_MODE=docker-context \
    tools/test/run-transaction-100m-capacity-gates.sh --print-plan >/dev/null 2>&1; then
  echo "missing off-host capacity env unexpectedly passed local prerequisite check" >&2
  exit 1
fi
test -f "${prereq}"
grep -F "CAPACITY_PREREQUISITE_STATUS=failed" "${prereq}" >/dev/null
grep -F "CAPACITY_PREREQUISITE_FAILURE_REASON=missing-required-env" "${prereq}" >/dev/null
