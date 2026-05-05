#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-30m-soak-live-evidence-manifest.sh [--print-plan]

Environment:
  SOAK_30M_MANIFEST_NAME        default transaction-read-30m-soak-live-evidence-manifest-<timestamp>
  SOAK_30M_MANIFEST_RUN_ID      default same as name
  SOAK_30M_MANIFEST_OUTPUT_DIR  default build/reports/k6/<name>
  SOAK_30M_MANIFEST_*_REF       local artifact refs for k6/nginx/spring/Hikari/PostgreSQL evidence
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

name="${SOAK_30M_MANIFEST_NAME:-transaction-read-30m-soak-live-evidence-manifest-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${SOAK_30M_MANIFEST_RUN_ID:-${name}}"
executed_at_utc="${SOAK_30M_MANIFEST_EXECUTED_AT_UTC:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
duration_min="${SOAK_30M_MANIFEST_DURATION_MIN:-30}"
source_ips="${SOAK_30M_MANIFEST_SOURCE_IPS:-1}"
output_dir="${SOAK_30M_MANIFEST_OUTPUT_DIR:-build/reports/k6/${name}}"
manifest_tsv="${output_dir}/${name}-30m-soak-live-evidence-manifest.tsv"
report_md="${output_dir}/${name}-30m-soak-live-evidence-manifest.md"
failure_env="${output_dir}/${name}-30m-soak-live-evidence-manifest-failure.env"

k6_summary_ref="${SOAK_30M_MANIFEST_K6_SUMMARY_REF:-${output_dir}/${name}-summary.json}"
nginx_aggregate_ref="${SOAK_30M_MANIFEST_NGINX_AGGREGATE_REF:-${output_dir}/${name}-nginx-aggregate.tsv}"
spring_metrics_ref="${SOAK_30M_MANIFEST_SPRING_METRICS_REF:-${output_dir}/${name}-spring-metrics.json}"
hikari_log_ref="${SOAK_30M_MANIFEST_HIKARI_LOG_REF:-${output_dir}/${name}-hikari.log}"
postgres_wait_ref="${SOAK_30M_MANIFEST_POSTGRES_WAIT_REF:-${output_dir}/${name}-postgres-wait.tsv}"
timeline_ref="${SOAK_30M_MANIFEST_TIMELINE_REF:-${output_dir}/${name}-p999-timeline.tsv}"
postgres_checkpoint_ref="${SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_REF:-${output_dir}/${name}-postgres-checkpoint.tsv}"
postgres_temp_file_ref="${SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_REF:-${output_dir}/${name}-postgres-temp-file.tsv}"
nginx_upstream_latency_ref="${SOAK_30M_MANIFEST_NGINX_UPSTREAM_LATENCY_REF:-${output_dir}/${name}-nginx-upstream-latency.tsv}"
hikari_config_ref="${SOAK_30M_MANIFEST_HIKARI_CONFIG_REF:-${output_dir}/${name}-hikari-config.tsv}"
hikari_zero_warning_soak_ref="${SOAK_30M_MANIFEST_HIKARI_ZERO_WARNING_SOAK_REF:-${output_dir}/${name}-hikari-zero-warning-soak.md}"

edge_429_rate="${SOAK_30M_MANIFEST_EDGE_429_RATE:-0}"
backend_429_count="${SOAK_30M_MANIFEST_BACKEND_429_COUNT:-0}"
unknown_429_count="${SOAK_30M_MANIFEST_UNKNOWN_429_COUNT:-0}"
five_xx_count="${SOAK_30M_MANIFEST_5XX_COUNT:-0}"
nginx_499_count="${SOAK_30M_MANIFEST_NGINX_499_COUNT:-0}"
hikari_validation_warnings="${SOAK_30M_MANIFEST_HIKARI_VALIDATION_WARNINGS:-0}"
db_pool_pending_max="${SOAK_30M_MANIFEST_DB_POOL_PENDING_MAX:-0}"
p95_ms="${SOAK_30M_MANIFEST_P95_MS:-0}"
p99_ms="${SOAK_30M_MANIFEST_P99_MS:-0}"
p999_ms="${SOAK_30M_MANIFEST_P999_MS:-0}"
max_ms="${SOAK_30M_MANIFEST_MAX_MS:-0}"
postgres_checkpoint_count="${SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_COUNT:-0}"
postgres_temp_file_count="${SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_COUNT:-0}"
postgres_checkpoint_start_count="${SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_START_COUNT:-${postgres_checkpoint_count}}"
postgres_checkpoint_end_count="${SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_END_COUNT:-${postgres_checkpoint_count}}"
postgres_temp_file_start_count="${SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_START_COUNT:-${postgres_temp_file_count}}"
postgres_temp_file_end_count="${SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_END_COUNT:-${postgres_temp_file_count}}"
postgres_temp_file_delta_max="${SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_DELTA_MAX:-0}"
nginx_upstream_p95_ms="${SOAK_30M_MANIFEST_NGINX_UPSTREAM_P95_MS:-0}"
hikari_max_lifetime_ms="${SOAK_30M_MANIFEST_HIKARI_MAX_LIFETIME_MS:-45000}"
hikari_keepalive_time_ms="${SOAK_30M_MANIFEST_HIKARI_KEEPALIVE_TIME_MS:-30000}"
postgres_idle_timeout_ms="${SOAK_30M_MANIFEST_POSTGRES_IDLE_TIMEOUT_MS:-300000}"
oci_nat_idle_timeout_ms="${SOAK_30M_MANIFEST_OCI_NAT_IDLE_TIMEOUT_MS:-350000}"
manifest_status="pass"
manifest_reasons=()

