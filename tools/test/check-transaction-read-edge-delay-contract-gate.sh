#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-edge-delay-contract-gate.sh"

echo "[transaction-read-edge-delay-contract] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_dir="${temp_dir}/summaries"
output_dir="${temp_dir}/output"
nginx_status="${temp_dir}/nginx-status.tsv"
mkdir -p "${summary_dir}"

write_summary() {
  local rate="$1"
  local total_429="$2"
  local retry_p95="$3"
  local delayed_rate="$4"
  cat >"${summary_dir}/burst-${rate}-summary.json" <<JSON
{
  "metrics": {
    "aquila_transaction_429_rate": {"values": {"rate": ${total_429}}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": ${total_429}}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_edge_delayed_rate": {"values": {"rate": ${delayed_rate}}},
    "aquila_transaction_edge_delayed_count": {"values": {"count": 40}},
    "aquila_transaction_retry_after_sleep_ms": {"values": {"p(95)": ${retry_p95}}},
    "aquila_transaction_retry_after_count": {"values": {"count": 18}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": 300}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": 310}},
    "aquila_transaction_hot_deep_cursor_ms": {"values": {"p(95)": 320}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": 305}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": 315}},
    "aquila_transaction_cold_deep_cursor_ms": {"values": {"p(95)": 325}}
  }
}
JSON
}

write_summary 80 0.080 180 0.14
write_summary 96 0.090 190 0.16

cat >"${nginx_status}" <<'TSV'
run	status	limit_req_status	count
burst-80	200	PASSED	400
burst-80	200	DELAYED	40
burst-80	429	REJECTED	38
burst-96	200	PASSED	420
burst-96	200	DELAYED	45
burst-96	429	REJECTED	42
TSV

echo "[transaction-read-edge-delay-contract] print plan"
plan="$(
  EDGE_DELAY_CONTRACT_NAME=delay-contract-check \
  EDGE_DELAY_CONTRACT_SUMMARY_DIR="${summary_dir}" \
  EDGE_DELAY_CONTRACT_NGINX_STATUS_TSV="${nginx_status}" \
  EDGE_DELAY_CONTRACT_SOURCE_MODE=synthetic-source-key \
  EDGE_DELAY_CONTRACT_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=delay-contract-check" <<<"${plan}" >/dev/null
grep -F "rates=80,96" <<<"${plan}" >/dev/null
grep -F "retry_after_p95_ms=250" <<<"${plan}" >/dev/null
grep -F "source_mode=synthetic-source-key" <<<"${plan}" >/dev/null

echo "[transaction-read-edge-delay-contract] report"
output="$(
  EDGE_DELAY_CONTRACT_NAME=delay-contract-check \
  EDGE_DELAY_CONTRACT_SUMMARY_DIR="${summary_dir}" \
  EDGE_DELAY_CONTRACT_NGINX_STATUS_TSV="${nginx_status}" \
  EDGE_DELAY_CONTRACT_SOURCE_MODE=synthetic-source-key \
  EDGE_DELAY_CONTRACT_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/delay-contract-check-delay-contract.tsv"
test "${report_md}" = "${output_dir}/delay-contract-check-delay-contract.md"
grep -F $'burst_rate\tstatus\ttotal_429_rate\tretry_after_p95_ms\tedge_delayed_rate\tnginx_499_count\t5xx_count' "${summary_tsv}" >/dev/null
grep -F $'80\tpass\t0.080\t180\t0.14\t0\t0' "${summary_tsv}" >/dev/null
grep -F $'96\tpass\t0.090\t190\t0.16\t0\t0' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "hot/archive edge budget: split" "${report_md}" >/dev/null
grep -F "per-IP source mode: synthetic-source-key" "${report_md}" >/dev/null
grep -F "Retry-After contract: 150ms + jitter 100ms" "${report_md}" >/dev/null
grep -F "burst 80/96 499/5xx target: 0" "${report_md}" >/dev/null

