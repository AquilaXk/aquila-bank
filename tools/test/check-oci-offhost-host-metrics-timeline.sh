#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-oci-offhost-host-metrics-timeline.sh"

echo "[oci-offhost-host-metrics-timeline] shell syntax"
bash -n "${runner}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/host-metrics-timeline.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
run_id	phase	host_role	host_name	host_id	vm_id	network_id	docker_context	sample_started_at_utc	sample_ended_at_utc	sample_count	cpu_pct_avg	cpu_pct_max	rx_mbps_avg	rx_mbps_max	tx_mbps_avg	tx_mbps_max	artifact_uri	summary_ref	artifact_pack_uri
offhost-run-20260504	arrival16	generator	k6-generator-a	ocid1.instance.oc1..generatora	vm-k6-a	subnet-generator-a	oci-k6-generator-a	2026-05-04T01:00:00Z	2026-05-04T01:01:00Z	60	31.2	44.1	10.5	18.2	12.1	19.7	oci://aquila-evidence/transaction-read/offhost-run-20260504/arrival16/generator.tsv	build/reports/k6/offhost-run-20260504/arrival16-summary.json	oci://aquila-evidence/transaction-read/offhost-run-20260504
offhost-run-20260504	arrival16	target	oci-a1-staging	ocid1.instance.oc1..target	vm-target	subnet-target	target	2026-05-04T01:00:00Z	2026-05-04T01:01:00Z	60	52.4	66.8	21.1	30.4	25.6	34.2	oci://aquila-evidence/transaction-read/offhost-run-20260504/arrival16/target.tsv	build/reports/k6/offhost-run-20260504/arrival16-summary.json	oci://aquila-evidence/transaction-read/offhost-run-20260504
offhost-run-20260504	vu16	generator	k6-generator-a	ocid1.instance.oc1..generatora	vm-k6-a	subnet-generator-a	oci-k6-generator-a	2026-05-04T01:05:00Z	2026-05-04T01:06:00Z	60	34.8	49.0	12.0	20.0	15.1	23.4	oci://aquila-evidence/transaction-read/offhost-run-20260504/vu16/generator.tsv	build/reports/k6/offhost-run-20260504/vu16-summary.json	oci://aquila-evidence/transaction-read/offhost-run-20260504
offhost-run-20260504	vu16	target	oci-a1-staging	ocid1.instance.oc1..target	vm-target	subnet-target	target	2026-05-04T01:05:00Z	2026-05-04T01:06:00Z	60	58.2	70.3	24.2	34.0	28.0	38.2	oci://aquila-evidence/transaction-read/offhost-run-20260504/vu16/target.tsv	build/reports/k6/offhost-run-20260504/vu16-summary.json	oci://aquila-evidence/transaction-read/offhost-run-20260504
offhost-run-20260504	burst-matrix	generator	k6-generator-a	ocid1.instance.oc1..generatora	vm-k6-a	subnet-generator-a	oci-k6-generator-a	2026-05-04T01:10:00Z	2026-05-04T01:15:00Z	300	43.1	59.3	18.7	31.2	22.4	36.8	oci://aquila-evidence/transaction-read/offhost-run-20260504/burst-matrix/generator.tsv	build/reports/k6/offhost-run-20260504/burst-matrix.tsv	oci://aquila-evidence/transaction-read/offhost-run-20260504
offhost-run-20260504	burst-matrix	target	oci-a1-staging	ocid1.instance.oc1..target	vm-target	subnet-target	target	2026-05-04T01:10:00Z	2026-05-04T01:15:00Z	300	63.7	76.2	35.4	51.8	40.0	59.0	oci://aquila-evidence/transaction-read/offhost-run-20260504/burst-matrix/target.tsv	build/reports/k6/offhost-run-20260504/burst-matrix.tsv	oci://aquila-evidence/transaction-read/offhost-run-20260504
TSV

echo "[oci-offhost-host-metrics-timeline] print plan"
plan="$(
  OCI_OFFHOST_HOST_METRICS_TIMELINE_NAME=offhost-timeline-check \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_RUN_ID=offhost-run-20260504 \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV="${input_tsv}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=offhost-timeline-check" <<<"${plan}" >/dev/null
grep -F "run_id=offhost-run-20260504" <<<"${plan}" >/dev/null
grep -F "required_phases=arrival16,vu16,burst-matrix" <<<"${plan}" >/dev/null
grep -F "input_tsv=${input_tsv}" <<<"${plan}" >/dev/null
grep -F "timeline_tsv=${output_dir}/offhost-timeline-check-host-metrics-timeline.tsv" <<<"${plan}" >/dev/null

