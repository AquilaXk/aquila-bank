#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-oci-offhost-host-metrics-load-coupled-timeline.sh [--print-plan]

Environment:
  OCI_OFFHOST_LOAD_COUPLED_TIMELINE_NAME         default oci-offhost-load-coupled-timeline-<timestamp>
  OCI_OFFHOST_LOAD_COUPLED_TIMELINE_RUN_ID       default same as name
  OCI_OFFHOST_LOAD_COUPLED_PHASES                default arrival16,vu16,burst-matrix
  OCI_OFFHOST_LOAD_COUPLED_ARRIVAL16_COMMAND     required shell command for arrival16 load
  OCI_OFFHOST_LOAD_COUPLED_VU16_COMMAND          required shell command for VU16 load
  OCI_OFFHOST_LOAD_COUPLED_BURST_MATRIX_COMMAND  required shell command for burst-matrix load
  OCI_OFFHOST_LOAD_COUPLED_SAMPLE_INTERVAL_SECONDS default 5
  OCI_OFFHOST_LOAD_COUPLED_SAMPLE_MODE           auto|local, default auto
  OCI_OFFHOST_LOAD_COUPLED_OUTPUT_DIR            default build/reports/k6/<name>/offhost-host-metrics-timeline
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

name="${OCI_OFFHOST_LOAD_COUPLED_TIMELINE_NAME:-oci-offhost-load-coupled-timeline-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${OCI_OFFHOST_LOAD_COUPLED_TIMELINE_RUN_ID:-${name}}"
required_phases="${OCI_OFFHOST_LOAD_COUPLED_PHASES:-arrival16,vu16,burst-matrix}"
sample_interval_seconds="${OCI_OFFHOST_LOAD_COUPLED_SAMPLE_INTERVAL_SECONDS:-5}"
sample_mode="${OCI_OFFHOST_LOAD_COUPLED_SAMPLE_MODE:-auto}"
output_dir="${OCI_OFFHOST_LOAD_COUPLED_OUTPUT_DIR:-build/reports/k6/${name}/offhost-host-metrics-timeline}"
artifact_pack_uri="${OCI_OFFHOST_LOAD_COUPLED_ARTIFACT_PACK_URI:-artifact://offhost-capacity-prerequisite/${name}}"
sampler_image="${OCI_OFFHOST_LOAD_COUPLED_SAMPLER_IMAGE:-busybox:1.36}"
timeline_input_tsv="${output_dir}/${name}-host-metrics-timeline.input.tsv"
timeline_tsv="${output_dir}/${name}-host-metrics-timeline.tsv"
timeline_json="${output_dir}/${name}-host-metrics-timeline.json"
report_md="${output_dir}/${name}-host-metrics-timeline.md"

host_name="$(hostname 2>/dev/null || echo unknown-host)"
generator_docker_context="${OCI_OFFHOST_LOAD_COUPLED_GENERATOR_DOCKER_CONTEXT:-${OCI_OFFHOST_GENERATOR_DOCKER_CONTEXT:-default}}"
target_docker_context="${OCI_OFFHOST_LOAD_COUPLED_TARGET_DOCKER_CONTEXT:-${OCI_OFFHOST_TARGET_DOCKER_CONTEXT:-target}}"
generator_sample_context="${OCI_OFFHOST_LOAD_COUPLED_GENERATOR_SAMPLE_CONTEXT:-${generator_docker_context}}"
target_sample_context="${OCI_OFFHOST_LOAD_COUPLED_TARGET_SAMPLE_CONTEXT:-local}"
generator_host_name="${OCI_OFFHOST_LOAD_COUPLED_GENERATOR_HOST_NAME:-${host_name}-${generator_docker_context}-generator}"
target_host_name="${OCI_OFFHOST_LOAD_COUPLED_TARGET_HOST_NAME:-${host_name}-${target_docker_context}-target}"
generator_host_id="${OCI_OFFHOST_LOAD_COUPLED_GENERATOR_HOST_ID:-load-coupled:${generator_host_name}:${generator_docker_context}:generator}"
target_host_id="${OCI_OFFHOST_LOAD_COUPLED_TARGET_HOST_ID:-load-coupled:${target_host_name}:${target_docker_context}:target}"
generator_vm_id="${OCI_OFFHOST_LOAD_COUPLED_GENERATOR_VM_ID:-load-coupled-vm:${generator_host_name}:${generator_docker_context}:generator}"
target_vm_id="${OCI_OFFHOST_LOAD_COUPLED_TARGET_VM_ID:-load-coupled-vm:${target_host_name}:${target_docker_context}:target}"
generator_network_id="${OCI_OFFHOST_LOAD_COUPLED_GENERATOR_NETWORK_ID:-load-coupled-network:${generator_docker_context}:generator}"
target_network_id="${OCI_OFFHOST_LOAD_COUPLED_TARGET_NETWORK_ID:-load-coupled-network:${target_docker_context}:target}"

