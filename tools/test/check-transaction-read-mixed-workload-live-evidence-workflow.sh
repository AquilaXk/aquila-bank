#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/transaction-read-mixed-workload-live-evidence.yml"

echo "[transaction-read-mixed-workload-live-evidence-workflow] workflow exists"
test -f "${workflow}"

echo "[transaction-read-mixed-workload-live-evidence-workflow] yaml syntax"
ruby -e 'require "yaml"; YAML.load_file(ARGV.fetch(0)); puts "ok"' "${workflow}" >/dev/null

echo "[transaction-read-mixed-workload-live-evidence-workflow] workflow contract"
grep -F "name: Transaction Read Mixed Workload Live Evidence" "${workflow}" >/dev/null
grep -F "workflow_dispatch:" "${workflow}" >/dev/null
grep -F "pull_request:" "${workflow}" >/dev/null
grep -F "evidence_manifest_tsv:" "${workflow}" >/dev/null
grep -F "report_name:" "${workflow}" >/dev/null
grep -F "mixed workload live evidence contract" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'pull_request'" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-mixed-workload-live-evidence-workflow.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/run-staging-fixture-principal-bootstrap-contract.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-mixed-workload-oci-k6.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-mixed-workload-live-evidence-autogen.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-mixed-workload-30m-timeline.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-oci-evidence-execution-gate.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-evidence-completeness-gate.sh" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'workflow_dispatch'" "${workflow}" >/dev/null
grep -F "runs-on: [self-hosted, oci-a1-staging]" "${workflow}" >/dev/null
grep -F "environment:" "${workflow}" >/dev/null
grep -F "name: staging" "${workflow}" >/dev/null
grep -F "uses: actions/setup-java@v4" "${workflow}" >/dev/null
grep -F "distribution: temurin" "${workflow}" >/dev/null
grep -F 'java-version: "21"' "${workflow}" >/dev/null
grep -F 'OCI_A1_STAGING_ENV: ${{ secrets.OCI_A1_STAGING_ENV }}' "${workflow}" >/dev/null
grep -F 'MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV_INPUT: ${{ inputs.evidence_manifest_tsv }}' "${workflow}" >/dev/null
grep -F 'MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV_VAR: ${{ vars.OCI_MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV }}' "${workflow}" >/dev/null
grep -F 'MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV="${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV_INPUT:-${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV:-${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'MIXED_WORKLOAD_EVIDENCE_READY=false' "${workflow}" >/dev/null
grep -F 'MIXED_WORKLOAD_OCI_DOCKER_CONTEXT="${MIXED_WORKLOAD_OCI_DOCKER_CONTEXT:-${K6_DOCKER_CONTEXT:-${CAPACITY_K6_DOCKER_CONTEXT:-default}}}"' "${workflow}" >/dev/null
grep -F 'MIXED_WORKLOAD_OCI_HOT_ACCOUNT_ID="${MIXED_WORKLOAD_OCI_HOT_ACCOUNT_ID:-${K6_HOT_ACCOUNT_ID:-910000001}}"' "${workflow}" >/dev/null
grep -F 'MIXED_WORKLOAD_OCI_COLD_ACCOUNT_ID="${MIXED_WORKLOAD_OCI_COLD_ACCOUNT_ID:-${K6_COLD_ACCOUNT_ID:-910000002}}"' "${workflow}" >/dev/null
grep -F 'MIXED_WORKLOAD_OCI_WRITE_SOURCE_ACCOUNT_ID="${MIXED_WORKLOAD_OCI_WRITE_SOURCE_ACCOUNT_ID:-${K6_WRITE_SOURCE_ACCOUNT_ID:-920000001}}"' "${workflow}" >/dev/null
grep -F 'MIXED_WORKLOAD_OCI_WRITE_TARGET_ACCOUNT_ID="${MIXED_WORKLOAD_OCI_WRITE_TARGET_ACCOUNT_ID:-${K6_WRITE_TARGET_ACCOUNT_ID:-920000002}}"' "${workflow}" >/dev/null
grep -F 'printf '\''MIXED_WORKLOAD_OCI_HOT_ACCOUNT_ID=%s\n'\'' "${MIXED_WORKLOAD_OCI_HOT_ACCOUNT_ID}" >>"${GITHUB_ENV}"' "${workflow}" >/dev/null
grep -F 'printf '\''MIXED_WORKLOAD_OCI_COLD_ACCOUNT_ID=%s\n'\'' "${MIXED_WORKLOAD_OCI_COLD_ACCOUNT_ID}" >>"${GITHUB_ENV}"' "${workflow}" >/dev/null
grep -F 'printf '\''MIXED_WORKLOAD_OCI_WRITE_SOURCE_ACCOUNT_ID=%s\n'\'' "${MIXED_WORKLOAD_OCI_WRITE_SOURCE_ACCOUNT_ID}" >>"${GITHUB_ENV}"' "${workflow}" >/dev/null
grep -F 'printf '\''MIXED_WORKLOAD_OCI_WRITE_TARGET_ACCOUNT_ID=%s\n'\'' "${MIXED_WORKLOAD_OCI_WRITE_TARGET_ACCOUNT_ID}" >>"${GITHUB_ENV}"' "${workflow}" >/dev/null
grep -F "name: Resolve mixed workload staging database URL" "${workflow}" >/dev/null
grep -F "tools/ops/resolve-oci-a1-staging-database-url.sh" "${workflow}" >/dev/null
grep -F "name: Ensure mixed workload fixture principal" "${workflow}" >/dev/null
grep -F 'STAGING_MIXED_WORKLOAD_WRITE_SOURCE_ACCOUNT_ID="${MIXED_WORKLOAD_OCI_WRITE_SOURCE_ACCOUNT_ID}"' "${workflow}" >/dev/null
grep -F 'STAGING_MIXED_WORKLOAD_WRITE_TARGET_ACCOUNT_ID="${MIXED_WORKLOAD_OCI_WRITE_TARGET_ACCOUNT_ID}"' "${workflow}" >/dev/null
grep -F "tools/ops/staging-fixture-principal-bootstrap.sh" "${workflow}" >/dev/null
grep -F 'mixed_workload_auth_token_file="${RUNNER_TEMP}/mixed-workload-live-auth-token"' "${workflow}" >/dev/null
grep -F 'printf '\''::add-mask::%s\n'\'' "${STAGING_REPLAY_TOKEN}"' "${workflow}" >/dev/null
grep -F 'printf '\''%s'\'' "${STAGING_REPLAY_TOKEN}" >"${mixed_workload_auth_token_file}"' "${workflow}" >/dev/null
grep -F 'chmod 600 "${mixed_workload_auth_token_file}"' "${workflow}" >/dev/null
grep -F 'printf '\''MIXED_WORKLOAD_OCI_AUTH_TOKEN_FILE=%s\n'\'' "${mixed_workload_auth_token_file}" >>"${GITHUB_ENV}"' "${workflow}" >/dev/null
if grep -F 'MIXED_WORKLOAD_OCI_AUTH_TOKEN_ENV_NAME=%s' "${workflow}" >/dev/null; then
  echo "mixed workload workflow must use token file propagation instead of env-name handoff" >&2
  exit 1
