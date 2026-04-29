#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/resolve-oci-a1-staging-database-url.sh"

base64_one_line() {
  printf '%s' "$1" | base64 | tr -d '\n'
}

assert_container_alias_resolves_to_host_local_url() {
  local backend_env result expected
  backend_env=$'SPRING_DATASOURCE_URL=jdbc:postgresql://aquila-postgres:5432/aquila\nSPRING_DATASOURCE_USERNAME=aquila\nSPRING_DATASOURCE_PASSWORD=p@ss word/!?'
  expected="postgresql://aquila:p%40ss%20word%2F%21%3F@127.0.0.1:5432/aquila"

  result="$(
    OCI_A1_BACKEND_ENV_B64="$(base64_one_line "$backend_env")" \
    STAGING_OCI_A1_DATABASE_URL="postgresql://aquila:old@146.56.149.120:15432/aquila" \
    "$script"
  )"

  if [[ "$result" != "$expected" ]]; then
    echo "expected host-local URL, got: ${result}" >&2
    exit 1
  fi
}

assert_container_alias_uses_custom_host_bind_port() {
  local backend_env result expected
  backend_env=$'SPRING_DATASOURCE_URL=jdbc:postgresql://aquila-postgres:5432/aquila\nSPRING_DATASOURCE_USERNAME=aquila\nSPRING_DATASOURCE_PASSWORD=secret'
  expected="postgresql://aquila:secret@127.0.0.1:15432/aquila"

  result="$(
    OCI_A1_BACKEND_ENV_B64="$(base64_one_line "$backend_env")" \
    POSTGRES_HOST_BIND="127.0.0.1:15432" \
    STAGING_OCI_A1_DATABASE_URL="postgresql://aquila:old@146.56.149.120:15432/aquila" \
    "$script"
  )"

  if [[ "$result" != "$expected" ]]; then
    echo "expected custom host bind URL, got: ${result}" >&2
    exit 1
  fi
}

assert_external_backend_keeps_explicit_staging_url() {
  local backend_env result expected
  backend_env=$'SPRING_DATASOURCE_URL=jdbc:postgresql://db.example.internal:5432/aquila\nSPRING_DATASOURCE_USERNAME=aquila\nSPRING_DATASOURCE_PASSWORD=secret'
  expected="postgresql://aquila:secret@db.example.internal:5432/aquila"

  result="$(
    OCI_A1_BACKEND_ENV_B64="$(base64_one_line "$backend_env")" \
    STAGING_OCI_A1_DATABASE_URL="$expected" \
    "$script"
  )"

  if [[ "$result" != "$expected" ]]; then
    echo "expected explicit staging URL fallback, got: ${result}" >&2
    exit 1
  fi
}

assert_container_alias_resolves_to_host_local_url
assert_container_alias_uses_custom_host_bind_port
assert_external_backend_keeps_explicit_staging_url

echo "[oci-a1-staging-db-url-resolver] ok"
