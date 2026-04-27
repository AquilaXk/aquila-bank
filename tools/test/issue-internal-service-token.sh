#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/issue-internal-service-token.sh <scope> [subject]

Environment:
  SECURITY_INTERNAL_SERVICE_TOKEN_ISSUER      default dev-internal-service
  SECURITY_INTERNAL_SERVICE_TOKEN_AUDIENCE    default aquila-internal-api
  SECURITY_INTERNAL_SERVICE_TOKEN_ACTIVE_KEY_ID default ops-202604
  SECURITY_INTERNAL_SERVICE_TOKEN_KEYS_OPS_202604 default dev-internal-service-secret-ops-202604
  SECURITY_INTERNAL_SERVICE_TOKEN_TTL_SECONDS default 300

Examples:
  tools/test/issue-internal-service-token.sh internal:outbox-ops outbox-backlog-local
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
if [[ "$#" -lt 1 || "$#" -gt 2 ]]; then
  usage
  exit 1
fi

scope="$1"
subject="${2:-local-ops}"
issuer="${SECURITY_INTERNAL_SERVICE_TOKEN_ISSUER:-dev-internal-service}"
audience="${SECURITY_INTERNAL_SERVICE_TOKEN_AUDIENCE:-aquila-internal-api}"
key_id="${SECURITY_INTERNAL_SERVICE_TOKEN_ACTIVE_KEY_ID:-ops-202604}"
ttl_seconds="${SECURITY_INTERNAL_SERVICE_TOKEN_TTL_SECONDS:-300}"
secret_var="SECURITY_INTERNAL_SERVICE_TOKEN_KEYS_${key_id//-/_}"
secret_var="$(printf "%s" "${secret_var}" | tr '[:lower:]' '[:upper:]')"
secret="${!secret_var:-${SECURITY_INTERNAL_SERVICE_TOKEN_KEYS_OPS_202604:-dev-internal-service-secret-ops-202604}}"
issued_at="$(date +%s)"
expires_at=$((issued_at + ttl_seconds))

if [[ -z "${scope}" || -z "${subject}" || -z "${issuer}" || -z "${audience}" || -z "${key_id}" || -z "${secret}" ]]; then
  echo "internal service token input must not be blank" >&2
  exit 1
fi
if ! [[ "${ttl_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "SECURITY_INTERNAL_SERVICE_TOKEN_TTL_SECONDS must be a positive integer" >&2
  exit 1
fi
command -v openssl >/dev/null 2>&1 || { echo "openssl command is required" >&2; exit 1; }

base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

header="$(printf '{"alg":"HS256","typ":"JWT","kid":"%s"}' "${key_id}" | base64url)"
payload="$(
  printf '{"iss":"%s","sub":"%s","aud":"%s","iat":%s,"exp":%s,"scope":"%s"}' \
    "${issuer}" "${subject}" "${audience}" "${issued_at}" "${expires_at}" "${scope}" \
    | base64url
)"
signing_input="${header}.${payload}"
signature="$(printf "%s" "${signing_input}" | openssl dgst -sha256 -hmac "${secret}" -binary | base64url)"
printf "%s.%s\n" "${signing_input}" "${signature}"
