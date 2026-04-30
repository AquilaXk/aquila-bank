#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-oci-a1-edge-backend-budget-matrix.sh"

echo "[oci-a1-budget-matrix] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

output_dir="${temp_dir}/output"

echo "[oci-a1-budget-matrix] print plan"
plan="$(
  OCI_A1_BUDGET_MATRIX_NAME=matrix-check \
  OCI_A1_BUDGET_MATRIX_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=matrix-check" <<<"${plan}" >/dev/null
grep -F "edge_transaction_hot_rate_rps=48" <<<"${plan}" >/dev/null
grep -F "edge_transaction_archive_rate_rps=48" <<<"${plan}" >/dev/null
grep -F "edge_transaction_read_burst=12" <<<"${plan}" >/dev/null
grep -F "edge_transaction_read_delay=4" <<<"${plan}" >/dev/null
grep -F "backend_admission_max=6" <<<"${plan}" >/dev/null
grep -F "backend_admission_adaptive_max=8" <<<"${plan}" >/dev/null
grep -F "hikari_max=6" <<<"${plan}" >/dev/null
grep -F "expected_429_source=edge-or-backend-admission" <<<"${plan}" >/dev/null

echo "[oci-a1-budget-matrix] report"
output="$(
  OCI_A1_BUDGET_MATRIX_NAME=matrix-check \
  OCI_A1_BUDGET_MATRIX_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
test "${report_md}" = "${output_dir}/matrix-check-budget-matrix.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "| edge transaction-hot rate | 48r/s |" "${report_md}" >/dev/null
grep -F "| edge transaction-archive rate | 48r/s |" "${report_md}" >/dev/null
grep -F "| edge transaction-read burst | 12 |" "${report_md}" >/dev/null
grep -F "| edge transaction-read delay | 4 |" "${report_md}" >/dev/null
grep -F "| backend admission max | 6 |" "${report_md}" >/dev/null
grep -F "| backend admission adaptive max | 8 |" "${report_md}" >/dev/null
grep -F "| Hikari max pool | 6 |" "${report_md}" >/dev/null
grep -F "| expected 429 source | edge-or-backend-admission |" "${report_md}" >/dev/null

echo "[oci-a1-budget-matrix] source contract"
grep -F 'limit_req_zone $binary_remote_addr zone=aquila_bank_transaction_hot_per_ip:10m rate=48r/s;' ops/nginx/nginx.conf >/dev/null
grep -F 'limit_req_zone $binary_remote_addr zone=aquila_bank_transaction_archive_per_ip:10m rate=48r/s;' ops/nginx/nginx.conf >/dev/null
grep -F 'limit_req zone=aquila_bank_transaction_hot_per_ip burst=12 delay=4;' ops/nginx/nginx.conf >/dev/null
grep -F 'limit_req zone=aquila_bank_transaction_archive_per_ip burst=12 delay=4;' ops/nginx/nginx.conf >/dev/null
grep -F 'maximum-pool-size: ${OCI_A1_DB_POOL_MAX_SIZE:6}' back/src/main/resources/application-oci-a1.yml >/dev/null
grep -F 'max: ${OCI_A1_TRANSACTION_READ_ADMISSION_MAX:6}' back/src/main/resources/application-oci-a1.yml >/dev/null
grep -F 'adaptive-max: ${OCI_A1_TRANSACTION_READ_ADMISSION_ADAPTIVE_MAX:8}' back/src/main/resources/application-oci-a1.yml >/dev/null
grep -F "OCI_PUBLIC_ARRIVAL_RATES" tools/test/run-oci-public-api-arrival-capacity-gate.sh >/dev/null