require_non_negative_number() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${key} must be a non-negative number: ${value}" >&2
    exit 1
  fi
}

require_non_negative_integer() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${key} must be a non-negative integer: ${value}" >&2
    exit 1
  fi
}

write_failure() {
  local key="$1"
  local ref="$2"
  mkdir -p "${output_dir}"
  {
    echo "SOAK_30M_MANIFEST_STATUS=failed"
    echo "SOAK_30M_MANIFEST_FAILURE_REASON=missing-artifact"
    echo "SOAK_30M_MANIFEST_MISSING_KEY=${key}"
    echo "SOAK_30M_MANIFEST_MISSING_REF=${ref}"
    echo "SOAK_30M_MANIFEST_RUN_ID=${run_id}"
  } >"${failure_env}"
}

is_uri_ref() {
  [[ "$1" =~ ^[A-Za-z][A-Za-z0-9+.-]*:// ]]
}

require_artifact_ref() {
  local key="$1"
  local ref="$2"
  if [[ -z "${ref}" ]]; then
    write_failure "${key}" "missing"
    echo "${key} is required and must be a non-empty artifact ref" >&2
    exit 1
  fi
  if ! is_uri_ref "${ref}" && [[ ! -s "${ref}" ]]; then
    write_failure "${key}" "${ref}"
    echo "${key} is required and must point to a non-empty artifact: ${ref}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[transaction-read-30m-soak-live-evidence-manifest] name=${name}"
  echo "[transaction-read-30m-soak-live-evidence-manifest] run_id=${run_id}"
  echo "[transaction-read-30m-soak-live-evidence-manifest] output_dir=${output_dir}"
  echo "[transaction-read-30m-soak-live-evidence-manifest] required_scenarios=hikari-lifetime,p999-long-correlation"
  echo "[transaction-read-30m-soak-live-evidence-manifest] required_artifacts=k6_summary,nginx_aggregate,spring_metrics,hikari_log,postgres_wait,timeline,postgres_checkpoint,postgres_temp_file,nginx_upstream_latency,hikari_config,hikari_zero_warning_soak"
  echo "[transaction-read-30m-soak-live-evidence-manifest] manifest_tsv=${manifest_tsv}"
  echo "[transaction-read-30m-soak-live-evidence-manifest] report_md=${report_md}"
  echo "[transaction-read-30m-soak-live-evidence-manifest] failure_env=${failure_env}"
}

require_non_negative_integer "SOAK_30M_MANIFEST_DURATION_MIN" "${duration_min}"
require_non_negative_integer "SOAK_30M_MANIFEST_SOURCE_IPS" "${source_ips}"
require_non_negative_number "SOAK_30M_MANIFEST_EDGE_429_RATE" "${edge_429_rate}"
require_non_negative_integer "SOAK_30M_MANIFEST_BACKEND_429_COUNT" "${backend_429_count}"
require_non_negative_integer "SOAK_30M_MANIFEST_UNKNOWN_429_COUNT" "${unknown_429_count}"
require_non_negative_integer "SOAK_30M_MANIFEST_5XX_COUNT" "${five_xx_count}"
require_non_negative_integer "SOAK_30M_MANIFEST_NGINX_499_COUNT" "${nginx_499_count}"
require_non_negative_integer "SOAK_30M_MANIFEST_HIKARI_VALIDATION_WARNINGS" "${hikari_validation_warnings}"
require_non_negative_integer "SOAK_30M_MANIFEST_DB_POOL_PENDING_MAX" "${db_pool_pending_max}"
require_non_negative_number "SOAK_30M_MANIFEST_P95_MS" "${p95_ms}"
require_non_negative_number "SOAK_30M_MANIFEST_P99_MS" "${p99_ms}"
require_non_negative_number "SOAK_30M_MANIFEST_P999_MS" "${p999_ms}"
require_non_negative_number "SOAK_30M_MANIFEST_MAX_MS" "${max_ms}"
require_non_negative_integer "SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_COUNT" "${postgres_checkpoint_count}"
require_non_negative_integer "SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_COUNT" "${postgres_temp_file_count}"
require_non_negative_integer "SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_START_COUNT" "${postgres_checkpoint_start_count}"
require_non_negative_integer "SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_END_COUNT" "${postgres_checkpoint_end_count}"
require_non_negative_integer "SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_START_COUNT" "${postgres_temp_file_start_count}"
require_non_negative_integer "SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_END_COUNT" "${postgres_temp_file_end_count}"
require_non_negative_integer "SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_DELTA_MAX" "${postgres_temp_file_delta_max}"
require_non_negative_number "SOAK_30M_MANIFEST_NGINX_UPSTREAM_P95_MS" "${nginx_upstream_p95_ms}"
require_non_negative_integer "SOAK_30M_MANIFEST_HIKARI_MAX_LIFETIME_MS" "${hikari_max_lifetime_ms}"
require_non_negative_integer "SOAK_30M_MANIFEST_HIKARI_KEEPALIVE_TIME_MS" "${hikari_keepalive_time_ms}"
require_non_negative_integer "SOAK_30M_MANIFEST_POSTGRES_IDLE_TIMEOUT_MS" "${postgres_idle_timeout_ms}"
require_non_negative_integer "SOAK_30M_MANIFEST_OCI_NAT_IDLE_TIMEOUT_MS" "${oci_nat_idle_timeout_ms}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

require_artifact_ref "SOAK_30M_MANIFEST_K6_SUMMARY_REF" "${k6_summary_ref}"
require_artifact_ref "SOAK_30M_MANIFEST_NGINX_AGGREGATE_REF" "${nginx_aggregate_ref}"
require_artifact_ref "SOAK_30M_MANIFEST_SPRING_METRICS_REF" "${spring_metrics_ref}"
require_artifact_ref "SOAK_30M_MANIFEST_HIKARI_LOG_REF" "${hikari_log_ref}"
require_artifact_ref "SOAK_30M_MANIFEST_POSTGRES_WAIT_REF" "${postgres_wait_ref}"
require_artifact_ref "SOAK_30M_MANIFEST_TIMELINE_REF" "${timeline_ref}"
require_artifact_ref "SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_REF" "${postgres_checkpoint_ref}"
require_artifact_ref "SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_REF" "${postgres_temp_file_ref}"
require_artifact_ref "SOAK_30M_MANIFEST_NGINX_UPSTREAM_LATENCY_REF" "${nginx_upstream_latency_ref}"
require_artifact_ref "SOAK_30M_MANIFEST_HIKARI_CONFIG_REF" "${hikari_config_ref}"
require_artifact_ref "SOAK_30M_MANIFEST_HIKARI_ZERO_WARNING_SOAK_REF" "${hikari_zero_warning_soak_ref}"

mkdir -p "${output_dir}"

if [[ "${hikari_validation_warnings}" != "0" ]]; then
  manifest_status="fail"
  manifest_reasons+=("hikari-validation-warning")
fi
if [[ "${db_pool_pending_max}" != "0" ]]; then
  manifest_status="fail"
  manifest_reasons+=("db-pool-pending")
fi
if [[ "${backend_429_count}" != "0" || "${unknown_429_count}" != "0" || "${five_xx_count}" != "0" || "${nginx_499_count}" != "0" ]]; then
  manifest_status="fail"
  manifest_reasons+=("hard-zero-budget")
fi
if (( postgres_temp_file_count > postgres_temp_file_delta_max )); then
  manifest_status="fail"
  manifest_reasons+=("postgres-temp-file-delta-budget")
fi
if (( ${#manifest_reasons[@]} == 0 )); then
  manifest_reason="ok"
else
  manifest_reason="$(IFS=,; echo "${manifest_reasons[*]}")"
fi

header=$'scenario\trun_id\texecuted_at_utc\tduration_min\tsource_ips\trun_script\tk6_summary_ref\tnginx_access_ref\tspring_metrics_ref\thikari_log_ref\tpostgres_wait_ref\tdeploy_event_ref\tcache_state_ref\ttimeline_ref\tedge_429_rate\tbackend_429_count\tunknown_429_count\tfive_xx_count\tnginx_499_count\thikari_validation_warnings\tdb_pool_pending_max\tp999_ms\tpostgres_checkpoint_ref\tpostgres_temp_file_ref\tnginx_upstream_latency_ref\tworkload_mix_ref\tworkload_component_ref\toutbox_lag_ref\toutbox_lag_max\tdeploy_retry_contract_ref\tdeploy_reconnect_success_count\tdeploy_499_budget_ref\tp95_ms\tp99_ms\tmax_ms\tpostgres_checkpoint_count\tpostgres_temp_file_count\tnginx_upstream_p95_ms\thikari_config_ref\thikari_max_lifetime_ms\thikari_keepalive_time_ms\tpostgres_idle_timeout_ms\toci_nat_idle_timeout_ms\thikari_zero_warning_soak_ref\tworkload_components\tread_p999_ms\tread_429_source_ref'
printf "%s\n" "${header}" >"${manifest_tsv}"

printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
  "hikari-lifetime" "${run_id}" "${executed_at_utc}" "${duration_min}" "${source_ips}" \
  "tools/test/run-transaction-read-weighted-10m-soak-gate.sh" \
  "${k6_summary_ref}" "${nginx_aggregate_ref}" "${spring_metrics_ref}" "${hikari_log_ref}" "${postgres_wait_ref}" \
  "n/a" "n/a" "${timeline_ref}" "${edge_429_rate}" "${backend_429_count}" "${unknown_429_count}" "${five_xx_count}" "${nginx_499_count}" \
  "${hikari_validation_warnings}" "${db_pool_pending_max}" "${p999_ms}" "n/a" "n/a" "n/a" \
  "n/a" "n/a" "n/a" "0" "n/a" "0" "n/a" \
  "${p95_ms}" "${p99_ms}" "${max_ms}" "0" "0" "${nginx_upstream_p95_ms}" \
  "${hikari_config_ref}" "${hikari_max_lifetime_ms}" "${hikari_keepalive_time_ms}" "${postgres_idle_timeout_ms}" "${oci_nat_idle_timeout_ms}" \
  "${hikari_zero_warning_soak_ref}" "n/a" "0" "n/a" >>"${manifest_tsv}"

printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
  "p999-long-correlation" "${run_id}" "${executed_at_utc}" "${duration_min}" "${source_ips}" \
  "tools/test/run-transaction-read-p999-spike-attribution.sh" \
  "${k6_summary_ref}" "${nginx_aggregate_ref}" "${spring_metrics_ref}" "${hikari_log_ref}" "${postgres_wait_ref}" \
  "n/a" "n/a" "${timeline_ref}" "${edge_429_rate}" "${backend_429_count}" "${unknown_429_count}" "${five_xx_count}" "${nginx_499_count}" \
  "${hikari_validation_warnings}" "${db_pool_pending_max}" "${p999_ms}" "${postgres_checkpoint_ref}" "${postgres_temp_file_ref}" "${nginx_upstream_latency_ref}" \
  "n/a" "n/a" "n/a" "0" "n/a" "0" "n/a" \
  "${p95_ms}" "${p99_ms}" "${max_ms}" "${postgres_checkpoint_count}" "${postgres_temp_file_count}" "${nginx_upstream_p95_ms}" \
  "n/a" "0" "0" "0" "0" "n/a" "n/a" "0" "n/a" >>"${manifest_tsv}"

cat >"${report_md}" <<REPORT
# Transaction Read 30m Soak Live Evidence Manifest

## Summary

- gate_status=${manifest_status}
- failure_reason=${manifest_reason}
- run_id=${run_id}
- duration_min=${duration_min}
- manifest rows: 2
- required scenarios: hikari-lifetime,p999-long-correlation
- k6 summary: ${k6_summary_ref}
- Hikari log: ${hikari_log_ref}
- PostgreSQL wait/checkpoint/temp file: ${postgres_wait_ref} / ${postgres_checkpoint_ref} / ${postgres_temp_file_ref}
- PostgreSQL checkpoint start/end/delta: ${postgres_checkpoint_start_count}/${postgres_checkpoint_end_count}/${postgres_checkpoint_count}
- PostgreSQL temp file start/end/delta: ${postgres_temp_file_start_count}/${postgres_temp_file_end_count}/${postgres_temp_file_count}
- PostgreSQL temp file delta budget: <=${postgres_temp_file_delta_max}
- Nginx upstream latency: ${nginx_upstream_latency_ref}
- p95/p99/p99.9/max: ${p95_ms}/${p99_ms}/${p999_ms}/${max_ms}

## Artifacts

- manifest TSV: ${manifest_tsv}
- report: ${report_md}
REPORT

echo "${manifest_tsv}"
