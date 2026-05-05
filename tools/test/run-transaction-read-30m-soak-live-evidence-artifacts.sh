#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-30m-soak-live-evidence-artifacts.sh [--print-plan]

Environment:
  SOAK_30M_ARTIFACTS_NAME                     default transaction-read-30m-soak-live-artifacts-<timestamp>
  SOAK_30M_ARTIFACTS_RUN_ID                   default same as name
  SOAK_30M_ARTIFACTS_DURATION_MIN             default 30, minimum 30
  SOAK_30M_ARTIFACTS_OUTPUT_DIR               default build/reports/k6/<name>
  SOAK_30M_ARTIFACTS_K6_SUMMARY_JSON          required k6 summary JSON from the live soak
  SOAK_30M_ARTIFACTS_NGINX_ACCESS_LOG         required Nginx JSON access log for the same run id
  SOAK_30M_ARTIFACTS_SPRING_METRICS_REF       required Spring/Prometheus metrics artifact
  SOAK_30M_ARTIFACTS_HIKARI_LOG               required backend/Hikari log artifact
  SOAK_30M_ARTIFACTS_POSTGRES_WAIT_REF        required PostgreSQL wait sampler TSV
  SOAK_30M_ARTIFACTS_TIMELINE_REF             required p99.9/Hikari/PG/GC timeline TSV
  SOAK_30M_ARTIFACTS_POSTGRES_CHECKPOINT_REF  required PostgreSQL checkpoint TSV
  SOAK_30M_ARTIFACTS_POSTGRES_TEMP_FILE_REF   required PostgreSQL temp file TSV
  SOAK_30M_ARTIFACTS_HIKARI_CONFIG_REF        required Hikari/PostgreSQL/NAT timeout TSV
  SOAK_30M_ARTIFACTS_POSTGRES_TEMP_FILE_DELTA_MAX default 0
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

name="${SOAK_30M_ARTIFACTS_NAME:-transaction-read-30m-soak-live-artifacts-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${SOAK_30M_ARTIFACTS_RUN_ID:-${name}}"
executed_at_utc="${SOAK_30M_ARTIFACTS_EXECUTED_AT_UTC:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
duration_min="${SOAK_30M_ARTIFACTS_DURATION_MIN:-30}"
source_ips="${SOAK_30M_ARTIFACTS_SOURCE_IPS:-1}"
output_dir="${SOAK_30M_ARTIFACTS_OUTPUT_DIR:-build/reports/k6/${name}}"

k6_summary_json="${SOAK_30M_ARTIFACTS_K6_SUMMARY_JSON:-}"
nginx_access_log="${SOAK_30M_ARTIFACTS_NGINX_ACCESS_LOG:-}"
spring_metrics_ref="${SOAK_30M_ARTIFACTS_SPRING_METRICS_REF:-}"
hikari_log="${SOAK_30M_ARTIFACTS_HIKARI_LOG:-}"
postgres_wait_ref="${SOAK_30M_ARTIFACTS_POSTGRES_WAIT_REF:-}"
timeline_ref="${SOAK_30M_ARTIFACTS_TIMELINE_REF:-}"
postgres_checkpoint_ref="${SOAK_30M_ARTIFACTS_POSTGRES_CHECKPOINT_REF:-}"
postgres_temp_file_ref="${SOAK_30M_ARTIFACTS_POSTGRES_TEMP_FILE_REF:-}"
hikari_config_ref="${SOAK_30M_ARTIFACTS_HIKARI_CONFIG_REF:-}"
postgres_temp_file_delta_max="${SOAK_30M_ARTIFACTS_POSTGRES_TEMP_FILE_DELTA_MAX:-0}"

nginx_aggregate_dir="${output_dir}/nginx-access-aggregate"
nginx_aggregate_tsv="${nginx_aggregate_dir}/${name}-nginx-access-aggregate.tsv"
nginx_upstream_latency_ref="${output_dir}/nginx-upstream-latency.tsv"
hikari_zero_warning_soak_ref="${output_dir}/hikari-zero-warning-soak.md"
failure_reason_ref="${output_dir}/failure-reason.env"
failure_report_ref="${output_dir}/${name}-30m-soak-live-evidence-failure.md"
manifest_dir="${output_dir}/manifest"
manifest_tsv="${manifest_dir}/${name}-30m-soak-live-evidence-manifest.tsv"