fi
grep -F "name: Generate mixed workload live evidence manifest artifacts" "${workflow}" >/dev/null
grep -F "if: env.MIXED_WORKLOAD_EVIDENCE_READY != 'true'" "${workflow}" >/dev/null
grep -F "MIXED_WORKLOAD_AUTOGEN_MODE: live" "${workflow}" >/dev/null
grep -F 'generated_env="${MIXED_WORKLOAD_LIVE_OUTPUT_DIR}/${MIXED_WORKLOAD_LIVE_NAME}-generated-evidence.env"' "${workflow}" >/dev/null
grep -F "bash tools/test/run-transaction-read-mixed-workload-live-evidence-autogen.sh" "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-mixed-workload-oci-k6.sh" "${workflow}" >/dev/null
grep -F "ops/k6/transaction-read-mixed-workload-100m.js" "${workflow}" >/dev/null
grep -F 'source "${generated_env}"' "${workflow}" >/dev/null
grep -F "Generated mixed workload evidence manifest" "${workflow}" >/dev/null
grep -F "name: Validate mixed workload evidence manifest" "${workflow}" >/dev/null
grep -F "mixed-workload-30m" "${workflow}" >/dev/null
grep -F 'MIXED_30M_TIMELINE_INPUT_TSV="${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}"' "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-mixed-workload-30m-timeline.sh" "${workflow}" >/dev/null
grep -F "Upload mixed workload live evidence artifact" "${workflow}" >/dev/null
grep -F "transaction-read-mixed-workload-live-evidence" "${workflow}" >/dev/null
grep -F "if-no-files-found: ignore" "${workflow}" >/dev/null
