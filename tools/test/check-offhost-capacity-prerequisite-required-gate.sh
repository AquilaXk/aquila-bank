#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/offhost-100m-capacity-prerequisite.yml"

echo "[offhost-capacity-prerequisite] workflow exists"
test -f "${workflow}"

echo "[offhost-capacity-prerequisite] workflow contract"
grep -F "name: off-host 100m capacity prerequisite" "${workflow}" >/dev/null
grep -F "workflow_dispatch:" "${workflow}" >/dev/null
grep -F "pull_request:" "${workflow}" >/dev/null
grep -F "CAPACITY_K6_GENERATOR_MODE: docker-context" "${workflow}" >/dev/null
grep -F "CAPACITY_REMOTE_PREFLIGHT: \"false\"" "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-100m-capacity-gates.sh --print-plan" "${workflow}" >/dev/null
grep -F "capacity-prerequisite-failure.env" "${workflow}" >/dev/null
grep -F "actions/upload-artifact@v4" "${workflow}" >/dev/null
grep -F "offhost-capacity-prerequisite-failure" "${workflow}" >/dev/null

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
