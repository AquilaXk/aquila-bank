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
grep -F "required: false" "${workflow}" >/dev/null
grep -F "report_name:" "${workflow}" >/dev/null
grep -F 'description: "Cold-labeled account id for k6; default must have fixture rows"' "${workflow}" >/dev/null
grep -F 'description: "Cold-labeled range start for k6; default matches the fixture-backed range"' "${workflow}" >/dev/null
grep -F 'description: "Cold-labeled range end for k6; default matches the fixture-backed range"' "${workflow}" >/dev/null
grep -F "docker_context:" "${workflow}" >/dev/null
grep -F 'SOAK_30M_DOCKER_CONTEXT_INPUT: ${{ inputs.docker_context }}' "${workflow}" >/dev/null
grep -F "workload_weights:" "${workflow}" >/dev/null
grep -F 'SOAK_30M_WORKLOAD_WEIGHTS_INPUT: ${{ inputs.workload_weights }}' "${workflow}" >/dev/null
grep -F "30m Soak Live Evidence Contract" "${workflow}" >/dev/null
grep -F "tools/test/check-transaction-read-30m-soak-live-evidence-gate.sh" "${workflow}" >/dev/null
grep -F "tools/test/check-transaction-read-30m-soak-live-evidence-workflow.sh" "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-30m-soak-live-evidence-manifest.sh" "${workflow}" >/dev/null
grep -F "tools/test/check-transaction-read-30m-soak-live-evidence-manifest.sh" "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-30m-soak-live-evidence-artifacts.sh" "${workflow}" >/dev/null
grep -F "tools/test/check-transaction-read-30m-soak-live-evidence-artifacts.sh" "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-oci-evidence-execution-gate.sh" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'workflow_dispatch'" "${workflow}" >/dev/null
grep -F "runs-on: [self-hosted, oci-a1-staging]" "${workflow}" >/dev/null
grep -F "timeout-minutes: 50" "${workflow}" >/dev/null
grep -F "environment:" "${workflow}" >/dev/null
grep -F "name: staging" "${workflow}" >/dev/null
grep -F 'OCI_A1_STAGING_ENV: ${{ secrets.OCI_A1_STAGING_ENV }}' "${workflow}" >/dev/null
grep -F 'SOAK_30M_REPORT_NAME: ${{ inputs.report_name || format(' "${workflow}" >/dev/null
grep -F 'SOAK_30M_EVIDENCE_MANIFEST_TSV: ${{ inputs.evidence_manifest_tsv }}' "${workflow}" >/dev/null
grep -F 'SOAK_30M_DURATION_INPUT: ${{ inputs.duration }}' "${workflow}" >/dev/null
grep -F "Prepare 30m soak evidence mode" "${workflow}" >/dev/null
grep -F 'SOAK_30M_USE_EXISTING_MANIFEST=true' "${workflow}" >/dev/null
grep -F "Load OCI A1 staging env" "${workflow}" >/dev/null
grep -F 'K6_RUN_PURPOSE="capacity"' "${workflow}" >/dev/null
grep -F 'K6_DOCKER_CONTEXT_SOURCE="input"' "${workflow}" >/dev/null
grep -F 'K6_DOCKER_CONTEXT_SOURCE="env"' "${workflow}" >/dev/null
grep -F 'K6_DOCKER_CONTEXT_SOURCE="default"' "${workflow}" >/dev/null
grep -F 'K6_WORKLOAD_SHAPE="weighted-random"' "${workflow}" >/dev/null
grep -F 'K6_WORKLOAD_WEIGHTS="${SOAK_30M_WORKLOAD_WEIGHTS_INPUT:-hot_first:40,hot_cursor:40,hot_deep_cursor:20}"' "${workflow}" >/dev/null
if grep -F 'K6_RUN_PURPOSE="30m-soak-live-evidence"' "${workflow}" >/dev/null; then
  echo "30m live k6 runner must use an accepted K6_RUN_PURPOSE" >&2
  exit 1
