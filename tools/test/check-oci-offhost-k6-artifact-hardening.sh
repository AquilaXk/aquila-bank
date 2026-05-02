#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-k6-transaction-100m-loadtest.sh"
doctor="tools/test/run-offhost-capacity-env-doctor.sh"
template="tools/test/offhost-capacity.env.example"

echo "[oci-offhost-k6-artifact] shell syntax"
bash -n "${runner}"
bash -n "${doctor}"

echo "[oci-offhost-k6-artifact] docker-context plan"
plan="$(
  K6_REPORT_NAME=oci-offhost-artifact-check \
  K6_OBSERVABILITY_MODE=summary-only \
  K6_GENERATOR_MODE=docker-context \
  K6_DOCKER_CONTEXT=oci-k6-remote \
  K6_REMOTE_BASE_URL=https://api.example.test \
  K6_REMOTE_WORKDIR=/srv/aquila-bank \
    "${runner}" --print-plan
)"
grep -F "k6 report name: oci-offhost-artifact-check" <<<"${plan}" >/dev/null
grep -F "generator mode=docker-context" <<<"${plan}" >/dev/null
grep -F "backend readiness gate=remote-preflight" <<<"${plan}" >/dev/null
grep -F "remote artifact image=busybox:1.36" <<<"${plan}" >/dev/null
grep -F "remote artifact collect=true" <<<"${plan}" >/dev/null
grep -F "remote summary local=build/reports/k6/oci-offhost-artifact-check-summary.{md,json}" <<<"${plan}" >/dev/null

echo "[oci-offhost-k6-artifact] runner contract"
grep -F "K6_REMOTE_ARTIFACT_IMAGE" "${runner}" >/dev/null
grep -F "K6_REMOTE_COLLECT_ARTIFACTS" "${runner}" >/dev/null
grep -F "normalize_remote_report_permissions" "${runner}" >/dev/null
grep -F "collect_remote_k6_artifacts" "${runner}" >/dev/null
grep -F "chmod -R a+rX /reports" "${runner}" >/dev/null
grep -F "remote summary collected" "${runner}" >/dev/null
grep -F "backend readiness skipped for docker-context; remote preflight owns readiness" "${runner}" >/dev/null
grep -F "assert_remote_k6_preflight" "${runner}" >/dev/null
grep -F 'collect_remote_k6_artifacts "${status}"' "${runner}" >/dev/null

echo "[oci-offhost-k6-artifact] doctor template"
grep -F "export CAPACITY_K6_AUTH_TOKEN_REQUIRED=false" "${template}" >/dev/null
grep -F "export CAPACITY_K6_AUTH_TOKEN=<optional-bearer-token>" "${template}" >/dev/null

echo "[oci-offhost-k6-artifact] doctor plan masks token"
doctor_plan="$(
  OFFHOST_CAPACITY_CHECK_CONNECTIVITY=false \
  CAPACITY_K6_DOCKER_CONTEXT=oci-k6-remote \
  CAPACITY_K6_REMOTE_BASE_URL=https://api.example.test \
  CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=https://prom.example.test/api/v1/write \
  CAPACITY_K6_AUTH_TOKEN_REQUIRED=true \
  CAPACITY_K6_AUTH_TOKEN=secret-token-value \
    "${doctor}" --print-plan
)"
grep -F "auth_token_required=true" <<<"${doctor_plan}" >/dev/null
grep -F "auth_token=present" <<<"${doctor_plan}" >/dev/null
if grep -F "secret-token-value" <<<"${doctor_plan}" >/dev/null; then
  echo "doctor leaked auth token in plan output" >&2
  exit 1
fi

echo "[oci-offhost-k6-artifact] doctor required token fails"
if OFFHOST_CAPACITY_CHECK_CONNECTIVITY=false \
  CAPACITY_K6_DOCKER_CONTEXT=oci-k6-remote \
  CAPACITY_K6_REMOTE_BASE_URL=https://api.example.test \
  CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=https://prom.example.test/api/v1/write \
  CAPACITY_K6_AUTH_TOKEN_REQUIRED=true \
    "${doctor}" --dry-run >/dev/null 2>&1; then
  echo "doctor unexpectedly passed required missing auth token" >&2
  exit 1
fi
