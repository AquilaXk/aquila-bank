#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-oci-a1-edge-backend-budget-matrix.sh [--print-plan]

Environment:
  OCI_A1_BUDGET_MATRIX_NAME       default oci-a1-edge-backend-budget-matrix
  OCI_A1_BUDGET_MATRIX_OUTPUT_DIR default build/reports/oci-a1-budget
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
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

name="${OCI_A1_BUDGET_MATRIX_NAME:-oci-a1-edge-backend-budget-matrix}"
output_dir="${OCI_A1_BUDGET_MATRIX_OUTPUT_DIR:-build/reports/oci-a1-budget}"
report_md="${output_dir}/${name}-budget-matrix.md"
nginx_config="ops/nginx/nginx.conf"
deploy_script="ops/deploy/oci/bluegreen-deploy.sh"
oci_profile="back/src/main/resources/application-oci-a1.yml"
arrival_gate="tools/test/run-oci-public-api-arrival-capacity-gate.sh"

edge_transaction_hot_rate_rps=48
edge_transaction_archive_rate_rps=48
edge_transaction_read_burst=12
edge_transaction_read_delay=4
backend_admission_max=6
backend_admission_adaptive_max=8
hikari_max=6
hikari_max_lifetime_ms=900000
hikari_keepalive_time_ms=120000
expected_429_source="edge-or-backend-admission"

contains() {
  local pattern="$1"
  local file="$2"
  if command -v rg >/dev/null 2>&1; then
    rg -F --quiet -- "${pattern}" "${file}"
    return
  fi
  grep -Fq -- "${pattern}" "${file}"
}

require_pattern() {
  local pattern="$1"
  local file="$2"
  if ! contains "${pattern}" "${file}"; then
    echo "[oci-a1-budget-matrix] missing pattern in ${file}: ${pattern}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[oci-a1-budget-matrix] name=${name}"
  echo "[oci-a1-budget-matrix] output=${report_md}"
  echo "[oci-a1-budget-matrix] edge_transaction_hot_rate_rps=${edge_transaction_hot_rate_rps}"
  echo "[oci-a1-budget-matrix] edge_transaction_archive_rate_rps=${edge_transaction_archive_rate_rps}"
  echo "[oci-a1-budget-matrix] edge_transaction_read_burst=${edge_transaction_read_burst}"
  echo "[oci-a1-budget-matrix] edge_transaction_read_delay=${edge_transaction_read_delay}"
  echo "[oci-a1-budget-matrix] backend_admission_max=${backend_admission_max}"
  echo "[oci-a1-budget-matrix] backend_admission_adaptive_max=${backend_admission_adaptive_max}"
  echo "[oci-a1-budget-matrix] hikari_max=${hikari_max}"
  echo "[oci-a1-budget-matrix] hikari_max_lifetime_ms=${hikari_max_lifetime_ms}"
  echo "[oci-a1-budget-matrix] hikari_keepalive_time_ms=${hikari_keepalive_time_ms}"
  echo "[oci-a1-budget-matrix] expected_429_source=${expected_429_source}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

require_pattern 'limit_req_zone $binary_remote_addr zone=aquila_bank_transaction_hot_per_ip:10m rate=48r/s;' "${nginx_config}"
require_pattern 'limit_req_zone $binary_remote_addr zone=aquila_bank_transaction_archive_per_ip:10m rate=48r/s;' "${nginx_config}"
require_pattern 'limit_req zone=aquila_bank_transaction_hot_per_ip burst=12 delay=4;' "${nginx_config}"
require_pattern 'limit_req zone=aquila_bank_transaction_archive_per_ip burst=12 delay=4;' "${nginx_config}"
require_pattern 'add_header X-Aquila-Reject-Source nginx-edge always;' "${nginx_config}"
require_pattern 'add_header X-Aquila-Reject-Reason edge-rate-limit always;' "${nginx_config}"
require_pattern 'keepalive_requests 1000;' "${nginx_config}"
require_pattern 'keepalive_timeout 60s;' "${nginx_config}"
require_pattern 'limit_req_zone \$binary_remote_addr zone=aquila_bank_transaction_hot_per_ip:10m rate=48r/s;' "${deploy_script}"
require_pattern 'limit_req_zone \$binary_remote_addr zone=aquila_bank_transaction_archive_per_ip:10m rate=48r/s;' "${deploy_script}"
require_pattern 'limit_req zone=aquila_bank_transaction_hot_per_ip burst=12 delay=4;' "${deploy_script}"
require_pattern 'limit_req zone=aquila_bank_transaction_archive_per_ip burst=12 delay=4;' "${deploy_script}"
require_pattern 'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=${OCI_A1_TRANSACTION_READ_ADMISSION_MAX:-6}' "${deploy_script}"
require_pattern 'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_MAX=${OCI_A1_TRANSACTION_READ_ADMISSION_ADAPTIVE_MAX:-8}' "${deploy_script}"
require_pattern 'maximum-pool-size: ${OCI_A1_DB_POOL_MAX_SIZE:6}' "${oci_profile}"
require_pattern 'max-lifetime: ${OCI_A1_DB_MAX_LIFETIME_MS:900000}' "${oci_profile}"
require_pattern 'keepalive-time: ${OCI_A1_DB_KEEPALIVE_TIME_MS:120000}' "${oci_profile}"
require_pattern 'max: ${OCI_A1_TRANSACTION_READ_ADMISSION_MAX:6}' "${oci_profile}"
require_pattern 'adaptive-max: ${OCI_A1_TRANSACTION_READ_ADMISSION_ADAPTIVE_MAX:8}' "${oci_profile}"
require_pattern 'OCI_PUBLIC_ARRIVAL_RATES:-4,5,6,7,8,10' "${arrival_gate}"
require_pattern 'OCI_PUBLIC_ARRIVAL_FAIL_RATE:-0.10' "${arrival_gate}"
require_pattern 'OCI_PUBLIC_ARRIVAL_ACCEPTED_P95_MS:-350' "${arrival_gate}"

mkdir -p "${output_dir}"
cat >"${report_md}" <<REPORT
# OCI A1 Edge/Backend Budget Matrix

## Summary

- gate_status=pass
- runtime: OCI A1 Flex 4 OCPU / 24GB + data 200GB self-managed PostgreSQL 18
- expected 429 source: ${expected_429_source}
- live target: arrival-10rps 429 < 10%, delayed ratio < 25%, 502/503 = 0, accepted request p95 < 350ms

## Matrix

| Budget | Value |
| --- | --- |
| edge transaction-hot rate | ${edge_transaction_hot_rate_rps}r/s |
| edge transaction-archive rate | ${edge_transaction_archive_rate_rps}r/s |
| edge transaction-read burst | ${edge_transaction_read_burst} |
| edge transaction-read delay | ${edge_transaction_read_delay} |
| backend admission max | ${backend_admission_max} |
| backend admission adaptive max | ${backend_admission_adaptive_max} |
| Hikari max pool | ${hikari_max} |
| Hikari max lifetime ms | ${hikari_max_lifetime_ms} |
| Hikari keepalive time ms | ${hikari_keepalive_time_ms} |
| expected 429 source | ${expected_429_source} |

## Checked Files

- ${nginx_config}
- ${deploy_script}
- ${oci_profile}
- ${arrival_gate}
REPORT

echo "${report_md}"
