#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/offhost-100m-capacity-prerequisite.yml"

echo "[offhost-capacity-prerequisite] workflow exists"
test -f "${workflow}"

echo "[offhost-capacity-prerequisite] workflow contract"
grep -F "name: off-host 100m capacity prerequisite" "${workflow}" >/dev/null
grep -F "workflow_dispatch:" "${workflow}" >/dev/null
grep -F "pull_request:" "${workflow}" >/dev/null
grep -F "off-host capacity prerequisite contract" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'pull_request'" "${workflow}" >/dev/null
grep -F "tools/test/check-offhost-capacity-prerequisite-required-gate.sh" "${workflow}" >/dev/null
grep -F "if: github.event_name == 'workflow_dispatch'" "${workflow}" >/dev/null
grep -F "environment:" "${workflow}" >/dev/null
grep -F "name: staging" "${workflow}" >/dev/null
grep -F 'OCI_A1_STAGING_ENV: ${{ secrets.OCI_A1_STAGING_ENV }}' "${workflow}" >/dev/null
grep -F "Load off-host capacity env" "${workflow}" >/dev/null
grep -F "CAPACITY_K6_DOCKER_CONTEXT_VAR" "${workflow}" >/dev/null
grep -F "host_metrics_tsv:" "${workflow}" >/dev/null
grep -F "vu16_summary_json:" "${workflow}" >/dev/null
grep -F "burst_matrix_tsv:" "${workflow}" >/dev/null
grep -F "source_evidence_tsv:" "${workflow}" >/dev/null
grep -F "host_metrics_run_id:" "${workflow}" >/dev/null
grep -F "generator_host_metrics_tsv:" "${workflow}" >/dev/null
grep -F "target_host_metrics_tsv:" "${workflow}" >/dev/null
grep -F "HOST_METRICS_TSV_INPUT" "${workflow}" >/dev/null
grep -F "VU16_SUMMARY_JSON_INPUT" "${workflow}" >/dev/null
grep -F "BURST_MATRIX_TSV_INPUT" "${workflow}" >/dev/null
grep -F "SOURCE_EVIDENCE_TSV_INPUT" "${workflow}" >/dev/null
grep -F "HOST_METRICS_RUN_ID_INPUT" "${workflow}" >/dev/null
grep -F "GENERATOR_HOST_METRICS_TSV_INPUT" "${workflow}" >/dev/null
grep -F "TARGET_HOST_METRICS_TSV_INPUT" "${workflow}" >/dev/null
grep -F 'DEFAULT_CAPACITY_K6_DOCKER_CONTEXT="default"' "${workflow}" >/dev/null
grep -F 'DEFAULT_CAPACITY_K6_REMOTE_BASE_URL="${STAGING_BASE_URL:-}"' "${workflow}" >/dev/null
grep -F 'DEFAULT_CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL="http://172.17.0.2:9090/api/v1/write"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_REMOTE_WORKDIR_ENV="${CAPACITY_K6_REMOTE_WORKDIR:-}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_DOCKER_CONTEXT="${DOCKER_CONTEXT_INPUT:-${CAPACITY_K6_DOCKER_CONTEXT_VAR:-${DEFAULT_CAPACITY_K6_DOCKER_CONTEXT}}}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_REMOTE_BASE_URL="${REMOTE_BASE_URL_INPUT:-${CAPACITY_K6_REMOTE_BASE_URL:-${CAPACITY_K6_REMOTE_BASE_URL_VAR:-${DEFAULT_CAPACITY_K6_REMOTE_BASE_URL}}}}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL="${REMOTE_PROMETHEUS_RW_URL_INPUT:-${CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL:-${CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL_VAR:-${DEFAULT_CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL}}}}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_REMOTE_WORKDIR="${REMOTE_WORKDIR_INPUT:-${CAPACITY_K6_REMOTE_WORKDIR_VAR:-}}"' "${workflow}" >/dev/null
grep -F 'if [[ -z "${CAPACITY_K6_REMOTE_WORKDIR}" ]]; then' "${workflow}" >/dev/null
grep -F 'if [[ "${CAPACITY_K6_DOCKER_CONTEXT}" == "default" ]]; then' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_REMOTE_WORKDIR="${GITHUB_WORKSPACE}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_REMOTE_WORKDIR="${CAPACITY_K6_REMOTE_WORKDIR_ENV:-${GITHUB_WORKSPACE}}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_HOST_METRICS_TSV="${HOST_METRICS_TSV_INPUT:-${CAPACITY_K6_HOST_METRICS_TSV:-${CAPACITY_K6_HOST_METRICS_TSV_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_VU16_SUMMARY_JSON="${VU16_SUMMARY_JSON_INPUT:-${CAPACITY_K6_VU16_SUMMARY_JSON:-${CAPACITY_K6_VU16_SUMMARY_JSON_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_BURST_MATRIX_TSV="${BURST_MATRIX_TSV_INPUT:-${CAPACITY_K6_BURST_MATRIX_TSV:-${CAPACITY_K6_BURST_MATRIX_TSV_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_SOURCE_EVIDENCE_TSV="${SOURCE_EVIDENCE_TSV_INPUT:-${CAPACITY_K6_SOURCE_EVIDENCE_TSV:-${CAPACITY_K6_SOURCE_EVIDENCE_TSV_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_HOST_METRICS_RUN_ID="${HOST_METRICS_RUN_ID_INPUT:-${CAPACITY_K6_HOST_METRICS_RUN_ID:-${CAPACITY_NAME}}}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_GENERATOR_HOST_METRICS_TSV="${GENERATOR_HOST_METRICS_TSV_INPUT:-${CAPACITY_K6_GENERATOR_HOST_METRICS_TSV:-${CAPACITY_K6_GENERATOR_HOST_METRICS_TSV_VAR:-${CAPACITY_K6_HOST_METRICS_TSV}}}}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_K6_TARGET_HOST_METRICS_TSV="${TARGET_HOST_METRICS_TSV_INPUT:-${CAPACITY_K6_TARGET_HOST_METRICS_TSV:-${CAPACITY_K6_TARGET_HOST_METRICS_TSV_VAR:-}}}"' "${workflow}" >/dev/null
grep -F 'CAPACITY_REMOTE_PREFLIGHT="true"' "${workflow}" >/dev/null
grep -F 'CAPACITY_REQUIRE_HOST_METRICS="true"' "${workflow}" >/dev/null
grep -F "Run off-host remote preflight" "${workflow}" >/dev/null
grep -F "tools/test/run-offhost-capacity-env-doctor.sh" "${workflow}" >/dev/null
grep -F "offhost-capacity-preflight.log" "${workflow}" >/dev/null
grep -F "Build off-host host metrics evidence artifact" "${workflow}" >/dev/null
grep -F 'OCI_OFFHOST_HOST_METRICS_NAME="${CAPACITY_NAME}"' "${workflow}" >/dev/null
grep -F 'OCI_OFFHOST_HOST_METRICS_RUN_ID="${CAPACITY_K6_HOST_METRICS_RUN_ID}"' "${workflow}" >/dev/null
grep -F 'OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV="${CAPACITY_K6_GENERATOR_HOST_METRICS_TSV}"' "${workflow}" >/dev/null
grep -F 'OCI_OFFHOST_TARGET_HOST_METRICS_TSV="${CAPACITY_K6_TARGET_HOST_METRICS_TSV}"' "${workflow}" >/dev/null
grep -F "tools/test/run-oci-offhost-host-metrics-evidence.sh" "${workflow}" >/dev/null
grep -F "tools/test/run-transaction-100m-capacity-gates.sh --print-plan" "${workflow}" >/dev/null
grep -F "capacity-prerequisite-plan.log" "${workflow}" >/dev/null
grep -F "actions/upload-artifact@" "${workflow}" >/dev/null
grep -F "offhost-capacity-prerequisite" "${workflow}" >/dev/null
grep -F "build/reports/k6/ci-offhost-100m-capacity-prerequisite/" "${workflow}" >/dev/null
if grep -F 'CAPACITY_K6_DOCKER_CONTEXT: ${{ inputs.docker_context || vars.CAPACITY_K6_DOCKER_CONTEXT }}' "${workflow}" >/dev/null; then
  echo "off-host workflow must not rely only on dispatch inputs and repository vars" >&2
  exit 1
fi
if grep -F 'default: "/srv/aquila-bank"' "${workflow}" >/dev/null; then
  echo "off-host workflow must not default remote workdir to a stale path" >&2
  exit 1
fi

echo "[offhost-capacity-prerequisite] local fail-fast contract"
temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
prereq="${temp_dir}/capacity-prerequisite-failure.env"
if CAPACITY_NAME=ci-offhost-prereq-check \
  CAPACITY_PREREQUISITE_FAILURE_REPORT_PATH="${prereq}" \
  CAPACITY_K6_GENERATOR_MODE=docker-context \
    tools/test/run-transaction-100m-capacity-gates.sh --print-plan >/dev/null 2>&1; then
  echo "missing off-host capacity env unexpectedly passed local prerequisite check" >&2
  exit 1
fi
test -f "${prereq}"
grep -F "CAPACITY_PREREQUISITE_STATUS=failed" "${prereq}" >/dev/null
grep -F "CAPACITY_PREREQUISITE_FAILURE_REASON=missing-required-env" "${prereq}" >/dev/null
