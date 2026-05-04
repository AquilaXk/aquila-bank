#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-oci-offhost-host-metrics-load-coupled-timeline.sh"

echo "[oci-offhost-host-metrics-load-coupled-timeline] shell syntax"
bash -n "${runner}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

output_dir="${temp_dir}/output"

echo "[oci-offhost-host-metrics-load-coupled-timeline] print plan"
plan="$(
  OCI_OFFHOST_LOAD_COUPLED_TIMELINE_NAME=offhost-load-coupled-check \
  OCI_OFFHOST_LOAD_COUPLED_TIMELINE_RUN_ID=offhost-load-coupled-run \
  OCI_OFFHOST_LOAD_COUPLED_ARRIVAL16_COMMAND="sleep 1" \
  OCI_OFFHOST_LOAD_COUPLED_VU16_COMMAND="sleep 1" \
  OCI_OFFHOST_LOAD_COUPLED_BURST_MATRIX_COMMAND="sleep 1" \
  OCI_OFFHOST_LOAD_COUPLED_SAMPLE_INTERVAL_SECONDS=1 \
  OCI_OFFHOST_LOAD_COUPLED_SAMPLE_MODE=local \
  OCI_OFFHOST_LOAD_COUPLED_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=offhost-load-coupled-check" <<<"${plan}" >/dev/null
grep -F "run_id=offhost-load-coupled-run" <<<"${plan}" >/dev/null
grep -F "required_phases=arrival16,vu16,burst-matrix" <<<"${plan}" >/dev/null
grep -F "sample_interval_seconds=1" <<<"${plan}" >/dev/null
grep -F "timeline_tsv=${output_dir}/offhost-load-coupled-check-host-metrics-timeline.tsv" <<<"${plan}" >/dev/null

echo "[oci-offhost-host-metrics-load-coupled-timeline] pass report"
output="$(
  OCI_OFFHOST_LOAD_COUPLED_TIMELINE_NAME=offhost-load-coupled-check \
  OCI_OFFHOST_LOAD_COUPLED_TIMELINE_RUN_ID=offhost-load-coupled-run \
  OCI_OFFHOST_LOAD_COUPLED_ARRIVAL16_COMMAND="sleep 1" \
  OCI_OFFHOST_LOAD_COUPLED_VU16_COMMAND="sleep 1" \
  OCI_OFFHOST_LOAD_COUPLED_BURST_MATRIX_COMMAND="sleep 1" \
  OCI_OFFHOST_LOAD_COUPLED_SAMPLE_INTERVAL_SECONDS=1 \
  OCI_OFFHOST_LOAD_COUPLED_SAMPLE_MODE=local \
  OCI_OFFHOST_LOAD_COUPLED_GENERATOR_HOST_NAME=k6-generator-a \
  OCI_OFFHOST_LOAD_COUPLED_GENERATOR_HOST_ID=ocid1.instance.oc1..generatora \
  OCI_OFFHOST_LOAD_COUPLED_GENERATOR_VM_ID=vm-k6-a \
  OCI_OFFHOST_LOAD_COUPLED_GENERATOR_NETWORK_ID=subnet-generator-a \
  OCI_OFFHOST_LOAD_COUPLED_GENERATOR_DOCKER_CONTEXT=oci-k6-generator-a \
  OCI_OFFHOST_LOAD_COUPLED_TARGET_HOST_NAME=oci-a1-staging \
  OCI_OFFHOST_LOAD_COUPLED_TARGET_HOST_ID=ocid1.instance.oc1..target \
  OCI_OFFHOST_LOAD_COUPLED_TARGET_VM_ID=vm-target \
  OCI_OFFHOST_LOAD_COUPLED_TARGET_NETWORK_ID=subnet-target \
  OCI_OFFHOST_LOAD_COUPLED_TARGET_DOCKER_CONTEXT=target \
  OCI_OFFHOST_LOAD_COUPLED_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
