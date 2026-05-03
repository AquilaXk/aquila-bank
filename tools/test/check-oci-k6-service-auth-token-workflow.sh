#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/oci-k6-service-auth-token.yml"

echo "[oci-k6-service-auth] workflow exists"
test -f "${workflow}"

echo "[oci-k6-service-auth] workflow contract"
grep -F "name: OCI k6 service auth token" "${workflow}" >/dev/null
grep -F "workflow_dispatch:" "${workflow}" >/dev/null
grep -F "pull_request:" "${workflow}" >/dev/null
grep -F "OCI k6 service auth token contract" "${workflow}" >/dev/null
grep -F "tools/test/check-oci-k6-service-auth-token-workflow.sh" "${workflow}" >/dev/null
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
grep -F "pre_allocated_vus:" "${workflow}" >/dev/null
grep -F "max_vus:" "${workflow}" >/dev/null
grep -F "burst_429_rate_threshold:" "${workflow}" >/dev/null
grep -F "backend_429_rate_threshold:" "${workflow}" >/dev/null
grep -F "overload_503_rate_threshold:" "${workflow}" >/dev/null
grep -F "OVERLOAD_MODE_INPUT" "${workflow}" >/dev/null
grep -F 'K6_OVERLOAD_MODE="${OVERLOAD_MODE_INPUT}"' "${workflow}" >/dev/null
grep -F 'if [[ -z "${K6_OVERLOAD_MODE}" && "${K6_SCENARIO_MODE}" == "burst" ]]; then' "${workflow}" >/dev/null
grep -F 'K6_OVERLOAD_MODE="true"' "${workflow}" >/dev/null
grep -F 'K6_OVERLOAD_MODE="${K6_OVERLOAD_MODE:-false}"' "${workflow}" >/dev/null
grep -F 'K6_BURST_RATE="${BURST_RATE_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_PRE_ALLOCATED_VUS="${PRE_ALLOCATED_VUS_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_MAX_VUS="${MAX_VUS_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_BURST_429_RATE_THRESHOLD="${BURST_429_RATE_THRESHOLD_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_BACKEND_429_RATE_THRESHOLD="${BACKEND_429_RATE_THRESHOLD_INPUT}"' "${workflow}" >/dev/null
grep -F 'K6_OVERLOAD_503_RATE_THRESHOLD="${OVERLOAD_503_RATE_THRESHOLD_INPUT}"' "${workflow}" >/dev/null
grep -F "Run auth preflight" "${workflow}" >/dev/null
grep -F "tools/test/run-k6-transaction-100m-loadtest.sh --auth-preflight-only" "${workflow}" >/dev/null
grep -F "Run authenticated k6 capacity" "${workflow}" >/dev/null
grep -F "tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps" "${workflow}" >/dev/null
grep -F "Build transaction read Nginx aggregate artifact" "${workflow}" >/dev/null
grep -F "K6_NGINX_ACCESS_LOG" "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-read-nginx-access-aggregate-artifact.sh" "${workflow}" >/dev/null
grep -F "transaction-read-nginx-access-aggregate" "${workflow}" >/dev/null
grep -F "actions/upload-artifact@" "${workflow}" >/dev/null
grep -F "oci-k6-service-auth-token" "${workflow}" >/dev/null

auth_preflight_line="$(grep -n -- "--auth-preflight-only" "${workflow}" | head -1 | cut -d: -f1)"
k6_run_line="$(grep -n -- "--no-up --no-deps" "${workflow}" | head -1 | cut -d: -f1)"
if [[ -z "${auth_preflight_line}" || -z "${k6_run_line}" || "${auth_preflight_line}" -ge "${k6_run_line}" ]]; then
  echo "auth preflight must run before authenticated k6 capacity" >&2
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