require_non_negative_integer() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${key} must be a non-negative integer: ${value}" >&2
    exit 1
  fi
}

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

number_greater_than() {
  awk -v left="$1" -v right="$2" 'BEGIN { exit !(left > right) }'
}

max_numeric_value() {
  local result="0"
  local value
  for value in "$@"; do
    if [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]] && number_greater_than "${value}" "${result}"; then
      result="${value}"
    fi
  done
  echo "${result}"
}

metric_value() {
  local metric="$1"
  local field="$2"
  jq -r --arg metric "${metric}" --arg field "${field}" \
    '.metrics[$metric].values[$field] // "0"' "${k6_summary_json}"
}

latency_percentile() {
  local field="$1"
  max_numeric_value \
    "$(metric_value aquila_transaction_hot_first_ms "${field}")" \
    "$(metric_value aquila_transaction_hot_cursor_ms "${field}")" \
    "$(metric_value aquila_transaction_hot_deep_cursor_ms "${field}")" \
    "$(metric_value aquila_transaction_cold_first_ms "${field}")" \
    "$(metric_value aquila_transaction_cold_cursor_ms "${field}")" \
    "$(metric_value aquila_transaction_cold_deep_cursor_ms "${field}")"
}

tsv_metric_field() {
  local file="$1"
  local key="$2"
  local field="$3"
  local fallback_field="${4:-value}"
  awk -F '\t' -v key="${key}" -v field="${field}" -v fallback_field="${fallback_field}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "metric") metric_col = i
        if ($i == field) field_col = i
        if ($i == fallback_field) fallback_col = i
      }
      next
    }
    (!metric_col && $1 == key) || (metric_col && $metric_col == key) {
      if (field_col && $field_col != "") {
        print $field_col
      } else if (fallback_col && $fallback_col != "") {
        print $fallback_col
      } else {
        print $2
      }
      found = 1
      exit
    }
    END { if (!found) print "0" }
  ' "${file}"
}

tsv_value() {
  local file="$1"
  local key="$2"
  tsv_metric_field "${file}" "${key}" "delta" "value"
}

hikari_config_value() {
  local key="$1"
  tsv_value "${hikari_config_ref}" "${key}"
}

hikari_warning_count() {
  { grep -E "Failed to validate connection|This connection has been closed|connection has been closed|Connection is not available" "${hikari_log}" || true; } | wc -l | tr -d ' '
}

timeline_pending_max() {
  awk -F '\t' '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "hikari_pending" || $i == "db_pool_pending" || $i == "db_pool_pending_max") col = i
      }
      next
    }
    col && $col + 0 > max { max = $col + 0 }
    END { print max + 0 }
  ' "${timeline_ref}"
}

nginx_499_count() {
  awk -F '\t' 'NR > 1 && $2 == "499" { count += $9 } END { print count + 0 }' "${nginx_aggregate_tsv}"
}

nginx_upstream_p95() {
  awk -F '\t' 'NR > 1 && $13 + 0 > max { max = $13 + 0 } END { printf "%.3f", max + 0 }' "${nginx_aggregate_tsv}"
}

write_nginx_upstream_latency() {
  local upstream_p95="$1"
  {
    printf "metric\tvalue\n"
    printf "nginx_upstream_p95_ms\t%s\n" "${upstream_p95}"
  } >"${nginx_upstream_latency_ref}"
}

