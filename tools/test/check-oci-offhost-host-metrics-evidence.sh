#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-oci-offhost-host-metrics-evidence.sh"

echo "[oci-offhost-host-metrics-evidence] shell syntax"
bash -n "${runner}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

generator_tsv="${temp_dir}/generator-host-metrics.tsv"
target_tsv="${temp_dir}/target-host-metrics.tsv"
output_dir="${temp_dir}/output"

cat >"${generator_tsv}" <<'TSV'
run_id	host_role	host_name	host_id	vm_id	network_id	docker_context	cpu_pct	rx_mbps	tx_mbps	artifact_uri	sample_count	cpu_pct_max	rx_mbps_max	tx_mbps_max
offhost-run-20260503	generator	k6-generator-a	ocid1.instance.oc1..generatora	vm-k6-a	subnet-generator-a	oci-k6-generator-a	41.2	18.5	21.1	oci://aquila-evidence/transaction-read/offhost-run-20260503/host/k6-generator-a	18	55.4	24.2	30.7
offhost-run-20260503	generator	k6-generator-b	ocid1.instance.oc1..generatorb	vm-k6-b	subnet-generator-b	oci-k6-generator-b	39.8	17.9	20.4	oci://aquila-evidence/transaction-read/offhost-run-20260503/host/k6-generator-b	18	52.1	23.8	29.9
TSV

cat >"${target_tsv}" <<'TSV'
run_id	host_role	host_name	host_id	vm_id	network_id	docker_context	cpu_pct	rx_mbps	tx_mbps	artifact_uri	sample_count	cpu_pct_max	rx_mbps_max	tx_mbps_max
offhost-run-20260503	target	oci-a1-staging	ocid1.instance.oc1..target	vm-target	subnet-target	target	63.5	38.2	44.6	oci://aquila-evidence/transaction-read/offhost-run-20260503/host/target	18	71.0	45.2	51.8
TSV

echo "[oci-offhost-host-metrics-evidence] print plan"
plan="$(
  OCI_OFFHOST_HOST_METRICS_NAME=offhost-host-check \
  OCI_OFFHOST_HOST_METRICS_RUN_ID=offhost-run-20260503 \
  OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV="${generator_tsv}" \
  OCI_OFFHOST_TARGET_HOST_METRICS_TSV="${target_tsv}" \
  OCI_OFFHOST_HOST_METRICS_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=offhost-host-check" <<<"${plan}" >/dev/null
grep -F "run_id=offhost-run-20260503" <<<"${plan}" >/dev/null
grep -F "generator_host_metrics_tsv=${generator_tsv}" <<<"${plan}" >/dev/null
grep -F "target_host_metrics_tsv=${target_tsv}" <<<"${plan}" >/dev/null
grep -F "required_columns=run_id,host_role,host_name,host_id,vm_id,network_id,docker_context,cpu_pct,rx_mbps,tx_mbps,artifact_uri" <<<"${plan}" >/dev/null
grep -F "combined_tsv=${output_dir}/offhost-host-check-host-metrics.tsv" <<<"${plan}" >/dev/null

echo "[oci-offhost-host-metrics-evidence] pass report"
output="$(
  OCI_OFFHOST_HOST_METRICS_NAME=offhost-host-check \
  OCI_OFFHOST_HOST_METRICS_RUN_ID=offhost-run-20260503 \
  OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV="${generator_tsv}" \
  OCI_OFFHOST_TARGET_HOST_METRICS_TSV="${target_tsv}" \
  OCI_OFFHOST_HOST_METRICS_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
combined_tsv="${output_dir}/offhost-host-check-host-metrics.tsv"
combined_json="${output_dir}/offhost-host-check-host-metrics.json"
test "${report_md}" = "${output_dir}/offhost-host-check-host-metrics.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "generator host metrics: verified" "${report_md}" >/dev/null
grep -F "target host metrics: verified" "${report_md}" >/dev/null
grep -F "host identity separation: verified" "${report_md}" >/dev/null
grep -F "VM/network separation: verified" "${report_md}" >/dev/null
grep -F "combined host metrics TSV: ${combined_tsv}" "${report_md}" >/dev/null
grep -F $'run_id\thost_role\thost_name\thost_id\tvm_id\tnetwork_id\tdocker_context\tcpu_pct\trx_mbps\ttx_mbps\tartifact_uri\tsource_file' "${combined_tsv}" >/dev/null
grep -F $'offhost-run-20260503\tgenerator\tk6-generator-a\tocid1.instance.oc1..generatora\tvm-k6-a\tsubnet-generator-a\toci-k6-generator-a\t41.2\t18.5\t21.1\toci://aquila-evidence/transaction-read/offhost-run-20260503/host/k6-generator-a\t' "${combined_tsv}" >/dev/null
grep -F $'offhost-run-20260503\ttarget\toci-a1-staging\tocid1.instance.oc1..target\tvm-target\tsubnet-target\ttarget\t63.5\t38.2\t44.6\toci://aquila-evidence/transaction-read/offhost-run-20260503/host/target\t' "${combined_tsv}" >/dev/null
jq -e '.items | length == 3' "${combined_json}" >/dev/null
jq -e '.items[] | select(.host_role == "generator" and .host_name == "k6-generator-b" and .cpu_pct == 39.8)' "${combined_json}" >/dev/null
jq -e '.summary.generator_count == 2 and .summary.target_count == 1 and .summary.gate_status == "pass"' "${combined_json}" >/dev/null

