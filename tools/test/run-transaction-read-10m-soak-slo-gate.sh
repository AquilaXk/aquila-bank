#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-10m-soak-slo-gate.sh [--print-plan]

Environment:
  SOAK_10M_GATE_NAME              default transaction-read-10m-soak-<timestamp>
  SOAK_10M_DURATION               default 10m, minimum 10m
  SOAK_10M_SUMMARY_JSON           required k6 summary json
  SOAK_10M_NGINX_STATUS_TSV       optional TSV: run,status,limit_req_status,count
  SOAK_10M_HIKARI_LOG             optional backend log containing Hikari warnings
  SOAK_10M_RESOURCE_SNAPSHOT_TSV  optional TSV: component,cpu_percent,memory_mib,note
  SOAK_10M_OUTPUT_DIR             default build/reports/k6/<gate>
  SOAK_10M_MAX_ACCEPTED_P95_MS    default 350
  SOAK_10M_MAX_429_RATE           default 0.10
  SOAK_10M_MAX_DELAYED_RATE       default 0.25
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

name="${SOAK_10M_GATE_NAME:-transaction-read-10m-soak-$(date +%Y-%m-%d-%H%M%S)}"
duration="${SOAK_10M_DURATION:-10m}"
summary_json="${SOAK_10M_SUMMARY_JSON:-}"
nginx_status_tsv="${SOAK_10M_NGINX_STATUS_TSV:-}"
hikari_log="${SOAK_10M_HIKARI_LOG:-}"
resource_snapshot="${SOAK_10M_RESOURCE_SNAPSHOT_TSV:-}"
output_dir="${SOAK_10M_OUTPUT_DIR:-build/reports/k6/${name}}"
max_accepted_p95_ms="${SOAK_10M_MAX_ACCEPTED_P95_MS:-350}"
max_429_rate="${SOAK_10M_MAX_429_RATE:-0.10}"
max_delayed_rate="${SOAK_10M_MAX_DELAYED_RATE:-0.25}"
report_md="${output_dir}/${name}-10m-soak.md"

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

nginx_count() {
  local status="$1"
  if [[ -z "${nginx_status_tsv}" || ! -s "${nginx_status_tsv}" ]]; then
    echo "0"
    return
  fi
  awk -F '\t' -v status="${status}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "status") status_col = i
        if ($i == "count") count_col = i
      }
      next
    }
    $status_col == status {
      total += $count_col
    }
    END { print total + 0 }
  ' "${nginx_status_tsv}"
}

hikari_warning_count() {
  if [[ -z "${hikari_log}" || ! -s "${hikari_log}" ]]; then
    echo "0"
    return
  fi
  grep -E "Failed to validate connection|connection has been closed|Connection is not available" "${hikari_log}" | wc -l | tr -d ' '
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
  echo "[transaction-read-10m-soak] name=${name}"
  echo "[transaction-read-10m-soak] duration=${duration}"
  echo "[transaction-read-10m-soak] summary_json=${summary_json:-missing}"
  echo "[transaction-read-10m-soak] nginx_status=${nginx_status_tsv:-missing}"
  echo "[transaction-read-10m-soak] hikari_log=${hikari_log:-missing}"
  echo "[transaction-read-10m-soak] resource_snapshot=${resource_snapshot:-missing}"
  echo "[transaction-read-10m-soak] max_accepted_p95_ms=${max_accepted_p95_ms}"
  echo "[transaction-read-10m-soak] max_429_rate=${max_429_rate}"
  echo "[transaction-read-10m-soak] max_delayed_rate=${max_delayed_rate}"
  echo "[transaction-read-10m-soak] report_md=${report_md}"
}

require_duration "SOAK_10M_DURATION" "${duration}"
require_non_negative_number "SOAK_10M_MAX_ACCEPTED_P95_MS" "${max_accepted_p95_ms}"
require_non_negative_number "SOAK_10M_MAX_429_RATE" "${max_429_rate}"
require_non_negative_number "SOAK_10M_MAX_DELAYED_RATE" "${max_delayed_rate}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "SOAK_10M_SUMMARY_JSON" "${summary_json}"
  exit 0
fi

require_file "SOAK_10M_SUMMARY_JSON" "${summary_json}"
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

accepted_p95_ms="$(accepted_p95 "${summary_json}")"
total_429_rate="$(metric_value "${summary_json}" aquila_transaction_429_rate rate)"
edge_delayed_rate="$(metric_value "${summary_json}" aquila_transaction_edge_delayed_rate rate)"
five_xx_count="$(
  awk \
    -v bad_gateway="$(metric_value "${summary_json}" aquila_transaction_502_count count)" \
    -v unavailable="$(metric_value "${summary_json}" aquila_transaction_503_count count)" \
    'BEGIN { print bad_gateway + unavailable }'
)"
nginx_499_count="$(nginx_count "499")"
hikari_warnings="$(hikari_warning_count)"

gate_status="pass"
if number_greater_than "${accepted_p95_ms}" "${max_accepted_p95_ms}" \
    || number_greater_than "${total_429_rate}" "${max_429_rate}" \
    || number_greater_than "${edge_delayed_rate}" "${max_delayed_rate}" \
    || number_greater_than "${five_xx_count}" "0" \
    || number_greater_than "${nginx_499_count}" "0" \
    || number_greater_than "${hikari_warnings}" "0"; then
  gate_status="fail"
fi

mkdir -p "${output_dir}"
cat >"${report_md}" <<REPORT
# Transaction Read 10m Soak SLO Gate

## Summary

- gate_status=${gate_status}
- duration: ${duration}
- target: p95 < ${max_accepted_p95_ms}ms, 429 < ${max_429_rate}, edge delayed < ${max_delayed_rate}, 499/5xx/Hikari warning = 0

## SLO

| Metric | Value |
| --- | ---: |
| accepted p95 ms | ${accepted_p95_ms} |
| total 429 rate | ${total_429_rate} |
| edge delayed rate | ${edge_delayed_rate} |
| 499 count | ${nginx_499_count} |
| 5xx count | ${five_xx_count} |
| Hikari validation warnings | ${hikari_warnings} |

## Resource Snapshot

$(resource_table "${resource_snapshot}")

## Artifacts

- summary json: ${summary_json}
- nginx status TSV: ${nginx_status_tsv:-missing}
- Hikari log: ${hikari_log:-missing}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read 10m soak SLO failed: ${report_md}" >&2
  exit 1
fi
