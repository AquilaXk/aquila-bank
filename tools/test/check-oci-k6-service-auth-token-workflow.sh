#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/oci-k6-service-auth-token.yml"

echo "[oci-k6-service-auth] workflow exists"
test -f "${workflow}"

echo "[oci-k6-service-auth] workflow contract"
grep -F "name: OCI k6 service auth token" "${workflow}" >/dev/null
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
grep -F "OCI k6 service auth token contract" "${workflow}" >/dev/null
grep -F "tools/test/check-oci-k6-service-auth-token-workflow.sh" "${workflow}" >/dev/null
grep -F "tools/test/check-transaction-read-429-source-gate.sh" "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-429-source-gate.sh" "${workflow}" >/dev/null
grep -F "tools/test/check-transaction-read-promotion-pacing-contract.sh" "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-promotion-pacing-contract.sh" "${workflow}" >/dev/null
grep -F "Validate promotion pacing contract" "${workflow}" >/dev/null
grep -F "Validate transaction read 429 source gate" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'workflow_dispatch'" "${workflow}" >/dev/null
grep -F "runs-on: [self-hosted, oci-a1-staging]" "${workflow}" >/dev/null
grep -F "environment:" "${workflow}" >/dev/null
grep -F "name: staging" "${workflow}" >/dev/null
grep -F 'OCI_A1_STAGING_ENV: ${{ secrets.OCI_A1_STAGING_ENV }}' "${workflow}" >/dev/null
grep -F "Load OCI k6 service auth env" "${workflow}" >/dev/null
grep -F 'DEFAULT_K6_DOCKER_CONTEXT="default"' "${workflow}" >/dev/null
grep -F 'DEFAULT_K6_REMOTE_BASE_URL="${STAGING_BASE_URL:-}"' "${workflow}" >/dev/null
grep -F 'DEFAULT_K6_REMOTE_PROMETHEUS_RW_SERVER_URL="http://172.17.0.2:9090/api/v1/write"' "${workflow}" >/dev/null
grep -F 'K6_DOCKER_CONTEXT="${DOCKER_CONTEXT_INPUT:-${CAPACITY_K6_DOCKER_CONTEXT_VAR:-${DEFAULT_K6_DOCKER_CONTEXT}}}"' "${workflow}" >/dev/null
grep -F 'K6_REMOTE_BASE_URL="${REMOTE_BASE_URL_INPUT:-${CAPACITY_K6_REMOTE_BASE_URL:-${CAPACITY_K6_REMOTE_BASE_URL_VAR:-${DEFAULT_K6_REMOTE_BASE_URL}}}}"' "${workflow}" >/dev/null
grep -F 'K6_REMOTE_PROMETHEUS_RW_SERVER_URL="${REMOTE_PROMETHEUS_RW_URL_INPUT:-${CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL:-${CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL_VAR:-${DEFAULT_K6_REMOTE_PROMETHEUS_RW_SERVER_URL}}}}"' "${workflow}" >/dev/null
grep -F 'K6_REMOTE_WORKDIR="${REMOTE_WORKDIR_INPUT:-${CAPACITY_K6_REMOTE_WORKDIR_VAR:-}}"' "${workflow}" >/dev/null
grep -F 'if [[ -z "${K6_REMOTE_WORKDIR}" ]]; then' "${workflow}" >/dev/null
grep -F 'if [[ "${K6_DOCKER_CONTEXT}" == "default" ]]; then' "${workflow}" >/dev/null
grep -F 'K6_REMOTE_WORKDIR="${GITHUB_WORKSPACE}"' "${workflow}" >/dev/null
grep -F 'K6_REMOTE_WORKDIR="${CAPACITY_K6_REMOTE_WORKDIR:-${GITHUB_WORKSPACE}}"' "${workflow}" >/dev/null
grep -F "STAGING_REPLAY_TOKEN" "${workflow}" >/dev/null
grep -F 'K6_AUTH_TOKEN_ENV_NAME="STAGING_REPLAY_TOKEN"' "${workflow}" >/dev/null
grep -F 'K6_AUTH_PREFLIGHT="true"' "${workflow}" >/dev/null
grep -F "K6_AUTH_PREFLIGHT_PATH" "${workflow}" >/dev/null
grep -F "burst_rate:" "${workflow}" >/dev/null
grep -F "arrival_rate:" "${workflow}" >/dev/null
grep -F "time_unit:" "${workflow}" >/dev/null
grep -F "pre_allocated_vus:" "${workflow}" >/dev/null
grep -F "max_vus:" "${workflow}" >/dev/null
grep -F "preemptive_pacing:" "${workflow}" >/dev/null
grep -F 'description: "k6 scenario mode (default constant-arrival-rate strict gate)"' "${workflow}" >/dev/null
grep -F 'default: "constant-arrival-rate"' "${workflow}" >/dev/null
grep -F "nginx_access_log:" "${workflow}" >/dev/null
grep -F "burst_429_rate_threshold:" "${workflow}" >/dev/null
grep -F "backend_429_rate_threshold:" "${workflow}" >/dev/null
grep -F "overload_503_rate_threshold:" "${workflow}" >/dev/null
grep -F "ARRIVAL_RATE_INPUT" "${workflow}" >/dev/null
grep -F "TIME_UNIT_INPUT" "${workflow}" >/dev/null
grep -F "PREEMPTIVE_PACING_INPUT" "${workflow}" >/dev/null
grep -F "NGINX_ACCESS_LOG_INPUT" "${workflow}" >/dev/null
grep -F "OVERLOAD_MODE_INPUT" "${workflow}" >/dev/null
grep -F 'DEFAULT_K6_NGINX_ACCESS_LOG="/var/log/nginx/access.log"' "${workflow}" >/dev/null
grep -F 'K6_RATE="${ARRIVAL_RATE_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_TIME_UNIT="${TIME_UNIT_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_OVERLOAD_MODE="${OVERLOAD_MODE_INPUT}"' "${workflow}" >/dev/null
grep -F 'if [[ -z "${K6_OVERLOAD_MODE}" && "${K6_SCENARIO_MODE}" == "burst" ]]; then' "${workflow}" >/dev/null
grep -F 'K6_OVERLOAD_MODE="true"' "${workflow}" >/dev/null
grep -F 'K6_OVERLOAD_MODE="${K6_OVERLOAD_MODE:-false}"' "${workflow}" >/dev/null
grep -F 'K6_BURST_RATE="${BURST_RATE_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_PRE_ALLOCATED_VUS="${PRE_ALLOCATED_VUS_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_MAX_VUS="${MAX_VUS_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_PREEMPTIVE_PACING="${PREEMPTIVE_PACING_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_PREEMPTIVE_PACING_RPS="${K6_PREEMPTIVE_PACING_RPS:-16}"' "${workflow}" >/dev/null
grep -F 'K6_PREEMPTIVE_PACING_MAX_SLEEP_MS="${K6_PREEMPTIVE_PACING_MAX_SLEEP_MS:-250}"' "${workflow}" >/dev/null
grep -F 'K6_PREEMPTIVE_PACING_JITTER_MS="${K6_PREEMPTIVE_PACING_JITTER_MS:-25}"' "${workflow}" >/dev/null
grep -F 'K6_CONSTANT_VUS_GATE_ROLE="not-selected"' "${workflow}" >/dev/null
grep -F 'K6_CONSTANT_VUS_GATE_ROLE="paced-saturation-contract"' "${workflow}" >/dev/null
grep -F 'K6_CONSTANT_VUS_GATE_ROLE="saturation-observation"' "${workflow}" >/dev/null
grep -F 'K6_BURST_429_RATE_THRESHOLD="${BURST_429_RATE_THRESHOLD_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_BACKEND_429_RATE_THRESHOLD="${BACKEND_429_RATE_THRESHOLD_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_OVERLOAD_503_RATE_THRESHOLD="${OVERLOAD_503_RATE_THRESHOLD_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_NGINX_ACCESS_LOG="${NGINX_ACCESS_LOG_INPUT:-${K6_NGINX_ACCESS_LOG:-${NGINX_ACCESS_LOG:-${CAPACITY_K6_NGINX_ACCESS_LOG:-${CAPACITY_K6_NGINX_ACCESS_LOG_VAR:-${DEFAULT_K6_NGINX_ACCESS_LOG}}}}}}"' "${workflow}" >/dev/null
grep -F "Write k6 pacing evidence artifact" "${workflow}" >/dev/null
grep -F "k6-pacing-input.json" "${workflow}" >/dev/null
grep -F "k6-pacing-summary.md" "${workflow}" >/dev/null
grep -F "K6_PACING_INPUT_REF" "${workflow}" >/dev/null
grep -F "K6_PACING_SUMMARY_REF" "${workflow}" >/dev/null
grep -F '"gate_role": "${K6_CONSTANT_VUS_GATE_ROLE}"' "${workflow}" >/dev/null
grep -F "constant-vus gate role: \${K6_CONSTANT_VUS_GATE_ROLE}" "${workflow}" >/dev/null
grep -F "Capture transaction read Nginx log window" "${workflow}" >/dev/null
grep -F 'K6_NGINX_LOG_SINCE="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"' "${workflow}" >/dev/null
grep -F "Run auth preflight" "${workflow}" >/dev/null
grep -F "tools/test/run-k6-transaction-100m-loadtest.sh --auth-preflight-only" "${workflow}" >/dev/null
grep -F "Run authenticated k6 capacity" "${workflow}" >/dev/null
grep -F "tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps" "${workflow}" >/dev/null
grep -F "Resolve transaction read Nginx access log" "${workflow}" >/dev/null
grep -F 'candidate_raw_log="${RUNNER_TEMP}/${K6_RUN_ID}-nginx-access.raw.jsonl"' "${workflow}" >/dev/null
grep -F 'candidate_run_log="${RUNNER_TEMP}/${K6_RUN_ID}-nginx-access.jsonl"' "${workflow}" >/dev/null
grep -F 'grep -F "\"k6_run_id\":\"${K6_RUN_ID}\"" "${candidate_raw_log}" >"${candidate_run_log}"' "${workflow}" >/dev/null
grep -F 'container_candidates=(' "${workflow}" >/dev/null
grep -F '"${K6_NGINX_CONTAINER:-}"' "${workflow}" >/dev/null
grep -F "docker ps --format '{{.Names}}'" "${workflow}" >/dev/null
grep -F 'docker exec "${container}" test -s /var/log/nginx/access.log' "${workflow}" >/dev/null
grep -F 'docker cp "${container}:/var/log/nginx/access.log" "${candidate_raw_log}"' "${workflow}" >/dev/null
grep -F 'docker logs --since "${K6_NGINX_LOG_SINCE:-1h}" "${container}" >"${candidate_raw_log}"' "${workflow}" >/dev/null
grep -F "Build transaction read Nginx aggregate artifact" "${workflow}" >/dev/null
grep -F "K6_NGINX_ACCESS_LOG" "${workflow}" >/dev/null
grep -F 'echo "::error::K6_NGINX_ACCESS_LOG is missing or empty; transaction-read-nginx-access-aggregate artifact required."' "${workflow}" >/dev/null
grep -F 'NGINX_ACCESS_AGGREGATE_RUN_ID="${K6_RUN_ID}"' "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-nginx-access-aggregate-artifact.sh" "${workflow}" >/dev/null
grep -F "tools/test/check-transaction-read-nginx-access-aggregate-artifact.sh" "${workflow}" >/dev/null
grep -F "Build transaction read 429 source gate artifact" "${workflow}" >/dev/null
grep -F 'summary_json="build/reports/k6/${K6_REPORT_NAME}-summary.json"' "${workflow}" >/dev/null
if grep -F 'summary_json="${report_dir}/${K6_REPORT_NAME}-summary.json"' "${workflow}" >/dev/null; then
  echo "429 source gate must use the root k6 summary JSON, not the nested report dir path" >&2
  exit 1
