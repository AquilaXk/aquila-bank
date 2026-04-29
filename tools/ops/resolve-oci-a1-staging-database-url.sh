#!/usr/bin/env bash
set -euo pipefail

BACKEND_ENV_B64="${OCI_A1_BACKEND_ENV_B64:-${BACKEND_ENV_B64:-}}"
STAGING_OCI_A1_DATABASE_URL="${STAGING_OCI_A1_DATABASE_URL:-}"
POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-aquila-postgres}"
POSTGRES_NETWORK_ALIAS="${POSTGRES_NETWORK_ALIAS:-aquila-postgres}"
POSTGRES_HOST_BIND="${POSTGRES_HOST_BIND:-127.0.0.1:5432}"

fail() {
  echo "::error::$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

decode_base64() {
  local encoded="$1"
  local output="$2"

  if printf '%s' "$encoded" | base64 -d >"$output" 2>/dev/null; then
    return 0
  fi

  if printf '%s' "$encoded" | base64 -D >"$output" 2>/dev/null; then
    return 0
  fi

  fail "Failed to decode OCI_A1_BACKEND_ENV_B64"
}

env_value() {
  local name="$1"
  local file="$2"
  sed -n "s/^${name}=//p" "$file" | tail -1
}

url_encode() {
  jq -nr --arg value "$1" '$value | @uri'
}

postgres_host_requires_container() {
  local db_host="$1"
  [[ "$db_host" == "$POSTGRES_CONTAINER_NAME" || "$db_host" == "$POSTGRES_NETWORK_ALIAS" ]]
}

resolve_host_bind() {
  local bind="$1"
  local host port

  if [[ "$bind" == *:* ]]; then
    host="${bind%:*}"
    port="${bind##*:}"
  else
    host="127.0.0.1"
    port="$bind"
  fi

  if [[ -z "$host" || "$host" == "0.0.0.0" || "$host" == "::" ]]; then
    host="127.0.0.1"
  fi

  [[ "$port" =~ ^[1-9][0-9]*$ ]] || fail "POSTGRES_HOST_BIND must include a positive port"
  printf '%s %s\n' "$host" "$port"
}

build_postgres_url() {
  local db_host="$1"
  local db_port="$2"
  local db_name="$3"
  local db_user="$4"
  local db_password="$5"

  printf 'postgresql://%s:%s@%s:%s/%s\n' \
    "$(url_encode "$db_user")" \
    "$(url_encode "$db_password")" \
    "$db_host" \
    "$db_port" \
    "$(url_encode "$db_name")"
}

fallback_explicit_url() {
  if [[ -n "$STAGING_OCI_A1_DATABASE_URL" ]]; then
    printf '%s\n' "$STAGING_OCI_A1_DATABASE_URL"
    return 0
  fi

  fail "STAGING_OCI_A1_DATABASE_URL is required when backend DB host is not the OCI PostgreSQL container alias"
}

require_command base64
require_command jq

if [[ -z "$BACKEND_ENV_B64" ]]; then
  fallback_explicit_url
  exit 0
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
backend_env="${tmp_dir}/backend.env"
decode_base64 "$BACKEND_ENV_B64" "$backend_env"

# backend env는 secret 본문이라 실행하지 않고 key lookup만 수행한다.
jdbc_url="$(env_value SPRING_DATASOURCE_URL "$backend_env")"
if [[ -z "$jdbc_url" ]]; then
  jdbc_url="$(env_value DB_URL "$backend_env")"
fi

if [[ "$jdbc_url" != jdbc:postgresql://* ]]; then
  fallback_explicit_url
  exit 0
fi

host_port="${jdbc_url#jdbc:postgresql://}"
host_port="${host_port%%/*}"
db_name="${jdbc_url#jdbc:postgresql://}"
db_name="${db_name#*/}"
db_name="${db_name%%\?*}"
db_host="${host_port%%:*}"
db_port="5432"
if [[ "$host_port" == *:* ]]; then
  db_port="${host_port##*:}"
fi

if ! postgres_host_requires_container "$db_host"; then
  fallback_explicit_url
  exit 0
fi

# backend는 Docker alias로 접근하지만 host psql은 published bind 주소로 접근한다.
db_user="$(env_value SPRING_DATASOURCE_USERNAME "$backend_env")"
if [[ -z "$db_user" ]]; then
  db_user="$(env_value DB_USERNAME "$backend_env")"
fi
db_password="$(env_value SPRING_DATASOURCE_PASSWORD "$backend_env")"
if [[ -z "$db_password" ]]; then
  db_password="$(env_value DB_PASSWORD "$backend_env")"
fi

[[ -n "$db_name" ]] || fail "Backend JDBC database name is required"
[[ -n "$db_user" ]] || fail "Backend database username is required"
[[ -n "$db_password" ]] || fail "Backend database password is required"

read -r host_db_host host_db_port < <(resolve_host_bind "$POSTGRES_HOST_BIND")
build_postgres_url "$host_db_host" "$host_db_port" "$db_name" "$db_user" "$db_password"
