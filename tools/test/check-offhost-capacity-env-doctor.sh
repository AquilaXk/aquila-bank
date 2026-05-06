#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-offhost-capacity-env-doctor.sh"
template="tools/test/offhost-capacity.env.example"

echo "[offhost-capacity-env-doctor] shell syntax"
bash -n "${script}"

echo "[offhost-capacity-env-doctor] template"
test -f "${template}"
grep -F "export CAPACITY_K6_GENERATOR_MODE=docker-context" "${template}" >/dev/null
grep -F "export CAPACITY_K6_DOCKER_CONTEXT=<remote-docker-context>" "${template}" >/dev/null
grep -F "export CAPACITY_K6_REMOTE_BASE_URL=http://<backend-host>:18080" "${template}" >/dev/null
grep -F "export CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://<prometheus-host>:9090/api/v1/write" "${template}" >/dev/null
grep -F "export CAPACITY_K6_HOST_METRICS_TSV=build/reports/k6/<run-id>/host-metrics.tsv" "${template}" >/dev/null
grep -F "export CAPACITY_K6_HOST_METRICS_TIMELINE_TSV=build/reports/k6/<run-id>/host-metrics-timeline.tsv" "${template}" >/dev/null
grep -F "export CAPACITY_K6_VU16_SUMMARY_JSON=build/reports/k6/<run-id>/vu16-summary.json" "${template}" >/dev/null
grep -F "export CAPACITY_K6_BURST_MATRIX_TSV=build/reports/k6/<run-id>/burst-reject-curve.tsv" "${template}" >/dev/null
grep -F "export CAPACITY_K6_SOURCE_EVIDENCE_TSV=build/reports/k6/<run-id>/source-evidence.tsv" "${template}" >/dev/null
grep -F "export CAPACITY_K6_GENERATOR_HOST_METRICS_TSV=build/reports/k6/<run-id>/generator-host-metrics.tsv" "${template}" >/dev/null
grep -F "export CAPACITY_K6_TARGET_HOST_METRICS_TSV=build/reports/k6/<run-id>/target-host-metrics.tsv" "${template}" >/dev/null

echo "[offhost-capacity-env-doctor] print template"
env_template="$("${script}" --print-env-template)"
grep -F "CAPACITY_K6_DOCKER_CONTEXT=<remote-docker-context>" <<<"${env_template}" >/dev/null
grep -F "CAPACITY_REMOTE_READINESS_PATH=/actuator/health/readiness" <<<"${env_template}" >/dev/null
grep -F "CAPACITY_K6_HOST_METRICS_TSV=build/reports/k6/<run-id>/host-metrics.tsv" <<<"${env_template}" >/dev/null
grep -F "CAPACITY_K6_HOST_METRICS_TIMELINE_TSV=build/reports/k6/<run-id>/host-metrics-timeline.tsv" <<<"${env_template}" >/dev/null
grep -F "CAPACITY_K6_BURST_MATRIX_TSV=build/reports/k6/<run-id>/burst-reject-curve.tsv" <<<"${env_template}" >/dev/null

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
env_file="${temp_dir}/offhost-capacity.env"
cat >"${env_file}" <<'ENV'
CAPACITY_K6_DOCKER_CONTEXT=capacity-k6-remote
CAPACITY_K6_REMOTE_BASE_URL=http://192.0.2.20:18080
CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.20:9090/api/v1/write
CAPACITY_REMOTE_PROMETHEUS_RW_REQUIRED=false
CAPACITY_K6_REMOTE_WORKDIR=/srv/aquila-bank
CAPACITY_REMOTE_PREFLIGHT_TIMEOUT_SECONDS=15
CAPACITY_REMOTE_PREFLIGHT_IMAGE=curlimages/curl:8.11.1
CAPACITY_REMOTE_READINESS_PATH=/actuator/health/readiness
CAPACITY_K6_HOST_METRICS_TSV=build/reports/k6/offhost-check/host-metrics.tsv
CAPACITY_K6_HOST_METRICS_TIMELINE_TSV=build/reports/k6/offhost-check/host-metrics-timeline.tsv
CAPACITY_K6_VU16_SUMMARY_JSON=build/reports/k6/offhost-check/vu16-summary.json
CAPACITY_K6_BURST_MATRIX_TSV=build/reports/k6/offhost-check/burst-reject-curve.tsv
CAPACITY_K6_SOURCE_EVIDENCE_TSV=build/reports/k6/offhost-check/source-evidence.tsv
CAPACITY_K6_GENERATOR_HOST_METRICS_TSV=build/reports/k6/offhost-check/generator-host-metrics.tsv
CAPACITY_K6_TARGET_HOST_METRICS_TSV=build/reports/k6/offhost-check/target-host-metrics.tsv
CAPACITY_K6_HOST_METRICS_RUN_ID=offhost-check
ENV