fi
grep -F 'aggregate_tsv="${report_dir}/transaction-read-nginx-access-aggregate/${K6_REPORT_NAME}-nginx-access-aggregate.tsv"' "${workflow}" >/dev/null
grep -F 'total_fail_rate="${K6_BURST_429_RATE_THRESHOLD}"' "${workflow}" >/dev/null
grep -F 'edge_fail_rate="${K6_BURST_429_RATE_THRESHOLD}"' "${workflow}" >/dev/null
grep -F 'if [[ "${K6_CONSTANT_VUS_GATE_ROLE}" == "saturation-observation" ]]; then' "${workflow}" >/dev/null
grep -F 'total_fail_rate=1' "${workflow}" >/dev/null
grep -F 'edge_fail_rate=1' "${workflow}" >/dev/null
grep -F 'SOURCE_429_GATE_MODE="${K6_CONSTANT_VUS_GATE_ROLE}"' "${workflow}" >/dev/null
grep -F 'SOURCE_429_TOTAL_FAIL_RATE="${total_fail_rate}"' "${workflow}" >/dev/null
grep -F 'SOURCE_429_EDGE_FAIL_RATE="${edge_fail_rate}"' "${workflow}" >/dev/null
grep -F 'SOURCE_429_BACKEND_FAIL_RATE="${K6_BACKEND_429_RATE_THRESHOLD}"' "${workflow}" >/dev/null
grep -F 'SOURCE_429_BACKEND_FAIL_COUNT="${backend_count_fail_threshold}"' "${workflow}" >/dev/null
grep -F 'SOURCE_429_BACKEND_ADMISSION_FAIL_COUNT="${backend_admission_count_fail_threshold}"' "${workflow}" >/dev/null
grep -F 'SOURCE_429_FAIRNESS_FAIL_COUNT="${fairness_count_fail_threshold}"' "${workflow}" >/dev/null
grep -F 'backend_count_fail_threshold=25' "${workflow}" >/dev/null
grep -F 'backend_admission_count_fail_threshold=10' "${workflow}" >/dev/null
grep -F 'fairness_count_fail_threshold=20' "${workflow}" >/dev/null
grep -F 'SOURCE_429_NGINX_AGGREGATE_TSV="${aggregate_tsv}"' "${workflow}" >/dev/null
grep -F "transaction-read-429-source" "${workflow}" >/dev/null
grep -F "transaction-read-nginx-access-aggregate" "${workflow}" >/dev/null
grep -F "actions/upload-artifact@" "${workflow}" >/dev/null
grep -F "oci-k6-service-auth-token" "${workflow}" >/dev/null

