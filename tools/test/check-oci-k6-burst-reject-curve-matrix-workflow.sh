#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/oci-k6-burst-reject-curve-matrix.yml"

echo "[oci-k6-burst-reject-curve-matrix] workflow exists"
test -f "${workflow}"

echo "[oci-k6-burst-reject-curve-matrix] workflow contract"
grep -F "name: OCI k6 burst reject curve matrix" "${workflow}" >/dev/null
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
grep -F "OCI k6 burst reject curve matrix contract" "${workflow}" >/dev/null
grep -F "tools/test/check-oci-k6-burst-reject-curve-matrix-workflow.sh" "${workflow}" >/dev/null
grep -F "tools/test/run-oci-k6-burst-reject-curve-matrix.sh" "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-nginx-access-aggregate-artifact.sh" "${workflow}" >/dev/null
grep -F "tools/test/check-transaction-read-nginx-access-aggregate-artifact.sh" "${workflow}" >/dev/null
grep -F "tools/test/run-k6-transaction-100m-loadtest.sh" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'workflow_dispatch'" "${workflow}" >/dev/null
grep -F "runs-on: [self-hosted, oci-a1-staging]" "${workflow}" >/dev/null
grep -F "environment:" "${workflow}" >/dev/null
grep -F "name: staging" "${workflow}" >/dev/null
grep -F 'OCI_A1_STAGING_ENV: ${{ secrets.OCI_A1_STAGING_ENV }}' "${workflow}" >/dev/null
grep -F "Load OCI k6 burst matrix env" "${workflow}" >/dev/null
grep -F 'DEFAULT_K6_DOCKER_CONTEXT="default"' "${workflow}" >/dev/null
grep -F 'DEFAULT_K6_REMOTE_BASE_URL="${STAGING_BASE_URL:-}"' "${workflow}" >/dev/null
grep -F 'DEFAULT_K6_REMOTE_PROMETHEUS_RW_SERVER_URL="http://172.17.0.2:9090/api/v1/write"' "${workflow}" >/dev/null
grep -F 'DEFAULT_K6_NGINX_ACCESS_LOG="/var/log/nginx/access.log"' "${workflow}" >/dev/null
grep -F "STAGING_REPLAY_TOKEN" "${workflow}" >/dev/null
grep -F 'K6_AUTH_TOKEN_ENV_NAME="STAGING_REPLAY_TOKEN"' "${workflow}" >/dev/null
grep -F 'K6_AUTH_PREFLIGHT="true"' "${workflow}" >/dev/null
grep -F 'K6_RUN_PURPOSE="capacity"' "${workflow}" >/dev/null
grep -F 'K6_OBSERVABILITY_MODE="prometheus"' "${workflow}" >/dev/null
grep -F 'K6_GENERATOR_MODE="docker-context"' "${workflow}" >/dev/null
grep -F "burst_rates:" "${workflow}" >/dev/null
grep -F 'default: "32,48,64,80,96"' "${workflow}" >/dev/null
grep -F "promotion_target_rate:" "${workflow}" >/dev/null
grep -F 'description: "Promotion target burst arrival rate per second"' "${workflow}" >/dev/null
grep -F 'default: "80"' "${workflow}" >/dev/null
grep -F 'K6_MATRIX_REPORT_NAME: ${{ inputs.report_name || format(' "${workflow}" >/dev/null
grep -F 'BURST_RATES_INPUT: ${{ inputs.burst_rates }}' "${workflow}" >/dev/null
grep -F 'PROMOTION_TARGET_RATE_INPUT: ${{ inputs.promotion_target_rate }}' "${workflow}" >/dev/null
grep -F 'K6_BURST_RATES="${BURST_RATES_INPUT:-32,48,64,80,96}"' "${workflow}" >/dev/null
grep -F 'K6_BURST_MATRIX_PROMOTION_TARGET_RATE="${PROMOTION_TARGET_RATE_INPUT:-80}"' "${workflow}" >/dev/null
grep -F "Run authenticated k6 burst matrix" "${workflow}" >/dev/null
grep -F 'IFS="," read -r -a burst_rates <<<"${K6_BURST_RATES}"' "${workflow}" >/dev/null
grep -F 'matrix_input_tsv="${matrix_dir}/burst-reject-curve-input.tsv"' "${workflow}" >/dev/null
grep -F $'printf "burst_rate\\tk6_run_id\\tsummary_json\\tnginx_aggregate_json\\tnginx_aggregate_tsv\\tnginx_aggregate_md\\n" >"${matrix_input_tsv}"' "${workflow}" >/dev/null
grep -F 'for burst_rate in "${burst_rates[@]}"; do' "${workflow}" >/dev/null
grep -F 'K6_REPORT_NAME="${K6_MATRIX_REPORT_NAME}-burst${burst_rate}"' "${workflow}" >/dev/null
grep -F 'K6_RUN_ID="${K6_REPORT_NAME}"' "${workflow}" >/dev/null
grep -F 'K6_SCENARIO_MODE="burst"' "${workflow}" >/dev/null
grep -F 'K6_BURST_RATE="${burst_rate}"' "${workflow}" >/dev/null
grep -F 'K6_PRE_ALLOCATED_VUS="${burst_rate}"' "${workflow}" >/dev/null
grep -F 'K6_MAX_VUS="$((burst_rate * 2))"' "${workflow}" >/dev/null
grep -F 'K6_OVERLOAD_MODE="true"' "${workflow}" >/dev/null
grep -F 'K6_BURST_429_RATE_THRESHOLD="${BURST_429_RATE_THRESHOLD_INPUT:-0.10}"' "${workflow}" >/dev/null
grep -F 'K6_BACKEND_429_RATE_THRESHOLD="${BACKEND_429_RATE_THRESHOLD_INPUT:-0.005}"' "${workflow}" >/dev/null
grep -F 'K6_OVERLOAD_503_RATE_THRESHOLD="${OVERLOAD_503_RATE_THRESHOLD_INPUT:-0}"' "${workflow}" >/dev/null
grep -F 'K6_BURST_MATRIX_PROMOTION_TARGET_RATE' "${workflow}" >/dev/null
grep -F 'K6_NGINX_LOG_SINCE="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"' "${workflow}" >/dev/null
grep -F "tools/test/run-k6-transaction-100m-loadtest.sh --auth-preflight-only" "${workflow}" >/dev/null
grep -F "tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps" "${workflow}" >/dev/null
grep -F 'candidate_raw_log="${RUNNER_TEMP}/${K6_RUN_ID}-nginx-access.raw.jsonl"' "${workflow}" >/dev/null
grep -F 'candidate_run_log="${RUNNER_TEMP}/${K6_RUN_ID}-nginx-access.jsonl"' "${workflow}" >/dev/null
grep -F 'grep -F "\"k6_run_id\":\"${K6_RUN_ID}\"" "${candidate_raw_log}" >"${candidate_run_log}"' "${workflow}" >/dev/null
grep -F 'container_candidates=(' "${workflow}" >/dev/null
grep -F 'docker exec "${container}" test -s /var/log/nginx/access.log' "${workflow}" >/dev/null
grep -F 'docker cp "${container}:/var/log/nginx/access.log" "${candidate_raw_log}"' "${workflow}" >/dev/null
grep -F 'docker logs --since "${K6_NGINX_LOG_SINCE:-1h}" "${container}" >"${candidate_raw_log}"' "${workflow}" >/dev/null
grep -F 'NGINX_ACCESS_AGGREGATE_RUN_ID="${K6_RUN_ID}"' "${workflow}" >/dev/null
grep -F 'NGINX_ACCESS_AGGREGATE_OUTPUT_DIR="${report_dir}/transaction-read-nginx-access-aggregate"' "${workflow}" >/dev/null
grep -F 'summary_json="build/reports/k6/${K6_REPORT_NAME}-summary.json"' "${workflow}" >/dev/null
grep -F 'aggregate_json="${report_dir}/transaction-read-nginx-access-aggregate/${K6_REPORT_NAME}-nginx-access-aggregate.json"' "${workflow}" >/dev/null
grep -F 'aggregate_tsv="${report_dir}/transaction-read-nginx-access-aggregate/${K6_REPORT_NAME}-nginx-access-aggregate.tsv"' "${workflow}" >/dev/null
grep -F 'aggregate_md="${report_dir}/transaction-read-nginx-access-aggregate/${K6_REPORT_NAME}-nginx-access-aggregate.md"' "${workflow}" >/dev/null
grep -F 'printf "%s\t%s\t%s\t%s\t%s\t%s\n" "${burst_rate}" "${K6_RUN_ID}" "${summary_json}" "${aggregate_json}" "${aggregate_tsv}" "${aggregate_md}" >>"${matrix_input_tsv}"' "${workflow}" >/dev/null
grep -F 'OCI_K6_BURST_MATRIX_NAME="${K6_MATRIX_REPORT_NAME}"' "${workflow}" >/dev/null
grep -F 'OCI_K6_BURST_MATRIX_INPUT_TSV="${matrix_input_tsv}"' "${workflow}" >/dev/null
grep -F 'OCI_K6_BURST_MATRIX_OUTPUT_DIR="${matrix_dir}/burst-reject-curve-matrix"' "${workflow}" >/dev/null
grep -F 'OCI_K6_BURST_MATRIX_PROMOTION_TARGET_RATE="${K6_BURST_MATRIX_PROMOTION_TARGET_RATE}"' "${workflow}" >/dev/null
grep -F "tools/test/run-oci-k6-burst-reject-curve-matrix.sh" "${workflow}" >/dev/null
grep -F "actions/upload-artifact@v7" "${workflow}" >/dev/null
grep -F "oci-k6-burst-reject-curve-matrix" "${workflow}" >/dev/null
grep -F "deployments: write" "${workflow}" >/dev/null
grep -F "Record transaction read admission profile evidence deployment" "${workflow}" >/dev/null
grep -F "staging-transaction-read-admission-profile" "${workflow}" >/dev/null
grep -F 'curl --fail-with-body --silent --show-error --location' "${workflow}" >/dev/null
grep -F '"${github_api_url}/repos/${GITHUB_REPOSITORY}/deployments"' "${workflow}" >/dev/null
grep -F '"${github_api_url}/repos/${GITHUB_REPOSITORY}/deployments/${deployment_id}/statuses"' "${workflow}" >/dev/null
grep -F 'Authorization: Bearer ${GH_TOKEN}' "${workflow}" >/dev/null
grep -F 'description "transaction read burst matrix target ${K6_BURST_MATRIX_PROMOTION_TARGET_RATE} passed"' "${workflow}" >/dev/null
grep -F '"state": "success"' "${workflow}" >/dev/null

