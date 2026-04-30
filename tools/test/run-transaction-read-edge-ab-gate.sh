#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-edge-ab-gate.sh [--print-plan]

Environment:
  EDGE_AB_GATE_NAME             default transaction-read-edge-ab-<timestamp>
  EDGE_AB_RUN_ID                required run id shared by direct and edge runs
  EDGE_AB_DIRECT_SUMMARY_JSON   required k6 summary for direct backend path
  EDGE_AB_EDGE_SUMMARY_JSON     required k6 summary for public edge path
  EDGE_AB_NGINX_TIMING_TSV      optional TSV: path,status,limit_req_status,request_time_ms,upstream_response_time_ms
  EDGE_AB_OUTPUT_DIR            default build/reports/k6/<gate>
  EDGE_AB_MAX_ACCEPTED_P95_MS   default 350
  EDGE_AB_MAX_P95_DELTA_MS      default 120
  EDGE_AB_MAX_429_RATE          default 0.10
  EDGE_AB_MAX_DELAYED_RATE      default 0.25
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

name="${EDGE_AB_GATE_NAME:-transaction-read-edge-ab-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${EDGE_AB_RUN_ID:-}"
direct_summary="${EDGE_AB_DIRECT_SUMMARY_JSON:-}"
edge_summary="${EDGE_AB_EDGE_SUMMARY_JSON:-}"
nginx_timing="${EDGE_AB_NGINX_TIMING_TSV:-}"
output_dir="${EDGE_AB_OUTPUT_DIR:-build/reports/k6/${name}}"
max_accepted_p95_ms="${EDGE_AB_MAX_ACCEPTED_P95_MS:-350}"
max_p95_delta_ms="${EDGE_AB_MAX_P95_DELTA_MS:-120}"
max_429_rate="${EDGE_AB_MAX_429_RATE:-0.10}"
max_delayed_rate="${EDGE_AB_MAX_DELAYED_RATE:-0.25}"
summary_tsv="${output_dir}/${name}-edge-ab.tsv"
report_md="${output_dir}/${name}-edge-ab.md"

require_file() {
  local name="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${name} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

require_non_empty() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "${name} is required" >&2
    exit 1
  fi
}

require_non_negative_number() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be zero or greater: ${value}" >&2
    exit 1
  fi
}

number_greater_than() {
  awk -v value="$1" -v threshold="$2" 'BEGIN { exit !(value > threshold) }'
}

is_numeric_value() {
  [[ "$1" =~ ^[0-9]+([.][0-9]+)?$ ]]
}

max_numeric_value() {
  local result="0"
  local value
  for value in "$@"; do
    if is_numeric_value "${value}" && number_greater_than "${value}" "${result}"; then
      result="${value}"
    fi
  done
  echo "${result}"
}

metric_value() {
  local summary_json="$1"
  local metric="$2"
  local field="$3"
  jq -r --arg metric "${metric}" --arg field "${field}" \
    '.metrics[$metric].values[$field] // "0"' "${summary_json}"
}

accepted_p95() {
  local summary_json="$1"
  max_numeric_value \
    "$(metric_value "${summary_json}" aquila_transaction_hot_first_ms "p(95)")" \
    "$(metric_value "${summary_json}" aquila_transaction_hot_cursor_ms "p(95)")" \
    "$(metric_value "${summary_json}" aquila_transaction_hot_deep_cursor_ms "p(95)")" \
    "$(metric_value "${summary_json}" aquila_transaction_cold_first_ms "p(95)")" \
    "$(metric_value "${summary_json}" aquila_transaction_cold_cursor_ms "p(95)")" \
    "$(metric_value "${summary_json}" aquila_transaction_cold_deep_cursor_ms "p(95)")"
}

five_xx_count() {
  local summary_json="$1"
  awk \
    -v bad_gateway="$(metric_value "${summary_json}" aquila_transaction_502_count count)" \
    -v unavailable="$(metric_value "${summary_json}" aquila_transaction_503_count count)" \
    'BEGIN { print bad_gateway + unavailable }'
}

queue_delay_max_sample() {
  local file="$1"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "n/a"
    return
  fi
  awk -F '\t' '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "request_time_ms") request_col = i
        if ($i == "upstream_response_time_ms") upstream_col = i
      }
      next
    }
    request_col && upstream_col {
      delay = ($request_col + 0) - ($upstream_col + 0)
      if (delay > max) max = delay
    }
    END {
      if (max == "") print "0"; else printf "%.0f", max
    }
  ' "${file}"
}

