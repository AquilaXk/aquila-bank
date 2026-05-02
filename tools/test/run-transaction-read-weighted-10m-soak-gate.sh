#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-weighted-10m-soak-gate.sh [--print-plan]

Environment:
  WEIGHTED_SOAK_10M_NAME                  default transaction-read-weighted-10m-<timestamp>
  WEIGHTED_SOAK_10M_DURATION              default 10m, minimum 10m
  WEIGHTED_SOAK_10M_SUMMARY_JSON          required k6 weighted-random summary json
  WEIGHTED_SOAK_10M_ACCESS_LOG            required Nginx JSON access log for 499/502 correlation
  WEIGHTED_SOAK_10M_HIKARI_LOG            optional backend log containing Hikari warnings
  WEIGHTED_SOAK_10M_RESOURCE_SNAPSHOT_TSV optional TSV: component,cpu_percent,memory_mib,note
  WEIGHTED_SOAK_10M_OUTPUT_DIR            default build/reports/k6/<gate>
  WEIGHTED_SOAK_10M_MAX_ACCEPTED_P95_MS   default 350
  WEIGHTED_SOAK_10M_MAX_TOTAL_429_RATE    default 0.05
  WEIGHTED_SOAK_10M_MAX_EDGE_429_RATE     default 0.05
  WEIGHTED_SOAK_10M_MAX_BACKEND_429_RATE  default 0.05
  WEIGHTED_SOAK_10M_MAX_RETRY_AFTER_P95_MS default 250
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

name="${WEIGHTED_SOAK_10M_NAME:-transaction-read-weighted-10m-$(date +%Y-%m-%d-%H%M%S)}"
duration="${WEIGHTED_SOAK_10M_DURATION:-10m}"
summary_json="${WEIGHTED_SOAK_10M_SUMMARY_JSON:-}"
access_log="${WEIGHTED_SOAK_10M_ACCESS_LOG:-}"
hikari_log="${WEIGHTED_SOAK_10M_HIKARI_LOG:-}"
resource_snapshot="${WEIGHTED_SOAK_10M_RESOURCE_SNAPSHOT_TSV:-}"
output_dir="${WEIGHTED_SOAK_10M_OUTPUT_DIR:-build/reports/k6/${name}}"
max_accepted_p95_ms="${WEIGHTED_SOAK_10M_MAX_ACCEPTED_P95_MS:-350}"
max_total_429_rate="${WEIGHTED_SOAK_10M_MAX_TOTAL_429_RATE:-0.05}"
max_edge_429_rate="${WEIGHTED_SOAK_10M_MAX_EDGE_429_RATE:-0.05}"
max_backend_429_rate="${WEIGHTED_SOAK_10M_MAX_BACKEND_429_RATE:-0.05}"
max_retry_after_p95_ms="${WEIGHTED_SOAK_10M_MAX_RETRY_AFTER_P95_MS:-250}"
report_md="${output_dir}/${name}-weighted-10m-soak.md"
failure_name="${name}-failure-correlation"
failure_output_dir="${output_dir}/failure-correlation"
failure_tsv="${failure_output_dir}/${failure_name}-failure-correlation.tsv"
failure_report="${failure_output_dir}/${failure_name}-failure-correlation.md"

require_duration() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*m$ ]]; then
    echo "${name} must use minutes such as 10m: ${value}" >&2
    exit 1
  fi
  local minutes="${value%m}"
  if ((minutes < 10)); then
    echo "${name} must be at least 10m: ${value}" >&2
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

require_file() {
  local name="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${name} is required and must be a non-empty file: ${file:-missing}" >&2
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
  local metric="$1"
  local field="$2"
  jq -r --arg metric "${metric}" --arg field "${field}" \
    '.metrics[$metric].values[$field] // "0"' "${summary_json}"
}

accepted_p95() {
  max_numeric_value \
    "$(metric_value aquila_transaction_hot_first_ms "p(95)")" \
    "$(metric_value aquila_transaction_hot_cursor_ms "p(95)")" \
    "$(metric_value aquila_transaction_hot_deep_cursor_ms "p(95)")" \
    "$(metric_value aquila_transaction_cold_first_ms "p(95)")" \
    "$(metric_value aquila_transaction_cold_cursor_ms "p(95)")" \
    "$(metric_value aquila_transaction_cold_deep_cursor_ms "p(95)")"
}

