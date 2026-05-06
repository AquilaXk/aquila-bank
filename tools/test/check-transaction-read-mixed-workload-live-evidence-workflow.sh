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
grep -F "bash tools/test/check-transaction-read-mixed-workload-30m-timeline.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-oci-evidence-execution-gate.sh" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'workflow_dispatch'" "${workflow}" >/dev/null
grep -F "runs-on: [self-hosted, oci-a1-staging]" "${workflow}" >/dev/null
grep -F "environment:" "${workflow}" >/dev/null
grep -F "name: staging" "${workflow}" >/dev/null
grep -F 'OCI_A1_STAGING_ENV: ${{ secrets.OCI_A1_STAGING_ENV }}' "${workflow}" >/dev/null
grep -F 'MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV_INPUT: ${{ inputs.evidence_manifest_tsv }}' "${workflow}" >/dev/null
grep -F 'MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV_VAR: ${{ vars.OCI_MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV }}' "${workflow}" >/dev/null
grep -F 'MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV="${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV_INPUT:-${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV:-${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV_VAR:-}}}"' "${workflow}" >/dev/null
grep -F "Generate mixed workload evidence manifest" "${workflow}" >/dev/null
grep -F "mixed-workload-30m" "${workflow}" >/dev/null
grep -F 'MIXED_30M_TIMELINE_INPUT_TSV="${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}"' "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-mixed-workload-30m-timeline.sh" "${workflow}" >/dev/null
grep -F "Upload mixed workload live evidence artifact" "${workflow}" >/dev/null
grep -F "transaction-read-mixed-workload-live-evidence" "${workflow}" >/dev/null
grep -F "if-no-files-found: ignore" "${workflow}" >/dev/null