echo "[offhost-capacity-env-doctor] plan"
plan="$(
  OFFHOST_CAPACITY_ENV_FILE="${env_file}" \
  OFFHOST_CAPACITY_CHECK_CONNECTIVITY=false \
    "${script}" --print-plan
)"
grep -F "env_file=${env_file}" <<<"${plan}" >/dev/null
grep -F "docker_context=capacity-k6-remote" <<<"${plan}" >/dev/null
grep -F "remote_base_url=http://192.0.2.20:18080" <<<"${plan}" >/dev/null
grep -F "remote_prometheus_rw_url=http://192.0.2.20:9090/api/v1/write" <<<"${plan}" >/dev/null
grep -F "remote_prometheus_rw_required=false" <<<"${plan}" >/dev/null
grep -F "remote_workdir=/srv/aquila-bank" <<<"${plan}" >/dev/null
grep -F "readiness_url=http://192.0.2.20:18080/actuator/health/readiness" <<<"${plan}" >/dev/null
grep -F "host_metrics_tsv=build/reports/k6/offhost-check/host-metrics.tsv" <<<"${plan}" >/dev/null
grep -F "host_metrics_timeline_tsv=build/reports/k6/offhost-check/host-metrics-timeline.tsv" <<<"${plan}" >/dev/null
grep -F "vu16_summary_json=build/reports/k6/offhost-check/vu16-summary.json" <<<"${plan}" >/dev/null
grep -F "burst_matrix_tsv=build/reports/k6/offhost-check/burst-reject-curve.tsv" <<<"${plan}" >/dev/null
grep -F "source_evidence_tsv=build/reports/k6/offhost-check/source-evidence.tsv" <<<"${plan}" >/dev/null
grep -F "generator_host_metrics_tsv=build/reports/k6/offhost-check/generator-host-metrics.tsv" <<<"${plan}" >/dev/null
grep -F "target_host_metrics_tsv=build/reports/k6/offhost-check/target-host-metrics.tsv" <<<"${plan}" >/dev/null
grep -F "host_metrics_run_id=offhost-check" <<<"${plan}" >/dev/null
grep -F "require_host_metrics=true" <<<"${plan}" >/dev/null
grep -F "timeout_seconds=15" <<<"${plan}" >/dev/null
grep -F "check_connectivity=false" <<<"${plan}" >/dev/null

echo "[offhost-capacity-env-doctor] dry run"
OFFHOST_CAPACITY_ENV_FILE="${env_file}" \
OFFHOST_CAPACITY_CHECK_CONNECTIVITY=false \
  "${script}" --dry-run >/dev/null

echo "[offhost-capacity-env-doctor] invalid input fails"
if "${script}" --print-plan >/dev/null 2>&1; then
  echo "missing off-host capacity env unexpectedly passed" >&2
  exit 1
fi
if OFFHOST_CAPACITY_ENV_FILE="${temp_dir}/missing.env" "${script}" --print-plan >/dev/null 2>&1; then
  echo "missing env file unexpectedly passed" >&2
  exit 1
fi
bad_env_file="${temp_dir}/bad.env"
cat >"${bad_env_file}" <<'ENV'
CAPACITY_K6_DOCKER_CONTEXT=capacity-k6-remote
CAPACITY_K6_REMOTE_BASE_URL=not-a-url
CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.20:9090/api/v1/write
ENV
if OFFHOST_CAPACITY_ENV_FILE="${bad_env_file}" "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad remote base url unexpectedly passed" >&2
  exit 1
