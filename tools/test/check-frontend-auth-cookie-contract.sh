#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail_if_found() {
  local pattern="$1"
  local path="$2"
  local message="$3"

  if rg -n "$pattern" "$ROOT_DIR/$path" >/tmp/aquila_front_auth_contract_match.txt; then
    echo "[FAIL] $message" >&2
    cat /tmp/aquila_front_auth_contract_match.txt >&2
    exit 1
  fi
}

fail_if_found "localStorage|sessionStorage" "front/src/lib/api/session.ts" \
  "customer session module must not use browser storage"
fail_if_found "accessToken\\?:" "front/src/lib/api/client.ts" \
  "API client request options must not accept browser-readable access token"
fail_if_found "headers\\.Authorization|Authorization =" "front/src/lib/api/client.ts" \
  "API client must not attach Authorization from browser state"
fail_if_found "searchParams\\.set\\(\"accessToken\"" "front/src/lib/api/client.ts" \
  "SSE stream URL must not append accessToken query parameter"
fail_if_found "currentSession\\.(accessToken|refreshToken)|session\\.accessToken" "front/src" \
  "customer frontend must not read token fields from CustomerSession"
fail_if_found "accessToken: string;|refreshToken: string;" "front/src/lib/api/types.ts" \
  "CustomerSession must not expose token fields"

echo "[OK] frontend auth cookie contract"