case "${sample_mode}" in
  auto|local) ;;
  *)
    echo "OCI_OFFHOST_LOAD_COUPLED_SAMPLE_MODE must be auto or local: ${sample_mode}" >&2
    exit 1
    ;;
esac
if ! [[ "${sample_interval_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "OCI_OFFHOST_LOAD_COUPLED_SAMPLE_INTERVAL_SECONDS must be a positive integer: ${sample_interval_seconds}" >&2
  exit 1
fi

IFS=',' read -r -a phase_items <<<"${required_phases}"

command_for_phase() {
  case "$1" in
    arrival16)
      echo "${OCI_OFFHOST_LOAD_COUPLED_ARRIVAL16_COMMAND:-}"
      ;;
    vu16)
      echo "${OCI_OFFHOST_LOAD_COUPLED_VU16_COMMAND:-}"
      ;;
    burst-matrix)
      echo "${OCI_OFFHOST_LOAD_COUPLED_BURST_MATRIX_COMMAND:-}"
      ;;
    *)
      echo ""
      ;;
  esac
}

command_key_for_phase() {
  case "$1" in
    arrival16) echo "OCI_OFFHOST_LOAD_COUPLED_ARRIVAL16_COMMAND" ;;
    vu16) echo "OCI_OFFHOST_LOAD_COUPLED_VU16_COMMAND" ;;
    burst-matrix) echo "OCI_OFFHOST_LOAD_COUPLED_BURST_MATRIX_COMMAND" ;;
    *) echo "OCI_OFFHOST_LOAD_COUPLED_${1}_COMMAND" ;;
  esac
}

summary_ref_for_phase() {
  local phase="$1"
  case "${phase}" in
    arrival16)
      echo "${OCI_OFFHOST_LOAD_COUPLED_ARRIVAL16_SUMMARY_REF:-build/reports/k6/${name}-arrival16-summary.json}"
      ;;
    vu16)
      echo "${OCI_OFFHOST_LOAD_COUPLED_VU16_SUMMARY_REF:-build/reports/k6/${name}-vu16-summary.json}"
      ;;
    burst-matrix)
      echo "${OCI_OFFHOST_LOAD_COUPLED_BURST_MATRIX_SUMMARY_REF:-${output_dir}/${name}-burst-matrix-runs.tsv}"
      ;;
    *)
      echo "${artifact_pack_uri}/${phase}/summary"
      ;;
  esac
}

validate_commands() {
  local phase command key
  for phase in "${phase_items[@]}"; do
    command="$(command_for_phase "${phase}")"
    if [[ -z "${command}" ]]; then
      key="$(command_key_for_phase "${phase}")"
      echo "${key} is required" >&2
      exit 1
    fi
  done
}

