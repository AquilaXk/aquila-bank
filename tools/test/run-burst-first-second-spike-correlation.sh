#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-burst-first-second-spike-correlation.sh [--print-plan]

Environment:
  BURST_SPIKE_NAME default burst-first-second-spike-<timestamp>
  BURST_SPIKE_RUN_ID default BURST_SPIKE_NAME
  BURST_SPIKE_K6_SUMMARY_JSON required k6 summary JSON
  BURST_SPIKE_RUNNER_LOG optional k6 runner log
  BURST_SPIKE_PROMETHEUS_SNAPSHOT_TSV optional metric/value snapshot TSV
  BURST_SPIKE_OUTPUT_DIR default build/reports/profiling/<name>
  BURST_SPIKE_WINDOW_SECONDS default 3

Snapshot metric keys:
  k6.vus.max
  k6.vus.active.max
  backend.cpu.max.percent
  postgres.cpu.max.percent
  db.hikari.pending.max
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

name="${BURST_SPIKE_NAME:-burst-first-second-spike-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${BURST_SPIKE_RUN_ID:-${name}}"
k6_summary_json="${BURST_SPIKE_K6_SUMMARY_JSON:-}"
runner_log="${BURST_SPIKE_RUNNER_LOG:-}"
snapshot_tsv="${BURST_SPIKE_PROMETHEUS_SNAPSHOT_TSV:-}"
output_dir="${BURST_SPIKE_OUTPUT_DIR:-build/reports/profiling/${name}}"
window_seconds="${BURST_SPIKE_WINDOW_SECONDS:-3}"
correlation_tsv="${output_dir}/${name}-first-${window_seconds}s-correlation.tsv"
report_md="${output_dir}/${name}-first-${window_seconds}s-correlation.md"

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_positive_integer "BURST_SPIKE_WINDOW_SECONDS" "${window_seconds}"

print_plan() {
  echo "[burst-first-second-spike] name=${name}"
  echo "[burst-first-second-spike] run_id=${run_id}"
  echo "[burst-first-second-spike] k6_summary_json=${k6_summary_json:-missing}"
  echo "[burst-first-second-spike] runner_log=${runner_log:-missing}"
  echo "[burst-first-second-spike] prometheus_snapshot=${snapshot_tsv:-missing}"
  echo "[burst-first-second-spike] window_seconds=${window_seconds}"
  echo "[burst-first-second-spike] correlation_tsv=${correlation_tsv}"
  echo "[burst-first-second-spike] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ -z "${k6_summary_json}" || ! -s "${k6_summary_json}" ]]; then
  echo "BURST_SPIKE_K6_SUMMARY_JSON is required: ${k6_summary_json:-missing}" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

metric_value() {
  local metric="$1"
  local field="$2"
  jq -r --arg metric "${metric}" --arg field "${field}" \
    '.metrics[$metric].values[$field] // "n/a"' "${k6_summary_json}"
}

metric_count() {
  local metric="$1"
  jq -r --arg metric "${metric}" '
    (.metrics[$metric].values // {}) as $values
    | if $values.count != null then
        $values.count
      elif ($values.passes != null or $values.fails != null) then
        (($values.passes // 0) + ($values.fails // 0))
      else
        0
      end
  ' "${k6_summary_json}"
}

snapshot_value() {
  local key="$1"
  if [[ -z "${snapshot_tsv}" || ! -s "${snapshot_tsv}" ]]; then
    echo "n/a"
    return 0
  fi
  awk -F '\t' -v key="${key}" '$1 == key {value = $2; found = 1} END {print found ? value : "n/a"}' "${snapshot_tsv}"
}

insufficient_vus_detected() {
  if [[ -n "${runner_log}" && -s "${runner_log}" ]] && grep -Fi "Insufficient VUs" "${runner_log}" >/dev/null; then
    echo "1"
  else
    echo "0"
  fi
}

mkdir -p "${output_dir}"

{
  printf "window_seconds\tmetric\tvalue\tsource\n"
  printf "%s\tdropped_iterations\t%s\tk6-summary\n" "${window_seconds}" "$(metric_count dropped_iterations)"
  printf "%s\tinterrupted_iterations\t%s\tk6-summary\n" "${window_seconds}" "$(metric_count interrupted_iterations)"
  printf "%s\thttp_reqs\t%s\tk6-summary\n" "${window_seconds}" "$(metric_count http_reqs)"
  printf "%s\ttransaction_429_rate\t%s\tk6-summary\n" "${window_seconds}" "$(metric_value aquila_transaction_429_rate rate)"
  printf "%s\ttransaction_503_rate\t%s\tk6-summary\n" "${window_seconds}" "$(metric_value aquila_transaction_503_rate rate)"
  printf "%s\thot_first_p999_ms\t%s\tk6-summary\n" "${window_seconds}" "$(metric_value aquila_transaction_hot_first_ms "p(99.9)")"
  printf "%s\thot_first_max_ms\t%s\tk6-summary\n" "${window_seconds}" "$(metric_value aquila_transaction_hot_first_ms max)"
  printf "%s\tinsufficient_vus_detected\t%s\trunner-log\n" "${window_seconds}" "$(insufficient_vus_detected)"
  printf "%s\tk6_vus_max\t%s\tprometheus-snapshot\n" "${window_seconds}" "$(snapshot_value k6.vus.max)"
  printf "%s\tk6_vus_active_max\t%s\tprometheus-snapshot\n" "${window_seconds}" "$(snapshot_value k6.vus.active.max)"
  printf "%s\tbackend_cpu_percent\t%s\tprometheus-snapshot\n" "${window_seconds}" "$(snapshot_value backend.cpu.max.percent)"
  printf "%s\tpostgres_cpu_percent\t%s\tprometheus-snapshot\n" "${window_seconds}" "$(snapshot_value postgres.cpu.max.percent)"
  printf "%s\thikari_pending\t%s\tprometheus-snapshot\n" "${window_seconds}" "$(snapshot_value db.hikari.pending.max)"
} >"${correlation_tsv}"

cat >"${report_md}" <<REPORT
# Burst First-Second Spike Correlation

## Summary

- name: ${name}
- run id: ${run_id}
- first window seconds: ${window_seconds}
- dropped iterations: $(metric_count dropped_iterations)
- interrupted iterations: $(metric_count interrupted_iterations)
- insufficient VUs detected: $(insufficient_vus_detected)
- hot first p99.9 ms: $(metric_value aquila_transaction_hot_first_ms "p(99.9)")
- hot first max ms: $(metric_value aquila_transaction_hot_first_ms max)

## Artifacts

- correlation TSV: ${correlation_tsv}
- k6 summary JSON: ${k6_summary_json}
- runner log: ${runner_log:-missing}
- Prometheus snapshot TSV: ${snapshot_tsv:-missing}

## Notes

- k6 summary 값은 전체 measured phase 기준이므로 first-window 원인 확정값이 아니라 VU 포화/429/latency를 함께 보존하는 방어용 artifact입니다.
- Prometheus snapshot이 있으면 첫 ${window_seconds}s 구간 query 결과를 같이 넣어 판단합니다.
REPORT

echo "${report_md}"
