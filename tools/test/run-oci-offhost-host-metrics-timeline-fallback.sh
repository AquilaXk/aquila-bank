#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-oci-offhost-host-metrics-timeline-fallback.sh [--print-plan]

Environment:
  OCI_OFFHOST_HOST_METRICS_TIMELINE_FALLBACK_NAME default oci-offhost-host-metrics-timeline-fallback-<timestamp>
  OCI_OFFHOST_HOST_METRICS_RUN_ID                 default same as name
  OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV          required generator host metrics snapshot TSV
  OCI_OFFHOST_TARGET_HOST_METRICS_TSV             required target host metrics snapshot TSV
  OCI_OFFHOST_HOST_METRICS_TIMELINE_REQUIRED_PHASES default arrival16,vu16,burst-matrix
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR    default build/reports/k6/<name>/offhost-host-metrics-timeline
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

name="${OCI_OFFHOST_HOST_METRICS_TIMELINE_FALLBACK_NAME:-oci-offhost-host-metrics-timeline-fallback-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${OCI_OFFHOST_HOST_METRICS_RUN_ID:-${name}}"
generator_tsv="${OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV:-}"
target_tsv="${OCI_OFFHOST_TARGET_HOST_METRICS_TSV:-}"
required_phases="${OCI_OFFHOST_HOST_METRICS_TIMELINE_REQUIRED_PHASES:-arrival16,vu16,burst-matrix}"
output_dir="${OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR:-build/reports/k6/${name}/offhost-host-metrics-timeline}"
sample_started_at="${OCI_OFFHOST_HOST_METRICS_TIMELINE_SAMPLE_STARTED_AT_UTC:-$(date -u +"%Y-%m-%dT%H:%M:%SZ")}"
sample_ended_at="${OCI_OFFHOST_HOST_METRICS_TIMELINE_SAMPLE_ENDED_AT_UTC:-${sample_started_at}}"
artifact_pack_uri="${OCI_OFFHOST_HOST_METRICS_TIMELINE_ARTIFACT_PACK_URI:-artifact://offhost-capacity-prerequisite/${name}}"
fallback_input_tsv="${output_dir}/${name}-host-metrics-timeline.input.tsv"
timeline_tsv="${output_dir}/${name}-host-metrics-timeline.tsv"
timeline_json="${output_dir}/${name}-host-metrics-timeline.json"
report_md="${output_dir}/${name}-host-metrics-timeline.md"

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[oci-offhost-host-metrics-timeline-fallback] name=${name}"
  echo "[oci-offhost-host-metrics-timeline-fallback] run_id=${run_id}"
  echo "[oci-offhost-host-metrics-timeline-fallback] generator_host_metrics_tsv=${generator_tsv:-missing}"
  echo "[oci-offhost-host-metrics-timeline-fallback] target_host_metrics_tsv=${target_tsv:-missing}"
  echo "[oci-offhost-host-metrics-timeline-fallback] required_phases=${required_phases}"
  echo "[oci-offhost-host-metrics-timeline-fallback] artifact_pack_uri=${artifact_pack_uri}"
  echo "[oci-offhost-host-metrics-timeline-fallback] output_dir=${output_dir}"
  echo "[oci-offhost-host-metrics-timeline-fallback] fallback_input_tsv=${fallback_input_tsv}"
  echo "[oci-offhost-host-metrics-timeline-fallback] timeline_tsv=${timeline_tsv}"
  echo "[oci-offhost-host-metrics-timeline-fallback] timeline_json=${timeline_json}"
  echo "[oci-offhost-host-metrics-timeline-fallback] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV" "${generator_tsv}"
  require_file "OCI_OFFHOST_TARGET_HOST_METRICS_TSV" "${target_tsv}"
  exit 0
fi

require_file "OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV" "${generator_tsv}"
require_file "OCI_OFFHOST_TARGET_HOST_METRICS_TSV" "${target_tsv}"

mkdir -p "${output_dir}"

awk -F '\t' \
  -v OFS='\t' \
  -v expected_run_id="${run_id}" \
  -v required_phases="${required_phases}" \
  -v sample_started_at="${sample_started_at}" \
  -v sample_ended_at="${sample_ended_at}" \
  -v artifact_pack_uri="${artifact_pack_uri}" '
  function fail(message, code) {
    print message > "/dev/stderr"
    exit code
  }
  function required_value(name) {
    value = row[col[name]]
    if (value == "") fail("missing host metrics fallback field: " name, 2)
    return value
  }
  function optional_value(name, fallback) {
    if (!(name in col) || row[col[name]] == "") return fallback
    return row[col[name]]
  }
  BEGIN {
    print "run_id", "phase", "host_role", "host_name", "host_id", "vm_id", "network_id", "docker_context", "sample_started_at_utc", "sample_ended_at_utc", "sample_count", "sample_source", "sample_interval_seconds", "cpu_pct_avg", "cpu_pct_max", "rx_mbps_avg", "rx_mbps_max", "tx_mbps_avg", "tx_mbps_max", "artifact_uri", "summary_ref", "artifact_pack_uri"
    split(required_phases, phase_items, ",")
  }
  FNR == 1 {
    delete col
    for (i = 1; i <= NF; i++) col[$i] = i
    split("run_id host_role host_name host_id vm_id network_id docker_context cpu_pct rx_mbps tx_mbps artifact_uri", required, " ")
    for (i in required) {
      if (!(required[i] in col)) fail("missing required fallback source column: " required[i], 3)
    }
    next
  }
  {
    split($0, row, FS)
    source_run_id = required_value("run_id")
    role = required_value("host_role")
    if (source_run_id != expected_run_id) {
      fail("host metrics fallback run id mismatch: run_id=" source_run_id " expected=" expected_run_id, 4)
    }
    if (role != "generator" && role != "target") {
      fail("host metrics fallback role must be generator or target: " role, 5)
    }
    host_name = required_value("host_name")
    host_id = required_value("host_id")
    vm_id = required_value("vm_id")
    network_id = required_value("network_id")
    docker_context = required_value("docker_context")
    cpu_pct = required_value("cpu_pct")
    rx_mbps = required_value("rx_mbps")
    tx_mbps = required_value("tx_mbps")
    artifact_uri = required_value("artifact_uri")
    sample_count = optional_value("sample_count", "1")
    cpu_pct_max = optional_value("cpu_pct_max", cpu_pct)
    rx_mbps_max = optional_value("rx_mbps_max", rx_mbps)
    tx_mbps_max = optional_value("tx_mbps_max", tx_mbps)
    for (i in phase_items) {
      phase = phase_items[i]
      summary_ref = artifact_pack_uri "/" phase "/summary"
      print expected_run_id, phase, role, host_name, host_id, vm_id, network_id, docker_context, sample_started_at, sample_ended_at, sample_count, "fallback-snapshot", "0", cpu_pct, cpu_pct_max, rx_mbps, rx_mbps_max, tx_mbps, tx_mbps_max, artifact_uri "#" phase, summary_ref, artifact_pack_uri
    }
  }
' "${generator_tsv}" "${target_tsv}" >"${fallback_input_tsv}"

OCI_OFFHOST_HOST_METRICS_TIMELINE_NAME="${name}" \
OCI_OFFHOST_HOST_METRICS_TIMELINE_RUN_ID="${run_id}" \
OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV="${fallback_input_tsv}" \
OCI_OFFHOST_HOST_METRICS_TIMELINE_REQUIRED_PHASES="${required_phases}" \
OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR="${output_dir}" \
  tools/test/run-oci-offhost-host-metrics-timeline.sh
