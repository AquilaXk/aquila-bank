#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: ops/deploy/oci/check-self-hosted-runner.sh [--dry-run|--print-plan]

Environment:
  OCI_A1_RUNNER_CHECK_NETWORK default true
  OCI_A1_RUNNER_CHECK_GHCR default true
  OCI_A1_RUNNER_REQUIRED_COMMANDS default "base64 curl jq psql"
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --dry-run)
      mode="dry-run"
      ;;
    --print-plan)
      mode="print-plan"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

check_network="${OCI_A1_RUNNER_CHECK_NETWORK:-true}"
check_ghcr="${OCI_A1_RUNNER_CHECK_GHCR:-true}"
required_commands="${OCI_A1_RUNNER_REQUIRED_COMMANDS:-base64 curl jq psql}"

fail() {
  echo "[oci-runner-doctor] $1" >&2
  exit 1
}

require_bool() {
  local name="$1"
  local value="$2"
  case "${value}" in
    true|false) ;;
    *) fail "${name} must be true or false: ${value}" ;;
  esac
}

print_plan() {
  echo "[oci-runner-doctor] mode=${mode}"
  echo "[oci-runner-doctor] required_commands=${required_commands}"
  echo "[oci-runner-doctor] check_network=${check_network}"
  echo "[oci-runner-doctor] check_ghcr=${check_ghcr}"
}

require_bool "OCI_A1_RUNNER_CHECK_NETWORK" "${check_network}"
require_bool "OCI_A1_RUNNER_CHECK_GHCR" "${check_ghcr}"
print_plan

if [[ "${mode}" == "print-plan" || "${mode}" == "dry-run" ]]; then
  echo "[oci-runner-doctor] ${mode} passed"
  exit 0
fi

missing=()
for command_name in ${required_commands}; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    missing+=("${command_name}")
  fi
done
if (( ${#missing[@]} > 0 )); then
  printf '[oci-runner-doctor] missing commands: %s\n' "${missing[*]}" >&2
  exit 1
fi

if (( EUID != 0 )) && ! sudo -n true; then
  fail "runner user must have passwordless sudo for bluegreen-deploy.sh"
fi

if ! docker info >/dev/null 2>&1; then
  fail "docker info failed; check docker service and runner user docker group"
fi

if [[ "${check_network}" == "true" ]]; then
  curl -fsS --max-time 10 https://github.com >/dev/null ||
    fail "github.com outbound check failed"
fi

if [[ "${check_ghcr}" == "true" ]]; then
  status="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 https://ghcr.io/v2/ || echo 000)"
  case "${status}" in
    2*|3*|401)
      ;;
    *)
      fail "GHCR outbound check failed: status=${status}"
      ;;
  esac
fi

echo "[oci-runner-doctor] passed"
