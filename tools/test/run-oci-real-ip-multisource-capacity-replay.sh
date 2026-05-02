#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-oci-real-ip-multisource-capacity-replay.sh [--print-plan]

Environment:
  OCI_REAL_IP_REPLAY_NAME                       default oci-real-ip-multisource-replay-<timestamp>
  OCI_REAL_IP_REPLAY_SINGLE_SOURCE_SUMMARY_JSON required direct/single-source k6 summary JSON
  OCI_REAL_IP_REPLAY_MULTI_SOURCE_SUMMARY_JSON  required trusted real-IP multi-source k6 summary JSON
  OCI_REAL_IP_REPLAY_NGINX_STATUS_TSV           required TSV: run,realip_remote_addr,limit_req_status,count
  OCI_REAL_IP_REPLAY_OUTPUT_DIR                 default build/reports/k6/<name>
  OCI_REAL_IP_REPLAY_MAX_MULTI_EDGE_429_RATE    default 0.10
  OCI_REAL_IP_REPLAY_MAX_ACCEPTED_P95_MS        default 200
  OCI_REAL_IP_REPLAY_MIN_BUCKET_COUNT           default 2
  OCI_REAL_IP_REPLAY_REAL_IP_HEADER             default X-Forwarded-For
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

name="${OCI_REAL_IP_REPLAY_NAME:-oci-real-ip-multisource-replay-$(date +%Y-%m-%d-%H%M%S)}"
single_summary="${OCI_REAL_IP_REPLAY_SINGLE_SOURCE_SUMMARY_JSON:-}"
multi_summary="${OCI_REAL_IP_REPLAY_MULTI_SOURCE_SUMMARY_JSON:-}"
nginx_status_tsv="${OCI_REAL_IP_REPLAY_NGINX_STATUS_TSV:-}"
output_dir="${OCI_REAL_IP_REPLAY_OUTPUT_DIR:-build/reports/k6/${name}}"
max_multi_edge_429_rate="${OCI_REAL_IP_REPLAY_MAX_MULTI_EDGE_429_RATE:-0.10}"
max_accepted_p95_ms="${OCI_REAL_IP_REPLAY_MAX_ACCEPTED_P95_MS:-200}"
min_bucket_count="${OCI_REAL_IP_REPLAY_MIN_BUCKET_COUNT:-2}"
real_ip_header="${OCI_REAL_IP_REPLAY_REAL_IP_HEADER:-X-Forwarded-For}"
summary_tsv="${output_dir}/${name}-real-ip-replay.tsv"
report_md="${output_dir}/${name}-real-ip-replay.md"

require_rate() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value >= 0 && value <= 1) }' || {
    echo "${name} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  }
}

require_positive_number() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be a positive number: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value > 0) }' || {
    echo "${name} must be greater than zero: ${value}" >&2
    exit 1
  }
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_file() {
  local name="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${name} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

number_greater_than() {
  awk -v value="$1" -v threshold="$2" 'BEGIN { exit !(value > threshold) }'
}

require_rate "OCI_REAL_IP_REPLAY_MAX_MULTI_EDGE_429_RATE" "${max_multi_edge_429_rate}"
require_positive_number "OCI_REAL_IP_REPLAY_MAX_ACCEPTED_P95_MS" "${max_accepted_p95_ms}"
require_positive_integer "OCI_REAL_IP_REPLAY_MIN_BUCKET_COUNT" "${min_bucket_count}"

case "${real_ip_header}" in
  X-Forwarded-For|X-Real-IP) ;;
  *)
    echo "OCI_REAL_IP_REPLAY_REAL_IP_HEADER must be X-Forwarded-For or X-Real-IP: ${real_ip_header}" >&2
    exit 1
    ;;
esac

