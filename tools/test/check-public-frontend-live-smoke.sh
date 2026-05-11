#!/usr/bin/env bash
set -euo pipefail

base_url="${PUBLIC_FRONTEND_BASE_URL:-}"
expect_hsts="${PUBLIC_FRONTEND_EXPECT_HSTS:-false}"
timeout_seconds="${PUBLIC_FRONTEND_CURL_TIMEOUT_SECONDS:-10}"
retry_count="${PUBLIC_FRONTEND_CURL_RETRY_COUNT:-3}"
retry_sleep_seconds="${PUBLIC_FRONTEND_CURL_RETRY_SLEEP_SECONDS:-1}"

if [[ -z "${base_url}" ]]; then
  echo "[public-frontend-live] skipped: PUBLIC_FRONTEND_BASE_URL is not set"
  exit 0
fi

base_url="${base_url%/}"
tmp_dir="$(mktemp -d)"

cleanup() {
  rm -rf "${tmp_dir}"
}
trap cleanup EXIT

request() {
  local path="$1"
  local label="$2"
  local header_path="${tmp_dir}/${label}.headers"
  local body_path="${tmp_dir}/${label}.body"
  local status attempt

  for ((attempt = 1; attempt <= retry_count; attempt += 1)); do
    : >"${header_path}"
    : >"${body_path}"
    status="$(curl -sS --max-time "${timeout_seconds}" \
      -D "${header_path}" \
      -o "${body_path}" \
      -w '%{http_code}' \
      "${base_url}${path}" || true)"
    if [[ "${status}" != "000" || "${attempt}" -eq "${retry_count}" ]]; then
      break
    fi
    sleep "${retry_sleep_seconds}"
  done

  printf '%s\n' "${status}" >"${tmp_dir}/${label}.status"
}

status_of() {
  local label="$1"
  cat "${tmp_dir}/${label}.status"
}

require_status() {
  local label="$1"
  local expected="$2"
  local actual
  actual="$(status_of "${label}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "[public-frontend-live] ${label} expected status ${expected}, got ${actual}" >&2
    sed -n '1,80p' "${tmp_dir}/${label}.headers" >&2
    sed -n '1,120p' "${tmp_dir}/${label}.body" >&2
    exit 1
  fi
}

require_header() {
  local label="$1"
  local name="$2"
  local expected="$3"
  local header_path="${tmp_dir}/${label}.headers"

  if ! grep -Eiq "^${name}:[[:space:]]*${expected}[[:space:]]*$" "${header_path}"; then
    echo "[public-frontend-live] ${label} missing header: ${name}: ${expected}" >&2
    sed -n '1,80p' "${header_path}" >&2
    exit 1
  fi
}

require_header_present() {
  local label="$1"
  local name="$2"
  local header_path="${tmp_dir}/${label}.headers"

  if ! grep -Eiq "^${name}:" "${header_path}"; then
    echo "[public-frontend-live] ${label} missing header: ${name}" >&2
    sed -n '1,80p' "${header_path}" >&2
    exit 1
  fi
}

require_body_contains() {
  local label="$1"
  local expected="$2"
  local body_path="${tmp_dir}/${label}.body"

  if ! grep -Fq -- "${expected}" "${body_path}"; then
    echo "[public-frontend-live] ${label} body missing: ${expected}" >&2
    sed -n '1,160p' "${body_path}" >&2
    exit 1
  fi
}

assert_root() {
  request "/" "root"
  require_status "root" "200"
  require_header "root" "X-Content-Type-Options" "nosniff"
  require_header "root" "X-Frame-Options" "DENY"
  require_header "root" "Referrer-Policy" "no-referrer"
  require_header "root" "X-Robots-Tag" "noindex, nofollow, noarchive"
  if [[ "${expect_hsts}" == "true" ]]; then
    require_header_present "root" "Strict-Transport-Security"
  fi

  require_body_contains "root" "Aquila Bank 개인 인터넷뱅킹"
  require_body_contains "root" "Aquila Bank"
  require_body_contains "root" "개인뱅킹 메뉴"
  require_body_contains "root" "통합검색"
  require_body_contains "root" "로그인 상태"
  require_body_contains "root" "brand-mascot"
  require_body_contains "root" "width=device-width"
}

assert_asset() {
  local path="$1"
  local label="$2"

  request "${path}" "${label}"
  require_status "${label}" "200"
}

assert_manifest() {
  request "/manifest.webmanifest" "manifest"
  require_status "manifest" "200"
  require_body_contains "manifest" "Aquila Bank 개인 인터넷뱅킹"
  require_body_contains "manifest" "https://bank.aquilaxk.site"
}

assert_root
assert_asset "/favicon.ico" "favicon"
assert_asset "/apple-touch-icon.png" "apple_touch_icon"
assert_manifest

echo "[public-frontend-live] live smoke passed"