fi
grep -F "Prepare OCI k6 Docker context access" "${workflow}" >/dev/null
grep -F 'docker_config_home="${DOCKER_CONFIG:-${HOME}/.docker}"' "${workflow}" >/dev/null
grep -F 'sudo chown -R "$(id -u):$(id -g)" "${docker_config_home}"' "${workflow}" >/dev/null
grep -F 'docker --context "${K6_DOCKER_CONTEXT}" info' "${workflow}" >/dev/null
grep -F 'falling back to default self-hosted runner Docker context' "${workflow}" >/dev/null
grep -F "K6_DOCKER_CONTEXT=default" "${workflow}" >/dev/null
grep -F 'printf '\''K6_REMOTE_WORKDIR=%s\n'\'' "${GITHUB_WORKSPACE}"' "${workflow}" >/dev/null
grep -F "Capture 30m soak evidence window" "${workflow}" >/dev/null
grep -F "Capture PostgreSQL baseline counters" "${workflow}" >/dev/null
grep -F 'SOAK_30M_POSTGRES_CHECKPOINT_START_COUNT=%s' "${workflow}" >/dev/null
grep -F 'SOAK_30M_POSTGRES_TEMP_FILE_START_COUNT=%s' "${workflow}" >/dev/null
grep -F "Run 30m authenticated k6 soak" "${workflow}" >/dev/null
grep -F 'local endpoint="$5"' "${workflow}" >/dev/null
grep -F 'if [[ "${K6_WORKLOAD_WEIGHTS}" == *cold_* ]]; then' "${workflow}" >/dev/null
grep -F 'api/v1/transactions/archive' "${workflow}" >/dev/null
grep -F 'K6_DURATION="${SOAK_30M_DURATION}"' "${workflow}" >/dev/null
grep -F "tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps" "${workflow}" >/dev/null
grep -F "Collect 30m soak live evidence artifacts" "${workflow}" >/dev/null
grep -F 'docker exec "${container}" sh -c' "${workflow}" >/dev/null
grep -F 'printf "metric\tstart_value\tend_value\tdelta\n"' "${workflow}" >/dev/null
grep -F 'stats reset invalidates delta evidence' "${workflow}" >/dev/null
grep -F 'backend_env_value()' "${workflow}" >/dev/null
grep -F 'backend_env_value_any()' "${workflow}" >/dev/null
grep -F 'hikari_config_source' "${workflow}" >/dev/null
grep -F 'postgres_idle_timeout_source' "${workflow}" >/dev/null
grep -F 'OCI_A1_DB_IDLE_IN_TX_TIMEOUT_MS' "${workflow}" >/dev/null
grep -F 'DB_IDLE_IN_TX_TIMEOUT_MS' "${workflow}" >/dev/null
grep -F 'expected_hikari_max_lifetime_ms' "${workflow}" >/dev/null
grep -F "Build 30m soak live evidence artifact pack" "${workflow}" >/dev/null
grep -F 'SOAK_30M_ARTIFACTS_DURATION_MIN="${SOAK_30M_DURATION_MIN}"' "${workflow}" >/dev/null
grep -F 'SOAK_30M_ARTIFACTS_POSTGRES_TEMP_FILE_DELTA_MAX="${SOAK_30M_POSTGRES_TEMP_FILE_DELTA_MAX:-0}"' "${workflow}" >/dev/null
grep -F 'tools/test/run-transaction-read-30m-soak-live-evidence-artifacts.sh' "${workflow}" >/dev/null
grep -F "Run 30m soak live evidence gate" "${workflow}" >/dev/null
grep -F 'SOAK_30M_LIVE_NAME="${SOAK_30M_REPORT_NAME}"' "${workflow}" >/dev/null
grep -F 'SOAK_30M_LIVE_INPUT_TSV="${SOAK_30M_EVIDENCE_MANIFEST_TSV}"' "${workflow}" >/dev/null
grep -F 'SOAK_30M_LIVE_OUTPUT_DIR="${report_dir}"' "${workflow}" >/dev/null
grep -F 'SOAK_30M_LIVE_MAX_POSTGRES_TEMP_FILE_DELTA="${SOAK_30M_POSTGRES_TEMP_FILE_DELTA_MAX:-0}"' "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-30m-soak-live-evidence-gate.sh" "${workflow}" >/dev/null
grep -F 'cat "${report_md}"' "${workflow}" >/dev/null
grep -F "actions/upload-artifact@v7" "${workflow}" >/dev/null
grep -F "transaction-read-30m-soak-live-evidence" "${workflow}" >/dev/null
grep -F "if: always()" "${workflow}" >/dev/null
grep -F "if-no-files-found: ignore" "${workflow}" >/dev/null

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
if grep -F "evidence_manifest_tsv is required" "${workflow}" >/dev/null; then
  echo "30m live evidence workflow must auto-build manifest when input is omitted" >&2
  exit 1
fi
if grep -F "if: always() && env.SOAK_30M_USE_EXISTING_MANIFEST != 'true'" "${workflow}" >/dev/null; then
  echo "30m live artifact collection must not run after preflight failure" >&2
  exit 1
fi

live_run_line="$(grep -n "Run 30m authenticated k6 soak" "${workflow}" | head -1 | cut -d: -f1)"
artifact_pack_line="$(grep -n "Build 30m soak live evidence artifact pack" "${workflow}" | head -1 | cut -d: -f1)"
gate_line="$(grep -n "Run 30m soak live evidence gate" "${workflow}" | head -1 | cut -d: -f1)"
if [[ -z "${live_run_line}" || -z "${artifact_pack_line}" || -z "${gate_line}" ||
  "${live_run_line}" -ge "${artifact_pack_line}" || "${artifact_pack_line}" -ge "${gate_line}" ]]; then
  echo "30m live run must build artifact pack before evidence gate" >&2
  exit 1
fi