fi

bad_required_env_file="${temp_dir}/bad-required.env"
cat >"${bad_required_env_file}" <<'ENV'
CAPACITY_K6_DOCKER_CONTEXT=capacity-k6-remote
CAPACITY_K6_REMOTE_BASE_URL=http://192.0.2.20:18080
CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.20:9090/api/v1/write
CAPACITY_REMOTE_PROMETHEUS_RW_REQUIRED=maybe
CAPACITY_K6_HOST_METRICS_TIMELINE_TSV=build/reports/k6/offhost-check/host-metrics-timeline.tsv
CAPACITY_K6_GENERATOR_HOST_METRICS_TSV=build/reports/k6/offhost-check/generator-host-metrics.tsv
CAPACITY_K6_TARGET_HOST_METRICS_TSV=build/reports/k6/offhost-check/target-host-metrics.tsv
ENV
if OFFHOST_CAPACITY_ENV_FILE="${bad_required_env_file}" OFFHOST_CAPACITY_CHECK_CONNECTIVITY=false "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad remote prometheus required flag unexpectedly passed" >&2
  exit 1
fi

missing_metrics_env_file="${temp_dir}/missing-metrics.env"
cat >"${missing_metrics_env_file}" <<'ENV'
CAPACITY_K6_DOCKER_CONTEXT=capacity-k6-remote
CAPACITY_K6_REMOTE_BASE_URL=http://192.0.2.20:18080
CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.20:9090/api/v1/write
ENV
if OFFHOST_CAPACITY_ENV_FILE="${missing_metrics_env_file}" OFFHOST_CAPACITY_CHECK_CONNECTIVITY=false "${script}" --print-plan >"${temp_dir}/missing-metrics.log" 2>&1; then
  echo "missing off-host host metrics unexpectedly passed" >&2
  exit 1
fi
grep -F "CAPACITY_K6_GENERATOR_HOST_METRICS_TSV is required when CAPACITY_REQUIRE_HOST_METRICS=true" "${temp_dir}/missing-metrics.log" >/dev/null

echo "[offhost-capacity-env-doctor] runner contract"
grep -F "OFFHOST_CAPACITY_ENV_FILE" "${script}" >/dev/null
grep -F "OFFHOST_CAPACITY_CHECK_CONNECTIVITY" "${script}" >/dev/null
grep -F "CAPACITY_REQUIRE_HOST_METRICS" "${script}" >/dev/null
grep -F "CAPACITY_K6_HOST_METRICS_RUN_ID" "${script}" >/dev/null
grep -F "CAPACITY_K6_HOST_METRICS_TSV" "${script}" >/dev/null
grep -F "CAPACITY_K6_HOST_METRICS_TIMELINE_TSV" "${script}" >/dev/null
grep -F "CAPACITY_K6_VU16_SUMMARY_JSON" "${script}" >/dev/null
grep -F "CAPACITY_K6_BURST_MATRIX_TSV" "${script}" >/dev/null
grep -F "CAPACITY_K6_SOURCE_EVIDENCE_TSV" "${script}" >/dev/null
grep -F "CAPACITY_K6_GENERATOR_HOST_METRICS_TSV" "${script}" >/dev/null
grep -F "CAPACITY_K6_TARGET_HOST_METRICS_TSV" "${script}" >/dev/null
grep -F "CAPACITY_REMOTE_PROMETHEUS_RW_REQUIRED" "${script}" >/dev/null
grep -F "docker context inspect" "${script}" >/dev/null
grep -F "docker --context \"\${docker_context}\" info" "${script}" >/dev/null
grep -F "docker --context \"\${docker_context}\" run --rm \"\${preflight_image}\"" "${script}" >/dev/null
grep -F "remote prometheus remote-write preflight failed" "${script}" >/dev/null
grep -F "remote prometheus remote-write optional; continuing" "${script}" >/dev/null