assert_host_separation() {
  if [[ "${generator_host_name}" == "${target_host_name}" ]]; then
    echo "generator and target host identity must differ: host_name=${generator_host_name}" >&2
    exit 1
  fi
  if [[ "${generator_host_id}" == "${target_host_id}" ]]; then
    echo "generator and target host identity must differ: host_id=${generator_host_id}" >&2
    exit 1
  fi
  if [[ "${generator_vm_id}" == "${target_vm_id}" ]]; then
    echo "generator and target host identity must differ: vm_id=${generator_vm_id}" >&2
    exit 1
  fi
  if [[ "${generator_network_id}" == "${target_network_id}" ]]; then
    echo "generator and target host identity must differ: network_id=${generator_network_id}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[oci-offhost-load-coupled-timeline] name=${name}"
  echo "[oci-offhost-load-coupled-timeline] run_id=${run_id}"
  echo "[oci-offhost-load-coupled-timeline] required_phases=${required_phases}"
  echo "[oci-offhost-load-coupled-timeline] sample_interval_seconds=${sample_interval_seconds}"
  echo "[oci-offhost-load-coupled-timeline] sample_mode=${sample_mode}"
  echo "[oci-offhost-load-coupled-timeline] generator_context=${generator_docker_context}"
  echo "[oci-offhost-load-coupled-timeline] target_context=${target_docker_context}"
  echo "[oci-offhost-load-coupled-timeline] artifact_pack_uri=${artifact_pack_uri}"
  echo "[oci-offhost-load-coupled-timeline] output_dir=${output_dir}"
  echo "[oci-offhost-load-coupled-timeline] timeline_tsv=${timeline_tsv}"
  echo "[oci-offhost-load-coupled-timeline] timeline_json=${timeline_json}"
  echo "[oci-offhost-load-coupled-timeline] report_md=${report_md}"
}

validate_commands
assert_host_separation
print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"

read_local_counters() {
  local cpu_total cpu_idle rx_bytes tx_bytes
  read -r cpu_total cpu_idle < <(
    awk '/^cpu / {
      idle = $5 + $6
      total = 0
      for (i = 2; i <= NF; i++) total += $i
      printf "%.0f %.0f\n", total, idle
      exit
    }' /proc/stat 2>/dev/null || echo "0 0"
  )
  read -r rx_bytes tx_bytes < <(
    awk -F '[: ]+' '
      NR > 2 && $2 != "lo" {
        rx += $3
        tx += $11
      }
      END {
        printf "%.0f %.0f\n", rx, tx
      }
    ' /proc/net/dev 2>/dev/null || echo "0 0"
  )
  printf "%s\t%s\t%s\t%s\n" "${cpu_total}" "${cpu_idle}" "${rx_bytes}" "${tx_bytes}"
}

read_docker_context_counters() {
  local context="$1"
  docker --context "${context}" run --rm --pid=host \
    -v /proc:/host/proc:ro \
    --entrypoint sh "${sampler_image}" \
    -c '
      cpu="$(awk "/^cpu / { idle = \$5 + \$6; total = 0; for (i = 2; i <= NF; i++) total += \$i; printf \"%.0f %.0f\", total, idle; exit }" /host/proc/stat 2>/dev/null || echo "0 0")"
      net="$(awk -F "[: ]+" "NR > 2 && \$2 != \"lo\" { rx += \$3; tx += \$11 } END { printf \"%.0f %.0f\", rx, tx }" /host/proc/net/dev 2>/dev/null || echo "0 0")"
      printf "%s\t%s\n" "${cpu}" "${net}"
    '
}

read_counters() {
  local context="$1"
  if [[ "${sample_mode}" == "local" || -z "${context}" || "${context}" == "local" || "${context}" == "target" ]]; then
    read_local_counters
    return 0
  fi
  if [[ "${context}" == "default" ]]; then
    read_local_counters
    return 0
  fi
  read_docker_context_counters "${context}"
}