write_hikari_zero_warning_report() {
  local warnings="$1"
  local pending="$2"
  local config_source="$3"
  local max_lifetime="$4"
  local expected_max_lifetime="$5"
  local status="pass"
  if [[ "${warnings}" != "0" || "${pending}" != "0" ]]; then
    status="fail"
  fi
  cat >"${hikari_zero_warning_soak_ref}" <<REPORT
# Hikari 30m Zero-warning Soak

## Summary

- run_id=${run_id}
- duration_min=${duration_min}
- hikari_validation_warnings=${warnings}
- db_pool_pending_max=${pending}
- hikari_config_source=${config_source}
- hikari_max_lifetime_ms=${max_lifetime}
- expected_hikari_max_lifetime_ms=${expected_max_lifetime}
- gate_status=${status}

## Artifacts

- hikari log: ${hikari_log}
- timeline: ${timeline_ref}
- config: ${hikari_config_ref}
REPORT
}

write_failure_report() {
  local reason="$1"
  local detail="$2"
  {
    printf "run_id=%s\n" "${run_id}"
    printf "failure_reason=%s\n" "${reason}"
    printf "failure_detail=%s\n" "${detail}"
    printf "manifest_tsv=%s\n" "${manifest_tsv}"
    printf "manifest_report=%s\n" "${manifest_dir}/${name}-30m-soak-live-evidence-manifest.md"
    printf "k6_summary_json=%s\n" "${k6_summary_json}"
    printf "nginx_aggregate_tsv=%s\n" "${nginx_aggregate_tsv}"
    printf "hikari_log=%s\n" "${hikari_log}"
    printf "postgres_wait_ref=%s\n" "${postgres_wait_ref}"
    printf "timeline_ref=%s\n" "${timeline_ref}"
    printf "postgres_checkpoint_ref=%s\n" "${postgres_checkpoint_ref}"
    printf "postgres_temp_file_ref=%s\n" "${postgres_temp_file_ref}"
    printf "postgres_checkpoint_start_count=%s\n" "${postgres_checkpoint_start_count:-0}"
    printf "postgres_checkpoint_end_count=%s\n" "${postgres_checkpoint_end_count:-0}"
    printf "postgres_checkpoint_delta=%s\n" "${postgres_checkpoint_count:-0}"
    printf "postgres_temp_file_start_count=%s\n" "${postgres_temp_file_start_count:-0}"
    printf "postgres_temp_file_end_count=%s\n" "${postgres_temp_file_end_count:-0}"
    printf "postgres_temp_file_delta=%s\n" "${postgres_temp_file_count:-0}"
    printf "postgres_temp_file_delta_max=%s\n" "${postgres_temp_file_delta_max}"
    printf "hikari_config_source=%s\n" "${hikari_config_source:-n/a}"
    printf "hikari_max_lifetime_ms=%s\n" "${hikari_max_lifetime_ms:-0}"
    printf "expected_hikari_max_lifetime_ms=%s\n" "${expected_hikari_max_lifetime_ms:-0}"
  } >"${failure_reason_ref}"

  cat >"${failure_report_ref}" <<REPORT
# Transaction Read 30m Soak Failure

## Summary

- run_id=${run_id}
- failure_reason=${reason}
- failure_detail=${detail}
- manifest: ${manifest_tsv}
- Nginx aggregate: ${nginx_aggregate_tsv}
- Hikari log: ${hikari_log}
- p999 timeline: ${timeline_ref}
- PostgreSQL wait/checkpoint/temp: ${postgres_wait_ref} / ${postgres_checkpoint_ref} / ${postgres_temp_file_ref}
- PostgreSQL checkpoint start/end/delta: ${postgres_checkpoint_start_count:-0}/${postgres_checkpoint_end_count:-0}/${postgres_checkpoint_count:-0}
- PostgreSQL temp file start/end/delta: ${postgres_temp_file_start_count:-0}/${postgres_temp_file_end_count:-0}/${postgres_temp_file_count:-0}
- PostgreSQL temp file delta budget: <=${postgres_temp_file_delta_max}
- Hikari config source/maxLifetime/expected: ${hikari_config_source:-n/a}/${hikari_max_lifetime_ms:-0}/${expected_hikari_max_lifetime_ms:-0}

## Operator Notes

- failure-reason env: ${failure_reason_ref}
- artifact directory: ${output_dir}
REPORT
}

