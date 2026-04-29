#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/staging-rollback-guard.sh"

echo "[staging-rollback-guard-contract] syntax: ${script}"
bash -n "${script}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

bin_dir="${temp_dir}/bin"
mkdir -p "${bin_dir}"

cat >"${bin_dir}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "curl-called" >"${ROLLBACK_CURL_MARKER}"
EOF
chmod +x "${bin_dir}/curl"

echo "[staging-rollback-guard-contract] partial hook config warns and skips"
partial_output="$(
  PATH="${bin_dir}:${PATH}" \
  ROLLBACK_CURL_MARKER="${temp_dir}/partial-curl" \
  STAGING_ROLLBACK_WEBHOOK_URL="https://rollback.example.com/hook" \
  STAGING_ROLLBACK_TOKEN="" \
  DEPLOY_SHA="0123456789abcdef0123456789abcdef01234567" \
  DEPLOY_LOG_URL="https://github.example/run" \
  GITHUB_REPOSITORY="AquilaXk/aquila-bank" \
  DEPLOYMENT_ID="123" \
  "${script}" 2>&1
)"

grep -F "rollback hook is partially configured; skipping" <<<"${partial_output}" >/dev/null
if [ -f "${temp_dir}/partial-curl" ]; then
  echo "partial rollback config must not dispatch curl" >&2
  exit 1
fi

echo "[staging-rollback-guard-contract] complete hook config dispatches"
PATH="${bin_dir}:${PATH}" \
ROLLBACK_CURL_MARKER="${temp_dir}/complete-curl" \
STAGING_ROLLBACK_WEBHOOK_URL="https://rollback.example.com/hook" \
STAGING_ROLLBACK_TOKEN="token" \
DEPLOY_SHA="0123456789abcdef0123456789abcdef01234567" \
DEPLOY_LOG_URL="https://github.example/run" \
GITHUB_REPOSITORY="AquilaXk/aquila-bank" \
DEPLOYMENT_ID="123" \
"${script}" >/dev/null

[ -f "${temp_dir}/complete-curl" ]
