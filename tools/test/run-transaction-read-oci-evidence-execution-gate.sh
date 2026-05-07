#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-oci-evidence-execution-gate.sh [--print-plan]

Environment:
  OCI_EVIDENCE_EXECUTION_NAME                    default transaction-read-oci-evidence-execution-<timestamp>
  OCI_EVIDENCE_EXECUTION_INPUT_TSV               required TSV with OCI execution manifest
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR              default build/reports/k6/<gate>
  OCI_EVIDENCE_EXECUTION_REQUIRED_SCENARIOS      default hikari-lifetime,mixed-workload-30m,cold-warm-cache,deploy-drain,p999-long-correlation
  OCI_EVIDENCE_EXECUTION_MAX_EDGE_429_RATE       default 0.10
  OCI_EVIDENCE_EXECUTION_MAX_P999_MS             default 500
  OCI_EVIDENCE_EXECUTION_MAX_POOL_PENDING        default 0
  OCI_EVIDENCE_EXECUTION_MAX_POSTGRES_TEMP_FILE_DELTA default 0
  OCI_EVIDENCE_EXECUTION_MIXED_MIN_DURATION_MIN  default 30
  OCI_EVIDENCE_EXECUTION_P999_MIN_DURATION_MIN   default 30
  OCI_EVIDENCE_EXECUTION_HIKARI_MIN_DURATION_MIN default 30
  OCI_EVIDENCE_EXECUTION_DEPLOY_MIN_DURATION_MIN default 5
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

name="${OCI_EVIDENCE_EXECUTION_NAME:-transaction-read-oci-evidence-execution-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${OCI_EVIDENCE_EXECUTION_INPUT_TSV:-}"
output_dir="${OCI_EVIDENCE_EXECUTION_OUTPUT_DIR:-build/reports/k6/${name}}"
required_scenarios="${OCI_EVIDENCE_EXECUTION_REQUIRED_SCENARIOS:-hikari-lifetime,mixed-workload-30m,cold-warm-cache,deploy-drain,p999-long-correlation}"
max_edge_429_rate="${OCI_EVIDENCE_EXECUTION_MAX_EDGE_429_RATE:-0.10}"
max_p999_ms="${OCI_EVIDENCE_EXECUTION_MAX_P999_MS:-500}"
max_pool_pending="${OCI_EVIDENCE_EXECUTION_MAX_POOL_PENDING:-0}"
max_postgres_temp_file_delta="${OCI_EVIDENCE_EXECUTION_MAX_POSTGRES_TEMP_FILE_DELTA:-0}"
mixed_min_duration_min="${OCI_EVIDENCE_EXECUTION_MIXED_MIN_DURATION_MIN:-30}"
p999_min_duration_min="${OCI_EVIDENCE_EXECUTION_P999_MIN_DURATION_MIN:-30}"
hikari_min_duration_min="${OCI_EVIDENCE_EXECUTION_HIKARI_MIN_DURATION_MIN:-30}"
deploy_min_duration_min="${OCI_EVIDENCE_EXECUTION_DEPLOY_MIN_DURATION_MIN:-5}"
summary_tsv="${output_dir}/${name}-oci-evidence-execution.tsv"
report_md="${output_dir}/${name}-oci-evidence-execution.md"
meta_file="${output_dir}/${name}-oci-evidence-execution.meta"

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

require_rate() {
  local key="$1"
  local value="$2"
  require_non_negative_number "${key}" "${value}"
  awk -v value="${value}" 'BEGIN { exit !(value >= 0 && value <= 1) }' || {
    echo "${key} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  }
}

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

scenarios() {
  if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
    echo "missing"
    return
  fi
  awk -F '\t' '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "scenario") scenario_col = i
      }
      next
    }
    scenario_col {
      if (result != "") result = result ","
      result = result $scenario_col
    }
    END {
      if (result == "") print "missing"; else print result
    }
  ' "${input_tsv}"
}

