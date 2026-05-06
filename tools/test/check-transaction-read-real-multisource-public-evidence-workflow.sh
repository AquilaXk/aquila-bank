#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/transaction-read-real-multisource-public-evidence.yml"

echo "[transaction-read-real-multisource-public-evidence-workflow] workflow exists"
test -f "${workflow}"

echo "[transaction-read-real-multisource-public-evidence-workflow] yaml syntax"
ruby -e 'require "yaml"; YAML.load_file(ARGV.fetch(0)); puts "ok"' "${workflow}" >/dev/null

echo "[transaction-read-real-multisource-public-evidence-workflow] workflow contract"
grep -F "name: Transaction Read Real Multi-Source Public Evidence" "${workflow}" >/dev/null
grep -F "workflow_dispatch:" "${workflow}" >/dev/null
grep -F "pull_request:" "${workflow}" >/dev/null
grep -F "real_multisource_run_id:" "${workflow}" >/dev/null
grep -F "real_multisource_contexts:" "${workflow}" >/dev/null
grep -F "single_source_summary_json:" "${workflow}" >/dev/null
grep -F "multi_source_summary_json:" "${workflow}" >/dev/null
grep -F "nginx_status_tsv:" "${workflow}" >/dev/null
grep -F "source_evidence_tsv:" "${workflow}" >/dev/null
grep -F "host_metrics_tsv:" "${workflow}" >/dev/null
grep -F "host_metrics_timeline_tsv:" "${workflow}" >/dev/null
grep -F "artifact_uri:" "${workflow}" >/dev/null
grep -F "real multi-source public evidence contract" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'pull_request'" "${workflow}" >/dev/null
grep -F "bash tools/test/check-transaction-read-real-multisource-public-evidence-workflow.sh" "${workflow}" >/dev/null
grep -F "bash tools/test/check-oci-real-multisource-public-evidence.sh" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'workflow_dispatch'" "${workflow}" >/dev/null
grep -F "runs-on: [self-hosted, oci-a1-staging]" "${workflow}" >/dev/null
grep -F "environment:" "${workflow}" >/dev/null
grep -F "name: staging" "${workflow}" >/dev/null
grep -F 'OCI_A1_STAGING_ENV: ${{ secrets.OCI_A1_STAGING_ENV }}' "${workflow}" >/dev/null
grep -F 'REAL_MULTISOURCE_RUN_ID_INPUT: ${{ inputs.real_multisource_run_id }}' "${workflow}" >/dev/null
grep -F 'REAL_MULTISOURCE_CONTEXTS_INPUT: ${{ inputs.real_multisource_contexts }}' "${workflow}" >/dev/null
grep -F 'SINGLE_SOURCE_SUMMARY_JSON_INPUT: ${{ inputs.single_source_summary_json }}' "${workflow}" >/dev/null
grep -F 'MULTI_SOURCE_SUMMARY_JSON_INPUT: ${{ inputs.multi_source_summary_json }}' "${workflow}" >/dev/null
grep -F 'NGINX_STATUS_TSV_INPUT: ${{ inputs.nginx_status_tsv }}' "${workflow}" >/dev/null
grep -F 'SOURCE_EVIDENCE_TSV_INPUT: ${{ inputs.source_evidence_tsv }}' "${workflow}" >/dev/null
grep -F 'HOST_METRICS_TSV_INPUT: ${{ inputs.host_metrics_tsv }}' "${workflow}" >/dev/null
grep -F 'HOST_METRICS_TIMELINE_TSV_INPUT: ${{ inputs.host_metrics_timeline_tsv }}' "${workflow}" >/dev/null
grep -F 'ARTIFACT_URI_INPUT: ${{ inputs.artifact_uri }}' "${workflow}" >/dev/null
grep -F 'OCI_REAL_MULTISOURCE_CONTEXTS_VAR: ${{ vars.OCI_REAL_MULTISOURCE_CONTEXTS }}' "${workflow}" >/dev/null
grep -F 'OCI_REAL_MULTISOURCE_ARTIFACT_URI_VAR: ${{ vars.OCI_REAL_MULTISOURCE_ARTIFACT_URI }}' "${workflow}" >/dev/null
grep -F 'OCI_REAL_MULTISOURCE_CONTEXTS="${REAL_MULTISOURCE_CONTEXTS_INPUT:-${OCI_REAL_MULTISOURCE_CONTEXTS:-${OCI_REAL_MULTISOURCE_CONTEXTS_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'OCI_REAL_MULTISOURCE_RUN_ID="${REAL_MULTISOURCE_RUN_ID_INPUT:-${OCI_REAL_MULTISOURCE_RUN_ID:-${OCI_REAL_MULTISOURCE_NAME}}}"' "${workflow}" >/dev/null
grep -F "Write real multisource missing evidence artifact" "${workflow}" >/dev/null
grep -F 'OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${SINGLE_SOURCE_SUMMARY_JSON_INPUT:-${OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON:-${OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${MULTI_SOURCE_SUMMARY_JSON_INPUT:-${OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON:-${OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${NGINX_STATUS_TSV_INPUT:-${OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV:-${OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${SOURCE_EVIDENCE_TSV_INPUT:-${OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV:-${OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${HOST_METRICS_TSV_INPUT:-${OCI_REAL_MULTISOURCE_HOST_METRICS_TSV:-${OCI_REAL_MULTISOURCE_HOST_METRICS_TSV_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV="${HOST_METRICS_TIMELINE_TSV_INPUT:-${OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV:-${OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'OCI_REAL_MULTISOURCE_ARTIFACT_URI="${ARTIFACT_URI_INPUT:-${OCI_REAL_MULTISOURCE_ARTIFACT_URI:-${OCI_REAL_MULTISOURCE_ARTIFACT_URI_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'tools/test/run-oci-real-multisource-public-evidence.sh' "${workflow}" >/dev/null
grep -F "Upload real multisource evidence artifact" "${workflow}" >/dev/null
grep -F "transaction-read-real-multisource-public-evidence" "${workflow}" >/dev/null
grep -F "build/reports/k6/\${{ env.OCI_REAL_MULTISOURCE_NAME }}/" "${workflow}" >/dev/null