hikari_warning_count() {
  if [[ -z "${hikari_log}" || ! -s "${hikari_log}" ]]; then
    echo "0"
    return
  fi
  grep -E "Failed to validate connection|connection has been closed|Connection is not available" "${hikari_log}" | wc -l | tr -d ' '
}

failure_tsv_sum() {
  local column_name="$1"
  if [[ ! -s "${failure_tsv}" ]]; then
    echo "0"
    return
  fi
  awk -F '\t' -v column_name="${column_name}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == column_name) col = i
      }
      next
    }
    col {
      total += $col
    }
    END { print total + 0 }
  ' "${failure_tsv}"
}

resource_table() {
  local file="$1"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "| component | cpu_percent | memory_mib | note |"
    echo "| --- | ---: | ---: | --- |"
    echo "| n/a | n/a | n/a | resource snapshot missing |"
    return
  fi
  awk -F '\t' '
    BEGIN {
      print "| component | cpu_percent | memory_mib | note |"
      print "| --- | ---: | ---: | --- |"
    }
    NR > 1 {
      printf "| %s | %s | %s | %s |\n", $1, $2, $3, $4
    }
  ' "${file}"
}

print_plan() {
  echo "[transaction-read-weighted-10m] name=${name}"
  echo "[transaction-read-weighted-10m] duration=${duration}"
  echo "[transaction-read-weighted-10m] summary_json=${summary_json:-missing}"
  echo "[transaction-read-weighted-10m] access_log=${access_log:-missing}"
  echo "[transaction-read-weighted-10m] hikari_log=${hikari_log:-missing}"
  echo "[transaction-read-weighted-10m] resource_snapshot=${resource_snapshot:-missing}"
  echo "[transaction-read-weighted-10m] max_accepted_p95_ms=${max_accepted_p95_ms}"
  echo "[transaction-read-weighted-10m] max_total_429_rate=${max_total_429_rate}"
  echo "[transaction-read-weighted-10m] max_edge_429_rate=${max_edge_429_rate}"
  echo "[transaction-read-weighted-10m] max_backend_429_rate=${max_backend_429_rate}"
  echo "[transaction-read-weighted-10m] max_retry_after_p95_ms=${max_retry_after_p95_ms}"
  echo "[transaction-read-weighted-10m] live_soak_required=true"
  echo "[transaction-read-weighted-10m] failure_report=${failure_report}"
  echo "[transaction-read-weighted-10m] report_md=${report_md}"
}

require_duration "WEIGHTED_SOAK_10M_DURATION" "${duration}"
require_non_negative_number "WEIGHTED_SOAK_10M_MAX_ACCEPTED_P95_MS" "${max_accepted_p95_ms}"
require_non_negative_number "WEIGHTED_SOAK_10M_MAX_TOTAL_429_RATE" "${max_total_429_rate}"
require_non_negative_number "WEIGHTED_SOAK_10M_MAX_EDGE_429_RATE" "${max_edge_429_rate}"
require_non_negative_number "WEIGHTED_SOAK_10M_MAX_BACKEND_429_RATE" "${max_backend_429_rate}"
require_non_negative_number "WEIGHTED_SOAK_10M_MAX_RETRY_AFTER_P95_MS" "${max_retry_after_p95_ms}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "WEIGHTED_SOAK_10M_SUMMARY_JSON" "${summary_json}"
  require_file "WEIGHTED_SOAK_10M_ACCESS_LOG" "${access_log}"
  exit 0
fi

require_file "WEIGHTED_SOAK_10M_SUMMARY_JSON" "${summary_json}"
require_file "WEIGHTED_SOAK_10M_ACCESS_LOG" "${access_log}"
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
set +e
FAILURE_CORRELATION_NAME="${failure_name}" \
FAILURE_CORRELATION_ACCESS_LOG="${access_log}" \
FAILURE_CORRELATION_OUTPUT_DIR="${failure_output_dir}" \
  tools/test/run-transaction-read-nginx-failure-correlation-gate.sh >/dev/null
failure_status_code=$?
set -e