print_plan() {
  echo "[transaction-read-oci-evidence-execution] name=${name}"
  echo "[transaction-read-oci-evidence-execution] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-oci-evidence-execution] output_dir=${output_dir}"
  echo "[transaction-read-oci-evidence-execution] scenarios=$(scenarios)"
  echo "[transaction-read-oci-evidence-execution] required_scenarios=${required_scenarios}"
  echo "[transaction-read-oci-evidence-execution] max_edge_429_rate=${max_edge_429_rate}"
  echo "[transaction-read-oci-evidence-execution] max_p999_ms=${max_p999_ms}"
  echo "[transaction-read-oci-evidence-execution] max_pool_pending=${max_pool_pending}"
  echo "[transaction-read-oci-evidence-execution] max_postgres_temp_file_delta=${max_postgres_temp_file_delta}"
  echo "[transaction-read-oci-evidence-execution] mixed_min_duration_min=${mixed_min_duration_min}"
  echo "[transaction-read-oci-evidence-execution] p999_min_duration_min=${p999_min_duration_min}"
  echo "[transaction-read-oci-evidence-execution] hikari_min_duration_min=${hikari_min_duration_min}"
  echo "[transaction-read-oci-evidence-execution] deploy_min_duration_min=${deploy_min_duration_min}"
  echo "[transaction-read-oci-evidence-execution] summary_tsv=${summary_tsv}"
  echo "[transaction-read-oci-evidence-execution] report_md=${report_md}"
}

require_rate "OCI_EVIDENCE_EXECUTION_MAX_EDGE_429_RATE" "${max_edge_429_rate}"
require_non_negative_number "OCI_EVIDENCE_EXECUTION_MAX_P999_MS" "${max_p999_ms}"
require_non_negative_integer "OCI_EVIDENCE_EXECUTION_MAX_POOL_PENDING" "${max_pool_pending}"
require_non_negative_integer "OCI_EVIDENCE_EXECUTION_MAX_POSTGRES_TEMP_FILE_DELTA" "${max_postgres_temp_file_delta}"
require_non_negative_integer "OCI_EVIDENCE_EXECUTION_MIXED_MIN_DURATION_MIN" "${mixed_min_duration_min}"
require_non_negative_integer "OCI_EVIDENCE_EXECUTION_P999_MIN_DURATION_MIN" "${p999_min_duration_min}"
require_non_negative_integer "OCI_EVIDENCE_EXECUTION_HIKARI_MIN_DURATION_MIN" "${hikari_min_duration_min}"
require_non_negative_integer "OCI_EVIDENCE_EXECUTION_DEPLOY_MIN_DURATION_MIN" "${deploy_min_duration_min}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "OCI_EVIDENCE_EXECUTION_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "OCI_EVIDENCE_EXECUTION_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

awk -F '\t' \
  -v required_scenarios="${required_scenarios}" \
  -v max_edge_429_rate="${max_edge_429_rate}" \
  -v max_p999_ms="${max_p999_ms}" \
  -v max_pool_pending="${max_pool_pending}" \
  -v max_postgres_temp_file_delta="${max_postgres_temp_file_delta}" \
  -v mixed_min_duration_min="${mixed_min_duration_min}" \
  -v p999_min_duration_min="${p999_min_duration_min}" \
  -v hikari_min_duration_min="${hikari_min_duration_min}" \
  -v deploy_min_duration_min="${deploy_min_duration_min}" '
