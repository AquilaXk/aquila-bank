#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-oci-offhost-host-metrics-evidence.sh [--print-plan]

Environment:
  OCI_OFFHOST_HOST_METRICS_NAME          default oci-offhost-host-metrics-<timestamp>
  OCI_OFFHOST_HOST_METRICS_RUN_ID        default same as name
  OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV required generator host CPU/network TSV
  OCI_OFFHOST_TARGET_HOST_METRICS_TSV    required target app/DB host CPU/network TSV
  OCI_OFFHOST_HOST_METRICS_OUTPUT_DIR    default build/reports/k6/<name>
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

name="${OCI_OFFHOST_HOST_METRICS_NAME:-oci-offhost-host-metrics-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${OCI_OFFHOST_HOST_METRICS_RUN_ID:-${name}}"
generator_host_metrics_tsv="${OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV:-}"
target_host_metrics_tsv="${OCI_OFFHOST_TARGET_HOST_METRICS_TSV:-}"
output_dir="${OCI_OFFHOST_HOST_METRICS_OUTPUT_DIR:-build/reports/k6/${name}}"
combined_tsv="${output_dir}/${name}-host-metrics.tsv"
combined_json="${output_dir}/${name}-host-metrics.json"
report_md="${output_dir}/${name}-host-metrics.md"
required_columns="run_id,host_role,host_name,host_id,vm_id,network_id,docker_context,cpu_pct,rx_mbps,tx_mbps,artifact_uri"

print_plan() {
  echo "[oci-offhost-host-metrics-evidence] name=${name}"
  echo "[oci-offhost-host-metrics-evidence] run_id=${run_id}"
  echo "[oci-offhost-host-metrics-evidence] generator_host_metrics_tsv=${generator_host_metrics_tsv:-missing}"
  echo "[oci-offhost-host-metrics-evidence] target_host_metrics_tsv=${target_host_metrics_tsv:-missing}"
  echo "[oci-offhost-host-metrics-evidence] required_columns=${required_columns}"
  echo "[oci-offhost-host-metrics-evidence] output_dir=${output_dir}"
  echo "[oci-offhost-host-metrics-evidence] combined_tsv=${combined_tsv}"
  echo "[oci-offhost-host-metrics-evidence] combined_json=${combined_json}"
  echo "[oci-offhost-host-metrics-evidence] report_md=${report_md}"
}

