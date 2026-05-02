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
weighted_gate="tools/test/run-transaction-read-weighted-10m-soak-gate.sh"
smoothing_gate="tools/test/run-transaction-read-short-burst-smoothing-matrix.sh"

edge_transaction_hot_rate_rps=96
edge_transaction_archive_rate_rps=96
edge_transaction_hot_burst=12
edge_transaction_archive_burst=12
edge_transaction_read_policy="small-delay-queue"
backend_admission_max=8
backend_admission_adaptive_max=12
backend_hot_admission_max=8
backend_hot_admission_adaptive_max=12
backend_archive_admission_max=6
backend_archive_admission_adaptive_max=10
weighted_vu16_max_429_rate=0.05
short_burst48_max_429_rate=0.10
hikari_max=8
hikari_max_lifetime_ms=600000
hikari_keepalive_time_ms=60000
backend_api_keepalive_timeout_seconds=2
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
  echo "[oci-a1-budget-matrix] edge_transaction_hot_burst=${edge_transaction_hot_burst}"
  echo "[oci-a1-budget-matrix] edge_transaction_archive_burst=${edge_transaction_archive_burst}"
  echo "[oci-a1-budget-matrix] edge_transaction_read_policy=${edge_transaction_read_policy}"
  echo "[oci-a1-budget-matrix] backend_admission_max=${backend_admission_max}"
  echo "[oci-a1-budget-matrix] backend_admission_adaptive_max=${backend_admission_adaptive_max}"
  echo "[oci-a1-budget-matrix] backend_hot_admission_max=${backend_hot_admission_max}"
  echo "[oci-a1-budget-matrix] backend_hot_admission_adaptive_max=${backend_hot_admission_adaptive_max}"
  echo "[oci-a1-budget-matrix] backend_archive_admission_max=${backend_archive_admission_max}"
  echo "[oci-a1-budget-matrix] backend_archive_admission_adaptive_max=${backend_archive_admission_adaptive_max}"
  echo "[oci-a1-budget-matrix] weighted_vu16_max_429_rate=${weighted_vu16_max_429_rate}"
  echo "[oci-a1-budget-matrix] short_burst48_max_429_rate=${short_burst48_max_429_rate}"
  echo "[oci-a1-budget-matrix] hikari_max=${hikari_max}"
  echo "[oci-a1-budget-matrix] hikari_max_lifetime_ms=${hikari_max_lifetime_ms}"
  echo "[oci-a1-budget-matrix] hikari_keepalive_time_ms=${hikari_keepalive_time_ms}"
  echo "[oci-a1-budget-matrix] backend_api_keepalive_timeout_seconds=${backend_api_keepalive_timeout_seconds}"
  echo "[oci-a1-budget-matrix] expected_429_source=${expected_429_source}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