function value(name, fallback) {
  if (!(name in col) || col[name] == "") return fallback
  return $(col[name])
}
function require_number(name, reason_value) {
  raw = value(name, "")
  if (raw !~ /^[0-9]+([.][0-9]+)?$/) {
    add_reason(reason_value)
    return 0
  }
  return raw + 0
}
function has_component(items, item) {
  return index("," items ",", "," item ",") > 0
}
function add_reason(reason_value) {
  if (reason == "ok") {
    reason = reason_value
  } else {
    reason = reason "," reason_value
  }
  status = "fail"
}
function require_ref(name, reason_value) {
  ref = value(name, "")
  if (ref == "" || ref == "n/a") {
    add_reason(reason_value)
  }
}
BEGIN {
  split(required_scenarios, required_items, ",")
  for (i in required_items) {
    required[required_items[i]] = 1
  }
  print "scenario\tstatus\treason\trun_id\tduration_min\tsource_ips\trun_script\tk6_summary_ref\tnginx_access_ref\tspring_metrics_ref\thikari_log_ref\tpostgres_wait_ref\tdeploy_event_ref\tcache_state_ref\ttimeline_ref\tedge_429_rate\tbackend_429_count\tunknown_429_count\tfive_xx_count\tnginx_499_count\thikari_validation_warnings\tdb_pool_pending_max\tp999_ms\tp95_ms\tp99_ms\tmax_ms\tpostgres_checkpoint_count\tpostgres_temp_file_count\tnginx_upstream_p95_ms\thikari_config_ref\thikari_max_lifetime_ms\thikari_keepalive_time_ms\tpostgres_idle_timeout_ms\toci_nat_idle_timeout_ms\thikari_zero_warning_soak_ref\tworkload_components\tread_p999_ms\tread_429_source_ref\tread_buckets\tread_bucket_ref\twrite_2xx_count\twrite_unexpected_status_count\twrite_status_ref"
}
NR == 1 {
  for (i = 1; i <= NF; i++) {
    col[$i] = i
  }
  next
}
{
  scenario = value("scenario", "unknown")
  run_id = value("run_id", "")
  executed_at = value("executed_at_utc", "")
  duration_min = value("duration_min", "0") + 0
  source_ips = value("source_ips", "0") + 0
  run_script = value("run_script", "")
  edge_429_rate = value("edge_429_rate", "1") + 0
  backend_429_count = value("backend_429_count", "1") + 0
  unknown_429_count = value("unknown_429_count", "1") + 0
  five_xx_count = value("five_xx_count", "1") + 0
  nginx_499_count = value("nginx_499_count", "1") + 0
  hikari_validation_warnings = value("hikari_validation_warnings", "1") + 0
  db_pool_pending_max = value("db_pool_pending_max", "999999") + 0
  p999_ms = value("p999_ms", "999999") + 0
  p95_ms = value("p95_ms", "")
  p99_ms = value("p99_ms", "")
  max_ms = value("max_ms", "")
  postgres_checkpoint_count = value("postgres_checkpoint_count", "")
  postgres_temp_file_count = value("postgres_temp_file_count", "")
  nginx_upstream_p95_ms = value("nginx_upstream_p95_ms", "")
  hikari_config_ref = value("hikari_config_ref", "")
  hikari_max_lifetime_ms = value("hikari_max_lifetime_ms", "")
  hikari_keepalive_time_ms = value("hikari_keepalive_time_ms", "")
  postgres_idle_timeout_ms = value("postgres_idle_timeout_ms", "")
  oci_nat_idle_timeout_ms = value("oci_nat_idle_timeout_ms", "")
  hikari_zero_warning_soak_ref = value("hikari_zero_warning_soak_ref", "")
  workload_components = value("workload_components", "")
  read_p999_ms = value("read_p999_ms", "")
  read_429_source_ref = value("read_429_source_ref", "")
  read_buckets = value("read_buckets", "")
  read_bucket_ref = value("read_bucket_ref", "")
  write_2xx_count = value("write_2xx_count", "")
  write_unexpected_status_count = value("write_unexpected_status_count", "")
  write_status_ref = value("write_status_ref", "")
  status = "pass"
  reason = "ok"
  seen[scenario] = 1

  if (run_id == "") add_reason("run-id-missing")
  if (executed_at == "") add_reason("executed-at-missing")
  if (run_script !~ /^tools\/test\/run-.*[.]sh$/) add_reason("run-script-invalid")
  require_ref("k6_summary_ref", "k6-summary-missing")
  require_ref("nginx_access_ref", "nginx-access-missing")
  require_ref("spring_metrics_ref", "spring-metrics-missing")
  require_ref("hikari_log_ref", "hikari-log-missing")
  require_ref("postgres_wait_ref", "postgres-wait-missing")
  require_ref("timeline_ref", "timeline-missing")

  if (scenario == "cold-warm-cache") require_ref("cache_state_ref", "cache-state-missing")
  if (scenario == "deploy-drain") require_ref("deploy_event_ref", "deploy-event-missing")
  if (scenario == "deploy-drain") {
    require_ref("deploy_retry_contract_ref", "deploy-retry-contract-missing")
    require_ref("deploy_499_budget_ref", "deploy-499-budget-missing")
    if (value("deploy_reconnect_success_count", "0") + 0 <= 0) add_reason("deploy-reconnect-missing")
  }
  if (scenario == "mixed-workload-30m" && duration_min < mixed_min_duration_min) add_reason("mixed-duration<" mixed_min_duration_min)
  if (scenario == "mixed-workload-30m") {
    require_ref("workload_mix_ref", "workload-mix-missing")
    require_ref("workload_component_ref", "workload-component-missing")
    require_ref("outbox_lag_ref", "outbox-lag-missing")
    require_ref("read_429_source_ref", "read-429-source-missing")
    require_ref("read_bucket_ref", "read-bucket-missing")
    require_ref("write_status_ref", "write-status-missing")
    read_p999_ms = require_number("read_p999_ms", "read-p999-missing")
    write_2xx_count = require_number("write_2xx_count", "write-2xx-missing")
    write_unexpected_status_count = require_number("write_unexpected_status_count", "write-unexpected-status-missing")
    if (read_p999_ms > max_p999_ms) add_reason("read-p999>" max_p999_ms)
    if (write_2xx_count <= 0) add_reason("write-2xx-missing")
    if (!has_component(workload_components, "read") || !has_component(workload_components, "write") || !has_component(workload_components, "auth") || !has_component(workload_components, "notification") || !has_component(workload_components, "sse")) {
      add_reason("workload-components-missing")
    }
    if (!has_component(read_buckets, "hot") || !has_component(read_buckets, "cold") || !has_component(read_buckets, "archive")) {
      add_reason("read-buckets-missing")
    }
    if (value("outbox_lag_max", "1") + 0 > 0) add_reason("outbox-lag>0")
  }
  if (scenario == "p999-long-correlation" && duration_min < p999_min_duration_min) add_reason("p999-duration<" p999_min_duration_min)
  if (scenario == "p999-long-correlation") {
    require_ref("postgres_checkpoint_ref", "postgres-checkpoint-missing")
    require_ref("postgres_temp_file_ref", "postgres-temp-file-missing")
    require_ref("nginx_upstream_latency_ref", "nginx-upstream-latency-missing")
    p95_ms = require_number("p95_ms", "p95-missing")
    p99_ms = require_number("p99_ms", "p99-missing")
    max_ms = require_number("max_ms", "max-missing")
    postgres_checkpoint_count = require_number("postgres_checkpoint_count", "postgres-checkpoint-count-missing")
    postgres_temp_file_count = require_number("postgres_temp_file_count", "postgres-temp-file-count-missing")
    nginx_upstream_p95_ms = require_number("nginx_upstream_p95_ms", "nginx-upstream-p95-missing")
    if (postgres_temp_file_count > max_postgres_temp_file_delta) add_reason("postgres-temp-file-delta>" max_postgres_temp_file_delta)
  }
  if (scenario == "hikari-lifetime" && duration_min < hikari_min_duration_min) add_reason("hikari-duration<" hikari_min_duration_min)
  if (scenario == "hikari-lifetime") {
    require_ref("hikari_config_ref", "hikari-config-missing")
    require_ref("hikari_zero_warning_soak_ref", "hikari-zero-warning-soak-missing")
    hikari_max_lifetime_ms = require_number("hikari_max_lifetime_ms", "hikari-maxLifetime-missing")
    hikari_keepalive_time_ms = require_number("hikari_keepalive_time_ms", "hikari-keepalive-missing")
    postgres_idle_timeout_ms = require_number("postgres_idle_timeout_ms", "postgres-idle-timeout-missing")
    oci_nat_idle_timeout_ms = require_number("oci_nat_idle_timeout_ms", "oci-nat-idle-timeout-missing")
    if (hikari_max_lifetime_ms >= oci_nat_idle_timeout_ms) add_reason("hikari-maxLifetime>=oci-nat-idle")
    if (hikari_keepalive_time_ms >= hikari_max_lifetime_ms) add_reason("hikari-keepalive>=maxLifetime")
    if (postgres_idle_timeout_ms <= 0) add_reason("postgres-idle-timeout-missing")
  }
  if (scenario == "deploy-drain" && duration_min < deploy_min_duration_min) add_reason("deploy-duration<" deploy_min_duration_min)

  if (edge_429_rate > max_edge_429_rate) add_reason("edge429>" max_edge_429_rate)
  if (backend_429_count > 0) add_reason("backend429>0")
  if (unknown_429_count > 0) add_reason("unknown429>0")
  if (five_xx_count > 0) add_reason("5xx>0")
  if (nginx_499_count > 0) add_reason("499>0")
  if (hikari_validation_warnings > 0) add_reason("hikari-warning>0")
  if (db_pool_pending_max > max_pool_pending) add_reason("pool-pending>" max_pool_pending)
  if (p999_ms > max_p999_ms) add_reason("p999>" max_p999_ms)

  if (status == "fail") fail_count++
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    scenario, status, reason, run_id, duration_min, source_ips, run_script,
    value("k6_summary_ref", ""), value("nginx_access_ref", ""), value("spring_metrics_ref", ""),
    value("hikari_log_ref", ""), value("postgres_wait_ref", ""), value("deploy_event_ref", ""),
    value("cache_state_ref", ""), value("timeline_ref", ""), value("edge_429_rate", "0"),
    value("backend_429_count", "0"), value("unknown_429_count", "0"), value("five_xx_count", "0"), value("nginx_499_count", "0"),
    value("hikari_validation_warnings", "0"), value("db_pool_pending_max", "0"), value("p999_ms", "0"),
    p95_ms, p99_ms, max_ms, postgres_checkpoint_count, postgres_temp_file_count, nginx_upstream_p95_ms,
    hikari_config_ref, hikari_max_lifetime_ms, hikari_keepalive_time_ms, postgres_idle_timeout_ms, oci_nat_idle_timeout_ms, hikari_zero_warning_soak_ref,
    workload_components, read_p999_ms, read_429_source_ref,
    read_buckets, read_bucket_ref, write_2xx_count, write_unexpected_status_count, write_status_ref
}
END {
  missing = ""
  for (scenario in required) {
    if (!(scenario in seen)) {
      if (missing == "") {
        missing = scenario
      } else {
        missing = missing "," scenario
      }
      fail_count++
    }
  }
  print "fail_count=" (fail_count + 0) > "/dev/stderr"
  print "missing_scenarios=" missing > "/dev/stderr"
}
' "${input_tsv}" >"${summary_tsv}" 2>"${meta_file}"