print_plan() {
  echo "[oci-real-ip-multisource-replay] name=${name}"
  echo "[oci-real-ip-multisource-replay] single_source_summary=${single_summary:-missing}"
  echo "[oci-real-ip-multisource-replay] multi_source_summary=${multi_summary:-missing}"
  echo "[oci-real-ip-multisource-replay] nginx_status_tsv=${nginx_status_tsv:-missing}"
  echo "[oci-real-ip-multisource-replay] max_multi_source_edge_429_rate=${max_multi_edge_429_rate}"
  echo "[oci-real-ip-multisource-replay] max_accepted_p95_ms=${max_accepted_p95_ms}"
  echo "[oci-real-ip-multisource-replay] min_multi_source_bucket_count=${min_bucket_count}"
  echo "[oci-real-ip-multisource-replay] real_ip_header=${real_ip_header}"
  echo "[oci-real-ip-multisource-replay] trusted_proxy_contract=set_real_ip_from 10.60.0.0/16"
  echo "[oci-real-ip-multisource-replay] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "OCI_REAL_IP_REPLAY_SINGLE_SOURCE_SUMMARY_JSON" "${single_summary}"
  require_file "OCI_REAL_IP_REPLAY_MULTI_SOURCE_SUMMARY_JSON" "${multi_summary}"
  require_file "OCI_REAL_IP_REPLAY_NGINX_STATUS_TSV" "${nginx_status_tsv}"
  exit 0
fi

require_file "OCI_REAL_IP_REPLAY_SINGLE_SOURCE_SUMMARY_JSON" "${single_summary}"
require_file "OCI_REAL_IP_REPLAY_MULTI_SOURCE_SUMMARY_JSON" "${multi_summary}"
require_file "OCI_REAL_IP_REPLAY_NGINX_STATUS_TSV" "${nginx_status_tsv}"
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

metric_value() {
  local file="$1"
  local metric="$2"
  local field="$3"
  jq -r --arg metric "${metric}" --arg field "${field}" \
    '.metrics[$metric].values[$field] // "0"' "${file}"
}

accepted_p95() {
  local file="$1"
  awk \
    -v hot_first="$(metric_value "${file}" aquila_transaction_hot_first_ms "p(95)")" \
    -v cold_deep="$(metric_value "${file}" aquila_transaction_cold_deep_cursor_ms "p(95)")" \
    'BEGIN { print (hot_first > cold_deep ? hot_first : cold_deep) }'
}

bucket_count() {
  local run="$1"
  awk -F '\t' -v run="${run}" '
    NR == 1 { next }
    $1 == run && $2 != "" {
      seen[$2] = 1
    }
    END {
      for (key in seen) count++
      print count + 0
    }
  ' "${nginx_status_tsv}"
}

mkdir -p "${output_dir}"

single_edge_429="$(metric_value "${single_summary}" aquila_transaction_edge_429_rate rate)"
single_backend_429="$(metric_value "${single_summary}" aquila_transaction_backend_429_rate rate)"
single_delayed="$(metric_value "${single_summary}" aquila_transaction_edge_delayed_rate rate)"
single_p95="$(accepted_p95 "${single_summary}")"
single_5xx="$(
  awk \
    -v bad_gateway="$(metric_value "${single_summary}" aquila_transaction_502_count count)" \
    -v unavailable="$(metric_value "${single_summary}" aquila_transaction_503_count count)" \
    'BEGIN { print bad_gateway + unavailable }'
)"
single_buckets="$(bucket_count single-source)"

multi_edge_429="$(metric_value "${multi_summary}" aquila_transaction_edge_429_rate rate)"
multi_backend_429="$(metric_value "${multi_summary}" aquila_transaction_backend_429_rate rate)"
multi_delayed="$(metric_value "${multi_summary}" aquila_transaction_edge_delayed_rate rate)"
multi_p95="$(accepted_p95 "${multi_summary}")"
multi_5xx="$(
  awk \
    -v bad_gateway="$(metric_value "${multi_summary}" aquila_transaction_502_count count)" \
    -v unavailable="$(metric_value "${multi_summary}" aquila_transaction_503_count count)" \
    'BEGIN { print bad_gateway + unavailable }'
)"
multi_buckets="$(bucket_count multi-source)"

multi_status="pass"
if number_greater_than "${multi_edge_429}" "${max_multi_edge_429_rate}" \
    || number_greater_than "${multi_backend_429}" "0" \
    || number_greater_than "${multi_p95}" "${max_accepted_p95_ms}" \
    || number_greater_than "${multi_5xx}" "0" \
    || ((multi_buckets < min_bucket_count)); then
  multi_status="fail"
fi

gate_status="${multi_status}"
bucket_status="pass"
if ((multi_buckets < min_bucket_count)); then
  bucket_status="fail"
fi

{
  printf "run\tstatus\tedge_429_rate\tbackend_429_rate\tedge_delayed_rate\taccepted_p95_ms\treal_ip_bucket_count\t5xx_count\n"
  printf "single-source\tobserve\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${single_edge_429}" "${single_backend_429}" "${single_delayed}" "${single_p95}" "${single_buckets}" "${single_5xx}"
  printf "multi-source\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${multi_status}" "${multi_edge_429}" "${multi_backend_429}" "${multi_delayed}" "${multi_p95}" "${multi_buckets}" "${multi_5xx}"
} >"${summary_tsv}"

cat >"${report_md}" <<REPORT
# OCI Real-IP Multi-Source Public Capacity Replay

## Summary

- gate_status=${gate_status}
- real client IP bucket split: ${bucket_status}
- trusted proxy contract: set_real_ip_from 10.60.0.0/16;
- real_ip_header: ${real_ip_header}
- multi-source edge 429 budget: <= ${max_multi_edge_429_rate}
- accepted p95 budget: <= ${max_accepted_p95_ms}ms

## Artifacts

- summary TSV: ${summary_tsv}
- single-source summary: ${single_summary}
- multi-source summary: ${multi_summary}
- Nginx status TSV: ${nginx_status_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "OCI real-IP multi-source replay failed: ${summary_tsv}" >&2
  exit 1
fi
