#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-operational-evidence-gate.sh"

echo "[transaction-read-operational-evidence] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

evidence_tsv="${temp_dir}/operational-evidence.tsv"
output_dir="${temp_dir}/output"

cat >"${evidence_tsv}" <<'TSV'
scenario	duration_min	source_ips	cold_p95_ms	warm_p95_ms	p999_ms	edge_429_rate	backend_429_count	five_xx_count	nginx_499_count	hikari_validation_warnings	db_pool_pending_max	sse_reject_count	deploy_drain_5xx_count	pg_wait_p95_ms	pg_wait_p999_ms	cpu_max_pct	memory_max_pct	disk_io_wait_pct	network_rx_drop_count	network_tx_drop_count	timeline_artifact	resource_timeline_artifact	workload_mix_ref	outbox_lag_max	cache_buffer_hit_ratio	cache_wait_event_ref	nginx_upstream_p999_ms	deploy_retry_contract_ref	deploy_reconnect_success_count
hikari-soak	10	1	420	210	390	0.01	0	0	0	0	0	0	0	3	21	55	61	2	0	0	oci/hikari-soak.json	oci/hikari-soak-resources.json	n/a	0	1	n/a	390	n/a	0
cold-warm	3	1	760	240	410	0.02	0	0	0	0	0	0	0	4	24	58	63	3	0	0	oci/cold-warm.json	oci/cold-warm-resources.json	n/a	0	0.99	oci/cold-warm-wait-events.tsv	410	n/a	0
mixed-workload	30	1	650	260	450	0.08	0	0	0	0	0	0	0	6	35	72	68	5	0	0	oci/mixed-workload.json	oci/mixed-workload-resources.json	oci/mixed-workload-components.json	0	1	n/a	450	n/a	0
real-ip-multisource	2	3	560	230	430	0.07	0	0	0	0	0	0	0	5	31	60	62	4	0	0	oci/real-ip.json	oci/real-ip-resources.json	n/a	0	1	n/a	430	n/a	0
deploy-drain	5	1	590	250	470	0.04	0	0	0	0	0	0	0	5	29	64	65	5	0	0	oci/deploy-drain.json	oci/deploy-drain-resources.json	n/a	0	1	n/a	470	oci/deploy-retry-contract.json	3
p999-long	30	1	700	280	490	0.06	0	0	0	0	0	0	0	8	40	66	64	6	0	0	oci/p999-long.json	oci/p999-long-resources.json	n/a	0	1	n/a	490	n/a	0
TSV

echo "[transaction-read-operational-evidence] print plan"
plan="$(
  OP_EVIDENCE_NAME=operational-check \
  OP_EVIDENCE_INPUT_TSV="${evidence_tsv}" \
  OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=operational-check" <<<"${plan}" >/dev/null
grep -F "input_tsv=${evidence_tsv}" <<<"${plan}" >/dev/null
grep -F "required_scenarios=hikari-soak,cold-warm,mixed-workload,real-ip-multisource,deploy-drain,p999-long" <<<"${plan}" >/dev/null
grep -F "max_edge_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "max_p999_ms=500" <<<"${plan}" >/dev/null
grep -F "max_pg_wait_p95_ms=20" <<<"${plan}" >/dev/null
grep -F "max_pg_wait_p999_ms=100" <<<"${plan}" >/dev/null
grep -F "max_cpu_pct=95" <<<"${plan}" >/dev/null
grep -F "max_memory_pct=90" <<<"${plan}" >/dev/null
grep -F "min_cache_buffer_hit_ratio=0.95" <<<"${plan}" >/dev/null
grep -F "max_nginx_upstream_p999_ms=500" <<<"${plan}" >/dev/null
grep -F "mixed_min_duration_min=30" <<<"${plan}" >/dev/null
grep -F "min_real_source_ips=2" <<<"${plan}" >/dev/null

echo "[transaction-read-operational-evidence] pass report"
output="$(
  OP_EVIDENCE_NAME=operational-check \
  OP_EVIDENCE_INPUT_TSV="${evidence_tsv}" \
  OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/operational-check-operational-evidence.tsv"