auth_preflight_line="$(grep -n -- "--auth-preflight-only" "${workflow}" | head -1 | cut -d: -f1)"
k6_run_line="$(grep -n -- "--no-up --no-deps" "${workflow}" | head -1 | cut -d: -f1)"
if [[ -z "${auth_preflight_line}" || -z "${k6_run_line}" || "${auth_preflight_line}" -ge "${k6_run_line}" ]]; then
  echo "auth preflight must run before authenticated k6 capacity" >&2
  exit 1
fi

nginx_aggregate_line="$(grep -n "Build transaction read Nginx aggregate artifact" "${workflow}" | head -1 | cut -d: -f1)"
source_gate_line="$(grep -n "Build transaction read 429 source gate artifact" "${workflow}" | head -1 | cut -d: -f1)"
upload_line="$(grep -n "Upload OCI k6 auth artifact" "${workflow}" | head -1 | cut -d: -f1)"
if [[ -z "${nginx_aggregate_line}" || -z "${source_gate_line}" || -z "${upload_line}" ||
  "${nginx_aggregate_line}" -ge "${source_gate_line}" || "${source_gate_line}" -ge "${upload_line}" ]]; then
  echo "429 source gate artifact must run after Nginx aggregate and before upload" >&2
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
if grep -F 'default: "/srv/aquila-bank"' "${workflow}" >/dev/null; then
  echo "service auth workflow must not default remote workdir to a stale path" >&2
  exit 1
fi