require_file() {
  local name="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${name} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV" "${generator_host_metrics_tsv}"
  require_file "OCI_OFFHOST_TARGET_HOST_METRICS_TSV" "${target_host_metrics_tsv}"
  exit 0
fi

require_file "OCI_OFFHOST_GENERATOR_HOST_METRICS_TSV" "${generator_host_metrics_tsv}"
require_file "OCI_OFFHOST_TARGET_HOST_METRICS_TSV" "${target_host_metrics_tsv}"
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
raw_tsv="${output_dir}/${name}-host-metrics.raw.tsv"

awk -F '\t' -v OFS='\t' -v expected_run_id="${run_id}" '
  function is_number(value) {
    return value ~ /^[0-9]+([.][0-9]+)?$/
  }
  function read_source(file, expected_role) {
    while ((getline line < file) > 0) {
      split(line, row, FS)
      if (FNR == 0) {
        # no-op for shellcheck-like parsers
      }
      if (source_line[file] == 0) {
        for (i = 1; i <= length(row); i++) {
          columns[file, row[i]] = i
        }
        split("run_id host_role host_name host_id vm_id network_id docker_context cpu_pct rx_mbps tx_mbps artifact_uri", required, " ")
        for (i in required) {
          if (!((file, required[i]) in columns)) {
            printf "missing required host metric column: %s\n", required[i] > "/dev/stderr"
            exit 2
          }
        }
        source_line[file]++
        continue
      }
      source_line[file]++
      run = row[columns[file, "run_id"]]
      role = row[columns[file, "host_role"]]
      host = row[columns[file, "host_name"]]
      host_id = row[columns[file, "host_id"]]
      vm_id = row[columns[file, "vm_id"]]
      network_id = row[columns[file, "network_id"]]
      context = row[columns[file, "docker_context"]]
      cpu = row[columns[file, "cpu_pct"]]
      rx = row[columns[file, "rx_mbps"]]
      tx = row[columns[file, "tx_mbps"]]
      artifact = row[columns[file, "artifact_uri"]]
      if (run != expected_run_id) {
        printf "host metric run id mismatch: host=%s run_id=%s expected=%s\n", host, run, expected_run_id > "/dev/stderr"
        exit 3
      }
      if (role != "generator" && role != "target") {
        printf "host metric role must be generator or target: host=%s role=%s\n", host, role > "/dev/stderr"
        exit 4
      }
      if (role != expected_role) {
        printf "host metric role mismatch for %s: host=%s role=%s\n", expected_role, host, role > "/dev/stderr"
        exit 5
      }
      if (host == "" || host_id == "" || vm_id == "" || network_id == "" || context == "" || artifact == "") {
        print "host metric contains blank host/identity/context/artifact" > "/dev/stderr"
        exit 6
      }
      if (!is_number(cpu) || !is_number(rx) || !is_number(tx)) {
        printf "host metric cpu/network values must be non-negative numbers: host=%s cpu=%s rx=%s tx=%s\n", host, cpu, rx, tx > "/dev/stderr"
        exit 7
      }
      identity = host "|" host_id "|" vm_id "|" network_id "|" context
      if (role == "generator") {
        generator_count++
        generator_host_names[host] = 1
        generator_host_ids[host_id] = 1
        generator_vm_ids[vm_id] = 1
        generator_network_ids[network_id] = 1
        generator_identities[identity] = 1
      }
      if (role == "target") {
        target_count++
        target_host_names[host] = 1
        target_host_ids[host_id] = 1
        target_vm_ids[vm_id] = 1
        target_network_ids[network_id] = 1
        target_identities[identity] = 1
      }
      print run, role, host, host_id, vm_id, network_id, context, cpu, rx, tx, artifact, file
    }
    close(file)
  }
  BEGIN {
    print "run_id", "host_role", "host_name", "host_id", "vm_id", "network_id", "docker_context", "cpu_pct", "rx_mbps", "tx_mbps", "artifact_uri", "source_file"
    read_source(ARGV[1], "generator")
    read_source(ARGV[2], "target")
    if (generator_count < 1) {
      print "generator host metrics row is required" > "/dev/stderr"
      exit 8
    }
    if (target_count < 1) {
      print "target host metrics row is required" > "/dev/stderr"
      exit 9
    }
    for (item in generator_host_names) {
      if (item in target_host_names) {
        print "generator and target host/VM/network identity must differ: host_name=" item > "/dev/stderr"
        exit 10
      }
    }
    for (item in generator_host_ids) {
      if (item in target_host_ids) {
        print "generator and target host/VM/network identity must differ: host_id=" item > "/dev/stderr"
        exit 11
      }
    }
    for (item in generator_vm_ids) {
      if (item in target_vm_ids) {
        print "generator and target host/VM/network identity must differ: vm_id=" item > "/dev/stderr"
        exit 12
      }
    }
    for (item in generator_network_ids) {
      if (item in target_network_ids) {
        print "generator and target host/VM/network identity must differ: network_id=" item > "/dev/stderr"
        exit 13
      }
    }
    for (item in generator_identities) {
      if (item in target_identities) {
        print "generator and target host/VM/network identity must differ: identity=" item > "/dev/stderr"
        exit 14
      }
    }
  }
' "${generator_host_metrics_tsv}" "${target_host_metrics_tsv}" >"${raw_tsv}"

mv "${raw_tsv}" "${combined_tsv}"

jq -Rn --arg name "${name}" --arg run_id "${run_id}" '
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
      summary: {
        gate_status: "pass",
        generator_count: ($items | map(select(.host_role == "generator")) | length),
        target_count: ($items | map(select(.host_role == "target")) | length),
        host_count: ($items | length)
      },
      items: $items
    }
' <"${combined_tsv}" >"${combined_json}"

generator_count="$(awk -F '\t' 'NR > 1 && $2 == "generator" { count++ } END { print count + 0 }' "${combined_tsv}")"
target_count="$(awk -F '\t' 'NR > 1 && $2 == "target" { count++ } END { print count + 0 }' "${combined_tsv}")"

host_table="$(awk -F '\t' '
  BEGIN {
    print "| Role | Host | Host id | VM id | Network id | Docker context | CPU % | RX Mbps | TX Mbps | Artifact |"
    print "| --- | --- | --- | --- | --- | --- | ---: | ---: | ---: | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $2, $3, $4, $5, $6, $7, $8, $9, $10, $11
  }
' "${combined_tsv}")"

cat >"${report_md}" <<REPORT
# OCI Off-Host Host Metrics Evidence

## Summary

- gate_status=pass
- run_id=${run_id}
- generator host metrics: verified
- target host metrics: verified
- host identity separation: verified
- VM/network separation: verified
- generator host count: ${generator_count}
- target host count: ${target_count}

## Host Metrics

${host_table}

## Artifacts

- generator host metrics TSV: ${generator_host_metrics_tsv}
- target host metrics TSV: ${target_host_metrics_tsv}
- combined host metrics TSV: ${combined_tsv}
- combined host metrics JSON: ${combined_json}

## Contract Notes

- off-host generator와 app/DB target host CPU/network evidence를 같은 run id로 묶는다.
- generator/target host, VM, network 식별자가 같으면 같은 장비 또는 같은 network path일 수 있어 운영 후보 evidence로 인정하지 않는다.
- host metrics가 비어 있거나 generator/target role 중 하나라도 없으면 prerequisite가 실패해야 한다.
REPORT

echo "${report_md}"