preflight_line="$(grep -n -- "--auth-preflight-only" "${workflow}" | head -1 | cut -d: -f1)"
k6_run_line="$(grep -n -- "--no-up --no-deps" "${workflow}" | head -1 | cut -d: -f1)"
matrix_line="$(grep -n -- "run-oci-k6-burst-reject-curve-matrix.sh" "${workflow}" | tail -1 | cut -d: -f1)"
admission_deployment_line="$(grep -n -- "Record transaction read admission profile evidence deployment" "${workflow}" | head -1 | cut -d: -f1)"
if [[ -z "${preflight_line}" || -z "${k6_run_line}" || "${preflight_line}" -ge "${k6_run_line}" ]]; then
  echo "auth preflight must run before authenticated k6 burst run" >&2
  exit 1
fi
if [[ -z "${matrix_line}" || "${k6_run_line}" -ge "${matrix_line}" ]]; then
  echo "matrix pack runner must run after burst k6 runs" >&2
  exit 1
fi
if [[ -z "${admission_deployment_line}" || "${matrix_line}" -ge "${admission_deployment_line}" ]]; then
  echo "admission profile evidence deployment must be recorded after burst matrix pack" >&2
  exit 1
fi

if grep -F 'secrets.STAGING_REPLAY_TOKEN' "${workflow}" >/dev/null; then
  echo "workflow must read the unified staging env secret, not a standalone replay token secret" >&2
  exit 1
fi
if grep -F 'K6_AUTH_TOKEN:' "${workflow}" >/dev/null; then
  echo "workflow must not map token value directly into K6_AUTH_TOKEN" >&2
  exit 1
fi
if grep -F 'gh api' "${workflow}" >/dev/null; then
  echo "self-hosted OCI burst matrix workflow must not depend on GitHub CLI" >&2
  exit 1
fi
if grep -F 'transaction read burst boundary matrix passed: ${matrix_report}' "${workflow}" >/dev/null; then
  echo "deployment status description must stay short; do not include artifact path" >&2
  exit 1
fi