sample_loop() {
  local role="$1"
  local sample_context="$2"
  local raw_path="$3"
  local stop_path="$4"
  local cpu_total cpu_idle rx_bytes tx_bytes epoch iso

  while true; do
    epoch="$(date -u +%s)"
    iso="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    if read -r cpu_total cpu_idle rx_bytes tx_bytes < <(read_counters "${sample_context}"); then
      printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
        "${epoch}" "${iso}" "${role}" "${cpu_total}" "${cpu_idle}" "${rx_bytes}" "${tx_bytes}" >>"${raw_path}"
    fi
    if [[ -f "${stop_path}" ]]; then
      break
    fi
    sleep "${sample_interval_seconds}"
  done
}

summarize_role() {
  local phase="$1"
  local role="$2"
  local raw_path="$3"
  local summary_ref="$4"
  local docker_context host role_host_id role_vm_id role_network_id

  if [[ "${role}" == "generator" ]]; then
    host="${generator_host_name}"
    role_host_id="${generator_host_id}"
    role_vm_id="${generator_vm_id}"
    role_network_id="${generator_network_id}"
    docker_context="${generator_docker_context}"
  else
    host="${target_host_name}"
    role_host_id="${target_host_id}"
    role_vm_id="${target_vm_id}"
    role_network_id="${target_network_id}"
    docker_context="${target_docker_context}"
  fi

  awk -F '\t' -v OFS='\t' \
    -v run_id="${run_id}" \
    -v phase="${phase}" \
    -v role="${role}" \
    -v host="${host}" \
    -v host_id="${role_host_id}" \
    -v vm_id="${role_vm_id}" \
    -v network_id="${role_network_id}" \
    -v docker_context="${docker_context}" \
    -v sample_interval="${sample_interval_seconds}" \
    -v artifact_uri="${raw_path}" \
    -v summary_ref="${summary_ref}" \
    -v artifact_pack_uri="${artifact_pack_uri}" '
    NR == 1 {
      first_epoch = $1
      first_iso = $2
      first_total = $4
      first_idle = $5
      first_rx = $6
      first_tx = $7
      prev_epoch = $1
      prev_total = $4
      prev_idle = $5
      prev_rx = $6
      prev_tx = $7
    }
    {
      count += 1
      last_epoch = $1
      last_iso = $2
      last_total = $4
      last_idle = $5
      last_rx = $6
      last_tx = $7
      if (NR > 1) {
        total_delta = $4 - prev_total
        idle_delta = $5 - prev_idle
        epoch_delta = $1 - prev_epoch
        if (total_delta > 0) {
          cpu_pct = ((total_delta - idle_delta) / total_delta) * 100
          if (!cpu_found || cpu_pct > cpu_max) cpu_max = cpu_pct
          cpu_found = 1
        }
        if (epoch_delta > 0) {
          rx_mbps = (($6 - prev_rx) * 8) / epoch_delta / 1000000
          tx_mbps = (($7 - prev_tx) * 8) / epoch_delta / 1000000
          if (!rx_found || rx_mbps > rx_max) rx_max = rx_mbps
          if (!tx_found || tx_mbps > tx_max) tx_max = tx_mbps
          rx_found = 1
          tx_found = 1
        }
      }
      prev_epoch = $1
      prev_total = $4
      prev_idle = $5
      prev_rx = $6
      prev_tx = $7
    }
    END {
      if (count < 2) {
        printf "load-coupled sampler requires at least two samples: phase=%s role=%s count=%d\n", phase, role, count > "/dev/stderr"
        exit 2
      }
      total_delta = last_total - first_total
      idle_delta = last_idle - first_idle
      elapsed = last_epoch - first_epoch
      if (total_delta > 0) {
        cpu_avg = ((total_delta - idle_delta) / total_delta) * 100
      } else {
        cpu_avg = 0
      }
      if (!cpu_found) cpu_max = cpu_avg
      if (elapsed > 0) {
        rx_avg = ((last_rx - first_rx) * 8) / elapsed / 1000000
        tx_avg = ((last_tx - first_tx) * 8) / elapsed / 1000000
      } else {
        rx_avg = 0
        tx_avg = 0
      }
      if (!rx_found) rx_max = rx_avg
      if (!tx_found) tx_max = tx_avg
      printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%d\tload-coupled\t%s\t%.2f\t%.2f\t%.3f\t%.3f\t%.3f\t%.3f\t%s\t%s\t%s\n", \
        run_id, phase, role, host, host_id, vm_id, network_id, docker_context, first_iso, last_iso, count, sample_interval, \
        cpu_avg, cpu_max, rx_avg, rx_max, tx_avg, tx_max, artifact_uri, summary_ref, artifact_pack_uri
    }
  ' "${raw_path}"
}

