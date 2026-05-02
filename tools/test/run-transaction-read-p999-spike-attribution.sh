#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-p999-spike-attribution.sh [--print-plan]

Environment:
  P999_ATTRIBUTION_NAME             default transaction-read-p999-attribution-<timestamp>
  P999_ATTRIBUTION_K6_SUMMARY_JSON  required k6 summary JSON
  P999_ATTRIBUTION_LAYER_TSV        optional TSV: layer,metric,value,source,note
  P999_ATTRIBUTION_OUTPUT_DIR       default build/reports/profiling/<name>
  P999_ATTRIBUTION_WARN_MS          default 180
  P999_ATTRIBUTION_FAIL_MS          default 500
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

name="${P999_ATTRIBUTION_NAME:-transaction-read-p999-attribution-$(date +%Y-%m-%d-%H%M%S)}"
summary_json="${P999_ATTRIBUTION_K6_SUMMARY_JSON:-}"
layer_tsv="${P999_ATTRIBUTION_LAYER_TSV:-}"
output_dir="${P999_ATTRIBUTION_OUTPUT_DIR:-build/reports/profiling/${name}}"
warn_ms="${P999_ATTRIBUTION_WARN_MS:-180}"
fail_ms="${P999_ATTRIBUTION_FAIL_MS:-500}"
attribution_tsv="${output_dir}/${name}-p999-attribution.tsv"
report_md="${output_dir}/${name}-p999-attribution.md"

require_positive_number() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be a positive number: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value > 0) }' || {
    echo "${name} must be greater than zero: ${value}" >&2
    exit 1
  }
}

number_greater_than() {
  awk -v value="$1" -v threshold="$2" 'BEGIN { exit !(value > threshold) }'
}

require_positive_number "P999_ATTRIBUTION_WARN_MS" "${warn_ms}"
require_positive_number "P999_ATTRIBUTION_FAIL_MS" "${fail_ms}"
if number_greater_than "${warn_ms}" "${fail_ms}"; then
  echo "P999_ATTRIBUTION_WARN_MS must be less than or equal to P999_ATTRIBUTION_FAIL_MS" >&2
  exit 1
fi

print_plan() {
  echo "[transaction-read-p999-attribution] name=${name}"
  echo "[transaction-read-p999-attribution] k6_summary_json=${summary_json:-missing}"
  echo "[transaction-read-p999-attribution] layer_tsv=${layer_tsv:-missing}"
  echo "[transaction-read-p999-attribution] output_dir=${output_dir}"
  echo "[transaction-read-p999-attribution] warn_ms=${warn_ms}"
  echo "[transaction-read-p999-attribution] fail_ms=${fail_ms}"
  echo "[transaction-read-p999-attribution] layers=nginx,network,authorization,serialization,db,backend"
  echo "[transaction-read-p999-attribution] attribution_tsv=${attribution_tsv}"
  echo "[transaction-read-p999-attribution] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  if [[ -z "${summary_json}" || ! -s "${summary_json}" ]]; then
    echo "P999_ATTRIBUTION_K6_SUMMARY_JSON is required: ${summary_json:-missing}" >&2
    exit 1
  fi
  exit 0
fi

if [[ -z "${summary_json}" || ! -s "${summary_json}" ]]; then
  echo "P999_ATTRIBUTION_K6_SUMMARY_JSON is required: ${summary_json:-missing}" >&2
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
    '.metrics[$metric].values[$field] // "n/a"' "${summary_json}"
}

mkdir -p "${output_dir}"

http_p999="$(metric_value http_req_duration "p(99.9)")"
http_max="$(metric_value http_req_duration max)"
edge_delayed_rate="$(metric_value aquila_transaction_edge_delayed_rate rate)"
edge_delayed_count="$(metric_value aquila_transaction_edge_delayed_count count)"
retry_after_p95="$(metric_value aquila_transaction_retry_after_sleep_ms "p(95)")"
retry_after_max="$(metric_value aquila_transaction_retry_after_sleep_ms max)"

tail_status="pass"
if [[ "${http_max}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  if number_greater_than "${http_max}" "${fail_ms}"; then
    tail_status="fail"
  elif number_greater_than "${http_max}" "${warn_ms}"; then
    tail_status="warn"
  fi
fi

{
  printf "layer\tmetric\tvalue\tsource\tnote\n"
  printf "network\thttp_req_duration_p999_ms\t%s\tk6:http_req_duration\tclient-visible total path\n" "${http_p999}"
  printf "network\thttp_req_duration_max_ms\t%s\tk6:http_req_duration\tclient-visible total path\n" "${http_max}"
  printf "nginx\tedge_delayed_rate\t%s\tk6:aquila_transaction_edge_delayed_rate\tdelayed 200 queue dependency\n" "${edge_delayed_rate}"
  printf "nginx\tedge_delayed_count\t%s\tk6:aquila_transaction_edge_delayed_count\tdelayed 200 count\n" "${edge_delayed_count}"
  printf "nginx\tretry_after_p95_ms\t%s\tk6:aquila_transaction_retry_after_sleep_ms\tclient backpressure sleep\n" "${retry_after_p95}"
  printf "nginx\tretry_after_max_ms\t%s\tk6:aquila_transaction_retry_after_sleep_ms\tclient backpressure sleep\n" "${retry_after_max}"
  if [[ -n "${layer_tsv}" && -s "${layer_tsv}" ]]; then
    awk -F '\t' 'NR > 1 { print }' "${layer_tsv}"
  fi
} >"${attribution_tsv}"

cat >"${report_md}" <<REPORT
# Transaction Read p99.9 Spike Attribution

## Summary

- tail_status=${tail_status}
- warn threshold ms: ${warn_ms}
- fail threshold ms: ${fail_ms}
- max client-visible latency ms: ${http_max}
- p99.9 client-visible latency ms: ${http_p999}
- edge delayed rate: ${edge_delayed_rate}
- edge delayed count: ${edge_delayed_count}

## Attribution Scope

- Nginx delayed queue, network, authorization, serialization, DB query, backend timing을 한 artifact로 분리합니다.
- 이 report는 원인 확정이 아니라 다음 profiler/JFR/Prometheus 확인 지점을 좁히는 경계 자료입니다.

## Artifacts

- attribution TSV: ${attribution_tsv}
- k6 summary JSON: ${summary_json}
- layer TSV: ${layer_tsv:-missing}
REPORT

echo "${report_md}"

if [[ "${tail_status}" == "fail" ]]; then
  echo "p99.9 attribution exceeded fail threshold: max=${http_max} fail=${fail_ms}" >&2
  exit 1
fi
