#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-100m-artifact-ready-k6.sh [--print-plan|--dry-run]

Required environment:
  K6_HOT_ACCOUNT_ID/K6_HOT_FROM/... or fixture manifest dataset metadata

Optional environment:
  FIXTURE_NAME              default transaction-100m-fixture
  FIXTURE_PATH              default build/fixtures/<FIXTURE_NAME>.dump
  FIXTURE_ARTIFACT_MIN_ROWS default 100000000
  K6_REPORT_NAME            default transaction-100m-artifact-ready-<timestamp>
  ARTIFACT_READY_K6_NO_DEPS default true

Examples:
  tools/test/run-transaction-100m-artifact-ready-k6.sh --print-plan
  K6_HOT_ACCOUNT_ID=910000001 K6_HOT_FROM=2026-04-01T00:00:00Z K6_HOT_TO=2026-04-30T00:00:00Z \
  K6_COLD_ACCOUNT_ID=910000002 K6_COLD_FROM=2026-01-01T00:00:00Z K6_COLD_TO=2026-01-31T00:00:00Z \
    tools/test/run-transaction-100m-artifact-ready-k6.sh
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
dataset_env_path="${FIXTURE_DATASET_ENV_PATH:-${fixture_path}.dataset.env}"
db_gate_artifact_path="${FIXTURE_DATASET_DB_REPORT_PATH:-${fixture_path}.db-gate.env}"
k6_no_deps="${ARTIFACT_READY_K6_NO_DEPS:-true}"
K6_REPORT_NAME="${K6_REPORT_NAME:-transaction-100m-artifact-ready-$(date +%Y-%m-%d-%H%M%S)}"
export K6_REPORT_NAME

require_bool_value "ARTIFACT_READY_K6_NO_DEPS" "${k6_no_deps}"

print_plan() {
  echo "[transaction-100m-artifact-ready-k6] mode=${mode}"
  echo "[transaction-100m-artifact-ready-k6] fixture=${fixture_name}"
  echo "[transaction-100m-artifact-ready-k6] dump=${fixture_path}"
  echo "[transaction-100m-artifact-ready-k6] artifact_gate=tools/test/validate-transaction-100m-fixture-artifact.sh --verify"
  echo "[transaction-100m-artifact-ready-k6] dataset_probe=tools/test/run-transaction-100m-fixture-dataset-probe.sh"
  echo "[transaction-100m-artifact-ready-k6] dataset_env=${dataset_env_path}"
  echo "[transaction-100m-artifact-ready-k6] db_gate_artifact=${db_gate_artifact_path}"
  echo "[transaction-100m-artifact-ready-k6] dataset_source=existing-artifact-or-probe"
  echo "[transaction-100m-artifact-ready-k6] k6_runner=tools/test/run-k6-transaction-100m-loadtest.sh --no-up"
  echo "[transaction-100m-artifact-ready-k6] k6_no_deps=${k6_no_deps}"
  echo "[transaction-100m-artifact-ready-k6] k6 report=${K6_REPORT_NAME}"
}

print_dry_run() {
  echo "FIXTURE_NAME=${fixture_name} FIXTURE_PATH=${fixture_path} tools/test/validate-transaction-100m-fixture-artifact.sh --verify"
  echo "db gate artifact: ${db_gate_artifact_path}"
  echo "source existing dataset env when db gate artifact passed"
  echo "FIXTURE_NAME=${fixture_name} FIXTURE_PATH=${fixture_path} FIXTURE_DATASET_ENV_PATH=${dataset_env_path} tools/test/run-transaction-100m-fixture-dataset-probe.sh"
  if [[ "${k6_no_deps}" == "true" ]]; then
    echo "K6_REPORT_NAME=${K6_REPORT_NAME} tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps"
  else
    echo "K6_REPORT_NAME=${K6_REPORT_NAME} tools/test/run-k6-transaction-100m-loadtest.sh --no-up"
  fi
}

dataset_artifacts_ready() {
  [[ -s "${dataset_env_path}" && -s "${db_gate_artifact_path}" ]] || return 1
  grep -F "FIXTURE_DATASET_DB_GATE_STATUS=passed" "${db_gate_artifact_path}" >/dev/null \
    || grep -F "FIXTURE_DATASET_DB_GATE=passed" "${db_gate_artifact_path}" >/dev/null
}

load_dataset_env_or_probe() {
  if dataset_artifacts_ready; then
    echo "[transaction-100m-artifact-ready-k6] using existing dataset env and db gate artifact"
  else
    FIXTURE_NAME="${fixture_name}" \
    FIXTURE_PATH="${fixture_path}" \
    FIXTURE_DATASET_ENV_PATH="${dataset_env_path}" \
    FIXTURE_DATASET_DB_REPORT_PATH="${db_gate_artifact_path}" \
      tools/test/run-transaction-100m-fixture-dataset-probe.sh
  fi
  set -a
  # manifest/db-gate artifact가 준비되어 있으면 runtime DB probe 없이 k6 입력만 노출합니다.
  source "${dataset_env_path}"
  set +a
}

run_k6() {
  local args=(--no-up)
  if [[ "${k6_no_deps}" == "true" ]]; then
    args+=(--no-deps)
  fi
  tools/test/run-k6-transaction-100m-loadtest.sh "${args[@]}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  print_dry_run
  exit 0
fi

FIXTURE_NAME="${fixture_name}" \
FIXTURE_PATH="${fixture_path}" \
  tools/test/validate-transaction-100m-fixture-artifact.sh --verify
load_dataset_env_or_probe
run_k6
