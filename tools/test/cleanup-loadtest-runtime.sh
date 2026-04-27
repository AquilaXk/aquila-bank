#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/cleanup-loadtest-runtime.sh [--dry-run]

Environment:
  LOADTEST_POSTGRES_CONTAINER_NAME          default aquila-bank-postgres-loadtest
  LOADTEST_BACKEND_CONTAINER_NAME           default aquila-bank-backend-loadtest
  LOADTEST_PROMETHEUS_CONTAINER_NAME        default aquila-bank-prometheus-loadtest
  LOADTEST_GRAFANA_CONTAINER_NAME           default aquila-bank-grafana-loadtest
  LOADTEST_ALERTMANAGER_CONTAINER_NAME      default aquila-bank-alertmanager-loadtest
  LOADTEST_POSTGRES_EXPORTER_CONTAINER_NAME default aquila-bank-postgres-exporter-loadtest
  LOADTEST_K6_CONTAINER_NAME                default aquila-bank-k6-transaction-100m
  LOADTEST_CLEANUP_NETWORK_NAME             optional; must contain loadtest
  LOADTEST_CLEANUP_DELETE_VOLUME            true|false, default false
  LOADTEST_CLEANUP_VOLUME_NAME              required when deleting volume; must contain loadtest
  LOADTEST_CLEANUP_VOLUME_CONFIRM           must be delete-loadtest-volume when deleting volume
USAGE
}

dry_run=false
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --dry-run)
      dry_run=true
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

delete_volume="${LOADTEST_CLEANUP_DELETE_VOLUME:-false}"
volume_name="${LOADTEST_CLEANUP_VOLUME_NAME:-}"
volume_confirm="${LOADTEST_CLEANUP_VOLUME_CONFIRM:-}"
network_name="${LOADTEST_CLEANUP_NETWORK_NAME:-}"

case "${delete_volume}" in
  true|false) ;;
  *) echo "LOADTEST_CLEANUP_DELETE_VOLUME must be true or false" >&2; exit 1 ;;
esac

run_cmd() {
  if [[ "${dry_run}" == "true" ]]; then
    printf "%q " "$@"
    printf "\n"
    return 0
  fi
  "$@" >/dev/null 2>&1 || true
}

require_loadtest_name() {
  local label="$1"
  local value="$2"
  if [[ -z "${value}" || "${value}" != *loadtest* ]]; then
    echo "${label} must contain loadtest: ${value}" >&2
    exit 1
  fi
}

containers=(
  "${LOADTEST_POSTGRES_CONTAINER_NAME:-aquila-bank-postgres-loadtest}"
  "${LOADTEST_BACKEND_CONTAINER_NAME:-aquila-bank-backend-loadtest}"
  "${LOADTEST_PROMETHEUS_CONTAINER_NAME:-aquila-bank-prometheus-loadtest}"
  "${LOADTEST_GRAFANA_CONTAINER_NAME:-aquila-bank-grafana-loadtest}"
  "${LOADTEST_ALERTMANAGER_CONTAINER_NAME:-aquila-bank-alertmanager-loadtest}"
  "${LOADTEST_POSTGRES_EXPORTER_CONTAINER_NAME:-aquila-bank-postgres-exporter-loadtest}"
  "${LOADTEST_K6_CONTAINER_NAME:-aquila-bank-k6-transaction-100m-loadtest}"
)

for container in "${containers[@]}"; do
  require_loadtest_name "container name" "${container}"
  run_cmd docker rm -f "${container}"
done

if [[ -n "${network_name}" ]]; then
  require_loadtest_name "network name" "${network_name}"
  run_cmd docker network rm "${network_name}"
else
  echo "network cleanup skipped: set LOADTEST_CLEANUP_NETWORK_NAME"
fi

if [[ "${delete_volume}" == "true" ]]; then
  require_loadtest_name "volume name" "${volume_name}"
  if [[ "${volume_confirm}" != "delete-loadtest-volume" ]]; then
    echo "LOADTEST_CLEANUP_VOLUME_CONFIRM=delete-loadtest-volume is required" >&2
    exit 1
  fi
  run_cmd docker volume rm "${volume_name}"
else
  echo "volume cleanup skipped: set LOADTEST_CLEANUP_DELETE_VOLUME=true"
fi
