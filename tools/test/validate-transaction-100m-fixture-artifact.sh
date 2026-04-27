#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/validate-transaction-100m-fixture-artifact.sh [--print-plan|--verify|--write-manifest]

Environment:
  FIXTURE_NAME                       default transaction-100m-fixture
  FIXTURE_PATH                       default build/fixtures/<FIXTURE_NAME>.dump
  FIXTURE_MANIFEST_PATH              default <FIXTURE_PATH>.manifest
  FIXTURE_CHECKSUM_PATH              default <FIXTURE_PATH>.sha256
  FIXTURE_ARTIFACT_MIN_ROWS          default 100000000
  FIXTURE_ARTIFACT_MIN_HOT_ROWS      default 1
  FIXTURE_ARTIFACT_MIN_ARCHIVE_ROWS  default 1
  FIXTURE_EXPECT_FLYWAY_VERSION      default latest local migration version

Examples:
  tools/test/validate-transaction-100m-fixture-artifact.sh --print-plan
  FIXTURE_ARTIFACT_MIN_ROWS=1000 tools/test/validate-transaction-100m-fixture-artifact.sh --verify
  tools/test/validate-transaction-100m-fixture-artifact.sh --write-manifest
USAGE
}

mode="verify"
if [[ "${1:-}" == "--print-plan" ]]; then
  mode="print-plan"
  shift
elif [[ "${1:-}" == "--verify" ]]; then
  mode="verify"
  shift
elif [[ "${1:-}" == "--write-manifest" ]]; then
  mode="write-manifest"
  shift
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
if [[ "$#" -ne 0 ]]; then
  usage
  exit 1
fi

fixture_name="${FIXTURE_NAME:-transaction-100m-fixture}"
fixture_dir="${FIXTURE_DIR:-build/fixtures}"
fixture_path="${FIXTURE_PATH:-${fixture_dir}/${fixture_name}.dump}"
manifest_path="${FIXTURE_MANIFEST_PATH:-${fixture_path}.manifest}"
checksum_path="${FIXTURE_CHECKSUM_PATH:-${fixture_path}.sha256}"
min_rows="${FIXTURE_ARTIFACT_MIN_ROWS:-100000000}"
min_hot_rows="${FIXTURE_ARTIFACT_MIN_HOT_ROWS:-1}"
min_archive_rows="${FIXTURE_ARTIFACT_MIN_ARCHIVE_ROWS:-1}"
compose_files=(-f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml)
psql_base=(docker compose "${compose_files[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-aquila_bank}")

require_non_negative_integer() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${key} must be zero or a positive integer: ${value}" >&2
    exit 1
  fi
}

latest_local_flyway_version() {
  local version
  version="$(
    find back/src/main/resources/db/migration -maxdepth 1 -type f -name 'V*__*.sql' \
      | sed -E 's#^.*/V([0-9]+)__.*$#\1#' \
      | sort -n \
      | tail -1
  )"
  if [[ -z "${version}" ]]; then
    echo "no local Flyway migrations found" >&2
    exit 1
  fi
  echo "${version}"
}

expected_flyway_version="${FIXTURE_EXPECT_FLYWAY_VERSION:-$(latest_local_flyway_version)}"

require_non_negative_integer "FIXTURE_ARTIFACT_MIN_ROWS" "${min_rows}"
require_non_negative_integer "FIXTURE_ARTIFACT_MIN_HOT_ROWS" "${min_hot_rows}"
require_non_negative_integer "FIXTURE_ARTIFACT_MIN_ARCHIVE_ROWS" "${min_archive_rows}"
require_non_negative_integer "FIXTURE_EXPECT_FLYWAY_VERSION" "${expected_flyway_version}"

print_plan() {
  echo "[transaction-100m-artifact] mode=${mode}"
  echo "[transaction-100m-artifact] fixture=${fixture_name}"
  echo "[transaction-100m-artifact] dump=${fixture_path}"
  echo "[transaction-100m-artifact] manifest=${manifest_path}"
  echo "[transaction-100m-artifact] checksum=${checksum_path}"
  echo "[transaction-100m-artifact] min_rows=${min_rows}"
  echo "[transaction-100m-artifact] min_hot_rows=${min_hot_rows}"
  echo "[transaction-100m-artifact] min_archive_rows=${min_archive_rows}"
  echo "[transaction-100m-artifact] expected_flyway_version=${expected_flyway_version}"
}

