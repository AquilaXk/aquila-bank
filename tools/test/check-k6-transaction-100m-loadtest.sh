#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-k6-transaction-100m-loadtest.sh"

echo "[k6-transaction-100m-loadtest] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
cleanup() {
  rm -rf "${temp_dir}"
  rm -f build/reports/k6/check-k6-secret-test*
}
trap cleanup EXIT

fake_bin="${temp_dir}/bin"
mkdir -p "${fake_bin}"

cat >"${fake_bin}/docker" <<'SH'
#!/usr/bin/env bash
if [[ "${1:-}" == "exec" ]]; then
  exit 1
fi
if [[ -n "${FAKE_DOCKER_ARGS_FILE:-}" ]]; then
  printf '%s\n' "$@" >"${FAKE_DOCKER_ARGS_FILE}"
fi
exit 0
SH
chmod +x "${fake_bin}/docker"

cat >"${fake_bin}/curl" <<'SH'
#!/usr/bin/env bash
follow_redirects=false
has_authorization=false
url=""
for arg in "$@"; do
  if [[ "${arg}" == "--location" || "${arg}" == "-L" ]]; then
    follow_redirects=true
  fi
  if [[ "${arg}" == "Authorization: Bearer "* ]]; then
    has_authorization=true
  fi
  if [[ "${arg}" == http://* || "${arg}" == https://* ]]; then
    url="${arg}"
  fi
done

if [[ "${follow_redirects}" == "true" && "${url}" == http://staging.example.com/* ]]; then
  # curl은 cross-host redirect에서 Authorization을 자동 전달하지 않아 최종 auth endpoint가 401을 낸다.
  printf "401\thttps://bank.example.com/api/v1/transactions?accountId=910000001&from=2026-04-01T00:00:00Z&to=2026-04-30T00:00:00Z&limit=1"
elif [[ "${follow_redirects}" == "true" && "${url}" == https://bank.example.com/* && "${has_authorization}" == "true" ]]; then
  printf "200\thttps://bank.example.com/api/v1/transactions?accountId=910000001&from=2026-04-01T00:00:00Z&to=2026-04-30T00:00:00Z&limit=1"
elif [[ "${follow_redirects}" == "true" ]]; then
  printf "200\thttps://staging.example.com/api/v1/transactions?accountId=910000001&from=2026-04-01T00:00:00Z&to=2026-04-30T00:00:00Z&limit=1"
else
  printf "308"
fi
SH
chmod +x "${fake_bin}/curl"

echo "[k6-transaction-100m-loadtest] auth preflight follows redirect and canonicalizes remote base"
output="$(
  PATH="${fake_bin}:${PATH}" \
  K6_GENERATOR_MODE=docker-context \
  K6_DOCKER_CONTEXT=default \
  K6_REMOTE_BASE_URL=http://staging.example.com \
  K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://127.0.0.1:9090/api/v1/write \
  K6_RUN_PURPOSE=capacity \
  K6_AUTH_TOKEN=fake-token \
  K6_AUTH_PREFLIGHT=true \
  K6_AUTH_PREFLIGHT_PATH='api/v1/transactions?accountId=910000001&from=2026-04-01T00:00:00Z&to=2026-04-30T00:00:00Z&limit=1' \
  K6_AUTH_PREFLIGHT_TIMEOUT_SECONDS=1 \
    "${runner}" --auth-preflight-only 2>&1
)"
grep -F "auth status preflight: expected_status=200 timeout=1 follow_redirects=true" <<<"${output}" >/dev/null
grep -F "auth preflight canonicalized base url=https://bank.example.com" <<<"${output}" >/dev/null

echo "[k6-transaction-100m-loadtest] auth preflight can keep strict no-redirect mode"
set +e
strict_output="$(
  PATH="${fake_bin}:${PATH}" \
  K6_GENERATOR_MODE=docker-context \
  K6_DOCKER_CONTEXT=default \
  K6_REMOTE_BASE_URL=http://staging.example.com \
  K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://127.0.0.1:9090/api/v1/write \
  K6_RUN_PURPOSE=capacity \
  K6_AUTH_TOKEN=fake-token \
  K6_AUTH_PREFLIGHT=true \
  K6_AUTH_PREFLIGHT_FOLLOW_REDIRECTS=false \
  K6_AUTH_PREFLIGHT_PATH='api/v1/transactions?accountId=910000001&from=2026-04-01T00:00:00Z&to=2026-04-30T00:00:00Z&limit=1' \
  K6_AUTH_PREFLIGHT_TIMEOUT_SECONDS=1 \
    "${runner}" --auth-preflight-only 2>&1
)"
strict_status=$?
set -e
if [[ "${strict_status}" -eq 0 ]]; then
  echo "strict no-redirect auth preflight unexpectedly passed" >&2
  exit 1
fi
grep -F "auth status preflight failed: expected=200 actual=308" <<<"${strict_output}" >/dev/null

backend_env_b64="$(
  python3 -c 'import base64; print(base64.b64encode(b"export AQUILA_TRANSACTION_CURSOR_HMAC_SECRET=\"quoted-cursor\"\nSECURITY_JWT_SECRET=\x27quoted-jwt\x27\n").decode())'
)"

echo "[k6-transaction-100m-loadtest] extracts quoted secrets from OCI_A1_BACKEND_ENV_B64 in docker-context mode"
remote_args_file="${temp_dir}/remote-docker.args"
PATH="${fake_bin}:${PATH}" \
FAKE_DOCKER_ARGS_FILE="${remote_args_file}" \
OCI_A1_BACKEND_ENV_B64="${backend_env_b64}" \
K6_REPORT_NAME="check-k6-secret-test-remote" \
K6_GENERATOR_MODE=docker-context \
K6_DOCKER_CONTEXT=default \
K6_REMOTE_BASE_URL=https://staging.example.com \
K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://127.0.0.1:9090/api/v1/write \
K6_OBSERVABILITY_MODE=summary-only \
K6_PREFLIGHT=false \
K6_POSTGRES_HEALTH_GATE=false \
K6_POSTGRES_RECOVERY_GATE=false \
K6_POSTGRES_EXPORTER_STABLE_GATE=false \
K6_EXPLAIN_SNAPSHOT=false \
K6_BACKEND_READINESS_GATE=false \
K6_REMOTE_PREFLIGHT=false \
K6_AUTH_PREFLIGHT=false \
K6_AUTH_TOKEN=fake-token \
K6_SUMMARY_GATE=false \
K6_ARCHIVE_RESULTS=false \
K6_ARCHIVE_FAILED_SUMMARY=false \
K6_REMOTE_COLLECT_ARTIFACTS=false \
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
  "${runner}" --no-up --no-deps >/dev/null
grep -Fx "AQUILA_TRANSACTION_CURSOR_HMAC_SECRET=quoted-cursor" "${remote_args_file}" >/dev/null
grep -Fx "SECURITY_JWT_SECRET=quoted-jwt" "${remote_args_file}" >/dev/null

echo "[k6-transaction-100m-loadtest] omits empty secret overrides in local compose mode"
local_args_file="${temp_dir}/local-docker.args"
env -u AQUILA_TRANSACTION_CURSOR_HMAC_SECRET -u SECURITY_JWT_SECRET -u OCI_A1_BACKEND_ENV_B64 -u BACKEND_ENV_B64 \
PATH="${fake_bin}:${PATH}" \
FAKE_DOCKER_ARGS_FILE="${local_args_file}" \
K6_REPORT_NAME="check-k6-secret-test-local" \
K6_GENERATOR_MODE=local \
K6_OBSERVABILITY_MODE=summary-only \
K6_PREFLIGHT=false \
K6_POSTGRES_HEALTH_GATE=false \
K6_POSTGRES_RECOVERY_GATE=false \
K6_POSTGRES_EXPORTER_STABLE_GATE=false \
K6_EXPLAIN_SNAPSHOT=false \
K6_BACKEND_READINESS_GATE=false \
K6_AUTH_PREFLIGHT=false \
K6_SUMMARY_GATE=false \
K6_ARCHIVE_RESULTS=false \
K6_ARCHIVE_FAILED_SUMMARY=false \
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
  "${runner}" --no-up --no-deps >/dev/null
if grep -E "^(AQUILA_TRANSACTION_CURSOR_HMAC_SECRET|SECURITY_JWT_SECRET)=" "${local_args_file}" >/dev/null; then
  echo "local compose run injected empty secret override" >&2
  exit 1
fi