require_pattern 'limit_req_zone $binary_remote_addr zone=aquila_bank_transaction_hot_per_ip:10m rate=${NGINX_TRANSACTION_READ_HOT_RATE_RPS}r/s;' "${nginx_config}"
require_pattern 'limit_req_zone $binary_remote_addr zone=aquila_bank_transaction_archive_per_ip:10m rate=${NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS}r/s;' "${nginx_config}"
require_pattern 'limit_req zone=aquila_bank_transaction_hot_per_ip burst=${NGINX_TRANSACTION_READ_HOT_BURST} ${NGINX_TRANSACTION_READ_HOT_LIMIT_MODE};' "${nginx_config}"
require_pattern 'limit_req zone=aquila_bank_transaction_archive_per_ip burst=${NGINX_TRANSACTION_READ_ARCHIVE_BURST} ${NGINX_TRANSACTION_READ_ARCHIVE_LIMIT_MODE};' "${nginx_config}"
require_pattern 'add_header X-Aquila-Reject-Source nginx-edge always;' "${nginx_config}"
require_pattern 'add_header X-Aquila-Reject-Reason edge-rate-limit always;' "${nginx_config}"
require_pattern 'keepalive_requests 1000;' "${nginx_config}"
require_pattern 'keepalive_timeout ${NGINX_BACKEND_API_KEEPALIVE_TIMEOUT_SECONDS}s;' "${nginx_config}"
require_pattern 'proxy_next_upstream error timeout http_502;' "${nginx_config}"
require_pattern 'proxy_next_upstream_tries 2;' "${nginx_config}"
require_pattern 'proxy_next_upstream_timeout 2s;' "${nginx_config}"
require_pattern 'backend_api_keepalive_timeout_seconds="${NGINX_BACKEND_API_KEEPALIVE_TIMEOUT_SECONDS:-2}"' "${deploy_script}"
require_pattern 'transaction_read_hot_rate_rps="${OCI_A1_TRANSACTION_READ_HOT_RATE_RPS:-96}"' "${deploy_script}"
require_pattern 'transaction_read_archive_rate_rps="${OCI_A1_TRANSACTION_READ_ARCHIVE_RATE_RPS:-96}"' "${deploy_script}"
require_pattern 'transaction_read_hot_burst="${OCI_A1_TRANSACTION_READ_HOT_BURST:-12}"' "${deploy_script}"
require_pattern 'transaction_read_archive_burst="${OCI_A1_TRANSACTION_READ_ARCHIVE_BURST:-12}"' "${deploy_script}"
require_pattern 'transaction_read_hot_delay="${OCI_A1_TRANSACTION_READ_HOT_DELAY:-1}"' "${deploy_script}"
require_pattern 'transaction_read_archive_delay="${OCI_A1_TRANSACTION_READ_ARCHIVE_DELAY:-1}"' "${deploy_script}"
require_pattern 'limit_req_zone \$binary_remote_addr zone=aquila_bank_transaction_hot_per_ip:10m rate=${transaction_read_hot_rate_rps}r/s;' "${deploy_script}"
require_pattern 'limit_req_zone \$binary_remote_addr zone=aquila_bank_transaction_archive_per_ip:10m rate=${transaction_read_archive_rate_rps}r/s;' "${deploy_script}"
require_pattern 'limit_req zone=aquila_bank_transaction_hot_per_ip burst=${transaction_read_hot_burst} ${transaction_read_hot_limit_mode};' "${deploy_script}"
require_pattern 'limit_req zone=aquila_bank_transaction_archive_per_ip burst=${transaction_read_archive_burst} ${transaction_read_archive_limit_mode};' "${deploy_script}"
require_pattern 'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=${OCI_A1_TRANSACTION_READ_ADMISSION_MAX:-8}' "${deploy_script}"
require_pattern 'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ADAPTIVE_MAX=${OCI_A1_TRANSACTION_READ_ADMISSION_ADAPTIVE_MAX:-12}' "${deploy_script}"
require_pattern 'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_HOT_MAX=${OCI_A1_TRANSACTION_READ_HOT_ADMISSION_MAX:-${OCI_A1_TRANSACTION_READ_ADMISSION_MAX:-8}}' "${deploy_script}"
require_pattern 'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_HOT_ADAPTIVE_MAX=${OCI_A1_TRANSACTION_READ_HOT_ADMISSION_ADAPTIVE_MAX:-${OCI_A1_TRANSACTION_READ_ADMISSION_ADAPTIVE_MAX:-12}}' "${deploy_script}"
require_pattern 'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ARCHIVE_MAX=${OCI_A1_TRANSACTION_READ_ARCHIVE_ADMISSION_MAX:-6}' "${deploy_script}"
require_pattern 'OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_ARCHIVE_ADAPTIVE_MAX=${OCI_A1_TRANSACTION_READ_ARCHIVE_ADMISSION_ADAPTIVE_MAX:-10}' "${deploy_script}"
require_pattern 'maximum-pool-size: ${OCI_A1_DB_POOL_MAX_SIZE:8}' "${oci_profile}"
require_pattern 'max-lifetime: ${OCI_A1_DB_MAX_LIFETIME_MS:600000}' "${oci_profile}"
require_pattern 'keepalive-time: ${OCI_A1_DB_KEEPALIVE_TIME_MS:60000}' "${oci_profile}"
require_pattern 'max: ${OCI_A1_TRANSACTION_READ_ADMISSION_MAX:8}' "${oci_profile}"
require_pattern 'adaptive-max: ${OCI_A1_TRANSACTION_READ_ADMISSION_ADAPTIVE_MAX:12}' "${oci_profile}"
require_pattern 'low-saturation-increase-every-successes: ${OCI_A1_TRANSACTION_READ_ADMISSION_LOW_SATURATION_INCREASE_EVERY_SUCCESSES:16}' "${oci_profile}"
require_pattern 'group: transaction-read-hot' "back/src/main/resources/application.yml"
require_pattern 'group: transaction-read-archive' "back/src/main/resources/application.yml"
require_pattern 'OCI_PUBLIC_ARRIVAL_RATES:-4,5,6,7,8,10,16' "${arrival_gate}"
require_pattern 'OCI_PUBLIC_ARRIVAL_FAIL_RATE:-0.10' "${arrival_gate}"
require_pattern 'OCI_PUBLIC_ARRIVAL_ACCEPTED_P95_MS:-350' "${arrival_gate}"
require_pattern 'WEIGHTED_SOAK_10M_MAX_TOTAL_429_RATE:-0.05' "${weighted_gate}"
require_pattern 'SHORT_BURST_SMOOTHING_MAX_BURST48_429_RATE:-0.10' "${smoothing_gate}"