echo "[oci-offhost-host-metrics-evidence] missing target fails"
if OCI_OFFHOST_HOST_METRICS_NAME=offhost-host-missing-target \
  OCI_OFFHOST_HOST_METRICS_RUN_ID=offhost-run-20260503 \
  OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV="${generator_tsv}" \
  OCI_OFFHOST_HOST_METRICS_OUTPUT_DIR="${temp_dir}/missing-output" \
    "${runner}" >"${temp_dir}/missing-target.log" 2>&1; then
  echo "off-host host metrics evidence unexpectedly passed without target metrics" >&2
  exit 1
fi
grep -F "OCI_OFFHOST_TARGET_HOST_METRICS_TSV is required" "${temp_dir}/missing-target.log" >/dev/null

echo "[oci-offhost-host-metrics-evidence] missing generator row fails"
generator_without_rows="${temp_dir}/generator-without-rows.tsv"
head -n 1 "${generator_tsv}" >"${generator_without_rows}"
if OCI_OFFHOST_HOST_METRICS_NAME=offhost-host-missing-generator-row \
  OCI_OFFHOST_HOST_METRICS_RUN_ID=offhost-run-20260503 \
  OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV="${generator_without_rows}" \
  OCI_OFFHOST_TARGET_HOST_METRICS_TSV="${target_tsv}" \
  OCI_OFFHOST_HOST_METRICS_OUTPUT_DIR="${temp_dir}/missing-generator-row-output" \
    "${runner}" >"${temp_dir}/missing-generator-row.log" 2>&1; then
  echo "off-host host metrics evidence unexpectedly passed without generator rows" >&2
  exit 1
fi
grep -F "generator host metrics row is required" "${temp_dir}/missing-generator-row.log" >/dev/null

echo "[oci-offhost-host-metrics-evidence] run id mismatch fails"
bad_run_id_tsv="${temp_dir}/bad-run-id.tsv"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $1 = "other-run"; print }' "${target_tsv}" >"${bad_run_id_tsv}"
if OCI_OFFHOST_HOST_METRICS_NAME=offhost-host-run-id-mismatch \
  OCI_OFFHOST_HOST_METRICS_RUN_ID=offhost-run-20260503 \
  OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV="${generator_tsv}" \
  OCI_OFFHOST_TARGET_HOST_METRICS_TSV="${bad_run_id_tsv}" \
  OCI_OFFHOST_HOST_METRICS_OUTPUT_DIR="${temp_dir}/run-id-mismatch-output" \
    "${runner}" >"${temp_dir}/run-id-mismatch.log" 2>&1; then
  echo "off-host host metrics evidence unexpectedly passed mismatched run id" >&2
  exit 1
fi
grep -F "host metric run id mismatch" "${temp_dir}/run-id-mismatch.log" >/dev/null

echo "[oci-offhost-host-metrics-evidence] negative metric fails"
bad_metric_tsv="${temp_dir}/bad-metric.tsv"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $8 = "-1"; print }' "${generator_tsv}" >"${bad_metric_tsv}"
if OCI_OFFHOST_HOST_METRICS_NAME=offhost-host-bad-metric \
  OCI_OFFHOST_HOST_METRICS_RUN_ID=offhost-run-20260503 \
  OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV="${bad_metric_tsv}" \
  OCI_OFFHOST_TARGET_HOST_METRICS_TSV="${target_tsv}" \
  OCI_OFFHOST_HOST_METRICS_OUTPUT_DIR="${temp_dir}/bad-metric-output" \
    "${runner}" >"${temp_dir}/bad-metric.log" 2>&1; then
  echo "off-host host metrics evidence unexpectedly passed negative metric" >&2
  exit 1
fi
grep -F "host metric cpu/network values must be non-negative numbers" "${temp_dir}/bad-metric.log" >/dev/null

echo "[oci-offhost-host-metrics-evidence] shared host identity fails"
shared_target_tsv="${temp_dir}/shared-target.tsv"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $3 = "k6-generator-a"; $4 = "ocid1.instance.oc1..generatora"; $5 = "vm-k6-a"; $6 = "subnet-generator-a"; print }' \
  "${target_tsv}" >"${shared_target_tsv}"
if OCI_OFFHOST_HOST_METRICS_NAME=offhost-host-shared-identity \
  OCI_OFFHOST_HOST_METRICS_RUN_ID=offhost-run-20260503 \
  OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV="${generator_tsv}" \
  OCI_OFFHOST_TARGET_HOST_METRICS_TSV="${shared_target_tsv}" \
  OCI_OFFHOST_HOST_METRICS_OUTPUT_DIR="${temp_dir}/shared-identity-output" \
    "${runner}" >"${temp_dir}/shared-identity.log" 2>&1; then
  echo "off-host host metrics evidence unexpectedly passed shared generator/target identity" >&2
  exit 1
fi
grep -F "generator and target host/VM/network identity must differ" "${temp_dir}/shared-identity.log" >/dev/null
