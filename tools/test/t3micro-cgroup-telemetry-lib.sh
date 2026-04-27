#!/usr/bin/env bash

t3micro_gc_java_tool_options() {
  local base_options="$1"
  local gc_log_path="$2"
  if [[ "${base_options}" == *"-Xlog:gc"* ]]; then
    printf "%s\n" "${base_options}"
    return 0
  fi
  printf "%s -Xlog:gc*:file=%s:time,uptime,level,tags\n" "${base_options}" "${gc_log_path}"
}

t3micro_start_container_telemetry() {
  local container_name="$1"
  local stats_path="$2"
  local marker_path="$3"
  local interval_seconds="$4"

  mkdir -p "$(dirname "${stats_path}")"
  : >"${stats_path}"
  touch "${marker_path}"
  {
    printf "timestamp\tcpu_percent\tmemory_usage\tmemory_limit\tpids\n"
    while [[ -f "${marker_path}" ]]; do
      local stats
      if stats="$(docker stats --no-stream --format '{{.CPUPerc}}\t{{.MemUsage}}\t{{.PIDs}}' "${container_name}" 2>/dev/null)"; then
        local cpu memory pids memory_usage memory_limit
        cpu="${stats%%$'\t'*}"
        memory="${stats#*$'\t'}"
        memory="${memory%$'\t'*}"
        pids="${stats##*$'\t'}"
        memory_usage="${memory%% / *}"
        memory_limit="${memory##* / }"
        printf "%s\t%s\t%s\t%s\t%s\n" \
          "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
          "${cpu}" "${memory_usage}" "${memory_limit}" "${pids}"
      fi
      sleep "${interval_seconds}"
    done
  } &
  T3MICRO_TELEMETRY_PID=$!
}

t3micro_stop_container_telemetry() {
  local marker_path="$1"
  local telemetry_pid="$2"
  rm -f "${marker_path}"
  if [[ -n "${telemetry_pid}" ]]; then
    wait "${telemetry_pid}" 2>/dev/null || true
  fi
}

t3micro_write_peak_summary() {
  local stats_path="$1"
  local gc_log_path="$2"
  local output_path="$3"
  mkdir -p "$(dirname "${output_path}")"
  awk -F '\t' '
    function to_mib(value) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      if (value ~ /GiB$/) { sub(/GiB$/, "", value); return value * 1024 }
      if (value ~ /MiB$/) { sub(/MiB$/, "", value); return value + 0 }
      if (value ~ /KiB$/) { sub(/KiB$/, "", value); return value / 1024 }
      if (value ~ /B$/) { sub(/B$/, "", value); return value / 1048576 }
      return value + 0
    }
    NR > 1 {
      cpu = $2
      gsub(/%$/, "", cpu)
      memory = to_mib($3)
      pids = $5 + 0
      if (cpu > peak_cpu) peak_cpu = cpu
      if (memory > peak_memory) peak_memory = memory
      if (pids > peak_pids) peak_pids = pids
      samples++
    }
    END {
      printf "statsSamples=%d\n", samples + 0
      printf "peakCpuPercent=%.2f\n", peak_cpu + 0
      printf "peakMemoryMiB=%.2f\n", peak_memory + 0
      printf "peakPids=%d\n", peak_pids + 0
    }
  ' "${stats_path}" >"${output_path}"
  if [[ -f "${gc_log_path}" ]]; then
    {
      printf "gcLogPath=%s\n" "${gc_log_path}"
      printf "gcLogBytes=%s\n" "$(wc -c <"${gc_log_path}" | tr -d '[:space:]')"
    } >>"${output_path}"
  else
    {
      printf "gcLogPath=%s\n" "${gc_log_path}"
      printf "gcLogBytes=0\n"
    } >>"${output_path}"
  fi
}