mkdir -p "${output_dir}"
cat >"${report_md}" <<REPORT
# OCI A1 Edge/Backend Budget Matrix

## Summary

- gate_status=pass
- runtime: OCI A1 Flex 4 OCPU / 24GB + data 200GB self-managed PostgreSQL 18
- expected 429 source: ${expected_429_source}
- live target: arrival-16rps 429 = 0, paced-weighted-vu16 429 <= ${weighted_vu16_max_429_rate}, short-burst-48 429 <= ${short_burst48_max_429_rate}, delayed ratio < 25%, 502/503 = 0, accepted request p95 <= 200ms, p99 <= 300ms

## Matrix

| Budget | Value |
| --- | --- |
| edge transaction-hot rate | ${edge_transaction_hot_rate_rps}r/s |
| edge transaction-archive rate | ${edge_transaction_archive_rate_rps}r/s |
| edge transaction-hot burst | ${edge_transaction_hot_burst} |
| edge transaction-archive burst | ${edge_transaction_archive_burst} |
| edge transaction-read policy | ${edge_transaction_read_policy} |
| backend admission max | ${backend_admission_max} |
| backend admission adaptive max | ${backend_admission_adaptive_max} |
| backend hot admission max | ${backend_hot_admission_max} |
| backend hot admission adaptive max | ${backend_hot_admission_adaptive_max} |
| backend archive admission max | ${backend_archive_admission_max} |
| backend archive admission adaptive max | ${backend_archive_admission_adaptive_max} |
| paced-weighted-vu16 max 429 rate | ${weighted_vu16_max_429_rate} |
| short-burst-48 max 429 rate | ${short_burst48_max_429_rate} |
| Hikari max pool | ${hikari_max} |
| Hikari max lifetime ms | ${hikari_max_lifetime_ms} |
| Hikari keepalive time ms | ${hikari_keepalive_time_ms} |
| Backend API keepalive timeout seconds | ${backend_api_keepalive_timeout_seconds} |
| expected 429 source | ${expected_429_source} |

## Checked Files

- ${nginx_config}
- ${deploy_script}
- ${oci_profile}
- ${arrival_gate}
- ${weighted_gate}
- ${smoothing_gate}
REPORT

echo "${report_md}"
