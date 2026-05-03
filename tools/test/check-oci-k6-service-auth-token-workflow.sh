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
grep -F "STAGING_REPLAY_TOKEN" "${workflow}" >/dev/null
grep -F 'K6_AUTH_TOKEN_ENV_NAME="STAGING_REPLAY_TOKEN"' "${workflow}" >/dev/null
grep -F 'K6_AUTH_PREFLIGHT="true"' "${workflow}" >/dev/null
grep -F "K6_AUTH_PREFLIGHT_PATH" "${workflow}" >/dev/null
grep -F "Run auth preflight" "${workflow}" >/dev/null
grep -F "tools/test/run-k6-transaction-100m-loadtest.sh --auth-preflight-only" "${workflow}" >/dev/null
grep -F "Run authenticated k6 capacity" "${workflow}" >/dev/null
grep -F "tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps" "${workflow}" >/dev/null
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
