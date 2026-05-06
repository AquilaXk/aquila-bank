#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-mixed-workload-oci-k6.sh"
k6_script="ops/k6/transaction-read-mixed-workload-100m.js"

echo "[transaction-read-mixed-workload-oci-k6] files exist"
test -f "${runner}"
test -f "${k6_script}"

echo "[transaction-read-mixed-workload-oci-k6] shell syntax"
bash -n "${runner}"

echo "[transaction-read-mixed-workload-oci-k6] k6 mixed endpoints"
grep -F "/api/v1/transactions" "${k6_script}" >/dev/null
grep -F "/api/v1/transfers" "${k6_script}" >/dev/null
grep -F "/api/v1/auth/sessions" "${k6_script}" >/dev/null
grep -F "/api/v1/notifications" "${k6_script}" >/dev/null
grep -F "/api/v1/notifications/stream" "${k6_script}" >/dev/null
grep -F "aquila_mixed_read_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_write_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_auth_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_notification_count" "${k6_script}" >/dev/null
grep -F "aquila_mixed_sse_connect_count" "${k6_script}" >/dev/null

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

output_dir="${temp_dir}/output"
token_file="${temp_dir}/token.secret"
printf "secret-token-value\n" >"${token_file}"

echo "[transaction-read-mixed-workload-oci-k6] live plan"
plan="$(
  MIXED_WORKLOAD_OCI_NAME=mixed-oci-check \
  MIXED_WORKLOAD_OCI_RUN_ID=mixed-oci-run-001 \
  MIXED_WORKLOAD_OCI_OUTPUT_DIR="${output_dir}" \
  MIXED_WORKLOAD_OCI_DOCKER_CONTEXT=oci-k6-generator \
  MIXED_WORKLOAD_OCI_BASE_URL=http://192.0.2.20:8080 \
  MIXED_WORKLOAD_OCI_REMOTE_WORKDIR=/srv/aquila-bank \
  MIXED_WORKLOAD_OCI_DURATION=30m \
  MIXED_WORKLOAD_OCI_HOT_ACCOUNT_ID=101 \
  MIXED_WORKLOAD_OCI_HOT_FROM=2026-04-01T00:00:00Z \
  MIXED_WORKLOAD_OCI_HOT_TO=2026-04-30T23:59:59Z \
  MIXED_WORKLOAD_OCI_COLD_ACCOUNT_ID=202 \
  MIXED_WORKLOAD_OCI_COLD_FROM=2026-01-01T00:00:00Z \
  MIXED_WORKLOAD_OCI_COLD_TO=2026-01-31T23:59:59Z \
  MIXED_WORKLOAD_OCI_WRITE_SOURCE_ACCOUNT_ID=920000001 \
  MIXED_WORKLOAD_OCI_WRITE_TARGET_ACCOUNT_ID=920000002 \
  MIXED_WORKLOAD_OCI_AUTH_TOKEN_FILE="${token_file}" \
    bash "${runner}" --print-plan
)"
grep -F "mode=live" <<<"${plan}" >/dev/null
grep -F "k6_script=ops/k6/transaction-read-mixed-workload-100m.js" <<<"${plan}" >/dev/null
grep -F "docker_context=oci-k6-generator" <<<"${plan}" >/dev/null
grep -F "base_url=configured" <<<"${plan}" >/dev/null
grep -F "remote_workdir=/srv/aquila-bank" <<<"${plan}" >/dev/null
grep -F "duration=30m" <<<"${plan}" >/dev/null
grep -F "components=read,write,auth,notification,sse" <<<"${plan}" >/dev/null
grep -F "auth_token=present source=file" <<<"${plan}" >/dev/null
grep -F "evidence_env=${output_dir}/mixed-oci-check-oci-mixed-evidence.env" <<<"${plan}" >/dev/null
if grep -F "secret-token-value" <<<"${plan}" >/dev/null; then
  echo "auth token leaked into mixed workload OCI plan output" >&2
  exit 1
fi
if grep -F "http://192.0.2.20:8080" <<<"${plan}" >/dev/null; then
  echo "raw OCI base URL leaked into mixed workload OCI plan output" >&2
  exit 1
fi

echo "[transaction-read-mixed-workload-oci-k6] missing live runtime env fails sanitized"
if MIXED_WORKLOAD_OCI_NAME=mixed-oci-missing bash "${runner}" --print-plan >"${temp_dir}/missing.log" 2>&1; then
  echo "missing runtime env unexpectedly passed" >&2
  exit 1
fi
grep -F "MIXED_WORKLOAD_OCI_DOCKER_CONTEXT is required" "${temp_dir}/missing.log" >/dev/null

echo "[transaction-read-mixed-workload-oci-k6] fixture artifact generation"
generated_env="$(
  MIXED_WORKLOAD_OCI_MODE=fixture \
  MIXED_WORKLOAD_OCI_NAME=mixed-oci-fixture \
  MIXED_WORKLOAD_OCI_RUN_ID=mixed-oci-run-fixture \
  MIXED_WORKLOAD_OCI_OUTPUT_DIR="${output_dir}/fixture" \
    bash "${runner}" | tail -1
)"
test "${generated_env}" = "${output_dir}/fixture/mixed-oci-fixture-oci-mixed-evidence.env"
test -f "${generated_env}"

# shellcheck disable=SC1090
source "${generated_env}"
test "${MIXED_WORKLOAD_RUNNER_ENV_FORMAT}" = "oci-mixed-v1"
test "${MIXED_WORKLOAD_RUNNER_RUN_SCRIPT}" = "${runner}"
test "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENTS}" = "read,write,auth,notification,sse"
test -f "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}"
test -f "${MIXED_WORKLOAD_RUNNER_WORKLOAD_MIX_REF}"
test -f "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}"
test -f "${MIXED_WORKLOAD_RUNNER_OUTBOX_LAG_REF}"
test -f "${MIXED_WORKLOAD_RUNNER_READ_429_SOURCE_REF}"
grep -F $'read\tpass\t' "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}" >/dev/null
grep -F $'write\tpass\t' "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}" >/dev/null
grep -F $'auth\tpass\t' "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}" >/dev/null
grep -F $'notification\tpass\t' "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}" >/dev/null
grep -F $'sse\tpass\t' "${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF}" >/dev/null
grep -F $'mixed-oci-run-fixture\t0' "${MIXED_WORKLOAD_RUNNER_OUTBOX_LAG_REF}" >/dev/null
grep -F $'mixed-oci-run-fixture\tnginx-edge\t0.08\t0\t0' "${MIXED_WORKLOAD_RUNNER_READ_429_SOURCE_REF}" >/dev/null

echo "[transaction-read-mixed-workload-oci-k6] fixture summary metrics"
jq -e '.metrics.aquila_mixed_read_count.values.count == 128' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_write_count.values.count == 32' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_auth_count.values.count == 16' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_notification_count.values.count == 16' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
jq -e '.metrics.aquila_mixed_sse_connect_count.values.count == 1' "${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF}" >/dev/null
