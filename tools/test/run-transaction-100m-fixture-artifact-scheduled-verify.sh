#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-fixture-artifact-scheduled-verify.sh [--print-plan|--dry-run]

Environment:
  SCHEDULED_FIXTURE_NAME                  default transaction-100m-fixture
  SCHEDULED_FIXTURE_DIR                   default build/fixtures
  SCHEDULED_FIXTURE_PATH                  default <dir>/<name>.dump
  SCHEDULED_FIXTURE_EXPORT_ENABLED        default true
  SCHEDULED_FIXTURE_VERIFY_ENABLED        default true
  SCHEDULED_FIXTURE_RESTORE_SMOKE_ENABLED default true
  SCHEDULED_FIXTURE_K6_ENABLED            default false
  SCHEDULED_FIXTURE_RESTORE_CONFIRM       passed to FRESH_VOLUME_CONFIRM for restore smoke
  SCHEDULED_FIXTURE_RESTORE_MIN_ROWS      default 1000

Examples:
  tools/test/run-transaction-100m-fixture-artifact-scheduled-verify.sh --print-plan
  SCHEDULED_FIXTURE_RESTORE_CONFIRM=erase-postgres-volume \
    tools/test/run-transaction-100m-fixture-artifact-scheduled-verify.sh
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

require_bool() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_non_negative_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${name} must be zero or a positive integer: ${value}" >&2
    exit 1
  fi
}

fixture_name="${SCHEDULED_FIXTURE_NAME:-transaction-100m-fixture}"
fixture_dir="${SCHEDULED_FIXTURE_DIR:-build/fixtures}"
fixture_path="${SCHEDULED_FIXTURE_PATH:-${fixture_dir}/${fixture_name}.dump}"
export_enabled="${SCHEDULED_FIXTURE_EXPORT_ENABLED:-true}"
verify_enabled="${SCHEDULED_FIXTURE_VERIFY_ENABLED:-true}"
restore_smoke_enabled="${SCHEDULED_FIXTURE_RESTORE_SMOKE_ENABLED:-true}"
k6_enabled="${SCHEDULED_FIXTURE_K6_ENABLED:-false}"
restore_confirm="${SCHEDULED_FIXTURE_RESTORE_CONFIRM:-}"
restore_min_rows="${SCHEDULED_FIXTURE_RESTORE_MIN_ROWS:-1000}"

require_bool "SCHEDULED_FIXTURE_EXPORT_ENABLED" "${export_enabled}"
require_bool "SCHEDULED_FIXTURE_VERIFY_ENABLED" "${verify_enabled}"
require_bool "SCHEDULED_FIXTURE_RESTORE_SMOKE_ENABLED" "${restore_smoke_enabled}"
require_bool "SCHEDULED_FIXTURE_K6_ENABLED" "${k6_enabled}"
require_non_negative_integer "SCHEDULED_FIXTURE_RESTORE_MIN_ROWS" "${restore_min_rows}"

steps=()
[[ "${export_enabled}" == "true" ]] && steps+=("export")
[[ "${verify_enabled}" == "true" ]] && steps+=("verify")
[[ "${restore_smoke_enabled}" == "true" ]] && steps+=("fresh-volume-restore-smoke")
steps_csv="$(IFS=','; echo "${steps[*]}")"

print_plan() {
  echo "[transaction-100m-fixture-scheduled] mode=${mode}"
  echo "[transaction-100m-fixture-scheduled] fixture=${fixture_name}"
  echo "[transaction-100m-fixture-scheduled] dump=${fixture_path}"
  echo "[transaction-100m-fixture-scheduled] export_enabled=${export_enabled}"
  echo "[transaction-100m-fixture-scheduled] verify_enabled=${verify_enabled}"
  echo "[transaction-100m-fixture-scheduled] fresh_volume_restore_smoke=${restore_smoke_enabled}"
  echo "[transaction-100m-fixture-scheduled] k6_enabled=${k6_enabled}"
  echo "[transaction-100m-fixture-scheduled] restore_min_rows=${restore_min_rows}"
  echo "[transaction-100m-fixture-scheduled] restore_confirm=${restore_confirm:-missing}"
  echo "[transaction-100m-fixture-scheduled] steps=${steps_csv:-none}"
}

print_dry_run() {
  if [[ "${export_enabled}" == "true" ]]; then
    echo "FIXTURE_NAME=${fixture_name} FIXTURE_DIR=${fixture_dir} FIXTURE_PATH=${fixture_path} tools/test/run-transaction-100m-existing-volume-fixture-export.sh"
  fi
  if [[ "${verify_enabled}" == "true" ]]; then
    echo "FIXTURE_NAME=${fixture_name} FIXTURE_PATH=${fixture_path} tools/test/validate-transaction-100m-fixture-artifact.sh --verify"
  fi
  if [[ "${restore_smoke_enabled}" == "true" ]]; then
    echo "FRESH_VOLUME_CONFIRM=${restore_confirm:-<required>} FRESH_VOLUME_K6_ENABLED=${k6_enabled} FRESH_VOLUME_RESTORE_VERIFY_MIN_ROWS=${restore_min_rows} FIXTURE_NAME=${fixture_name} FIXTURE_PATH=${fixture_path} tools/test/run-transaction-100m-fresh-volume-restore-k6.sh"
  fi
}

run_export() {
  FIXTURE_NAME="${fixture_name}" \
  FIXTURE_DIR="${fixture_dir}" \
  FIXTURE_PATH="${fixture_path}" \
    tools/test/run-transaction-100m-existing-volume-fixture-export.sh
}

run_verify() {
  FIXTURE_NAME="${fixture_name}" \
  FIXTURE_PATH="${fixture_path}" \
    tools/test/validate-transaction-100m-fixture-artifact.sh --verify
}

run_restore_smoke() {
  FRESH_VOLUME_CONFIRM="${restore_confirm}" \
  FRESH_VOLUME_K6_ENABLED="${k6_enabled}" \
  FRESH_VOLUME_RESTORE_VERIFY_MIN_ROWS="${restore_min_rows}" \
  FIXTURE_NAME="${fixture_name}" \
  FIXTURE_PATH="${fixture_path}" \
    tools/test/run-transaction-100m-fresh-volume-restore-k6.sh
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_dry_run
  exit 0
fi

if [[ "${restore_smoke_enabled}" == "true" && "${restore_confirm}" != "erase-postgres-volume" ]]; then
  echo "SCHEDULED_FIXTURE_RESTORE_CONFIRM=erase-postgres-volume is required for fresh volume restore smoke" >&2
  exit 1
fi

[[ "${export_enabled}" != "true" ]] || run_export
[[ "${verify_enabled}" != "true" ]] || run_verify
[[ "${restore_smoke_enabled}" != "true" ]] || run_restore_smoke
