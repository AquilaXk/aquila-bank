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
host_metrics_tsv="${temp_dir}/host-metrics.tsv"
timeline_tsv="${temp_dir}/host-metrics-timeline.tsv"
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
source_name	run_id	docker_context	realip_remote_addr	edge_429_rate	backend_429_rate	accepted_p95_ms	accepted_count	fairness_ratio	five_xx_count	artifact_uri
source-a	oci-source-evidence-20260503	oci-k6-a	198.51.100.10	0.081	0	154.0	1200	0.98	0	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/source-a
source-b	oci-source-evidence-20260503	oci-k6-b	198.51.100.11	0.089	0	158.0	1180	1.02	0	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/source-b
TSV

cat >"${host_metrics_tsv}" <<'TSV'
run_id	host_role	host_name	host_id	vm_id	network_id	docker_context	cpu_pct	rx_mbps	tx_mbps	artifact_uri
oci-source-evidence-20260503	generator	k6-a	ocid1.instance.oc1..generatora	vm-k6-a	subnet-generator-a	oci-k6-a	41.2	18.5	21.1	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/host/k6-a
oci-source-evidence-20260503	generator	k6-b	ocid1.instance.oc1..generatorb	vm-k6-b	subnet-generator-b	oci-k6-b	39.8	17.9	20.4	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/host/k6-b
oci-source-evidence-20260503	target	oci-a1-staging	ocid1.instance.oc1..target	vm-target	subnet-target	target	63.5	38.2	44.6	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/host/target
TSV