echo "[oci-offhost-host-metrics-timeline] pass report"
output="$(
  OCI_OFFHOST_HOST_METRICS_TIMELINE_NAME=offhost-timeline-check \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_RUN_ID=offhost-run-20260504 \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV="${input_tsv}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
timeline_tsv="${output_dir}/offhost-timeline-check-host-metrics-timeline.tsv"
timeline_json="${output_dir}/offhost-timeline-check-host-metrics-timeline.json"
test "${report_md}" = "${output_dir}/offhost-timeline-check-host-metrics-timeline.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "required phases: arrival16,vu16,burst-matrix" "${report_md}" >/dev/null
grep -F "generator/target timeline: verified" "${report_md}" >/dev/null
grep -F "artifact pack URI: oci://aquila-evidence/transaction-read/offhost-run-20260504" "${report_md}" >/dev/null
grep -F $'run_id\tphase\thost_role\thost_name\thost_id\tvm_id\tnetwork_id\tdocker_context' "${timeline_tsv}" >/dev/null
jq -e '.summary.gate_status == "pass" and .summary.phase_count == 3 and (.items | length == 6)' "${timeline_json}" >/dev/null
jq -e '.items[] | select(.phase == "burst-matrix" and .host_role == "target" and .cpu_pct_max == 76.2)' "${timeline_json}" >/dev/null

echo "[oci-offhost-host-metrics-timeline] missing phase fails"
awk -F '\t' 'NR == 1 || $2 != "burst-matrix"' "${input_tsv}" >"${input_tsv}.missing-phase"
if OCI_OFFHOST_HOST_METRICS_TIMELINE_NAME=offhost-timeline-missing-phase \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_RUN_ID=offhost-run-20260504 \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV="${input_tsv}.missing-phase" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR="${temp_dir}/missing-phase-output" \
    "${runner}" >"${temp_dir}/missing-phase.log" 2>&1; then
  echo "off-host host metrics timeline unexpectedly passed missing burst-matrix phase" >&2
  exit 1
fi
grep -F "missing required host metrics timeline phase: burst-matrix" "${temp_dir}/missing-phase.log" >/dev/null

echo "[oci-offhost-host-metrics-timeline] missing target role fails"
awk -F '\t' 'NR == 1 || !($2 == "vu16" && $3 == "target")' "${input_tsv}" >"${input_tsv}.missing-target"
if OCI_OFFHOST_HOST_METRICS_TIMELINE_NAME=offhost-timeline-missing-target \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_RUN_ID=offhost-run-20260504 \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV="${input_tsv}.missing-target" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR="${temp_dir}/missing-target-output" \
    "${runner}" >"${temp_dir}/missing-target.log" 2>&1; then
  echo "off-host host metrics timeline unexpectedly passed missing vu16 target" >&2
  exit 1
fi
grep -F "missing target timeline row for phase: vu16" "${temp_dir}/missing-target.log" >/dev/null

echo "[oci-offhost-host-metrics-timeline] shared identity fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $3 == "target" { $4 = "k6-generator-a"; $5 = "ocid1.instance.oc1..generatora"; $6 = "vm-k6-a"; $7 = "subnet-generator-a" } { print }' \
  "${input_tsv}" >"${input_tsv}.shared"
if OCI_OFFHOST_HOST_METRICS_TIMELINE_NAME=offhost-timeline-shared \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_RUN_ID=offhost-run-20260504 \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV="${input_tsv}.shared" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR="${temp_dir}/shared-output" \
    "${runner}" >"${temp_dir}/shared.log" 2>&1; then
  echo "off-host host metrics timeline unexpectedly passed shared identity" >&2
  exit 1
fi
grep -F "generator and target host/VM/network identity must differ" "${temp_dir}/shared.log" >/dev/null

echo "[oci-offhost-host-metrics-timeline] missing summary ref fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $2 == "arrival16" && $3 == "generator" { $19 = "n/a" } { print }' \
  "${input_tsv}" >"${input_tsv}.missing-summary"
if OCI_OFFHOST_HOST_METRICS_TIMELINE_NAME=offhost-timeline-missing-summary \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_RUN_ID=offhost-run-20260504 \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV="${input_tsv}.missing-summary" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR="${temp_dir}/missing-summary-output" \
    "${runner}" >"${temp_dir}/missing-summary.log" 2>&1; then
  echo "off-host host metrics timeline unexpectedly passed missing summary ref" >&2
  exit 1
fi
grep -F "host metrics timeline requires summary/artifact pack refs" "${temp_dir}/missing-summary.log" >/dev/null
