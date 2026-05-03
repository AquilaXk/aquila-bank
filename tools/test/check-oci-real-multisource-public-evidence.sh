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
source_evidence_tsv="${temp_dir}/source-evidence.tsv"
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

cat >"${source_evidence_tsv}" <<'TSV'
source_name	run_id	docker_context	realip_remote_addr	edge_429_rate	backend_429_rate	accepted_p95_ms	five_xx_count	artifact_uri
source-a	oci-source-evidence-20260503	oci-k6-a	198.51.100.10	0.081	0	154.0	0	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/source-a
source-b	oci-source-evidence-20260503	oci-k6-b	198.51.100.11	0.089	0	158.0	0	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/source-b
TSV

echo "[oci-real-multisource-public-evidence] print plan"
plan="$(
  OCI_REAL_MULTISOURCE_NAME=real-multi-check \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=real-multi-check" <<<"${plan}" >/dev/null
grep -F "run_id=oci-source-evidence-20260503" <<<"${plan}" >/dev/null
grep -F "docker_context_count=2" <<<"${plan}" >/dev/null
grep -F "true_multi_source_required=true" <<<"${plan}" >/dev/null
grep -F "minimum_remote_docker_contexts=2" <<<"${plan}" >/dev/null
grep -F "source_evidence_tsv=${source_evidence_tsv}" <<<"${plan}" >/dev/null
grep -F "artifact_uri=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503" <<<"${plan}" >/dev/null
grep -F "multi_source_runner=tools/test/run-k6-transaction-100m-multisource.sh" <<<"${plan}" >/dev/null
grep -F "replay_gate=tools/test/run-oci-real-ip-multisource-capacity-replay.sh" <<<"${plan}" >/dev/null

echo "[oci-real-multisource-public-evidence] pass report"
output="$(
  OCI_REAL_MULTISOURCE_NAME=real-multi-check \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
contexts_tsv="${output_dir}/real-multi-check-real-multisource-contexts.tsv"
source_summary_tsv="${output_dir}/real-multi-check-real-multisource-source-summary.tsv"
test "${report_md}" = "${output_dir}/real-multi-check-real-multisource-public-evidence.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "actual execution run id: oci-source-evidence-20260503" "${report_md}" >/dev/null
grep -F "artifact reference: oci://aquila-evidence/transaction-read/oci-source-evidence-20260503" "${report_md}" >/dev/null
grep -F "docker context count: 2" "${report_md}" >/dev/null
grep -F "single-source vs multi-source comparison: fixed" "${report_md}" >/dev/null
grep -F "real IP bucket split: verified" "${report_md}" >/dev/null
grep -F "source-level real IP/429/latency split: verified" "${report_md}" >/dev/null
grep -F "true multi-source public traffic evidence: fixed" "${report_md}" >/dev/null
grep -F $'index\tdocker_context' "${contexts_tsv}" >/dev/null
grep -F $'1\toci-k6-a' "${contexts_tsv}" >/dev/null
grep -F $'2\toci-k6-b' "${contexts_tsv}" >/dev/null
grep -F $'source_name\trun_id\tdocker_context\trealip_remote_addr\tedge_429_rate\tbackend_429_rate\taccepted_p95_ms\tfive_xx_count\tartifact_uri' "${source_summary_tsv}" >/dev/null
grep -F $'source-a\toci-source-evidence-20260503\toci-k6-a\t198.51.100.10\t0.081\t0\t154.0\t0\toci://aquila-evidence/transaction-read/oci-source-evidence-20260503/source-a' "${source_summary_tsv}" >/dev/null
grep -F $'source-b\toci-source-evidence-20260503\toci-k6-b\t198.51.100.11\t0.089\t0\t158.0\t0\toci://aquila-evidence/transaction-read/oci-source-evidence-20260503/source-b' "${source_summary_tsv}" >/dev/null

echo "[oci-real-multisource-public-evidence] one context fails"
if OCI_REAL_MULTISOURCE_NAME=real-multi-one-context \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "real multi-source public evidence unexpectedly passed one context" >&2
  exit 1
fi

echo "[oci-real-multisource-public-evidence] one source evidence fails"
awk -F '\t' 'NR == 1 || $1 == "source-a"' "${source_evidence_tsv}" >"${source_evidence_tsv}.one-source"
if OCI_REAL_MULTISOURCE_NAME=real-multi-one-source \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}.one-source" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "real multi-source public evidence unexpectedly passed one source evidence row" >&2
  exit 1
fi

echo "[oci-real-multisource-public-evidence] blank source artifact fails"
awk -F '\t' 'BEGIN { OFS = "\t" } NR == 1 { print; next } { if ($1 == "source-b") $9 = ""; print }' "${source_evidence_tsv}" >"${source_evidence_tsv}.blank-artifact"
if OCI_REAL_MULTISOURCE_NAME=real-multi-blank-artifact \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}.blank-artifact" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "real multi-source public evidence unexpectedly passed blank source artifact" >&2
  exit 1
fi
