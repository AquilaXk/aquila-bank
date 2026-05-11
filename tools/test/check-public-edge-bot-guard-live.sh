#!/usr/bin/env bash
set -euo pipefail

base_url="${PUBLIC_EDGE_BASE_URL:-}"
expect_https_redirect="${PUBLIC_EDGE_EXPECT_HTTPS_REDIRECT:-false}"
expect_hsts="${PUBLIC_EDGE_EXPECT_HSTS:-false}"
timeout_seconds="${PUBLIC_EDGE_CURL_TIMEOUT_SECONDS:-10}"
retry_count="${PUBLIC_EDGE_CURL_RETRY_COUNT:-3}"
retry_sleep_seconds="${PUBLIC_EDGE_CURL_RETRY_SLEEP_SECONDS:-1}"

if [[ -z "${base_url}" ]]; then
  echo "[public-edge-bot-guard] skipped: PUBLIC_EDGE_BASE_URL is not set"
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

require_header() {
  local label="$1"
  local name="$2"
  local expected="$3"
  local header_path="${tmp_dir}/${label}.headers"

  if ! grep -Eiq "^${name}:[[:space:]]*${expected}[[:space:]]*$" "${header_path}"; then
    echo "[public-edge-bot-guard] ${label} missing header: ${name}: ${expected}" >&2
    echo "[public-edge-bot-guard] status=$(status_of "${label}")" >&2
    sed -n '1,80p' "${header_path}" >&2
    exit 1
  fi
}

require_header_present() {
  local label="$1"
  local name="$2"
  local header_path="${tmp_dir}/${label}.headers"

  if ! grep -Eiq "^${name}:" "${header_path}"; then
    echo "[public-edge-bot-guard] ${label} missing header: ${name}" >&2
    echo "[public-edge-bot-guard] status=$(status_of "${label}")" >&2
    sed -n '1,80p' "${header_path}" >&2
    exit 1
  fi
}

require_status() {
  local label="$1"
  local expected="$2"
  local actual
  actual="$(status_of "${label}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "[public-edge-bot-guard] ${label} expected status ${expected}, got ${actual}" >&2
    sed -n '1,80p' "${tmp_dir}/${label}.headers" >&2
    sed -n '1,120p' "${tmp_dir}/${label}.body" >&2
    exit 1
  fi
}

require_root_headers() {
  local label="$1"

  require_header "${label}" "X-Content-Type-Options" "nosniff"
  require_header "${label}" "X-Frame-Options" "DENY"
  require_header "${label}" "Referrer-Policy" "no-referrer"
  require_header "${label}" "Permissions-Policy" "geolocation=\\(\\), microphone=\\(\\), camera=\\(\\)"
  require_header "${label}" "X-Robots-Tag" "noindex, nofollow, noarchive"

  if [[ "${expect_hsts}" == "true" ]]; then
    require_header_present "${label}" "Strict-Transport-Security"
  fi
}

assert_root() {
  request "/" "root"
  local status
  status="$(status_of "root")"

  case "${status}" in
    200)
      if [[ "${expect_https_redirect}" == "true" ]]; then
        echo "[public-edge-bot-guard] root expected HTTPS redirect, got 200" >&2
        exit 1
      fi
      require_root_headers "root"
      ;;
    301|302|307|308)
      require_root_headers "root"
      require_header_present "root" "Location"
      if [[ "${expect_https_redirect}" == "true" ]]; then
        if ! grep -Eiq '^Location:[[:space:]]*https://' "${tmp_dir}/root.headers"; then
          echo "[public-edge-bot-guard] root redirect must target https" >&2
          sed -n '1,80p' "${tmp_dir}/root.headers" >&2
          exit 1
        fi
      fi
      ;;
    *)
      echo "[public-edge-bot-guard] root unexpected status: ${status}" >&2
      sed -n '1,80p' "${tmp_dir}/root.headers" >&2
      exit 1
      ;;
  esac
}

assert_scanner_path() {
  local path="$1"
  local label="$2"

  request "${path}" "${label}"
  require_status "${label}" "404"
  require_header "${label}" "X-Aquila-Reject-Source" "nginx-bot-guard"
  require_header "${label}" "X-Aquila-Reject-Reason" "scanner-path"
}

assert_health() {
  request "/actuator/health" "health"
  require_status "health" "200"
  if ! grep -Fq '"status":"UP"' "${tmp_dir}/health.body"; then
    echo "[public-edge-bot-guard] health body must include status UP" >&2
    sed -n '1,120p' "${tmp_dir}/health.body" >&2
    exit 1
  fi
}

assert_root
assert_scanner_path "/.env" "scanner_env"
assert_scanner_path "/.git/config" "scanner_git"
assert_scanner_path "/wp-login.php" "scanner_wp_login"
assert_scanner_path "/xmlrpc.php" "scanner_xmlrpc"
assert_scanner_path "/phpmyadmin/" "scanner_phpmyadmin"
assert_health

echo "[public-edge-bot-guard] live smoke passed"
