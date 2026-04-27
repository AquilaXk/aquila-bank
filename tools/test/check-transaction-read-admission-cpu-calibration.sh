#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-read-admission-cpu-calibration.sh"

echo "[transaction-read-admission-cpu-calibration] shell syntax"
bash -n "${script}"

echo "[transaction-read-admission-cpu-calibration] print plan"
plan="$(
  CALIBRATION_NAME=transaction-read-admission-cpu-check \
  CALIBRATION_K6_DOCKER_CONTEXT=calibration-k6-remote \
  CALIBRATION_K6_REMOTE_BASE_URL=http://192.0.2.30:8080 \
  CALIBRATION_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.30:9090/api/v1/write \
  CALIBRATION_K6_REMOTE_WORKDIR=/srv/aquila-bank \
    "${script}" --print-plan
)"
grep -F "calibration=transaction-read-admission-cpu-check" <<<"${plan}" >/dev/null
grep -F "profiles=admission3-vu8,admission6-vu16,admission8-vu16" <<<"${plan}" >/dev/null
grep -F "backend_cpu_threshold_percent=120" <<<"${plan}" >/dev/null
grep -F "k6_generator_mode=docker-context" <<<"${plan}" >/dev/null
grep -F "k6_docker_context=calibration-k6-remote" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/k6/transaction-read-admission-cpu-check/capacity-summary.tsv" <<<"${plan}" >/dev/null

echo "[transaction-read-admission-cpu-calibration] runner contract"
grep -F "CAPACITY_SINGLE_HOST_PROFILES" "${script}" >/dev/null
grep -F "CAPACITY_RUN_CPU_SPLIT=false" "${script}" >/dev/null
grep -F "CAPACITY_RUN_LONG_SOAK=false" "${script}" >/dev/null
grep -F "CAPACITY_BACKEND_CPU_THRESHOLD_PERCENT" "${script}" >/dev/null
grep -F "CAPACITY_K6_GENERATOR_MODE" "${script}" >/dev/null
grep -F "run-transaction-100m-capacity-gates.sh" "${script}" >/dev/null

echo "[transaction-read-admission-cpu-calibration] invalid input fails"
if CALIBRATION_PROFILES=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad CALIBRATION_PROFILES unexpectedly succeeded" >&2
  exit 1
fi
if CALIBRATION_BACKEND_CPU_THRESHOLD_PERCENT=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "CALIBRATION_BACKEND_CPU_THRESHOLD_PERCENT=0 unexpectedly succeeded" >&2
  exit 1
fi
if CALIBRATION_K6_GENERATOR_MODE=local "${script}" --print-plan >/dev/null 2>&1; then
  echo "local k6 generator for calibration unexpectedly succeeded" >&2
  exit 1
fi
if CALIBRATION_K6_GENERATOR_MODE=docker-context "${script}" --print-plan >/dev/null 2>&1; then
  echo "docker-context calibration without remote runtime unexpectedly succeeded" >&2
  exit 1
fi
