#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-oci-real-ip-multisource-capacity-replay.sh"

echo "[oci-real-ip-multisource-replay] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

single_summary="${temp_dir}/single-summary.json"
multi_summary="${temp_dir}/multi-summary.json"
status_tsv="${temp_dir}/nginx-status.tsv"
output_dir="${temp_dir}/output"

cat >"${single_summary}" <<'JSON'
{
  "metrics": {
    "aquila_transaction_edge_429_rate": {"values": {"rate": 0.2088}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_edge_delayed_rate": {"values": {"rate": 0.65}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": 188.9}},
    "aquila_transaction_cold_deep_cursor_ms": {"values": {"p(95)": 99.9}}
  }
}
JSON

cat >"${multi_summary}" <<'JSON'
{
  "metrics": {
    "aquila_transaction_edge_429_rate": {"values": {"rate": 0.0850}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_edge_delayed_rate": {"values": {"rate": 0.09}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": 155.0}},
    "aquila_transaction_cold_deep_cursor_ms": {"values": {"p(95)": 96.0}}
  }
}
JSON

cat >"${status_tsv}" <<'TSV'
run	realip_remote_addr	limit_req_status	count
single-source	198.51.100.10	REJECTED	400
multi-source	198.51.100.10	PASSED	1200
multi-source	198.51.100.11	PASSED	1180
multi-source	198.51.100.12	PASSED	1190
multi-source	198.51.100.13	REJECTED	80
TSV

echo "[oci-real-ip-multisource-replay] print plan"
plan="$(
  OCI_REAL_IP_REPLAY_NAME=real-ip-replay-check \
  OCI_REAL_IP_REPLAY_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_IP_REPLAY_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_IP_REPLAY_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_IP_REPLAY_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=real-ip-replay-check" <<<"${plan}" >/dev/null
grep -F "single_source_summary=${single_summary}" <<<"${plan}" >/dev/null
grep -F "multi_source_summary=${multi_summary}" <<<"${plan}" >/dev/null
grep -F "nginx_status_tsv=${status_tsv}" <<<"${plan}" >/dev/null
grep -F "max_multi_source_edge_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "min_multi_source_bucket_count=2" <<<"${plan}" >/dev/null
grep -F "real_ip_header=X-Forwarded-For" <<<"${plan}" >/dev/null

echo "[oci-real-ip-multisource-replay] report"
output="$(
  OCI_REAL_IP_REPLAY_NAME=real-ip-replay-check \
  OCI_REAL_IP_REPLAY_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_IP_REPLAY_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_IP_REPLAY_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_IP_REPLAY_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/real-ip-replay-check-real-ip-replay.tsv"
test "${report_md}" = "${output_dir}/real-ip-replay-check-real-ip-replay.md"
test -s "${summary_tsv}"
grep -F $'run\tstatus\tedge_429_rate\tbackend_429_rate\tedge_delayed_rate\taccepted_p95_ms\treal_ip_bucket_count\t5xx_count' "${summary_tsv}" >/dev/null
grep -F $'single-source\tobserve\t0.2088\t0\t0.65\t188.9\t1\t0' "${summary_tsv}" >/dev/null
grep -F $'multi-source\tpass\t0.0850\t0\t0.09\t155.0\t4\t0' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "real client IP bucket split: pass" "${report_md}" >/dev/null
grep -F 'set_real_ip_from 10.60.0.0/16;' "${report_md}" >/dev/null

echo "[oci-real-ip-multisource-replay] bucket fail report"
awk -F '\t' 'BEGIN { OFS = "\t" } NR == 1 || $1 == "single-source" { print; next } { $2 = "198.51.100.10"; print }' \
  "${status_tsv}" >"${status_tsv}.bucket-fail"
if OCI_REAL_IP_REPLAY_NAME=real-ip-replay-bucket-fail \
  OCI_REAL_IP_REPLAY_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_IP_REPLAY_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_IP_REPLAY_NGINX_STATUS_TSV="${status_tsv}.bucket-fail" \
  OCI_REAL_IP_REPLAY_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "real-IP replay unexpectedly passed without bucket split" >&2
  exit 1
fi

echo "[oci-real-ip-multisource-replay] edge 429 fail report"
jq '.metrics.aquila_transaction_edge_429_rate.values.rate = 0.18' "${multi_summary}" >"${multi_summary}.fail"
if OCI_REAL_IP_REPLAY_NAME=real-ip-replay-edge-fail \
  OCI_REAL_IP_REPLAY_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_IP_REPLAY_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}.fail" \
  OCI_REAL_IP_REPLAY_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_IP_REPLAY_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "real-IP replay unexpectedly passed high multi-source 429" >&2
  exit 1
fi
