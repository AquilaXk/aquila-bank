#!/usr/bin/env bash
set -euo pipefail

name="${OCI_OFFHOST_HOST_METRICS_SNAPSHOT_NAME:-oci-offhost-host-metrics-snapshot-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${OCI_OFFHOST_HOST_METRICS_RUN_ID:-${name}}"
output_dir="${OCI_OFFHOST_HOST_METRICS_OUTPUT_DIR:-build/reports/k6/${name}/offhost-host-metrics-snapshot}"
generator_context="${OCI_OFFHOST_GENERATOR_DOCKER_CONTEXT:-default}"
target_context="${OCI_OFFHOST_TARGET_DOCKER_CONTEXT:-target}"
sample_interval_seconds="${OCI_OFFHOST_HOST_METRICS_SAMPLE_INTERVAL_SECONDS:-1}"
host_name="${OCI_OFFHOST_HOST_NAME:-$(hostname 2>/dev/null || echo unknown-host)}"
generator_host_name="${OCI_OFFHOST_GENERATOR_HOST_NAME:-${host_name}-${generator_context}-generator}"
target_host_name="${OCI_OFFHOST_TARGET_HOST_NAME:-${host_name}-${target_context}-target}"
generator_host_id="${OCI_OFFHOST_GENERATOR_HOST_ID:-snapshot:${generator_host_name}:${generator_context}:generator}"
target_host_id="${OCI_OFFHOST_TARGET_HOST_ID:-snapshot:${target_host_name}:${target_context}:target}"
generator_vm_id="${OCI_OFFHOST_GENERATOR_VM_ID:-snapshot-vm:${generator_host_name}:${generator_context}:generator}"
target_vm_id="${OCI_OFFHOST_TARGET_VM_ID:-snapshot-vm:${target_host_name}:${target_context}:target}"
generator_network_id="${OCI_OFFHOST_GENERATOR_NETWORK_ID:-snapshot-network:${generator_context}:generator}"
target_network_id="${OCI_OFFHOST_TARGET_NETWORK_ID:-snapshot-network:${target_context}:target}"

generator_tsv="${output_dir}/${name}-generator-host-metrics.tsv"
target_tsv="${output_dir}/${name}-target-host-metrics.tsv"
summary_json="${output_dir}/${name}-host-metrics-snapshot.json"

if ! [[ "${sample_interval_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "OCI_OFFHOST_HOST_METRICS_SAMPLE_INTERVAL_SECONDS must be a positive integer: ${sample_interval_seconds}" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

read_cpu_totals() {
  if [[ ! -r /proc/stat ]]; then
    echo "0 0"
    return
  fi
  awk '/^cpu / {
    idle = $5 + $6
    total = 0
    for (i = 2; i <= NF; i++) total += $i
    printf "%.0f %.0f\n", total, idle
    exit
  }' /proc/stat
}

read_network_bytes() {
  if [[ ! -r /proc/net/dev ]]; then
    echo "0 0"
    return
  fi
  awk -F '[: ]+' '
    NR > 2 && $2 != "lo" {
      rx += $3
      tx += $11
    }
    END {
      printf "%.0f %.0f\n", rx, tx
    }
  ' /proc/net/dev
}

read -r cpu_total_start cpu_idle_start < <(read_cpu_totals)
read -r rx_start tx_start < <(read_network_bytes)
sleep "${sample_interval_seconds}"
read -r cpu_total_end cpu_idle_end < <(read_cpu_totals)
read -r rx_end tx_end < <(read_network_bytes)

cpu_pct="$(
  awk -v total_start="${cpu_total_start}" -v idle_start="${cpu_idle_start}" \
      -v total_end="${cpu_total_end}" -v idle_end="${cpu_idle_end}" '
    BEGIN {
      total_delta = total_end - total_start
      idle_delta = idle_end - idle_start
      if (total_delta <= 0) {
        printf "0.00"
      } else {
        printf "%.2f", ((total_delta - idle_delta) / total_delta) * 100
      }
    }
  '
)"
rx_mbps="$(
  awk -v start="${rx_start}" -v finish="${rx_end}" -v seconds="${sample_interval_seconds}" '
    BEGIN { printf "%.3f", ((finish - start) * 8) / seconds / 1000000 }
  '
)"
tx_mbps="$(
  awk -v start="${tx_start}" -v finish="${tx_end}" -v seconds="${sample_interval_seconds}" '
    BEGIN { printf "%.3f", ((finish - start) * 8) / seconds / 1000000 }
  '
)"

mkdir -p "${output_dir}"
header=$'run_id\thost_role\thost_name\thost_id\tvm_id\tnetwork_id\tdocker_context\tcpu_pct\trx_mbps\ttx_mbps\tartifact_uri\tsample_count\tcpu_pct_max\trx_mbps_max\ttx_mbps_max'
printf '%s\n' "${header}" >"${generator_tsv}"
printf '%s\n' "${header}" >"${target_tsv}"

printf '%s\tgenerator\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\tartifact://offhost-capacity-prerequisite/%s/generator-host-metrics.tsv\t1\t%s\t%s\t%s\n' \
  "${run_id}" "${generator_host_name}" "${generator_host_id}" "${generator_vm_id}" "${generator_network_id}" "${generator_context}" "${cpu_pct}" "${rx_mbps}" "${tx_mbps}" "${name}" "${cpu_pct}" "${rx_mbps}" "${tx_mbps}" \
  >>"${generator_tsv}"
printf '%s\ttarget\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\tartifact://offhost-capacity-prerequisite/%s/target-host-metrics.tsv\t1\t%s\t%s\t%s\n' \
  "${run_id}" "${target_host_name}" "${target_host_id}" "${target_vm_id}" "${target_network_id}" "${target_context}" "${cpu_pct}" "${rx_mbps}" "${tx_mbps}" "${name}" "${cpu_pct}" "${rx_mbps}" "${tx_mbps}" \
  >>"${target_tsv}"

jq -n \
  --arg name "${name}" \
  --arg run_id "${run_id}" \
  --arg generator_tsv "${generator_tsv}" \
  --arg target_tsv "${target_tsv}" \
  --arg generator_host_name "${generator_host_name}" \
  --arg target_host_name "${target_host_name}" \
  --arg generator_host_id "${generator_host_id}" \
  --arg target_host_id "${target_host_id}" \
  --arg generator_vm_id "${generator_vm_id}" \
  --arg target_vm_id "${target_vm_id}" \
  --arg generator_network_id "${generator_network_id}" \
  --arg target_network_id "${target_network_id}" \
  --arg generator_context "${generator_context}" \
  --arg target_context "${target_context}" \
  '{
    name: $name,
    run_id: $run_id,
    generator: {
      tsv: $generator_tsv,
      host_name: $generator_host_name,
      host_id: $generator_host_id,
      vm_id: $generator_vm_id,
      network_id: $generator_network_id,
      docker_context: $generator_context
    },
    target: {
      tsv: $target_tsv,
      host_name: $target_host_name,
      host_id: $target_host_id,
      vm_id: $target_vm_id,
      network_id: $target_network_id,
      docker_context: $target_context
    }
  }' >"${summary_json}"

echo "generator_host_metrics_tsv=${generator_tsv}"
echo "target_host_metrics_tsv=${target_tsv}"
echo "summary_json=${summary_json}"