print_plan() {
  echo "[transaction-read-30m-soak-live-evidence-artifacts] name=${name}"
  echo "[transaction-read-30m-soak-live-evidence-artifacts] run_id=${run_id}"
  echo "[transaction-read-30m-soak-live-evidence-artifacts] duration_min=${duration_min}"
  echo "[transaction-read-30m-soak-live-evidence-artifacts] source_ips=${source_ips}"
  echo "[transaction-read-30m-soak-live-evidence-artifacts] output_dir=${output_dir}"
  echo "[transaction-read-30m-soak-live-evidence-artifacts] k6_summary_json=${k6_summary_json:-missing}"
  echo "[transaction-read-30m-soak-live-evidence-artifacts] nginx_access_log=${nginx_access_log:-missing}"
  echo "[transaction-read-30m-soak-live-evidence-artifacts] artifact_pack_refs=k6_summary,nginx_aggregate,spring_metrics,hikari_log,postgres_wait,timeline,postgres_checkpoint,postgres_temp_file,nginx_upstream_latency,hikari_config,hikari_zero_warning_soak"
  echo "[transaction-read-30m-soak-live-evidence-artifacts] failure_reason_ref=${failure_reason_ref}"
  echo "[transaction-read-30m-soak-live-evidence-artifacts] manifest_tsv=${manifest_tsv}"
}

require_non_negative_integer "SOAK_30M_ARTIFACTS_DURATION_MIN" "${duration_min}"
require_non_negative_integer "SOAK_30M_ARTIFACTS_SOURCE_IPS" "${source_ips}"
require_non_negative_integer "SOAK_30M_ARTIFACTS_POSTGRES_TEMP_FILE_DELTA_MAX" "${postgres_temp_file_delta_max}"
if (( duration_min < 30 )); then
  echo "SOAK_30M_ARTIFACTS_DURATION_MIN must be at least 30: ${duration_min}" >&2
  exit 1
fi

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

for command_name in jq awk grep wc; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    echo "${command_name} is required" >&2
    exit 1
  }
done

require_file "SOAK_30M_ARTIFACTS_K6_SUMMARY_JSON" "${k6_summary_json}"
require_file "SOAK_30M_ARTIFACTS_NGINX_ACCESS_LOG" "${nginx_access_log}"
require_file "SOAK_30M_ARTIFACTS_SPRING_METRICS_REF" "${spring_metrics_ref}"
require_file "SOAK_30M_ARTIFACTS_HIKARI_LOG" "${hikari_log}"
require_file "SOAK_30M_ARTIFACTS_POSTGRES_WAIT_REF" "${postgres_wait_ref}"
require_file "SOAK_30M_ARTIFACTS_TIMELINE_REF" "${timeline_ref}"
require_file "SOAK_30M_ARTIFACTS_POSTGRES_CHECKPOINT_REF" "${postgres_checkpoint_ref}"
require_file "SOAK_30M_ARTIFACTS_POSTGRES_TEMP_FILE_REF" "${postgres_temp_file_ref}"
require_file "SOAK_30M_ARTIFACTS_HIKARI_CONFIG_REF" "${hikari_config_ref}"

mkdir -p "${output_dir}" "${nginx_aggregate_dir}" "${manifest_dir}"

NGINX_ACCESS_AGGREGATE_NAME="${name}" \
NGINX_ACCESS_AGGREGATE_LOG="${nginx_access_log}" \
NGINX_ACCESS_AGGREGATE_RUN_ID="${run_id}" \
NGINX_ACCESS_AGGREGATE_OUTPUT_DIR="${nginx_aggregate_dir}" \
  tools/test/run-transaction-read-nginx-access-aggregate-artifact.sh >/dev/null