sha256_file() {
  local path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${path}" | awk '{print $1}'
  else
    shasum -a 256 "${path}" | awk '{print $1}'
  fi
}

manifest_value() {
  local key="$1"
  awk -F '=' -v key="${key}" '$1 == key {print $2}' "${manifest_path}" | tail -1
}

assert_file_present() {
  local path="$1"
  local label="$2"
  if [[ ! -s "${path}" ]]; then
    echo "${label} not found or empty: ${path}" >&2
    exit 1
  fi
}

assert_manifest_integer_at_least() {
  local key="$1"
  local min="$2"
  local value
  value="$(manifest_value "${key}")"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "manifest ${key} is missing or invalid: ${value}" >&2
    exit 1
  fi
  if ((value < min)); then
    echo "manifest ${key} below minimum: value=${value} min=${min}" >&2
    exit 1
  fi
}

applied_flyway_version() {
  "${psql_base[@]}" --no-align --tuples-only --command "
    SELECT COALESCE(MAX(version::integer), 0)
    FROM flyway_schema_history
    WHERE success
      AND version ~ '^[0-9]+$';
  " | tr -d '[:space:]'
}

table_count() {
  local table="$1"
  "${psql_base[@]}" --no-align --tuples-only --command "SELECT count(*) FROM public.${table};" \
    | tr -d '[:space:]'
}

write_manifest() {
  assert_file_present "${fixture_path}" "fixture dump"
  mkdir -p "$(dirname "${manifest_path}")" "$(dirname "${checksum_path}")"

  local checksum flyway_version hot_rows archive_rows total_rows
  checksum="$(sha256_file "${fixture_path}")"
  flyway_version="$(applied_flyway_version)"
  hot_rows="$(table_count transaction_read_model)"
  archive_rows="$(table_count transaction_read_model_archive)"
  total_rows=$((hot_rows + archive_rows))

  printf "%s  %s\n" "${checksum}" "$(basename "${fixture_path}")" >"${checksum_path}"
  {
    echo "fixture_name=${fixture_name}"
    echo "dump_path=${fixture_path}"
    echo "sha256=${checksum}"
    echo "flyway_version=${flyway_version}"
    echo "total_rows=${total_rows}"
    echo "hot_rows=${hot_rows}"
    echo "archive_rows=${archive_rows}"
    echo "generated_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } >"${manifest_path}"
  echo "[transaction-100m-artifact] manifest written=${manifest_path}"
  echo "[transaction-100m-artifact] checksum written=${checksum_path}"
}

verify_artifact() {
  assert_file_present "${fixture_path}" "fixture dump"
  assert_file_present "${checksum_path}" "fixture checksum"
  assert_file_present "${manifest_path}" "fixture manifest"

  local expected_checksum actual_checksum manifest_checksum manifest_flyway
  expected_checksum="$(awk '{print $1}' "${checksum_path}" | tail -1)"
  actual_checksum="$(sha256_file "${fixture_path}")"
  manifest_checksum="$(manifest_value sha256)"
  if [[ "${expected_checksum}" != "${actual_checksum}" ]]; then
    echo "fixture checksum mismatch: expected=${expected_checksum} actual=${actual_checksum}" >&2
    exit 1
  fi
  if [[ "${manifest_checksum}" != "${actual_checksum}" ]]; then
    echo "manifest checksum mismatch: manifest=${manifest_checksum} actual=${actual_checksum}" >&2
    exit 1
  fi

  manifest_flyway="$(manifest_value flyway_version)"
  if ! [[ "${manifest_flyway}" =~ ^[0-9]+$ ]]; then
    echo "manifest flyway_version is missing or invalid: ${manifest_flyway}" >&2
    exit 1
  fi
  if ((manifest_flyway < expected_flyway_version)); then
    echo "fixture Flyway version is stale: manifest=${manifest_flyway} expected=${expected_flyway_version}" >&2
    exit 1
  fi

  assert_manifest_integer_at_least total_rows "${min_rows}"
  assert_manifest_integer_at_least hot_rows "${min_hot_rows}"
  assert_manifest_integer_at_least archive_rows "${min_archive_rows}"
  echo "[transaction-100m-artifact] ready dump=${fixture_path}"
}

print_plan
case "${mode}" in
  print-plan)
    exit 0
    ;;
  write-manifest)
    write_manifest
    ;;
  verify)
    verify_artifact
    ;;
esac
