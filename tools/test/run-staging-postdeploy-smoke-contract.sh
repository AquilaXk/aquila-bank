#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/staging-postdeploy-smoke.sh"

echo "[staging-postdeploy-smoke-contract] syntax: ${script}"
bash -n "${script}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

bin_dir="${temp_dir}/bin"
mkdir -p "${bin_dir}"

cat >"${bin_dir}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

method=""
has_body=false
body=""

while (($# > 0)); do
  case "$1" in
    --request)
      method="$2"
      shift 2
      ;;
    --data)
      has_body=true
      body="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

printf '%s\t%s\t%s\n' "${method}" "${has_body}" "${body}" >>"${CURL_CALLS}"

if [[ "${method}" == "GET" && "${has_body}" == "true" ]]; then
  echo "GET smoke request must not include a JSON body" >&2
  exit 44
fi
EOF
chmod +x "${bin_dir}/curl"

calls="${temp_dir}/curl-calls.tsv"

echo "[staging-postdeploy-smoke-contract] GET write smoke omits body"
: >"${calls}"
PATH="${bin_dir}:${PATH}" \
CURL_CALLS="${calls}" \
STAGING_BASE_URL="https://staging.example.com" \
STAGING_SMOKE_READ_PATH="/actuator/health" \
STAGING_SMOKE_WRITE_PATH="/actuator/health" \
STAGING_SMOKE_WRITE_METHOD="GET" \
"${script}"

if grep -F $'GET\ttrue' "${calls}" >/dev/null; then
  echo "GET write smoke unexpectedly included a body" >&2
  exit 1
fi

echo "[staging-postdeploy-smoke-contract] POST write smoke keeps body"
: >"${calls}"
PATH="${bin_dir}:${PATH}" \
CURL_CALLS="${calls}" \
STAGING_BASE_URL="https://staging.example.com" \
STAGING_SMOKE_READ_PATH="/actuator/health" \
STAGING_SMOKE_WRITE_PATH="/api/test" \
STAGING_SMOKE_WRITE_METHOD="POST" \
STAGING_SMOKE_WRITE_BODY='{"ok":true}' \
"${script}"

grep -F $'POST\ttrue\t{"ok":true}' "${calls}" >/dev/null
