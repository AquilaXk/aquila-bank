#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-ec2-local-db-capacity-env-doctor.sh"
template="tools/test/ec2-local-db-capacity.env.example"

echo "[ec2-local-db-doctor] shell syntax"
bash -n "${script}"

echo "[ec2-local-db-doctor] template"
test -f "${template}"
grep -F "EC2_DIRECT_BACKEND_BASE_URL=http://127.0.0.1:18080" "${template}" >/dev/null
grep -F "EC2_NGINX_BASE_URL=http://127.0.0.1" "${template}" >/dev/null
grep -F "EC2_LOADTEST_AUTH_REQUIRED=true" "${template}" >/dev/null
grep -F "EC2_LOCAL_DB_TUNNEL_CHECK=true" "${template}" >/dev/null
grep -F "EC2_LOCAL_DB_HOST=host.docker.internal" "${template}" >/dev/null
grep -F "EC2_LOCAL_DB_PORT=25432" "${template}" >/dev/null

echo "[ec2-local-db-doctor] print template"
env_template="$(bash "${script}" --print-env-template)"
grep -F "EC2_LOADTEST_AUTH_TOKEN=<fixture-jwt-or-limited-loadtest-token>" <<<"${env_template}" >/dev/null
grep -F "EC2_LOCAL_DB_PROBE_IMAGE=postgres:18-alpine" <<<"${env_template}" >/dev/null

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
env_file="${temp_dir}/ec2-local-db-capacity.env"
cat >"${env_file}" <<'ENV'
EC2_CAPACITY_RUN_ID=ec2-local-db-check
EC2_DIRECT_BACKEND_BASE_URL=http://127.0.0.1:18080
EC2_NGINX_BASE_URL=http://127.0.0.1
EC2_LOADTEST_AUTH_REQUIRED=true
EC2_LOADTEST_AUTH_TOKEN=fixture.jwt.value
EC2_K6_HOT_ACCOUNT_ID=910000001
EC2_K6_HOT_FROM=2026-04-01T00:00:00Z
EC2_K6_HOT_TO=2026-04-30T00:00:00Z
EC2_K6_COLD_ACCOUNT_ID=910000002
EC2_K6_COLD_FROM=2026-01-01T00:00:00Z
EC2_K6_COLD_TO=2026-01-31T00:00:00Z
EC2_DOCKER_NETWORK=aquila-bank-prod
EC2_LOCAL_DB_TUNNEL_CHECK=true
EC2_LOCAL_DB_PROBE_IMAGE=postgres:18-alpine
EC2_LOCAL_DB_HOST=host.docker.internal
EC2_LOCAL_DB_PORT=25432
EC2_LOCAL_DB_NAME=aquila_bank
EC2_LOCAL_DB_USER=postgres
EC2_LOCAL_DB_PASSWORD=postgres
ENV

echo "[ec2-local-db-doctor] plan"
plan="$(
  EC2_LOCAL_DB_CAPACITY_ENV_FILE="${env_file}" \
    bash "${script}" --print-plan
)"
grep -F "run_id=ec2-local-db-check" <<<"${plan}" >/dev/null
grep -F "direct_base_url=http://127.0.0.1:18080" <<<"${plan}" >/dev/null
grep -F "nginx_base_url=http://127.0.0.1" <<<"${plan}" >/dev/null
grep -F "auth_required=true auth_token=set" <<<"${plan}" >/dev/null
grep -F "tunnel_check=true" <<<"${plan}" >/dev/null
grep -F "docker_network=aquila-bank-prod" <<<"${plan}" >/dev/null
grep -F "db=postgres@host.docker.internal:25432/aquila_bank password=set" <<<"${plan}" >/dev/null

echo "[ec2-local-db-doctor] dry-run"
EC2_LOCAL_DB_CAPACITY_ENV_FILE="${env_file}" \
  bash "${script}" --dry-run >/dev/null

echo "[ec2-local-db-doctor] invalid input fails"
if bash "${script}" --print-plan >/dev/null 2>&1; then
  echo "missing EC2 local DB env unexpectedly passed" >&2
  exit 1
fi
bad_env="${temp_dir}/bad.env"
cat >"${bad_env}" <<'ENV'
EC2_DIRECT_BACKEND_BASE_URL=not-a-url
EC2_NGINX_BASE_URL=http://127.0.0.1
EC2_LOADTEST_AUTH_REQUIRED=true
EC2_K6_HOT_ACCOUNT_ID=910000001
EC2_K6_HOT_FROM=2026-04-01T00:00:00Z
EC2_K6_HOT_TO=2026-04-30T00:00:00Z
EC2_K6_COLD_ACCOUNT_ID=910000002
EC2_K6_COLD_FROM=2026-01-01T00:00:00Z
EC2_K6_COLD_TO=2026-01-31T00:00:00Z
EC2_LOCAL_DB_TUNNEL_CHECK=false
ENV
if EC2_LOCAL_DB_CAPACITY_ENV_FILE="${bad_env}" bash "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad direct URL unexpectedly passed" >&2
  exit 1
fi
missing_auth="${temp_dir}/missing-auth.env"
cat >"${missing_auth}" <<'ENV'
EC2_DIRECT_BACKEND_BASE_URL=http://127.0.0.1:18080
EC2_NGINX_BASE_URL=http://127.0.0.1
EC2_LOADTEST_AUTH_REQUIRED=true
EC2_K6_HOT_ACCOUNT_ID=910000001
EC2_K6_HOT_FROM=2026-04-01T00:00:00Z
EC2_K6_HOT_TO=2026-04-30T00:00:00Z
EC2_K6_COLD_ACCOUNT_ID=910000002
EC2_K6_COLD_FROM=2026-01-01T00:00:00Z
EC2_K6_COLD_TO=2026-01-31T00:00:00Z
EC2_LOCAL_DB_TUNNEL_CHECK=false
ENV
if EC2_LOCAL_DB_CAPACITY_ENV_FILE="${missing_auth}" bash "${script}" --print-plan >/dev/null 2>&1; then
  echo "missing auth token unexpectedly passed" >&2
  exit 1
fi

echo "[ec2-local-db-doctor] runner contract"
grep -F "BEGIN READ ONLY; SELECT 1 AS ec2_local_db_tunnel_probe; ROLLBACK;" "${script}" >/dev/null
grep -F -- "--add-host host.docker.internal:host-gateway" "${script}" >/dev/null
grep -F "EC2_LOADTEST_AUTH_TOKEN" "${script}" >/dev/null
grep -F "docker network inspect" "${script}" >/dev/null