write_timeline_header() {
  printf "run_id\tphase\thost_role\thost_name\thost_id\tvm_id\tnetwork_id\tdocker_context\tsample_started_at_utc\tsample_ended_at_utc\tsample_count\tsample_source\tsample_interval_seconds\tcpu_pct_avg\tcpu_pct_max\trx_mbps_avg\trx_mbps_max\ttx_mbps_avg\ttx_mbps_max\tartifact_uri\tsummary_ref\tartifact_pack_uri\n" >"${timeline_input_tsv}"
}

run_phase() {
  local phase="$1"
  local command="$2"
  local summary_ref="$3"
  local phase_dir="${output_dir}/${phase}"
  local generator_raw="${phase_dir}/generator-samples.tsv"
  local target_raw="${phase_dir}/target-samples.tsv"
  local stop_path="${phase_dir}/sampler.stop"
  local command_log="${phase_dir}/load-command.log"
  local generator_pid target_pid phase_status

  mkdir -p "${phase_dir}"
  : >"${generator_raw}"
  : >"${target_raw}"
  rm -f "${stop_path}"

  sample_loop "generator" "${generator_sample_context}" "${generator_raw}" "${stop_path}" &
  generator_pid="$!"
  sample_loop "target" "${target_sample_context}" "${target_raw}" "${stop_path}" &
  target_pid="$!"

  set +e
  bash -lc "${command}" >"${command_log}" 2>&1
  phase_status=$?
  set -e

  touch "${stop_path}"
  wait "${generator_pid}" || true
  wait "${target_pid}" || true

  summarize_role "${phase}" "generator" "${generator_raw}" "${summary_ref}" >>"${timeline_input_tsv}"
  summarize_role "${phase}" "target" "${target_raw}" "${summary_ref}" >>"${timeline_input_tsv}"

  if [[ "${phase_status}" -ne 0 ]]; then
    echo "load-coupled phase command failed: phase=${phase} status=${phase_status} log=${command_log}" >&2
    return "${phase_status}"
  fi
  return 0
}

write_timeline_header
status=0
for phase in "${phase_items[@]}"; do
  command="$(command_for_phase "${phase}")"
  summary_ref="$(summary_ref_for_phase "${phase}")"
  if ! run_phase "${phase}" "${command}" "${summary_ref}"; then
    status=1
  fi
done

validator_output="$(
  OCI_OFFHOST_HOST_METRICS_TIMELINE_NAME="${name}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_RUN_ID="${run_id}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV="${timeline_input_tsv}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_REQUIRED_PHASES="${required_phases}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_MAX_INTERVAL_SECONDS="${sample_interval_seconds}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_REQUIRE_LOAD_COUPLED=true \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR="${output_dir}" \
    tools/test/run-oci-offhost-host-metrics-timeline.sh
)"
report_path="$(tail -1 <<<"${validator_output}")"
{
  echo
  echo "## Load-Coupled Generation"
  echo
  echo "- load-coupled samples: generated"
  echo "- sample interval seconds: ${sample_interval_seconds}"
  echo "- timeline input TSV: ${timeline_input_tsv}"
} >>"${report_path}"

echo "${report_path}"
exit "${status}"
