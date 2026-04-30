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
grep -F "edge_transaction_hot_rate_rps=64" <<<"${plan}" >/dev/null
grep -F "edge_transaction_archive_rate_rps=64" <<<"${plan}" >/dev/null
grep -F "edge_transaction_hot_burst=8" <<<"${plan}" >/dev/null
grep -F "edge_transaction_archive_burst=8" <<<"${plan}" >/dev/null
grep -F "edge_transaction_read_policy=fail-fast-nodelay" <<<"${plan}" >/dev/null
grep -F "backend_admission_max=6" <<<"${plan}" >/dev/null
grep -F "backend_admission_adaptive_max=8" <<<"${plan}" >/dev/null
grep -F "hikari_max=6" <<<"${plan}" >/dev/null
grep -F "hikari_max_lifetime_ms=900000" <<<"${plan}" >/dev/null
grep -F "hikari_keepalive_time_ms=120000" <<<"${plan}" >/dev/null
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
grep -F "| edge transaction-hot rate | 64r/s |" "${report_md}" >/dev/null
grep -F "| edge transaction-archive rate | 64r/s |" "${report_md}" >/dev/null
grep -F "| edge transaction-hot burst | 8 |" "${report_md}" >/dev/null
grep -F "| edge transaction-archive burst | 8 |" "${report_md}" >/dev/null
grep -F "| edge transaction-read policy | fail-fast-nodelay |" "${report_md}" >/dev/null
grep -F "| backend admission max | 6 |" "${report_md}" >/dev/null
grep -F "| backend admission adaptive max | 8 |" "${report_md}" >/dev/null
grep -F "| Hikari max pool | 6 |" "${report_md}" >/dev/null
grep -F "| Hikari max lifetime ms | 900000 |" "${report_md}" >/dev/null
grep -F "| Hikari keepalive time ms | 120000 |" "${report_md}" >/dev/null
grep -F "| expected 429 source | edge-or-backend-admission |" "${report_md}" >/dev/null

echo "[oci-a1-budget-matrix] source contract"
grep -F 'limit_req_zone $binary_remote_addr zone=aquila_bank_transaction_hot_per_ip:10m rate=${NGINX_TRANSACTION_READ_HOT_RATE_RPS}r/s;' ops/nginx/nginx.conf >/dev/null
grep -F 'limit_req_zone $binary_remote_addr zone=aquila_bank_transaction_archive_per_ip:10m rate=${NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS}r/s;' ops/nginx/nginx.conf >/dev/null
grep -F 'limit_req zone=aquila_bank_transaction_hot_per_ip burst=${NGINX_TRANSACTION_READ_HOT_BURST} nodelay;' ops/nginx/nginx.conf >/dev/null
grep -F 'limit_req zone=aquila_bank_transaction_archive_per_ip burst=${NGINX_TRANSACTION_READ_ARCHIVE_BURST} nodelay;' ops/nginx/nginx.conf >/dev/null
grep -F 'maximum-pool-size: ${OCI_A1_DB_POOL_MAX_SIZE:6}' back/src/main/resources/application-oci-a1.yml >/dev/null
grep -F 'max-lifetime: ${OCI_A1_DB_MAX_LIFETIME_MS:900000}' back/src/main/resources/application-oci-a1.yml >/dev/null
grep -F 'keepalive-time: ${OCI_A1_DB_KEEPALIVE_TIME_MS:120000}' back/src/main/resources/application-oci-a1.yml >/dev/null
grep -F 'max: ${OCI_A1_TRANSACTION_READ_ADMISSION_MAX:6}' back/src/main/resources/application-oci-a1.yml >/dev/null
grep -F 'adaptive-max: ${OCI_A1_TRANSACTION_READ_ADMISSION_ADAPTIVE_MAX:8}' back/src/main/resources/application-oci-a1.yml >/dev/null
grep -F 'OCI_PUBLIC_ARRIVAL_RATES:-4,5,6,7,8,10,16' tools/test/run-oci-public-api-arrival-capacity-gate.sh >/dev/null
