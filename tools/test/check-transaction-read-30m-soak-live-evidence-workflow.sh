#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/transaction-read-30m-soak-live-evidence.yml"

echo "[transaction-read-30m-soak-live-evidence-workflow] workflow exists"
test -f "${workflow}"

echo "[transaction-read-30m-soak-live-evidence-workflow] workflow contract"
grep -F "name: Transaction Read 30m Soak Live Evidence" "${workflow}" >/dev/null
grep -F "workflow_dispatch:" "${workflow}" >/dev/null
grep -F "pull_request:" "${workflow}" >/dev/null
dispatch_input_count="$(awk '
  /^  workflow_dispatch:/ { in_dispatch = 1; next }
  in_dispatch && /^    inputs:/ { in_inputs = 1; next }
  in_inputs && /^  [A-Za-z_]/ { exit }
  in_inputs && /^      [A-Za-z0-9_]+:/ { count++ }
  END { print count + 0 }
' "${workflow}")"
if [[ "${dispatch_input_count}" -gt 25 ]]; then
  echo "workflow_dispatch inputs must be 25 or fewer, got ${dispatch_input_count}" >&2
  exit 1
fi

grep -F "evidence_manifest_tsv:" "${workflow}" >/dev/null
grep -F 'description: "OCI evidence manifest TSV path on the self-hosted runner workspace"' "${workflow}" >/dev/null
grep -F "report_name:" "${workflow}" >/dev/null
grep -F "30m Soak Live Evidence Contract" "${workflow}" >/dev/null
grep -F "tools/test/check-transaction-read-30m-soak-live-evidence-gate.sh" "${workflow}" >/dev/null
grep -F "tools/test/check-transaction-read-30m-soak-live-evidence-workflow.sh" "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-oci-evidence-execution-gate.sh" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'workflow_dispatch'" "${workflow}" >/dev/null
grep -F "runs-on: [self-hosted, oci-a1-staging]" "${workflow}" >/dev/null
grep -F "environment:" "${workflow}" >/dev/null
grep -F "name: staging" "${workflow}" >/dev/null
grep -F 'SOAK_30M_REPORT_NAME: ${{ inputs.report_name || format(' "${workflow}" >/dev/null
grep -F 'SOAK_30M_EVIDENCE_MANIFEST_TSV: ${{ inputs.evidence_manifest_tsv }}' "${workflow}" >/dev/null
grep -F "Run 30m soak live evidence gate" "${workflow}" >/dev/null
grep -F 'SOAK_30M_LIVE_NAME="${SOAK_30M_REPORT_NAME}"' "${workflow}" >/dev/null
grep -F 'SOAK_30M_LIVE_INPUT_TSV="${SOAK_30M_EVIDENCE_MANIFEST_TSV}"' "${workflow}" >/dev/null
grep -F 'SOAK_30M_LIVE_OUTPUT_DIR="${report_dir}"' "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-30m-soak-live-evidence-gate.sh" "${workflow}" >/dev/null
grep -F 'cat "${report_md}"' "${workflow}" >/dev/null
grep -F "actions/upload-artifact@v7" "${workflow}" >/dev/null
grep -F "transaction-read-30m-soak-live-evidence" "${workflow}" >/dev/null
grep -F "if-no-files-found: error" "${workflow}" >/dev/null

gate_line="$(grep -n "Run 30m soak live evidence gate" "${workflow}" | head -1 | cut -d: -f1)"
upload_line="$(grep -n "Upload 30m soak live evidence artifact" "${workflow}" | head -1 | cut -d: -f1)"
if [[ -z "${gate_line}" || -z "${upload_line}" || "${gate_line}" -ge "${upload_line}" ]]; then
  echo "30m live evidence gate must run before artifact upload" >&2
  exit 1
fi

if grep -F "fallback" "${workflow}" >/dev/null; then
  echo "30m live evidence workflow must not accept fallback/snapshot evidence" >&2
  exit 1
fi
if grep -F "secrets." "${workflow}" >/dev/null; then
  echo "30m live evidence workflow must not require secrets; it validates artifact refs only" >&2
  exit 1
fi
