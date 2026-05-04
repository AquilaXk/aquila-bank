#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-oci-offhost-host-metrics-timeline.sh [--print-plan]

Environment:
  OCI_OFFHOST_HOST_METRICS_TIMELINE_NAME            default oci-offhost-host-metrics-timeline-<timestamp>
  OCI_OFFHOST_HOST_METRICS_TIMELINE_RUN_ID          default same as name
  OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV       required host metrics timeline TSV
  OCI_OFFHOST_HOST_METRICS_TIMELINE_REQUIRED_PHASES default arrival16,vu16,burst-matrix
  OCI_OFFHOST_HOST_METRICS_TIMELINE_MAX_INTERVAL_SECONDS default 5
  OCI_OFFHOST_HOST_METRICS_TIMELINE_REQUIRE_LOAD_COUPLED default false
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR      default build/reports/k6/<name>
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

name="${OCI_OFFHOST_HOST_METRICS_TIMELINE_NAME:-oci-offhost-host-metrics-timeline-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${OCI_OFFHOST_HOST_METRICS_TIMELINE_RUN_ID:-${name}}"
input_tsv="${OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV:-}"
required_phases="${OCI_OFFHOST_HOST_METRICS_TIMELINE_REQUIRED_PHASES:-arrival16,vu16,burst-matrix}"
max_sample_interval_seconds="${OCI_OFFHOST_HOST_METRICS_TIMELINE_MAX_INTERVAL_SECONDS:-5}"
require_load_coupled="${OCI_OFFHOST_HOST_METRICS_TIMELINE_REQUIRE_LOAD_COUPLED:-false}"
output_dir="${OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR:-build/reports/k6/${name}}"
timeline_tsv="${output_dir}/${name}-host-metrics-timeline.tsv"
timeline_json="${output_dir}/${name}-host-metrics-timeline.json"
report_md="${output_dir}/${name}-host-metrics-timeline.md"
required_columns="run_id,phase,host_role,host_name,host_id,vm_id,network_id,docker_context,sample_started_at_utc,sample_ended_at_utc,sample_count,sample_source,sample_interval_seconds,cpu_pct_avg,cpu_pct_max,rx_mbps_avg,rx_mbps_max,tx_mbps_avg,tx_mbps_max,artifact_uri,summary_ref,artifact_pack_uri"

case "${require_load_coupled}" in
  true|false) ;;
  *)
    echo "OCI_OFFHOST_HOST_METRICS_TIMELINE_REQUIRE_LOAD_COUPLED must be true or false: ${require_load_coupled}" >&2
    exit 1
    ;;
