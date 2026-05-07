#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/transaction-read-deploy-drain-live-evidence.yml"

echo "[transaction-read-deploy-drain-live-evidence-workflow] workflow exists"
test -f "${workflow}"

echo "[transaction-read-deploy-drain-live-evidence-workflow] yaml syntax"
ruby -e 'require "yaml"; YAML.load_file(ARGV.fetch(0)); puts "ok"' "${workflow}" >/dev/null

echo "[transaction-read-deploy-drain-live-evidence-workflow] workflow contract"
grep -F "name: Transaction Read Deploy Drain Live Evidence" "${workflow}" >/dev/null
grep -F "workflow_dispatch:" "${workflow}" >/dev/null
grep -F "pull_request:" "${workflow}" >/dev/null
grep -F "evidence_manifest_tsv:" "${workflow}" >/dev/null
grep -F "deploy_499_budget_count:" "${workflow}" >/dev/null
grep -F "report_name:" "${workflow}" >/dev/null
grep -F "deploy drain live evidence contract" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'pull_request'" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-deploy-drain-live-evidence-workflow.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-deploy-drain-live-evidence-autogen.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-deploy-drain-oci-k6.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-deploy-drain-under-load-gate.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-oci-evidence-execution-gate.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-evidence-completeness-gate.sh" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'workflow_dispatch'" "${workflow}" >/dev/null
grep -F "packages: read" "${workflow}" >/dev/null
grep -F "runs-on: [self-hosted, oci-a1-staging]" "${workflow}" >/dev/null
grep -F "environment:" "${workflow}" >/dev/null
grep -F "name: staging" "${workflow}" >/dev/null
grep -F "uses: actions/setup-java@v4" "${workflow}" >/dev/null
grep -F "distribution: temurin" "${workflow}" >/dev/null
grep -F 'java-version: "21"' "${workflow}" >/dev/null
grep -F 'OCI_A1_STAGING_ENV: ${{ secrets.OCI_A1_STAGING_ENV }}' "${workflow}" >/dev/null
grep -F 'DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV_INPUT: ${{ inputs.evidence_manifest_tsv }}' "${workflow}" >/dev/null
grep -F 'DEPLOY_DRAIN_499_BUDGET_COUNT_INPUT: ${{ inputs.deploy_499_budget_count }}' "${workflow}" >/dev/null
grep -F 'DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV_VAR: ${{ vars.OCI_DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV }}' "${workflow}" >/dev/null
grep -F 'DEPLOY_DRAIN_499_BUDGET_COUNT_VAR: ${{ vars.OCI_DEPLOY_DRAIN_499_BUDGET_COUNT }}' "${workflow}" >/dev/null
grep -F 'DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV="${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV_INPUT:-${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV:-${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'DEPLOY_DRAIN_499_BUDGET_COUNT="${DEPLOY_DRAIN_499_BUDGET_COUNT_INPUT:-${DEPLOY_DRAIN_499_BUDGET_COUNT:-${DEPLOY_DRAIN_499_BUDGET_COUNT_VAR:-0}}}"' "${workflow}" >/dev/null
grep -F "DEPLOY_DRAIN_EVIDENCE_READY=false" "${workflow}" >/dev/null
grep -F "name: Generate deploy drain live evidence manifest artifacts" "${workflow}" >/dev/null
grep -F "if: env.DEPLOY_DRAIN_EVIDENCE_READY != 'true'" "${workflow}" >/dev/null
grep -F "DEPLOY_DRAIN_AUTOGEN_MODE: live" "${workflow}" >/dev/null
grep -F 'DEPLOY_DRAIN_OCI_GITHUB_TOKEN: ${{ github.token }}' "${workflow}" >/dev/null
grep -F 'generated_env="${DEPLOY_DRAIN_LIVE_OUTPUT_DIR}/${DEPLOY_DRAIN_LIVE_NAME}-generated-evidence.env"' "${workflow}" >/dev/null
grep -F "bash tools/test/run-transaction-read-deploy-drain-live-evidence-autogen.sh" "${workflow}" >/dev/null
grep -F 'source "${generated_env}"' "${workflow}" >/dev/null
grep -F "Generated deploy drain evidence manifest" "${workflow}" >/dev/null
grep -F "name: Validate deploy drain evidence manifest" "${workflow}" >/dev/null
grep -F "deploy-drain" "${workflow}" >/dev/null
grep -F 'DEPLOY_DRAIN_GATE_INPUT_TSV="${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV}"' "${workflow}" >/dev/null
grep -F 'DEPLOY_DRAIN_GATE_499_BUDGET_COUNT="${DEPLOY_DRAIN_499_BUDGET_COUNT}"' "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-deploy-drain-under-load-gate.sh" "${workflow}" >/dev/null
grep -F "Upload deploy drain live evidence artifact" "${workflow}" >/dev/null
grep -F "transaction-read-deploy-drain-live-evidence" "${workflow}" >/dev/null
grep -F "if-no-files-found: ignore" "${workflow}" >/dev/null
