#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-deploy-drain-oci-k6.sh"

echo "[transaction-read-deploy-drain-oci-k6] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

output_dir="${temp_dir}/output"
name="deploy-drain-oci-k6-check"
run_id="deploy-drain-oci-k6-20260507"

echo "[transaction-read-deploy-drain-oci-k6] print plan"
plan="$(
  DEPLOY_DRAIN_OCI_MODE=fixture \
  DEPLOY_DRAIN_OCI_NAME="${name}" \
  DEPLOY_DRAIN_OCI_RUN_ID="${run_id}" \
  DEPLOY_DRAIN_OCI_OUTPUT_DIR="${output_dir}" \
  DEPLOY_DRAIN_OCI_BASE_URL="https://staging.example.invalid" \
    bash "${runner}" --print-plan
)"
grep -F "mode=fixture" <<<"${plan}" >/dev/null
grep -F "name=${name}" <<<"${plan}" >/dev/null
grep -F "run_id=${run_id}" <<<"${plan}" >/dev/null
grep -F "k6_script=ops/k6/transaction-read-100m.js" <<<"${plan}" >/dev/null
grep -F "deploy_script=ops/deploy/oci/bluegreen-deploy.sh" <<<"${plan}" >/dev/null
grep -F "base_url=configured" <<<"${plan}" >/dev/null
grep -F "duration=5m" <<<"${plan}" >/dev/null
grep -F "deploy_actions=backend-restart,blue-green-drain" <<<"${plan}" >/dev/null
if grep -F "https://staging.example.invalid" <<<"${plan}" >/dev/null; then
  echo "print plan leaked raw base URL" >&2
  exit 1
fi

echo "[transaction-read-deploy-drain-oci-k6] nginx current-run log fallback contract"
grep -F 'docker exec "${container}" sh -c' "${runner}" >/dev/null
grep -F 'docker logs --since "${nginx_log_since}" "${container}"' "${runner}" >/dev/null
grep -F '"k6_run_id":"${run_id}"' "${runner}" >/dev/null
grep -F 'nginx-run-lines-missing' "${runner}" >/dev/null

echo "[transaction-read-deploy-drain-oci-k6] fixture generation"
evidence_env="$(
  DEPLOY_DRAIN_OCI_MODE=fixture \
  DEPLOY_DRAIN_OCI_NAME="${name}" \
  DEPLOY_DRAIN_OCI_RUN_ID="${run_id}" \
  DEPLOY_DRAIN_OCI_OUTPUT_DIR="${output_dir}" \
    bash "${runner}" | tail -1
)"
test "${evidence_env}" = "${output_dir}/${name}-oci-deploy-drain-evidence.env"
test -f "${evidence_env}"

# shellcheck disable=SC1090
source "${evidence_env}"
test "${DEPLOY_DRAIN_RUNNER_ENV_FORMAT}" = "oci-deploy-drain-v1"
test "${DEPLOY_DRAIN_RUNNER_RUN_SCRIPT}" = "${runner}"
test "${DEPLOY_DRAIN_RUNNER_K6_SUMMARY_REF}" = "${output_dir}/oci-deploy-drain/${name}-k6-summary.json"
test -f "${DEPLOY_DRAIN_RUNNER_K6_SUMMARY_REF}"
test -f "${DEPLOY_DRAIN_RUNNER_NGINX_ACCESS_REF}"
test -f "${DEPLOY_DRAIN_RUNNER_DEPLOY_EVENT_REF}"
test -f "${DEPLOY_DRAIN_RUNNER_DEPLOY_RETRY_CONTRACT_REF}"
test -f "${DEPLOY_DRAIN_RUNNER_DEPLOY_499_BUDGET_REF}"
test "${DEPLOY_DRAIN_RUNNER_EDGE_429_RATE}" = "0.04"
test "${DEPLOY_DRAIN_RUNNER_BACKEND_429_COUNT}" = "0"
test "${DEPLOY_DRAIN_RUNNER_UNKNOWN_429_COUNT}" = "0"
test "${DEPLOY_DRAIN_RUNNER_FIVE_XX_COUNT}" = "0"
test "${DEPLOY_DRAIN_RUNNER_NGINX_499_COUNT}" = "0"
test "${DEPLOY_DRAIN_RUNNER_DEPLOY_ACTIONS}" = "backend-restart,blue-green-drain"
test "${DEPLOY_DRAIN_RUNNER_CLIENT_RETRY_SUCCESS_COUNT}" = "2"
test "${DEPLOY_DRAIN_RUNNER_DEPLOY_RECONNECT_SUCCESS_COUNT}" = "2"

echo "[transaction-read-deploy-drain-oci-k6] live missing env fails before docker/deploy"
if DEPLOY_DRAIN_OCI_MODE=live \
  DEPLOY_DRAIN_OCI_NAME="${name}-missing" \
  DEPLOY_DRAIN_OCI_OUTPUT_DIR="${temp_dir}/missing-output" \
    bash "${runner}" >"${temp_dir}/missing.log" 2>&1; then
  echo "deploy drain OCI runner unexpectedly passed missing live env" >&2
  exit 1
fi
grep -F "DEPLOY_DRAIN_OCI_BASE_URL is required" "${temp_dir}/missing.log" >/dev/null
