#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-oci-real-multisource-public-evidence.sh"

echo "[oci-real-multisource-public-evidence] shell syntax"
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

echo "[oci-real-multisource-public-evidence] print plan"
plan="$(
  OCI_REAL_MULTISOURCE_NAME=real-multi-check \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=real-multi-check" <<<"${plan}" >/dev/null
grep -F "docker_context_count=2" <<<"${plan}" >/dev/null
grep -F "multi_source_runner=tools/test/run-k6-transaction-100m-multisource.sh" <<<"${plan}" >/dev/null
grep -F "replay_gate=tools/test/run-oci-real-ip-multisource-capacity-replay.sh" <<<"${plan}" >/dev/null

echo "[oci-real-multisource-public-evidence] pass report"
output="$(
  OCI_REAL_MULTISOURCE_NAME=real-multi-check \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
contexts_tsv="${output_dir}/real-multi-check-real-multisource-contexts.tsv"
test "${report_md}" = "${output_dir}/real-multi-check-real-multisource-public-evidence.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "docker context count: 2" "${report_md}" >/dev/null
grep -F "single-source vs multi-source comparison: fixed" "${report_md}" >/dev/null
grep -F "real IP bucket split: delegated to replay gate" "${report_md}" >/dev/null
grep -F $'index\tdocker_context' "${contexts_tsv}" >/dev/null
grep -F $'1\toci-k6-a' "${contexts_tsv}" >/dev/null
grep -F $'2\toci-k6-b' "${contexts_tsv}" >/dev/null

echo "[oci-real-multisource-public-evidence] one context fails"
if OCI_REAL_MULTISOURCE_NAME=real-multi-one-context \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "real multi-source public evidence unexpectedly passed one context" >&2
  exit 1
fi