print_plan() {
  echo "[transaction-read-edge-ab] name=${name}"
  echo "[transaction-read-edge-ab] run_id=${run_id:-missing}"
  echo "[transaction-read-edge-ab] direct_summary=${direct_summary:-missing}"
  echo "[transaction-read-edge-ab] edge_summary=${edge_summary:-missing}"
  echo "[transaction-read-edge-ab] nginx_timing=${nginx_timing:-missing}"
  echo "[transaction-read-edge-ab] max_accepted_p95_ms=${max_accepted_p95_ms}"
  echo "[transaction-read-edge-ab] max_p95_delta_ms=${max_p95_delta_ms}"
  echo "[transaction-read-edge-ab] max_429_rate=${max_429_rate}"
  echo "[transaction-read-edge-ab] max_delayed_rate=${max_delayed_rate}"
  echo "[transaction-read-edge-ab] summary_tsv=${summary_tsv}"
  echo "[transaction-read-edge-ab] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_non_empty "EDGE_AB_RUN_ID" "${run_id}"
  require_file "EDGE_AB_DIRECT_SUMMARY_JSON" "${direct_summary}"
  require_file "EDGE_AB_EDGE_SUMMARY_JSON" "${edge_summary}"
  exit 0
fi

require_non_empty "EDGE_AB_RUN_ID" "${run_id}"
require_file "EDGE_AB_DIRECT_SUMMARY_JSON" "${direct_summary}"
require_file "EDGE_AB_EDGE_SUMMARY_JSON" "${edge_summary}"
require_non_negative_number "EDGE_AB_MAX_ACCEPTED_P95_MS" "${max_accepted_p95_ms}"
require_non_negative_number "EDGE_AB_MAX_P95_DELTA_MS" "${max_p95_delta_ms}"
require_non_negative_number "EDGE_AB_MAX_429_RATE" "${max_429_rate}"
require_non_negative_number "EDGE_AB_MAX_DELAYED_RATE" "${max_delayed_rate}"
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

direct_p95="$(accepted_p95 "${direct_summary}")"
edge_p95="$(accepted_p95 "${edge_summary}")"
p95_delta="$(awk -v edge="${edge_p95}" -v direct="${direct_p95}" 'BEGIN { printf "%.0f", edge - direct }')"
direct_5xx="$(five_xx_count "${direct_summary}")"
edge_5xx="$(five_xx_count "${edge_summary}")"
direct_429="$(metric_value "${direct_summary}" aquila_transaction_429_rate rate)"
edge_429="$(metric_value "${edge_summary}" aquila_transaction_429_rate rate)"
edge_delayed_rate="$(metric_value "${edge_summary}" aquila_transaction_edge_delayed_rate rate)"
queue_delay_max="$(queue_delay_max_sample "${nginx_timing}")"

gate_status="pass"
if number_greater_than "${edge_p95}" "${max_accepted_p95_ms}" \
    || number_greater_than "${p95_delta}" "${max_p95_delta_ms}" \
    || number_greater_than "${edge_429}" "${max_429_rate}" \
    || number_greater_than "${edge_delayed_rate}" "${max_delayed_rate}" \
    || number_greater_than "${direct_5xx}" "0" \
    || number_greater_than "${edge_5xx}" "0"; then
  gate_status="fail"
fi

mkdir -p "${output_dir}"
{
  printf "path\taccepted_p95_ms\ttotal_429_rate\tedge_429_rate\tbackend_429_rate\tdelayed_rate\tdelayed_count\t5xx_count\n"
  printf "direct\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${direct_p95}" \
    "${direct_429}" \
    "$(metric_value "${direct_summary}" aquila_transaction_edge_429_rate rate)" \
    "$(metric_value "${direct_summary}" aquila_transaction_backend_429_rate rate)" \
    "$(metric_value "${direct_summary}" aquila_transaction_edge_delayed_rate rate)" \
    "$(metric_value "${direct_summary}" aquila_transaction_edge_delayed_count count)" \
    "${direct_5xx}"
  printf "edge\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${edge_p95}" \
    "${edge_429}" \
    "$(metric_value "${edge_summary}" aquila_transaction_edge_429_rate rate)" \
    "$(metric_value "${edge_summary}" aquila_transaction_backend_429_rate rate)" \
    "${edge_delayed_rate}" \
    "$(metric_value "${edge_summary}" aquila_transaction_edge_delayed_count count)" \
    "${edge_5xx}"
} >"${summary_tsv}"

cat >"${report_md}" <<REPORT
# Transaction Read Direct Backend vs Public Edge A/B Gate

## Summary

- gate_status=${gate_status}
- run id: ${run_id}
- accepted p95 target: < ${max_accepted_p95_ms}ms
- public edge p95 delta target: <= ${max_p95_delta_ms}ms
- 429 target: < ${max_429_rate}
- delayed ratio target: < ${max_delayed_rate}

## Comparison

| Metric | Value |
| --- | ---: |
| direct accepted p95 | ${direct_p95}ms |
| edge accepted p95 | ${edge_p95}ms |
| accepted p95 delta | ${p95_delta}ms |
| direct 5xx count | ${direct_5xx} |
| edge 5xx count | ${edge_5xx} |
| edge delayed rate | ${edge_delayed_rate} |
| nginx queue delay max sample | ${queue_delay_max}ms |

## Accepted Path Overhead Split

- Nginx queue delay: \`request_time_ms - upstream_response_time_ms\` from \`${nginx_timing:-missing}\`.
- Nginx upstream response time: \`upstream_response_time_ms\` from access log TSV.
- backend controller/service timer: aquila_transaction_read_http_stage_seconds, labels endpoint/stage/outcome.
- repository timer: aquila_transaction_query_latency_seconds, labels query_shape/outcome.
- serialization proxy timer: response_mapping stage in aquila_transaction_read_http_stage_seconds.

## Artifacts

- direct summary: ${direct_summary}
- edge summary: ${edge_summary}
- summary TSV: ${summary_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read edge A/B gate failed: ${report_md}" >&2
  exit 1
fi