cat >"${timeline_tsv}" <<'TSV'
run_id	phase	host_role	host_name	host_id	vm_id	network_id	docker_context	sample_started_at_utc	sample_ended_at_utc	sample_count	sample_source	sample_interval_seconds	cpu_pct_avg	cpu_pct_max	rx_mbps_avg	rx_mbps_max	tx_mbps_avg	tx_mbps_max	artifact_uri	summary_ref	artifact_pack_uri
oci-source-evidence-20260503	arrival16	generator	k6-a	ocid1.instance.oc1..generatora	vm-k6-a	subnet-generator-a	oci-k6-a	2026-05-03T01:00:00Z	2026-05-03T01:01:00Z	12	load-coupled	5	31.2	44.1	10.5	18.2	12.1	19.7	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/arrival16/generator.tsv	build/reports/k6/oci-source-evidence-20260503/arrival16-summary.json	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503
oci-source-evidence-20260503	arrival16	target	oci-a1-staging	ocid1.instance.oc1..target	vm-target	subnet-target	target	2026-05-03T01:00:00Z	2026-05-03T01:01:00Z	12	load-coupled	5	52.4	66.8	21.1	30.4	25.6	34.2	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/arrival16/target.tsv	build/reports/k6/oci-source-evidence-20260503/arrival16-summary.json	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503
oci-source-evidence-20260503	vu16	generator	k6-a	ocid1.instance.oc1..generatora	vm-k6-a	subnet-generator-a	oci-k6-a	2026-05-03T01:05:00Z	2026-05-03T01:06:00Z	12	load-coupled	5	34.8	49.0	12.0	20.0	15.1	23.4	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/vu16/generator.tsv	build/reports/k6/oci-source-evidence-20260503/vu16-summary.json	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503
oci-source-evidence-20260503	vu16	target	oci-a1-staging	ocid1.instance.oc1..target	vm-target	subnet-target	target	2026-05-03T01:05:00Z	2026-05-03T01:06:00Z	12	load-coupled	5	58.2	70.3	24.2	34.0	28.0	38.2	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/vu16/target.tsv	build/reports/k6/oci-source-evidence-20260503/vu16-summary.json	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503
oci-source-evidence-20260503	burst-matrix	generator	k6-a	ocid1.instance.oc1..generatora	vm-k6-a	subnet-generator-a	oci-k6-a	2026-05-03T01:10:00Z	2026-05-03T01:15:00Z	60	load-coupled	5	43.1	59.3	18.7	31.2	22.4	36.8	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/burst-matrix/generator.tsv	build/reports/k6/oci-source-evidence-20260503/burst-matrix.tsv	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503
oci-source-evidence-20260503	burst-matrix	target	oci-a1-staging	ocid1.instance.oc1..target	vm-target	subnet-target	target	2026-05-03T01:10:00Z	2026-05-03T01:15:00Z	60	load-coupled	5	63.7	76.2	35.4	51.8	40.0	59.0	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503/burst-matrix/target.tsv	build/reports/k6/oci-source-evidence-20260503/burst-matrix.tsv	oci://aquila-evidence/transaction-read/oci-source-evidence-20260503
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
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${host_metrics_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV="${timeline_tsv}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=real-multi-check" <<<"${plan}" >/dev/null
grep -F "run_id=oci-source-evidence-20260503" <<<"${plan}" >/dev/null
grep -F "docker_context_count=2" <<<"${plan}" >/dev/null
grep -F "true_multi_source_required=true" <<<"${plan}" >/dev/null
grep -F "source_fairness_required=true" <<<"${plan}" >/dev/null
grep -F "fairness_ratio_range=0.80..1.25" <<<"${plan}" >/dev/null
grep -F "max_edge_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "max_backend_429_rate=0.005" <<<"${plan}" >/dev/null
grep -F "max_accepted_p95_ms=200" <<<"${plan}" >/dev/null
grep -F "load_coupled_timeline_required=true" <<<"${plan}" >/dev/null
grep -F "minimum_remote_docker_contexts=2" <<<"${plan}" >/dev/null
grep -F "source_evidence_tsv=${source_evidence_tsv}" <<<"${plan}" >/dev/null
grep -F "host_metrics_tsv=${host_metrics_tsv}" <<<"${plan}" >/dev/null
grep -F "host_metrics_timeline_tsv=${timeline_tsv}" <<<"${plan}" >/dev/null
grep -F "artifact_uri=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503" <<<"${plan}" >/dev/null

echo "[oci-real-multisource-public-evidence] pass report"
output="$(
  OCI_REAL_MULTISOURCE_NAME=real-multi-check \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${host_metrics_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV="${timeline_tsv}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
contexts_tsv="${output_dir}/real-multi-check-real-multisource-contexts.tsv"
source_summary_tsv="${output_dir}/real-multi-check-real-multisource-source-summary.tsv"
host_metrics_summary_tsv="${output_dir}/real-multi-check-real-multisource-host-metrics.tsv"
test "${report_md}" = "${output_dir}/real-multi-check-real-multisource-public-evidence.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "actual execution run id: oci-source-evidence-20260503" "${report_md}" >/dev/null
grep -F "artifact reference: oci://aquila-evidence/transaction-read/oci-source-evidence-20260503" "${report_md}" >/dev/null
grep -F "docker context count: 2" "${report_md}" >/dev/null
grep -F "source fairness: verified" "${report_md}" >/dev/null
grep -F "host metrics timeline: verified" "${report_md}" >/dev/null
grep -F "single-source vs multi-source comparison: fixed" "${report_md}" >/dev/null
grep -F "source-level real IP/429/latency/fairness split: verified" "${report_md}" >/dev/null
grep -F "host-level CPU/network split: verified" "${report_md}" >/dev/null
grep -F "source edge/backend 429 budget: verified" "${report_md}" >/dev/null
grep -F "load-coupled host metrics timeline: verified" "${report_md}" >/dev/null
grep -F "true multi-source public traffic evidence: fixed" "${report_md}" >/dev/null
grep -F $'index\tdocker_context' "${contexts_tsv}" >/dev/null
grep -F $'1\toci-k6-a' "${contexts_tsv}" >/dev/null
grep -F $'2\toci-k6-b' "${contexts_tsv}" >/dev/null
grep -F $'source_name\trun_id\tdocker_context\trealip_remote_addr\tedge_429_rate\tbackend_429_rate\taccepted_p95_ms\taccepted_count\tfairness_ratio\tfive_xx_count\tartifact_uri' "${source_summary_tsv}" >/dev/null
grep -F $'source-a\toci-source-evidence-20260503\toci-k6-a\t198.51.100.10\t0.081\t0\t154.0\t1200\t0.98\t0\toci://aquila-evidence/transaction-read/oci-source-evidence-20260503/source-a' "${source_summary_tsv}" >/dev/null
grep -F $'run_id\thost_role\thost_name\thost_id\tvm_id\tnetwork_id\tdocker_context\tcpu_pct\trx_mbps\ttx_mbps\tartifact_uri' "${host_metrics_summary_tsv}" >/dev/null
grep -F $'oci-source-evidence-20260503\tgenerator\tk6-a\tocid1.instance.oc1..generatora\tvm-k6-a\tsubnet-generator-a\toci-k6-a\t41.2\t18.5\t21.1\toci://aquila-evidence/transaction-read/oci-source-evidence-20260503/host/k6-a' "${host_metrics_summary_tsv}" >/dev/null

echo "[oci-real-multisource-public-evidence] one context fails"
if OCI_REAL_MULTISOURCE_NAME=real-multi-one-context \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${host_metrics_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV="${timeline_tsv}" \
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
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${host_metrics_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV="${timeline_tsv}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "real multi-source public evidence unexpectedly passed one source evidence row" >&2
  exit 1
fi

echo "[oci-real-multisource-public-evidence] bad fairness fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "source-b" { $9 = "1.60" } { print }' \
  "${source_evidence_tsv}" >"${source_evidence_tsv}.bad-fairness"
if OCI_REAL_MULTISOURCE_NAME=real-multi-bad-fairness \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}.bad-fairness" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${host_metrics_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV="${timeline_tsv}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >"${temp_dir}/bad-fairness.log" 2>&1; then
  echo "real multi-source public evidence unexpectedly passed bad fairness ratio" >&2
  exit 1
fi
grep -F "source evidence metric out of contract" "${temp_dir}/bad-fairness.log" >/dev/null

echo "[oci-real-multisource-public-evidence] backend 429 budget fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "source-b" { $6 = "0.020" } { print }' \
  "${source_evidence_tsv}" >"${source_evidence_tsv}.bad-backend"
if OCI_REAL_MULTISOURCE_NAME=real-multi-bad-backend \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}.bad-backend" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${host_metrics_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV="${timeline_tsv}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >"${temp_dir}/bad-backend.log" 2>&1; then
  echo "real multi-source public evidence unexpectedly passed backend 429 over budget" >&2
  exit 1
fi
grep -F "source evidence metric out of contract" "${temp_dir}/bad-backend.log" >/dev/null

echo "[oci-real-multisource-public-evidence] fallback timeline fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $12 = "fallback-snapshot"; $13 = "0" } { print }' \
  "${timeline_tsv}" >"${timeline_tsv}.fallback"
if OCI_REAL_MULTISOURCE_NAME=real-multi-fallback-timeline \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${host_metrics_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV="${timeline_tsv}.fallback" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >"${temp_dir}/fallback-timeline.log" 2>&1; then
  echo "real multi-source public evidence unexpectedly accepted fallback timeline" >&2
  exit 1
fi
grep -F "load-coupled sampler source required" "${temp_dir}/fallback-timeline.log" >/dev/null

echo "[oci-real-multisource-public-evidence] blank source artifact fails"
awk -F '\t' 'BEGIN { OFS = "\t" } NR == 1 { print; next } { if ($1 == "source-b") $11 = ""; print }' "${source_evidence_tsv}" >"${source_evidence_tsv}.blank-artifact"
if OCI_REAL_MULTISOURCE_NAME=real-multi-blank-artifact \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}.blank-artifact" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${host_metrics_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV="${timeline_tsv}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "real multi-source public evidence unexpectedly passed blank source artifact" >&2
  exit 1
fi

echo "[oci-real-multisource-public-evidence] missing target host metrics fails"
awk -F '\t' 'NR == 1 || $2 != "target"' "${host_metrics_tsv}" >"${host_metrics_tsv}.missing-target"
if OCI_REAL_MULTISOURCE_NAME=real-multi-missing-target-host \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${host_metrics_tsv}.missing-target" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV="${timeline_tsv}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "real multi-source public evidence unexpectedly passed without target host metrics" >&2
  exit 1
fi

echo "[oci-real-multisource-public-evidence] missing generator host metrics fails"
awk -F '\t' 'NR == 1 || $7 != "oci-k6-b"' "${host_metrics_tsv}" >"${host_metrics_tsv}.missing-generator"
if OCI_REAL_MULTISOURCE_NAME=real-multi-missing-generator-host \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${host_metrics_tsv}.missing-generator" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV="${timeline_tsv}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "real multi-source public evidence unexpectedly passed without each generator host metrics" >&2
  exit 1
fi

echo "[oci-real-multisource-public-evidence] missing timeline fails"
if OCI_REAL_MULTISOURCE_NAME=real-multi-missing-timeline \
  OCI_REAL_MULTISOURCE_RUN_ID=oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${status_tsv}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${source_evidence_tsv}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${host_metrics_tsv}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI=oci://aquila-evidence/transaction-read/oci-source-evidence-20260503 \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "real multi-source public evidence unexpectedly passed without host metrics timeline" >&2
  exit 1
fi
