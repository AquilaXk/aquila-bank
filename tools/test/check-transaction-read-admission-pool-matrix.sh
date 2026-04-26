#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-read-admission-pool-matrix.sh"

echo "[transaction-read-matrix] shell syntax"
bash -n "${script}"

echo "[transaction-read-matrix] print plan"
base_plan="$(
  K6_REPORT_NAME=transaction-read-matrix-check \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan --no-deps
)"
grep -F "dependencies=no-deps" <<<"${base_plan}" >/dev/null

plan="$(
  MATRIX_NAME=transaction-read-matrix-check \
  MATRIX_ADMISSION_VALUES=3,4 \
  MATRIX_DB_POOL_VALUES=4,6 \
  MATRIX_VU_VALUES=3,8 \
  K6_DURATION=5s \
    "${script}" --print-plan
)"
grep -F "matrix=transaction-read-matrix-check" <<<"${plan}" >/dev/null
grep -F "admission_values=3,4" <<<"${plan}" >/dev/null
grep -F "db_pool_values=4,6" <<<"${plan}" >/dev/null
grep -F "vu_values=3,8" <<<"${plan}" >/dev/null
grep -F "combinations=8" <<<"${plan}" >/dev/null
grep -F "execution=serial" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/k6/transaction-read-matrix-check/matrix-summary.tsv" <<<"${plan}" >/dev/null

echo "[transaction-read-matrix] runner contract"
grep -F "OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX" "${script}" >/dev/null
grep -F "DB_POOL_MAX_SIZE" "${script}" >/dev/null
grep -F "K6_VUS" "${script}" >/dev/null
grep -F "docker stats --no-stream" "${script}" >/dev/null
grep -F "hikaricp_connections_active" "${script}" >/dev/null
grep -F "aquila_api_admission_requests_total" "${script}" >/dev/null
grep -F "run-k6-transaction-100m-loadtest.sh --no-up --no-deps" "${script}" >/dev/null
grep -F "wait_for_backend_readiness" "${script}" >/dev/null
grep -F "wait_for_prometheus_readiness" "${script}" >/dev/null
grep -F "/actuator/health" "${script}" >/dev/null
grep -F "MATRIX_METRIC_SCRAPE_WAIT_SECONDS" "${script}" >/dev/null
grep -F "returned HTTP 429" "${script}" >/dev/null
grep -F "matrix-summary.tsv" "${script}" >/dev/null
grep -F "continue_on_failure" "${script}" >/dev/null

echo "[transaction-read-matrix] invalid input fails"
if MATRIX_ADMISSION_VALUES=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "MATRIX_ADMISSION_VALUES=0 unexpectedly succeeded" >&2
  exit 1
fi
if MATRIX_DB_POOL_VALUES=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "MATRIX_DB_POOL_VALUES=bad unexpectedly succeeded" >&2
  exit 1
fi
if MATRIX_VU_VALUES= "${script}" --print-plan >/dev/null 2>&1; then
  echo "empty MATRIX_VU_VALUES unexpectedly succeeded" >&2
  exit 1
fi
