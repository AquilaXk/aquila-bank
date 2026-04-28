#!/usr/bin/env bash
set -euo pipefail

direct_script="tools/test/run-ec2-direct-backend-100m-k6-smoke.sh"
nginx_script="tools/test/run-ec2-nginx-100m-k6-smoke.sh"
burst_script="tools/test/run-ec2-local-db-307-burst-gate.sh"
k6_wrapper="tools/test/run-k6-transaction-100m-loadtest.sh"

echo "[ec2-k6-runners] shell syntax"
bash -n "${direct_script}" "${nginx_script}" "${burst_script}" "${k6_wrapper}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
env_file="${temp_dir}/ec2-local-db-capacity.env"
cat >"${env_file}" <<'ENV'
EC2_CAPACITY_RUN_ID=ec2-runner-check
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
EC2_LOCAL_DB_TUNNEL_CHECK=false
ENV

echo "[ec2-k6-runners] direct plan"
direct_plan="$(
  EC2_LOCAL_DB_CAPACITY_ENV_FILE="${env_file}" \
    "${direct_script}" --print-plan
)"
grep -F "base_url=http://127.0.0.1:18080" <<<"${direct_plan}" >/dev/null
grep -F "report_name=ec2-runner-check-direct-backend" <<<"${direct_plan}" >/dev/null

echo "[ec2-k6-runners] nginx plan"
nginx_plan="$(
  EC2_LOCAL_DB_CAPACITY_ENV_FILE="${env_file}" \
    "${nginx_script}" --print-plan
)"
grep -F "base_url=http://127.0.0.1" <<<"${nginx_plan}" >/dev/null
grep -F "report_name=ec2-runner-check-nginx" <<<"${nginx_plan}" >/dev/null

echo "[ec2-k6-runners] burst plan"
burst_plan="$(
  EC2_LOCAL_DB_CAPACITY_ENV_FILE="${env_file}" \
    "${burst_script}" --print-plan
)"
grep -F "rate=307 duration=20s vus=307" <<<"${burst_plan}" >/dev/null
grep -F "report_name=ec2-runner-check-burst-307" <<<"${burst_plan}" >/dev/null

echo "[ec2-k6-runners] dry-run commands"
direct_dry="$(
  EC2_LOCAL_DB_CAPACITY_ENV_FILE="${env_file}" \
    "${direct_script}" --dry-run
)"
grep -F "K6_BASE_URL=http://127.0.0.1:18080" <<<"${direct_dry}" >/dev/null
grep -F "K6_RUN_PURPOSE=smoke" <<<"${direct_dry}" >/dev/null
burst_dry="$(
  EC2_LOCAL_DB_CAPACITY_ENV_FILE="${env_file}" \
    "${burst_script}" --dry-run
)"
grep -F "K6_SCENARIO_MODE=burst" <<<"${burst_dry}" >/dev/null
grep -F "K6_BURST_RATE=307" <<<"${burst_dry}" >/dev/null
grep -F "K6_MAX_VUS=307" <<<"${burst_dry}" >/dev/null

echo "[ec2-k6-runners] wrapper contract"
grep -F "K6_BASE_URL" "${k6_wrapper}" >/dev/null
grep -F "K6_BACKEND_READINESS_BASE_URL" "${k6_wrapper}" >/dev/null
grep -F 'BASE_URL="${K6_BASE_URL:-}"' "${k6_wrapper}" >/dev/null

echo "[ec2-k6-runners] contract check passed"