echo "[transaction-read-edge-delay-contract] fail report"
write_summary 96 0.090 480 0.16
if EDGE_DELAY_CONTRACT_NAME=delay-contract-fail \
  EDGE_DELAY_CONTRACT_SUMMARY_DIR="${summary_dir}" \
  EDGE_DELAY_CONTRACT_NGINX_STATUS_TSV="${nginx_status}" \
  EDGE_DELAY_CONTRACT_SOURCE_MODE=synthetic-source-key \
  EDGE_DELAY_CONTRACT_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "delay contract unexpectedly passed retry p95 regression" >&2
  exit 1
fi

echo "[transaction-read-edge-delay-contract] runner contract"
grep -F "aquila_bank_transaction_hot_per_ip" ops/nginx/nginx.conf >/dev/null
grep -F "aquila_bank_transaction_archive_per_ip" ops/nginx/nginx.conf >/dev/null
grep -F 'rate=${NGINX_TRANSACTION_READ_HOT_RATE_RPS}r/s' ops/nginx/nginx.conf >/dev/null
grep -F 'rate=${NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS}r/s' ops/nginx/nginx.conf >/dev/null
grep -F 'limit_req zone=aquila_bank_transaction_hot_per_ip burst=${NGINX_TRANSACTION_READ_HOT_BURST} ${NGINX_TRANSACTION_READ_HOT_LIMIT_MODE};' ops/nginx/nginx.conf >/dev/null
grep -F 'limit_req zone=aquila_bank_transaction_archive_per_ip burst=${NGINX_TRANSACTION_READ_ARCHIVE_BURST} ${NGINX_TRANSACTION_READ_ARCHIVE_LIMIT_MODE};' ops/nginx/nginx.conf >/dev/null
grep -F 'NGINX_EDGE_RETRY_AFTER_MILLIS="${NGINX_EDGE_RETRY_AFTER_MILLIS:-150}"' tools/ops/render-nginx-runtime-config.sh >/dev/null
grep -F 'NGINX_EDGE_RETRY_JITTER_MILLIS="${NGINX_EDGE_RETRY_JITTER_MILLIS:-100}"' tools/ops/render-nginx-runtime-config.sh >/dev/null
grep -F 'NGINX_TRANSACTION_READ_BUDGET_PROFILE="${NGINX_TRANSACTION_READ_BUDGET_PROFILE:-${OCI_A1_TRANSACTION_READ_BUDGET_PROFILE:-burst64}}"' tools/ops/render-nginx-runtime-config.sh >/dev/null
grep -F 'NGINX_TRANSACTION_READ_HOT_RATE_RPS="${NGINX_TRANSACTION_READ_HOT_RATE_RPS:-${transaction_read_profile_hot_rate_rps}}"' tools/ops/render-nginx-runtime-config.sh >/dev/null
grep -F 'NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS="${NGINX_TRANSACTION_READ_ARCHIVE_RATE_RPS:-${transaction_read_profile_archive_rate_rps}}"' tools/ops/render-nginx-runtime-config.sh >/dev/null
grep -F 'NGINX_TRANSACTION_READ_HOT_BURST="${NGINX_TRANSACTION_READ_HOT_BURST:-${transaction_read_profile_hot_burst}}"' tools/ops/render-nginx-runtime-config.sh >/dev/null
grep -F 'NGINX_TRANSACTION_READ_ARCHIVE_BURST="${NGINX_TRANSACTION_READ_ARCHIVE_BURST:-${transaction_read_profile_archive_burst}}"' tools/ops/render-nginx-runtime-config.sh >/dev/null
grep -F 'NGINX_TRANSACTION_READ_HOT_DELAY="${NGINX_TRANSACTION_READ_HOT_DELAY:-${transaction_read_profile_hot_delay}}"' tools/ops/render-nginx-runtime-config.sh >/dev/null
grep -F 'NGINX_TRANSACTION_READ_ARCHIVE_DELAY="${NGINX_TRANSACTION_READ_ARCHIVE_DELAY:-${transaction_read_profile_archive_delay}}"' tools/ops/render-nginx-runtime-config.sh >/dev/null

echo "[transaction-read-edge-delay-contract] invalid input fails"
if EDGE_DELAY_CONTRACT_SOURCE_MODE=single-source "${runner}" --print-plan >/dev/null 2>&1; then
  echo "single-source source mode unexpectedly succeeded without explicit allowance" >&2
  exit 1
fi