test "${report_md}" = "${output_dir}/operational-check-operational-evidence.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "Hikari validation warning: 0" "${report_md}" >/dev/null
grep -F "499/5xx/backend429 hard target: 0" "${report_md}" >/dev/null
grep -F "mixed workload min duration: 30m" "${report_md}" >/dev/null
grep -F "p99.9 long observation max: 500ms" "${report_md}" >/dev/null
grep -F "resource timeline artifact: required" "${report_md}" >/dev/null
grep -F "PG wait p95/p99.9 max: 20ms / 100ms" "${report_md}" >/dev/null
grep -F "mixed workload outbox lag hard target: 0" "${report_md}" >/dev/null
grep -F "cache buffer hit min: 0.95" "${report_md}" >/dev/null
grep -F "deploy retry/reconnect contract: required" "${report_md}" >/dev/null
grep -F $'mixed-workload\tpass\tok\t30\t1\t650\t260\t450\t0.08\t0\t0\t0\t0\t0\t0\t0\t6\t35\t72\t68\t5\t0\t0\toci/mixed-workload.json\toci/mixed-workload-resources.json\toci/mixed-workload-components.json\t0\t1\tn/a\t450\tn/a\t0' "${summary_tsv}" >/dev/null
grep -F $'real-ip-multisource\tpass\tok\t2\t3' "${summary_tsv}" >/dev/null

echo "[transaction-read-operational-evidence] fail report"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "mixed-workload" { $6 = 560; $7 = 0.12; $9 = 1; $10 = 1; $11 = 1; $16 = 120; $20 = 1; $23 = "n/a"; $24 = "n/a"; $25 = 1 } { print }' \
  "${evidence_tsv}" >"${evidence_tsv}.fail"
if OP_EVIDENCE_NAME=operational-fail \
  OP_EVIDENCE_INPUT_TSV="${evidence_tsv}.fail" \
  OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "operational evidence gate unexpectedly passed Hikari/499/p999/429 failure" >&2
  exit 1
fi

OP_EVIDENCE_NAME=operational-fail \
OP_EVIDENCE_INPUT_TSV="${evidence_tsv}.fail" \
OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F $'mixed-workload\tfail\t' "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "edge429>0.10" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "5xx>0" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "499>0" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "hikari-warning>0" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "pg-wait-p999>100" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "network-drop>0" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "resource-timeline-missing" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "workload-mix-missing" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "outbox-lag>0" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null

echo "[transaction-read-operational-evidence] cache closure fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "cold-warm" { $26 = 0.90; $27 = "n/a"; $28 = 560 } { print }' \
  "${evidence_tsv}" >"${evidence_tsv}.cache-fail"
if OP_EVIDENCE_NAME=operational-cache-fail \
  OP_EVIDENCE_INPUT_TSV="${evidence_tsv}.cache-fail" \
  OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "operational evidence gate unexpectedly passed cache closure failure" >&2
  exit 1
fi
OP_EVIDENCE_NAME=operational-cache-fail \
OP_EVIDENCE_INPUT_TSV="${evidence_tsv}.cache-fail" \
OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "cache-buffer-hit<0.95" "${output_dir}/operational-cache-fail-operational-evidence.tsv" >/dev/null
grep -F "cache-wait-event-missing" "${output_dir}/operational-cache-fail-operational-evidence.tsv" >/dev/null
grep -F "nginx-p999>500" "${output_dir}/operational-cache-fail-operational-evidence.tsv" >/dev/null

echo "[transaction-read-operational-evidence] deploy closure fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "deploy-drain" { $29 = "n/a"; $30 = 0 } { print }' \
  "${evidence_tsv}" >"${evidence_tsv}.deploy-fail"
if OP_EVIDENCE_NAME=operational-deploy-fail \
  OP_EVIDENCE_INPUT_TSV="${evidence_tsv}.deploy-fail" \
  OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "operational evidence gate unexpectedly passed deploy closure failure" >&2
  exit 1
fi
OP_EVIDENCE_NAME=operational-deploy-fail \
OP_EVIDENCE_INPUT_TSV="${evidence_tsv}.deploy-fail" \
OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "deploy-retry-contract-missing" "${output_dir}/operational-deploy-fail-operational-evidence.tsv" >/dev/null
grep -F "deploy-reconnect-missing" "${output_dir}/operational-deploy-fail-operational-evidence.tsv" >/dev/null

echo "[transaction-read-operational-evidence] missing scenario fails"
awk -F '\t' '$1 != "deploy-drain"' "${evidence_tsv}" >"${evidence_tsv}.missing"
if OP_EVIDENCE_NAME=operational-missing \
  OP_EVIDENCE_INPUT_TSV="${evidence_tsv}.missing" \
  OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "operational evidence gate unexpectedly passed missing deploy-drain scenario" >&2
  exit 1
fi

echo "[transaction-read-operational-evidence] real IP source fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "real-ip-multisource" { $3 = 1 } { print }' \
  "${evidence_tsv}" >"${evidence_tsv}.source-fail"
if OP_EVIDENCE_NAME=operational-source-fail \
  OP_EVIDENCE_INPUT_TSV="${evidence_tsv}.source-fail" \
  OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "operational evidence gate unexpectedly passed single source real-IP evidence" >&2
  exit 1
fi
