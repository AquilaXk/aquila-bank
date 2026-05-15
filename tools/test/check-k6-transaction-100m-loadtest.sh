#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-k6-transaction-100m-loadtest.sh"

echo "[k6-transaction-100m-loadtest] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

fake_bin="${temp_dir}/bin"
mkdir -p "${fake_bin}"

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