timeline_tsv="${output_dir}/offhost-load-coupled-check-host-metrics-timeline.tsv"
timeline_json="${output_dir}/offhost-load-coupled-check-host-metrics-timeline.json"
test "${report_md}" = "${output_dir}/offhost-load-coupled-check-host-metrics-timeline.md"
test -s "${timeline_tsv}"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "load-coupled samples: generated" "${report_md}" >/dev/null
grep -F "sample interval seconds: 1" "${report_md}" >/dev/null
grep -F $'offhost-load-coupled-run\tarrival16\tgenerator\tk6-generator-a\tocid1.instance.oc1..generatora\tvm-k6-a\tsubnet-generator-a\toci-k6-generator-a' "${timeline_tsv}" >/dev/null
grep -F $'offhost-load-coupled-run\tburst-matrix\ttarget\toci-a1-staging\tocid1.instance.oc1..target\tvm-target\tsubnet-target\ttarget' "${timeline_tsv}" >/dev/null
jq -e '.summary.gate_status == "pass" and .summary.phase_count == 3 and (.items | length == 6)' "${timeline_json}" >/dev/null
jq -e '.items[] | select(.phase == "vu16" and .host_role == "target" and .sample_source == "load-coupled" and .sample_interval_seconds == 1)' "${timeline_json}" >/dev/null

echo "[oci-offhost-host-metrics-load-coupled-timeline] missing command fails"
if OCI_OFFHOST_LOAD_COUPLED_TIMELINE_NAME=offhost-load-coupled-missing \
  OCI_OFFHOST_LOAD_COUPLED_TIMELINE_RUN_ID=offhost-load-coupled-run \
  OCI_OFFHOST_LOAD_COUPLED_ARRIVAL16_COMMAND="sleep 1" \
  OCI_OFFHOST_LOAD_COUPLED_VU16_COMMAND="sleep 1" \
  OCI_OFFHOST_LOAD_COUPLED_OUTPUT_DIR="${temp_dir}/missing-output" \
    "${runner}" >"${temp_dir}/missing.log" 2>&1; then
  echo "off-host load-coupled timeline unexpectedly passed without burst-matrix command" >&2
  exit 1
fi
grep -F "OCI_OFFHOST_LOAD_COUPLED_BURST_MATRIX_COMMAND is required" "${temp_dir}/missing.log" >/dev/null

echo "[oci-offhost-host-metrics-load-coupled-timeline] shared identity fails"
if OCI_OFFHOST_LOAD_COUPLED_TIMELINE_NAME=offhost-load-coupled-shared \
  OCI_OFFHOST_LOAD_COUPLED_TIMELINE_RUN_ID=offhost-load-coupled-run \
  OCI_OFFHOST_LOAD_COUPLED_ARRIVAL16_COMMAND="sleep 1" \
  OCI_OFFHOST_LOAD_COUPLED_VU16_COMMAND="sleep 1" \
  OCI_OFFHOST_LOAD_COUPLED_BURST_MATRIX_COMMAND="sleep 1" \
  OCI_OFFHOST_LOAD_COUPLED_SAMPLE_INTERVAL_SECONDS=1 \
  OCI_OFFHOST_LOAD_COUPLED_SAMPLE_MODE=local \
  OCI_OFFHOST_LOAD_COUPLED_GENERATOR_HOST_NAME=same-host \
  OCI_OFFHOST_LOAD_COUPLED_TARGET_HOST_NAME=same-host \
  OCI_OFFHOST_LOAD_COUPLED_OUTPUT_DIR="${temp_dir}/shared-output" \
    "${runner}" >"${temp_dir}/shared.log" 2>&1; then
  echo "off-host load-coupled timeline unexpectedly passed shared host identity" >&2
  exit 1
fi
grep -F "generator and target host identity must differ" "${temp_dir}/shared.log" >/dev/null
