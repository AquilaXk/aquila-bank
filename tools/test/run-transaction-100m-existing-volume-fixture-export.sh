#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-existing-volume-fixture-export.sh [--print-plan|--dry-run]

Environment:
  FIXTURE_NAME                 default transaction-100m-fixture
  FIXTURE_DIR                  default build/fixtures
  FIXTURE_PATH                 default <FIXTURE_DIR>/<FIXTURE_NAME>.dump
  FIXTURE_EXPORT_VERIFY        true|false, default true
  FIXTURE_RECOVERY_PREFLIGHT   true|false, default true
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --dry-run)
      mode="dry-run"
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

require_bool_value() {
  local key="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${key} must be true or false: ${value}" >&2
    exit 1
  fi
}

fixture_name="${FIXTURE_NAME:-transaction-100m-fixture}"
fixture_dir="${FIXTURE_DIR:-build/fixtures}"
fixture_path="${FIXTURE_PATH:-${fixture_dir}/${fixture_name}.dump}"
manifest_path="${FIXTURE_MANIFEST_PATH:-${fixture_path}.manifest}"
checksum_path="${FIXTURE_CHECKSUM_PATH:-${fixture_path}.sha256}"
verify_after_dump="${FIXTURE_EXPORT_VERIFY:-true}"
recovery_preflight="${FIXTURE_RECOVERY_PREFLIGHT:-true}"

require_bool_value "FIXTURE_EXPORT_VERIFY" "${verify_after_dump}"
require_bool_value "FIXTURE_RECOVERY_PREFLIGHT" "${recovery_preflight}"

print_plan() {
  echo "[transaction-100m-existing-export] mode=${mode}"
  echo "[transaction-100m-existing-export] fixture=${fixture_name}"
  echo "[transaction-100m-existing-export] dump=${fixture_path}"
  echo "[transaction-100m-existing-export] manifest=${manifest_path}"
  echo "[transaction-100m-existing-export] checksum=${checksum_path}"
  echo "[transaction-100m-existing-export] verify_after_dump=${verify_after_dump}"
  echo "[transaction-100m-existing-export] recovery_preflight=${recovery_preflight}"
}

print_dry_run() {
  echo "FIXTURE_MODE=dump FIXTURE_NAME=${fixture_name} FIXTURE_PATH=${fixture_path} FIXTURE_WRITE_MANIFEST=true FIXTURE_RECOVERY_PREFLIGHT=${recovery_preflight} tools/test/run-transaction-100m-fixture-restore.sh"
  if [[ "${verify_after_dump}" == "true" ]]; then
    echo "FIXTURE_NAME=${fixture_name} FIXTURE_PATH=${fixture_path} tools/test/validate-transaction-100m-fixture-artifact.sh --verify"
  fi
  echo "manifest=${manifest_path}"
  echo "checksum=${checksum_path}"
}

run_export() {
  mkdir -p "${fixture_dir}"
  FIXTURE_MODE=dump \
  FIXTURE_NAME="${fixture_name}" \
  FIXTURE_PATH="${fixture_path}" \
  FIXTURE_WRITE_MANIFEST=true \
  FIXTURE_RECOVERY_PREFLIGHT="${recovery_preflight}" \
    tools/test/run-transaction-100m-fixture-restore.sh

  if [[ "${verify_after_dump}" == "true" ]]; then
    FIXTURE_NAME="${fixture_name}" \
    FIXTURE_PATH="${fixture_path}" \
      tools/test/validate-transaction-100m-fixture-artifact.sh --verify
  fi
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_dry_run
  exit 0
fi

run_export
