#!/usr/bin/env bash
set -euo pipefail

echo "[t3micro-telemetry-peak] shell syntax"
bash -n tools/test/t3micro-cgroup-telemetry-lib.sh

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
stats_path="${temp_dir}/docker-stats.tsv"
summary_path="${temp_dir}/peak-summary.properties"
gc_log_path="${temp_dir}/gc.log"

cat >"${stats_path}" <<'TSV'
timestamp	cpu_percent	memory_usage	memory_limit	pids
2026-04-27T00:00:00Z	12.50%	512MiB	1GiB	32
2026-04-27T00:00:05Z	87.25%	1.50GB	2GB	48
2026-04-27T00:00:10Z	3.00%	768 MB	2 GB	12
TSV
printf "gc sample\n" >"${gc_log_path}"

source tools/test/t3micro-cgroup-telemetry-lib.sh
t3micro_write_peak_summary "${stats_path}" "${gc_log_path}" "${summary_path}"

grep -F "statsSamples=3" "${summary_path}" >/dev/null
grep -F "peakCpuPercent=87.25" "${summary_path}" >/dev/null
grep -F "peakMemoryMiB=1536.00" "${summary_path}" >/dev/null
grep -F "peakPids=48" "${summary_path}" >/dev/null
grep -F "gcLogBytes=10" "${summary_path}" >/dev/null
