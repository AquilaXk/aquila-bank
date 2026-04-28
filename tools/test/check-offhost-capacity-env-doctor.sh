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

echo "[offhost-capacity-env-doctor] print template"
env_template="$("${script}" --print-env-template)"
grep -F "CAPACITY_K6_DOCKER_CONTEXT=<remote-docker-context>" <<<"${env_template}" >/dev/null
grep -F "CAPACITY_REMOTE_READINESS_PATH=/actuator/health/readiness" <<<"${env_template}" >/dev/null

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
env_file="${temp_dir}/offhost-capacity.env"
cat >"${env_file}" <<'ENV'
CAPACITY_K6_DOCKER_CONTEXT=capacity-k6-remote
CAPACITY_K6_REMOTE_BASE_URL=http://192.0.2.20:18080
CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.20:9090/api/v1/write
CAPACITY_K6_REMOTE_WORKDIR=/srv/aquila-bank
CAPACITY_REMOTE_PREFLIGHT_TIMEOUT_SECONDS=15
CAPACITY_REMOTE_PREFLIGHT_IMAGE=curlimages/curl:8.11.1
CAPACITY_REMOTE_READINESS_PATH=/actuator/health/readiness
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
grep -F "remote_workdir=/srv/aquila-bank" <<<"${plan}" >/dev/null
grep -F "readiness_url=http://192.0.2.20:18080/actuator/health/readiness" <<<"${plan}" >/dev/null
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

echo "[offhost-capacity-env-doctor] runner contract"
grep -F "OFFHOST_CAPACITY_ENV_FILE" "${script}" >/dev/null
grep -F "OFFHOST_CAPACITY_CHECK_CONNECTIVITY" "${script}" >/dev/null
grep -F "docker context inspect" "${script}" >/dev/null
grep -F "docker --context \"\${docker_context}\" info" "${script}" >/dev/null
grep -F "docker --context \"\${docker_context}\" run --rm \"\${preflight_image}\"" "${script}" >/dev/null
grep -F "remote prometheus remote-write preflight failed" "${script}" >/dev/null
