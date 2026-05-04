#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-oci-offhost-host-metrics-timeline-fallback.sh"

echo "[oci-offhost-host-metrics-timeline-fallback] shell syntax"
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
offhost-fallback-run	generator	k6-generator-a	ocid1.instance.oc1..generatora	vm-k6-a	subnet-generator-a	oci-k6-generator-a	31.2	10.5	12.1	artifact://offhost/generator.tsv	60	44.1	18.2	19.7
TSV

cat >"${target_tsv}" <<'TSV'
run_id	host_role	host_name	host_id	vm_id	network_id	docker_context	cpu_pct	rx_mbps	tx_mbps	artifact_uri	sample_count	cpu_pct_max	rx_mbps_max	tx_mbps_max
offhost-fallback-run	target	oci-a1-staging	ocid1.instance.oc1..target	vm-target	subnet-target	target	52.4	21.1	25.6	artifact://offhost/target.tsv	60	66.8	30.4	34.2
TSV

echo "[oci-offhost-host-metrics-timeline-fallback] print plan"
plan="$(
  OCI_OFFHOST_HOST_METRICS_TIMELINE_FALLBACK_NAME=offhost-fallback-check \
  OCI_OFFHOST_HOST_METRICS_RUN_ID=offhost-fallback-run \
  OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV="${generator_tsv}" \
  OCI_OFFHOST_TARGET_HOST_METRICS_TSV="${target_tsv}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=offhost-fallback-check" <<<"${plan}" >/dev/null
grep -F "run_id=offhost-fallback-run" <<<"${plan}" >/dev/null
grep -F "required_phases=arrival16,vu16,burst-matrix" <<<"${plan}" >/dev/null
grep -F "timeline_tsv=${output_dir}/offhost-fallback-check-host-metrics-timeline.tsv" <<<"${plan}" >/dev/null

echo "[oci-offhost-host-metrics-timeline-fallback] pass report"
output="$(
  OCI_OFFHOST_HOST_METRICS_TIMELINE_FALLBACK_NAME=offhost-fallback-check \
  OCI_OFFHOST_HOST_METRICS_RUN_ID=offhost-fallback-run \
  OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV="${generator_tsv}" \
  OCI_OFFHOST_TARGET_HOST_METRICS_TSV="${target_tsv}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR="${output_dir}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_SAMPLE_STARTED_AT_UTC=2026-05-04T04:00:00Z \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_SAMPLE_ENDED_AT_UTC=2026-05-04T04:01:00Z \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
timeline_tsv="${output_dir}/offhost-fallback-check-host-metrics-timeline.tsv"
timeline_json="${output_dir}/offhost-fallback-check-host-metrics-timeline.json"
input_tsv="${output_dir}/offhost-fallback-check-host-metrics-timeline.input.tsv"
test "${report_md}" = "${output_dir}/offhost-fallback-check-host-metrics-timeline.md"
test -s "${input_tsv}"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "generator/target timeline: verified" "${report_md}" >/dev/null
grep -F "artifact pack URI: artifact://offhost-capacity-prerequisite/offhost-fallback-check" "${report_md}" >/dev/null
grep -F $'offhost-fallback-run\tarrival16\tgenerator\tk6-generator-a\tocid1.instance.oc1..generatora\tvm-k6-a\tsubnet-generator-a\toci-k6-generator-a\t2026-05-04T04:00:00Z\t2026-05-04T04:01:00Z\t60\t31.2\t44.1\t10.5\t18.2\t12.1\t19.7\tartifact://offhost/generator.tsv#arrival16\tartifact://offhost-capacity-prerequisite/offhost-fallback-check/arrival16/summary\tartifact://offhost-capacity-prerequisite/offhost-fallback-check' "${timeline_tsv}" >/dev/null
grep -F $'offhost-fallback-run\tburst-matrix\ttarget\toci-a1-staging\tocid1.instance.oc1..target\tvm-target\tsubnet-target\ttarget\t2026-05-04T04:00:00Z\t2026-05-04T04:01:00Z\t60\t52.4\t66.8\t21.1\t30.4\t25.6\t34.2\tartifact://offhost/target.tsv#burst-matrix\tartifact://offhost-capacity-prerequisite/offhost-fallback-check/burst-matrix/summary\tartifact://offhost-capacity-prerequisite/offhost-fallback-check' "${timeline_tsv}" >/dev/null
jq -e '.summary.gate_status == "pass" and .summary.phase_count == 3 and (.items | length == 6)' "${timeline_json}" >/dev/null

echo "[oci-offhost-host-metrics-timeline-fallback] missing target fails"
if OCI_OFFHOST_HOST_METRICS_TIMELINE_FALLBACK_NAME=offhost-fallback-missing-target \
  OCI_OFFHOST_HOST_METRICS_RUN_ID=offhost-fallback-run \
  OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV="${generator_tsv}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR="${temp_dir}/missing-target-output" \
    "${runner}" >"${temp_dir}/missing-target.log" 2>&1; then
  echo "off-host fallback timeline unexpectedly passed without target metrics" >&2
  exit 1
fi
grep -F "OCI_OFFHOST_TARGET_HOST_METRICS_TSV is required" "${temp_dir}/missing-target.log" >/dev/null
