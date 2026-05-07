#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-oci-capacity-evidence-hardening.sh"

echo "[transaction-read-oci-capacity-evidence] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/oci-capacity.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
scenario	duration_min	source_ips	k6_ref	nginx_ref	spring_ref	hikari_ref	pg_wait_ref	cpu_ref	memory_ref	disk_io_ref	network_ref	prometheus_ref	grafana_ref	hikari_validation_warnings	connection_acquisition_p95_ms	edge_429_rate	backend_429_rate	unknown_429_count	five_xx_count	accepted_p95_ms	write_integrity_status	mixed_components	source_ip_breakdown_ref
hikari-10m	10	1	oci://hikari/k6.json	oci://hikari/nginx.jsonl	oci://hikari/spring.json	oci://hikari/hikari.log	oci://hikari/pg.tsv	oci://hikari/cpu.json	oci://hikari/memory.json	oci://hikari/disk.json	oci://hikari/network.json	oci://hikari/prometheus	oci://hikari/grafana	0	18	0.02	0.00	0	0	320	n/a	read	oci://hikari/source-ip.tsv
prometheus-30m	30	1	oci://timeline/k6.json	oci://timeline/nginx.jsonl	oci://timeline/spring.json	oci://timeline/hikari.log	oci://timeline/pg.tsv	oci://timeline/cpu.json	oci://timeline/memory.json	oci://timeline/disk.json	oci://timeline/network.json	oci://timeline/prometheus	oci://timeline/grafana	0	21	0.07	0.01	0	0	340	n/a	read	oci://timeline/source-ip.tsv
mixed-interference	30	1	oci://mixed/k6.json	oci://mixed/nginx.jsonl	oci://mixed/spring.json	oci://mixed/hikari.log	oci://mixed/pg.tsv	oci://mixed/cpu.json	oci://mixed/memory.json	oci://mixed/disk.json	oci://mixed/network.json	oci://mixed/prometheus	oci://mixed/grafana	0	22	0.09	0.01	0	0	345	pass	read,write,auth,notification,sse	oci://mixed/source-ip.tsv
TSV

echo "[transaction-read-oci-capacity-evidence] print plan"
plan="$(
  OCI_CAPACITY_EVIDENCE_NAME=oci-capacity-check \
  OCI_CAPACITY_EVIDENCE_INPUT_TSV="${input_tsv}" \
  OCI_CAPACITY_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "required_scenarios=hikari-10m,prometheus-30m,mixed-interference" <<<"${plan}" >/dev/null
grep -F "min_source_ips=1" <<<"${plan}" >/dev/null
grep -F "timeline_refs=k6,nginx,spring,hikari,pg_wait,cpu,memory,disk_io,network,prometheus,grafana" <<<"${plan}" >/dev/null

echo "[transaction-read-oci-capacity-evidence] pass report"
output="$(
  OCI_CAPACITY_EVIDENCE_NAME=oci-capacity-check \
  OCI_CAPACITY_EVIDENCE_INPUT_TSV="${input_tsv}" \
  OCI_CAPACITY_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/oci-capacity-check-oci-capacity-evidence.tsv"
test "${report_md}" = "${output_dir}/oci-capacity-check-oci-capacity-evidence.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "10m Hikari validation warning budget: 0" "${report_md}" >/dev/null
grep -F "source attribution requires source_ips >= 1 and source_ip_breakdown_ref" "${report_md}" >/dev/null
grep -F "30m Prometheus/Grafana timeline includes CPU, memory, disk IO, network, Hikari, pg wait" "${report_md}" >/dev/null
grep -F "mixed workload requires read, write, auth, notification, sse and write_integrity_status=pass" "${report_md}" >/dev/null
grep -F $'scenario\tstatus\tduration_min\tsource_ips\thikari_validation_warnings\tconnection_acquisition_p95_ms\tunknown_429_count\tfive_xx_count\taccepted_p95_ms\twrite_integrity_status\tmissing_ref' "${summary_tsv}" >/dev/null
grep -F $'mixed-interference\tpass\t30\t1\t0\t22\t0\t0\t345\tpass\tnone' "${summary_tsv}" >/dev/null

echo "[transaction-read-oci-capacity-evidence] Hikari warning fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "hikari-10m" { $15 = "1" } { print }' \
  "${input_tsv}" >"${input_tsv}.hikari-fail"
if OCI_CAPACITY_EVIDENCE_NAME=oci-hikari-fail \
  OCI_CAPACITY_EVIDENCE_INPUT_TSV="${input_tsv}.hikari-fail" \
  OCI_CAPACITY_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "OCI capacity evidence unexpectedly passed Hikari warning" >&2
  exit 1
fi

echo "[transaction-read-oci-capacity-evidence] mixed write integrity fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "mixed-interference" { $22 = "fail" } { print }' \
  "${input_tsv}" >"${input_tsv}.mixed-fail"
if OCI_CAPACITY_EVIDENCE_NAME=oci-mixed-fail \
  OCI_CAPACITY_EVIDENCE_INPUT_TSV="${input_tsv}.mixed-fail" \
  OCI_CAPACITY_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "OCI capacity evidence unexpectedly passed mixed write integrity failure" >&2
  exit 1
fi
