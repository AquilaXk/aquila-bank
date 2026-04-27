#!/usr/bin/env bash
set -euo pipefail

echo "[loadtest-outbox-preflight] shell syntax"
bash -n tools/test/issue-internal-service-token.sh
bash -n tools/test/run-outbox-provider-backlog-local-gate.sh
bash -n tools/test/run-k6-transaction-100m-loadtest.sh
bash -n tools/test/run-defensive-runtime-http-admission-compose.sh

echo "[loadtest-outbox-preflight] compose token env"
grep -F "SECURITY_INTERNAL_SERVICE_TOKEN_ISSUER" compose.loadtest.yml >/dev/null
grep -F "SECURITY_INTERNAL_SERVICE_TOKEN_AUDIENCE" compose.loadtest.yml >/dev/null
grep -F "SECURITY_INTERNAL_SERVICE_TOKEN_KEYS_OPS_202604" compose.loadtest.yml >/dev/null

echo "[loadtest-outbox-preflight] local gate env"
local_plan="$(tools/test/run-outbox-provider-backlog-local-gate.sh --print-plan)"
grep -F "token_issuer=dev-internal-service" <<<"${local_plan}" >/dev/null
grep -F "token_audience=aquila-internal-api" <<<"${local_plan}" >/dev/null
grep -F "notification_ops_enabled=true" tools/test/run-outbox-provider-backlog-local-gate.sh >/dev/null

echo "[loadtest-outbox-preflight] k6/admission preflight plan"
k6_plan="$(tools/test/run-k6-transaction-100m-loadtest.sh --print-plan)"
grep -F "outbox_preflight=false" <<<"${k6_plan}" >/dev/null
grep -F "K6_OUTBOX_PREFLIGHT" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F "run_outbox_preflight" tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null

admission_plan="$(tools/test/run-defensive-runtime-http-admission-compose.sh --print-plan)"
grep -F "base_url=http://localhost:18080" <<<"${admission_plan}" >/dev/null
grep -F "outbox_preflight=false" <<<"${admission_plan}" >/dev/null
grep -F "run_outbox_preflight" tools/test/run-defensive-runtime-http-admission-compose.sh >/dev/null