esac
if ! [[ "${max_sample_interval_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "OCI_OFFHOST_HOST_METRICS_TIMELINE_MAX_INTERVAL_SECONDS must be a positive integer: ${max_sample_interval_seconds}" >&2
  exit 1
fi

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[oci-offhost-host-metrics-timeline] name=${name}"
  echo "[oci-offhost-host-metrics-timeline] run_id=${run_id}"
  echo "[oci-offhost-host-metrics-timeline] input_tsv=${input_tsv:-missing}"
  echo "[oci-offhost-host-metrics-timeline] required_phases=${required_phases}"
  echo "[oci-offhost-host-metrics-timeline] max_sample_interval_seconds=${max_sample_interval_seconds}"
  echo "[oci-offhost-host-metrics-timeline] require_load_coupled=${require_load_coupled}"
  echo "[oci-offhost-host-metrics-timeline] required_columns=${required_columns}"
  echo "[oci-offhost-host-metrics-timeline] output_dir=${output_dir}"
  echo "[oci-offhost-host-metrics-timeline] timeline_tsv=${timeline_tsv}"
  echo "[oci-offhost-host-metrics-timeline] timeline_json=${timeline_json}"
  echo "[oci-offhost-host-metrics-timeline] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV" "${input_tsv}"
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
raw_tsv="${output_dir}/${name}-host-metrics-timeline.raw.tsv"
meta_file="${output_dir}/${name}-host-metrics-timeline.meta"

awk -F '\t' -v OFS='\t' -v expected_run_id="${run_id}" -v required_phases="${required_phases}" \
  -v max_sample_interval_seconds="${max_sample_interval_seconds}" -v require_load_coupled="${require_load_coupled}" '
  function is_number(value) {
    return value ~ /^[0-9]+([.][0-9]+)?$/
  }
  function is_positive_integer(value) {
    return value ~ /^[1-9][0-9]*$/
  }
  function fail(message, code) {
    print message > "/dev/stderr"
    exit code
  }
  BEGIN {
    split(required_phases, required_phase_items, ",")
    for (i in required_phase_items) {
      required_phase[required_phase_items[i]] = 1
    }
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) col[$i] = i
    split("run_id phase host_role host_name host_id vm_id network_id docker_context sample_started_at_utc sample_ended_at_utc sample_count sample_source sample_interval_seconds cpu_pct_avg cpu_pct_max rx_mbps_avg rx_mbps_max tx_mbps_avg tx_mbps_max artifact_uri summary_ref artifact_pack_uri", required, " ")
    for (i in required) {
      if (!(required[i] in col)) {
        fail("missing required host metrics timeline column: " required[i], 2)
      }
    }
    print
    next
  }
  {
    row_run_id = $(col["run_id"])
    phase = $(col["phase"])
    role = $(col["host_role"])
    host_name = $(col["host_name"])
    host_id = $(col["host_id"])
    vm_id = $(col["vm_id"])
    network_id = $(col["network_id"])
    docker_context = $(col["docker_context"])
    sample_started_at = $(col["sample_started_at_utc"])
    sample_ended_at = $(col["sample_ended_at_utc"])
    sample_count = $(col["sample_count"])
    sample_source = $(col["sample_source"])
    sample_interval_seconds = $(col["sample_interval_seconds"])
    cpu_avg = $(col["cpu_pct_avg"])
    cpu_max = $(col["cpu_pct_max"])
    rx_avg = $(col["rx_mbps_avg"])
    rx_max = $(col["rx_mbps_max"])
    tx_avg = $(col["tx_mbps_avg"])
    tx_max = $(col["tx_mbps_max"])
    artifact_uri = $(col["artifact_uri"])
    summary_ref = $(col["summary_ref"])
    artifact_pack_uri = $(col["artifact_pack_uri"])

    if (row_run_id != expected_run_id) {
      fail("host metrics timeline run id mismatch: run_id=" row_run_id " expected=" expected_run_id, 3)
    }
    if (!(phase in required_phase)) {
      fail("unexpected host metrics timeline phase: " phase, 4)
    }
    if (role != "generator" && role != "target") {
      fail("host metrics timeline role must be generator or target: " role, 5)
    }
    if (host_name == "" || host_id == "" || vm_id == "" || network_id == "" || docker_context == "") {
      fail("host metrics timeline contains blank host identity/context", 6)
    }
    if (sample_started_at == "" || sample_ended_at == "" || artifact_uri == "" || summary_ref == "" || summary_ref == "n/a" || artifact_pack_uri == "" || artifact_pack_uri == "n/a") {
      fail("host metrics timeline requires summary/artifact pack refs", 7)
    }
    if (!is_positive_integer(sample_count)) {
      fail("host metrics timeline sample_count must be positive: " sample_count, 8)
    }
    if (sample_source != "load-coupled" && sample_source != "fallback-snapshot") {
      fail("host metrics timeline sample_source must be load-coupled or fallback-snapshot: " sample_source, 19)
    }
    if (!is_number(sample_interval_seconds)) {
      fail("host metrics timeline sample_interval_seconds must be a non-negative number: " sample_interval_seconds, 20)
    }
    if (require_load_coupled == "true" && sample_source != "load-coupled") {
      fail("load-coupled sampler source required: phase=" phase " role=" role " source=" sample_source, 21)
    }
    if (sample_source == "load-coupled" && sample_interval_seconds <= 0) {
      fail("load-coupled sampler interval must be positive: " sample_interval_seconds, 22)
    }
    if (sample_source == "load-coupled" && sample_interval_seconds > max_sample_interval_seconds) {
      fail("load-coupled sampler interval must be <= " max_sample_interval_seconds " seconds: " sample_interval_seconds, 23)
    }
    if (!is_number(cpu_avg) || !is_number(cpu_max) || !is_number(rx_avg) || !is_number(rx_max) || !is_number(tx_avg) || !is_number(tx_max)) {
      fail("host metrics timeline CPU/network values must be non-negative numbers: host=" host_name, 9)
    }

    seen_phase[phase] = 1
    role_count[phase, role]++
    if (artifact_pack == "") artifact_pack = artifact_pack_uri
    if (artifact_pack != artifact_pack_uri) {
      fail("host metrics timeline artifact pack URI mismatch: " artifact_pack_uri " expected=" artifact_pack, 10)
    }
    if (role == "generator") {
      generator_host_names[phase, host_name] = 1
      generator_host_ids[phase, host_id] = 1
      generator_vm_ids[phase, vm_id] = 1
      generator_network_ids[phase, network_id] = 1
    } else {
      target_host_names[phase, host_name] = 1
      target_host_ids[phase, host_id] = 1
      target_vm_ids[phase, vm_id] = 1
      target_network_ids[phase, network_id] = 1
    }
    print
  }
  END {
    if (NR <= 1) {
      fail("host metrics timeline is empty", 11)
    }
    for (phase in required_phase) {
      if (!(phase in seen_phase)) {
        fail("missing required host metrics timeline phase: " phase, 12)
      }
      if (role_count[phase, "generator"] < 1) {
        fail("missing generator timeline row for phase: " phase, 13)
      }
      if (role_count[phase, "target"] < 1) {
        fail("missing target timeline row for phase: " phase, 14)
      }
    }
    for (key in generator_host_names) {
      split(key, parts, SUBSEP)
      phase = parts[1]
      value = parts[2]
      if ((phase, value) in target_host_names) {
        fail("generator and target host/VM/network identity must differ: phase=" phase " host_name=" value, 15)
      }
    }
    for (key in generator_host_ids) {
      split(key, parts, SUBSEP)
      phase = parts[1]
      value = parts[2]
      if ((phase, value) in target_host_ids) {
        fail("generator and target host/VM/network identity must differ: phase=" phase " host_id=" value, 16)
      }
    }
    for (key in generator_vm_ids) {
      split(key, parts, SUBSEP)
      phase = parts[1]
      value = parts[2]
      if ((phase, value) in target_vm_ids) {
        fail("generator and target host/VM/network identity must differ: phase=" phase " vm_id=" value, 17)
      }
    }
    for (key in generator_network_ids) {
      split(key, parts, SUBSEP)
      phase = parts[1]
      value = parts[2]
      if ((phase, value) in target_network_ids) {
        fail("generator and target host/VM/network identity must differ: phase=" phase " network_id=" value, 18)
      }
    }
    phase_count = 0
    for (phase in seen_phase) phase_count++
    print "phase_count=" phase_count > "'"${meta_file}"'"
    print "artifact_pack_uri=" artifact_pack > "'"${meta_file}"'"
  }
' "${input_tsv}" >"${raw_tsv}"

mv "${raw_tsv}" "${timeline_tsv}"

jq -Rn --arg name "${name}" --arg run_id "${run_id}" --arg required_phases "${required_phases}" '
  def number_or_string:
    if test("^-?[0-9]+([.][0-9]+)?$") then tonumber else . end;
  (input | split("\t")) as $headers
  | [
      inputs
      | split("\t") as $row
      | reduce range(0; $headers | length) as $i (
          {};
          .[$headers[$i]] = (($row[$i] // "") | number_or_string)
        )
    ] as $items
  | {
      name: $name,
      run_id: $run_id,
      required_phases: ($required_phases | split(",")),
      summary: {
        gate_status: "pass",
        phase_count: ($items | map(.phase) | unique | length),
        row_count: ($items | length),
        generator_count: ($items | map(select(.host_role == "generator")) | length),
        target_count: ($items | map(select(.host_role == "target")) | length),
        artifact_pack_uri: ($items[0].artifact_pack_uri // "")
      },
      items: $items
    }
' <"${timeline_tsv}" >"${timeline_json}"

phase_count="$(awk -F '=' '/^phase_count=/ { print $2 }' "${meta_file}")"
artifact_pack_uri="$(awk -F '=' '/^artifact_pack_uri=/ { print $2 }' "${meta_file}")"

timeline_table="$(awk -F '\t' '
  BEGIN {
    print "| Phase | Role | Source | Interval s | Host | VM id | Network id | CPU avg % | CPU max % | RX max Mbps | TX max Mbps | Summary |"
    print "| --- | --- | --- | ---: | --- | --- | --- | ---: | ---: | ---: | ---: | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $2, $3, $12, $13, $4, $6, $7, $14, $15, $17, $19, $21
  }
' "${timeline_tsv}")"

cat >"${report_md}" <<REPORT
# OCI Off-Host Host Metrics Timeline

## Summary

- gate_status=pass
- run_id=${run_id}
- required phases: ${required_phases}
- max sample interval seconds: ${max_sample_interval_seconds}
- phase count: ${phase_count}
- generator/target timeline: verified
- load-coupled samples: $(if [[ "${require_load_coupled}" == "true" ]]; then printf 'required'; else printf 'optional'; fi)
- host identity separation: verified
- artifact pack URI: ${artifact_pack_uri}

## Timeline

${timeline_table}

## Contract Notes

- arrival16, vu16, burst-matrix 구간의 generator/target CPU/network timeline을 같은 run id와 artifact pack으로 묶는다.
- promotion evidence는 sample_source=load-coupled와 5초 이하 sampler interval을 요구한다.
- fallback-snapshot은 prerequisite 보조값으로만 쓰고 live 부하 구간 headroom 대체 증거로 쓰지 않는다.
- 각 phase에서 generator와 target의 host/VM/network 식별자가 같으면 운영 후보 evidence로 인정하지 않는다.
- summary_ref는 각 부하 구간 결과와 host metrics timeline을 조인하는 기준이다.

## Artifacts

- timeline TSV: ${timeline_tsv}
- timeline JSON: ${timeline_json}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"