p95_ms="$(latency_percentile "p(95)")"
p99_ms="$(latency_percentile "p(99)")"
p999_ms="$(latency_percentile "p(99.9)")"
max_ms="$(latency_percentile "max")"
edge_429_rate="$(metric_value aquila_transaction_edge_429_rate rate)"
backend_429_count="$(metric_value aquila_transaction_backend_429_count count)"
unknown_429_count="$(metric_value aquila_transaction_unknown_429_count count)"
five_xx_count="$(
  awk \
    -v bad_gateway="$(metric_value aquila_transaction_502_count count)" \
    -v unavailable="$(metric_value aquila_transaction_503_count count)" \
    'BEGIN { print bad_gateway + unavailable }'
)"
hikari_validation_warnings="$(hikari_warning_count)"
db_pool_pending_max="$(timeline_pending_max)"
nginx_499_count="$(nginx_499_count)"
nginx_upstream_p95_ms="$(nginx_upstream_p95)"
postgres_checkpoint_count="$(tsv_value "${postgres_checkpoint_ref}" "postgres_checkpoint_count")"
postgres_temp_file_count="$(tsv_value "${postgres_temp_file_ref}" "postgres_temp_file_count")"
postgres_checkpoint_start_count="$(tsv_metric_field "${postgres_checkpoint_ref}" "postgres_checkpoint_count" "start_value" "value")"
postgres_checkpoint_end_count="$(tsv_metric_field "${postgres_checkpoint_ref}" "postgres_checkpoint_count" "end_value" "value")"
postgres_temp_file_start_count="$(tsv_metric_field "${postgres_temp_file_ref}" "postgres_temp_file_count" "start_value" "value")"
postgres_temp_file_end_count="$(tsv_metric_field "${postgres_temp_file_ref}" "postgres_temp_file_count" "end_value" "value")"
require_non_negative_integer "postgres_checkpoint_count" "${postgres_checkpoint_count}"
require_non_negative_integer "postgres_temp_file_count" "${postgres_temp_file_count}"
require_non_negative_integer "postgres_checkpoint_start_count" "${postgres_checkpoint_start_count}"
require_non_negative_integer "postgres_checkpoint_end_count" "${postgres_checkpoint_end_count}"
require_non_negative_integer "postgres_temp_file_start_count" "${postgres_temp_file_start_count}"
require_non_negative_integer "postgres_temp_file_end_count" "${postgres_temp_file_end_count}"
hikari_max_lifetime_ms="$(hikari_config_value "hikari_max_lifetime_ms")"
hikari_keepalive_time_ms="$(hikari_config_value "hikari_keepalive_time_ms")"
expected_hikari_max_lifetime_ms="$(hikari_config_value "expected_hikari_max_lifetime_ms")"
hikari_config_source="$(hikari_config_value "hikari_config_source")"
postgres_idle_timeout_ms="$(hikari_config_value "postgres_idle_timeout_ms")"
oci_nat_idle_timeout_ms="$(hikari_config_value "oci_nat_idle_timeout_ms")"

write_nginx_upstream_latency "${nginx_upstream_p95_ms}"
write_hikari_zero_warning_report "${hikari_validation_warnings}" "${db_pool_pending_max}" "${hikari_config_source}" "${hikari_max_lifetime_ms}" "${expected_hikari_max_lifetime_ms}"