fail_count="$(awk -F '=' '/^fail_count=/ { print $2 }' "${meta_file}")"
missing_scenarios="$(awk -F '=' '/^missing_scenarios=/ { print $2 }' "${meta_file}")"
gate_status="pass"
if [[ "${fail_count}" != "0" ]]; then
  gate_status="fail"
fi

result_table="$(awk -F '\t' '
  BEGIN {
    print "| Scenario | Status | Reason | Run id | Duration min | Source IPs | Script | Edge 429 | Unknown 429 | 499 | 5xx | Hikari warnings | p95 ms | p99 ms | p99.9 ms | max ms | Timeline |"
    print "| --- | --- | --- | --- | ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n",
      $1, $2, $3, $4, $5, $6, $7, $16, $18, $20, $19, $21, $24, $25, $23, $26, $15
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read OCI Evidence Execution Gate

## Summary

- gate_status=${gate_status}
- missing_scenarios=${missing_scenarios:-none}
- required scenarios: ${required_scenarios}
- execution artifacts: k6, nginx access, Spring metrics, Hikari log, PostgreSQL wait, timeline
- mixed workload min duration: ${mixed_min_duration_min}m
- p99.9 long correlation min duration: ${p999_min_duration_min}m
- Hikari lifetime min duration: ${hikari_min_duration_min}m
- deploy drain min duration: ${deploy_min_duration_min}m
- edge 429 max rate: ${max_edge_429_rate}
- p99.9 max: ${max_p999_ms}ms
- PostgreSQL temp file delta max: ${max_postgres_temp_file_delta}
- backend 429/unknown 429/499/5xx/Hikari warning/pool pending target: 0
- unknown 429 hard-zero
- p99.9 closure artifacts: PostgreSQL checkpoint, temp file, Nginx upstream latency
- latency percentiles: p95, p99, p99.9, max
- p999 long correlation metrics: PostgreSQL checkpoint delta, temp file delta, Nginx upstream p95
- Hikari lifetime alignment artifacts: config ref, zero-warning soak ref, timeout basis
- mixed workload closure artifacts: workload mix, component split, outbox lag
- mixed workload required components: read,write,auth,notification,sse
- mixed workload read p99.9 and 429 source artifact: required
- mixed workload hot/cold/archive read bucket artifact: required
- mixed workload write 2xx and status classification artifact: required
- deploy drain closure artifacts: 499 budget, retry contract, reconnect success

## Result Table

${result_table}

## Evidence Contract

- OCI evidence는 run id, 실행 시각, 실행 script, k6 summary, Nginx access, Spring metrics, Hikari log, PostgreSQL wait, timeline을 같은 row에 남긴다.
- p99.9 long correlation은 PostgreSQL checkpoint, temp file, Nginx upstream latency artifact를 같은 run id로 묶고 temp file delta budget을 검증한다.
- Hikari lifetime은 maxLifetime/keepaliveTime/PostgreSQL idle/NAT idle 기준과 30m zero-warning artifact를 같은 run id로 묶는다.
- mixed workload는 workload mix, component split, outbox lag artifact를 같은 run id로 묶고 outbox lag max 0을 요구한다.
- mixed workload는 read/write/auth/notification/SSE component와 read p99.9, 429 source artifact를 분리 기록한다.
- cold/warm cache는 cache state artifact, deploy drain은 deploy event, retry/reconnect, 499 budget artifact를 추가로 요구한다.
- 이 gate는 실제 실행을 대신하지 않고, 실행 결과가 PR/issue에서 재검증 가능한 artifact manifest인지 닫는다.

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read OCI evidence execution gate failed: ${summary_tsv}" >&2
  exit 1
fi