accepted_p95_ms="$(accepted_p95)"
total_429_rate="$(metric_value aquila_transaction_429_rate rate)"
edge_429_rate="$(metric_value aquila_transaction_edge_429_rate rate)"
backend_429_rate="$(metric_value aquila_transaction_backend_429_rate rate)"
backend_429_count="$(metric_value aquila_transaction_backend_429_count count)"
unknown_429_count="$(metric_value aquila_transaction_unknown_429_count count)"
edge_delayed_rate="$(metric_value aquila_transaction_edge_delayed_rate rate)"
five_xx_count="$(
  awk \
    -v bad_gateway="$(metric_value aquila_transaction_502_count count)" \
    -v unavailable="$(metric_value aquila_transaction_503_count count)" \
    'BEGIN { print bad_gateway + unavailable }'
)"
nginx_499_count="$(failure_tsv_sum nginx_499_count)"
nginx_502_count="$(failure_tsv_sum upstream_502_count)"
hikari_warnings="$(hikari_warning_count)"
adaptive_multiplier_p95="$(metric_value aquila_transaction_retry_after_adaptive_multiplier "p(95)")"
adaptive_multiplier_max="$(metric_value aquila_transaction_retry_after_adaptive_multiplier max)"
reject_streak_max="$(metric_value aquila_transaction_retry_after_reject_streak max)"
retry_after_sleep_p95="$(metric_value aquila_transaction_retry_after_sleep_ms "p(95)")"
preemptive_pacing_count="$(metric_value aquila_transaction_preemptive_pacing_count count)"
preemptive_pacing_sleep_p95="$(metric_value aquila_transaction_preemptive_pacing_sleep_ms "p(95)")"
preemptive_pacing_sleep_max="$(metric_value aquila_transaction_preemptive_pacing_sleep_ms max)"

gate_status="pass"
if number_greater_than "${accepted_p95_ms}" "${max_accepted_p95_ms}" \
    || number_greater_than "${total_429_rate}" "${max_total_429_rate}" \
    || number_greater_than "${edge_429_rate}" "${max_edge_429_rate}" \
    || number_greater_than "${backend_429_rate}" "${max_backend_429_rate}" \
    || number_greater_than "${unknown_429_count}" "0" \
    || number_greater_than "${edge_delayed_rate}" "0.25" \
    || number_greater_than "${five_xx_count}" "0" \
    || number_greater_than "${nginx_499_count}" "0" \
    || number_greater_than "${nginx_502_count}" "0" \
    || number_greater_than "${hikari_warnings}" "0" \
    || number_greater_than "${retry_after_sleep_p95}" "${max_retry_after_p95_ms}" \
    || [[ "${failure_status_code}" -ne 0 ]]; then
  gate_status="fail"
fi

cat >"${report_md}" <<REPORT
# Transaction Read Weighted 10m Soak Gate

## Summary

- gate_status=${gate_status}
- duration: ${duration}
- target: accepted p95 < ${max_accepted_p95_ms}ms, total/edge/backend 429 <= ${max_total_429_rate}/${max_edge_429_rate}/${max_backend_429_rate}, Retry-After p95 <= ${max_retry_after_p95_ms}ms, unknown 429/499/5xx/Hikari warning = 0
- live criterion: OCI 1억 row live run 기준

## SLO

| Metric | Value |
| --- | ---: |
| accepted p95 ms | ${accepted_p95_ms} |
| total 429 rate | ${total_429_rate} |
| edge 429 rate | ${edge_429_rate} |
| backend 429 rate | ${backend_429_rate} |
| backend 429 count | ${backend_429_count} |
| unknown 429 count | ${unknown_429_count} |
| edge delayed rate | ${edge_delayed_rate} |
| 499 count | ${nginx_499_count} |
| Nginx upstream 502 count | ${nginx_502_count} |
| 5xx count | ${five_xx_count} |
| Hikari validation warnings | ${hikari_warnings} |
| retry-after sleep p95 ms | ${retry_after_sleep_p95} |
| retry-after adaptive multiplier p95 | ${adaptive_multiplier_p95} |
| retry-after adaptive multiplier max | ${adaptive_multiplier_max} |
| retry-after reject streak max | ${reject_streak_max} |
| preemptive pacing count | ${preemptive_pacing_count} |
| preemptive pacing sleep p95 ms | ${preemptive_pacing_sleep_p95} |
| preemptive pacing sleep max ms | ${preemptive_pacing_sleep_max} |

## Resource Snapshot

$(resource_table "${resource_snapshot}")

## Artifacts

- summary json: ${summary_json}
- failure correlation report: ${failure_report}
- Hikari log: ${hikari_log:-missing}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read weighted 10m soak failed: ${report_md}" >&2
  exit 1
fi