SOAK_30M_MANIFEST_NAME="${name}" \
SOAK_30M_MANIFEST_RUN_ID="${run_id}" \
SOAK_30M_MANIFEST_EXECUTED_AT_UTC="${executed_at_utc}" \
SOAK_30M_MANIFEST_DURATION_MIN="${duration_min}" \
SOAK_30M_MANIFEST_SOURCE_IPS="${source_ips}" \
SOAK_30M_MANIFEST_K6_SUMMARY_REF="${k6_summary_json}" \
SOAK_30M_MANIFEST_NGINX_AGGREGATE_REF="${nginx_aggregate_tsv}" \
SOAK_30M_MANIFEST_SPRING_METRICS_REF="${spring_metrics_ref}" \
SOAK_30M_MANIFEST_HIKARI_LOG_REF="${hikari_log}" \
SOAK_30M_MANIFEST_POSTGRES_WAIT_REF="${postgres_wait_ref}" \
SOAK_30M_MANIFEST_TIMELINE_REF="${timeline_ref}" \
SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_REF="${postgres_checkpoint_ref}" \
SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_REF="${postgres_temp_file_ref}" \
SOAK_30M_MANIFEST_NGINX_UPSTREAM_LATENCY_REF="${nginx_upstream_latency_ref}" \
SOAK_30M_MANIFEST_HIKARI_CONFIG_REF="${hikari_config_ref}" \
SOAK_30M_MANIFEST_HIKARI_ZERO_WARNING_SOAK_REF="${hikari_zero_warning_soak_ref}" \
SOAK_30M_MANIFEST_EDGE_429_RATE="${edge_429_rate}" \
SOAK_30M_MANIFEST_BACKEND_429_COUNT="${backend_429_count}" \
SOAK_30M_MANIFEST_UNKNOWN_429_COUNT="${unknown_429_count}" \
SOAK_30M_MANIFEST_5XX_COUNT="${five_xx_count}" \
SOAK_30M_MANIFEST_NGINX_499_COUNT="${nginx_499_count}" \
SOAK_30M_MANIFEST_HIKARI_VALIDATION_WARNINGS="${hikari_validation_warnings}" \
SOAK_30M_MANIFEST_DB_POOL_PENDING_MAX="${db_pool_pending_max}" \
SOAK_30M_MANIFEST_P95_MS="${p95_ms}" \
SOAK_30M_MANIFEST_P99_MS="${p99_ms}" \
SOAK_30M_MANIFEST_P999_MS="${p999_ms}" \
SOAK_30M_MANIFEST_MAX_MS="${max_ms}" \
SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_START_COUNT="${postgres_checkpoint_start_count}" \
SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_END_COUNT="${postgres_checkpoint_end_count}" \
SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_COUNT="${postgres_checkpoint_count}" \
SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_START_COUNT="${postgres_temp_file_start_count}" \
SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_END_COUNT="${postgres_temp_file_end_count}" \
SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_COUNT="${postgres_temp_file_count}" \
SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_DELTA_MAX="${postgres_temp_file_delta_max}" \
SOAK_30M_MANIFEST_NGINX_UPSTREAM_P95_MS="${nginx_upstream_p95_ms}" \
SOAK_30M_MANIFEST_HIKARI_MAX_LIFETIME_MS="${hikari_max_lifetime_ms}" \
SOAK_30M_MANIFEST_HIKARI_KEEPALIVE_TIME_MS="${hikari_keepalive_time_ms}" \
SOAK_30M_MANIFEST_POSTGRES_IDLE_TIMEOUT_MS="${postgres_idle_timeout_ms}" \
SOAK_30M_MANIFEST_OCI_NAT_IDLE_TIMEOUT_MS="${oci_nat_idle_timeout_ms}" \
SOAK_30M_MANIFEST_OUTPUT_DIR="${manifest_dir}" \
  tools/test/run-transaction-read-30m-soak-live-evidence-manifest.sh | tail -1

failure_reasons=()
failure_details=()
if [[ "${hikari_validation_warnings}" != "0" ]]; then
  failure_reasons+=("hikari-validation-warning")
  failure_details+=("hikari_validation_warnings=${hikari_validation_warnings}")
fi
if (( postgres_temp_file_count > postgres_temp_file_delta_max )); then
  failure_reasons+=("postgres-temp-file-delta-budget")
  failure_details+=("postgres_temp_file_delta=${postgres_temp_file_count},max=${postgres_temp_file_delta_max}")
fi
if (( ${#failure_reasons[@]} > 0 )); then
  failure_reason="$(IFS=,; echo "${failure_reasons[*]}")"
  failure_detail="$(IFS=';'; echo "${failure_details[*]}")"
  write_failure_report "${failure_reason}" "${failure_detail}"
  if [[ "${failure_reason}" == *"hikari-validation-warning"* ]]; then
    echo "hikari validation warnings must be zero: ${hikari_validation_warnings}" >&2
  fi
  if [[ "${failure_reason}" == *"postgres-temp-file-delta-budget"* ]]; then
    echo "postgres temp file delta exceeds budget: ${postgres_temp_file_count} > ${postgres_temp_file_delta_max}" >&2
  fi
  exit 1
fi
