#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-oci-evidence-execution-gate.sh [--print-plan]

Environment:
  OCI_EVIDENCE_EXECUTION_NAME                    default transaction-read-oci-evidence-execution-<timestamp>
  OCI_EVIDENCE_EXECUTION_INPUT_TSV               required TSV with OCI execution manifest
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR              default build/reports/k6/<gate>
  OCI_EVIDENCE_EXECUTION_REQUIRED_SCENARIOS      default hikari-lifetime,real-ip-multisource,mixed-workload-30m,cold-warm-cache,deploy-drain,p999-long-correlation
  OCI_EVIDENCE_EXECUTION_MAX_EDGE_429_RATE       default 0.10
  OCI_EVIDENCE_EXECUTION_MAX_P999_MS             default 500
  OCI_EVIDENCE_EXECUTION_MAX_POOL_PENDING        default 0
  OCI_EVIDENCE_EXECUTION_MIXED_MIN_DURATION_MIN  default 30
  OCI_EVIDENCE_EXECUTION_P999_MIN_DURATION_MIN   default 30
  OCI_EVIDENCE_EXECUTION_HIKARI_MIN_DURATION_MIN default 10
  OCI_EVIDENCE_EXECUTION_DEPLOY_MIN_DURATION_MIN default 5
  OCI_EVIDENCE_EXECUTION_MIN_REAL_SOURCE_IPS     default 2
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
required_scenarios="${OCI_EVIDENCE_EXECUTION_REQUIRED_SCENARIOS:-hikari-lifetime,real-ip-multisource,mixed-workload-30m,cold-warm-cache,deploy-drain,p999-long-correlation}"
max_edge_429_rate="${OCI_EVIDENCE_EXECUTION_MAX_EDGE_429_RATE:-0.10}"
max_p999_ms="${OCI_EVIDENCE_EXECUTION_MAX_P999_MS:-500}"
max_pool_pending="${OCI_EVIDENCE_EXECUTION_MAX_POOL_PENDING:-0}"
mixed_min_duration_min="${OCI_EVIDENCE_EXECUTION_MIXED_MIN_DURATION_MIN:-30}"
p999_min_duration_min="${OCI_EVIDENCE_EXECUTION_P999_MIN_DURATION_MIN:-30}"
hikari_min_duration_min="${OCI_EVIDENCE_EXECUTION_HIKARI_MIN_DURATION_MIN:-10}"
deploy_min_duration_min="${OCI_EVIDENCE_EXECUTION_DEPLOY_MIN_DURATION_MIN:-5}"
min_real_source_ips="${OCI_EVIDENCE_EXECUTION_MIN_REAL_SOURCE_IPS:-2}"
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
  echo "[transaction-read-oci-evidence-execution] mixed_min_duration_min=${mixed_min_duration_min}"
  echo "[transaction-read-oci-evidence-execution] p999_min_duration_min=${p999_min_duration_min}"
  echo "[transaction-read-oci-evidence-execution] hikari_min_duration_min=${hikari_min_duration_min}"
  echo "[transaction-read-oci-evidence-execution] deploy_min_duration_min=${deploy_min_duration_min}"
  echo "[transaction-read-oci-evidence-execution] min_real_source_ips=${min_real_source_ips}"
  echo "[transaction-read-oci-evidence-execution] summary_tsv=${summary_tsv}"
  echo "[transaction-read-oci-evidence-execution] report_md=${report_md}"
}

require_rate "OCI_EVIDENCE_EXECUTION_MAX_EDGE_429_RATE" "${max_edge_429_rate}"
require_non_negative_number "OCI_EVIDENCE_EXECUTION_MAX_P999_MS" "${max_p999_ms}"
require_non_negative_integer "OCI_EVIDENCE_EXECUTION_MAX_POOL_PENDING" "${max_pool_pending}"
require_non_negative_integer "OCI_EVIDENCE_EXECUTION_MIXED_MIN_DURATION_MIN" "${mixed_min_duration_min}"
require_non_negative_integer "OCI_EVIDENCE_EXECUTION_P999_MIN_DURATION_MIN" "${p999_min_duration_min}"
require_non_negative_integer "OCI_EVIDENCE_EXECUTION_HIKARI_MIN_DURATION_MIN" "${hikari_min_duration_min}"
require_non_negative_integer "OCI_EVIDENCE_EXECUTION_DEPLOY_MIN_DURATION_MIN" "${deploy_min_duration_min}"
require_non_negative_integer "OCI_EVIDENCE_EXECUTION_MIN_REAL_SOURCE_IPS" "${min_real_source_ips}"

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
  -v mixed_min_duration_min="${mixed_min_duration_min}" \
  -v p999_min_duration_min="${p999_min_duration_min}" \
  -v hikari_min_duration_min="${hikari_min_duration_min}" \
  -v deploy_min_duration_min="${deploy_min_duration_min}" \
  -v min_real_source_ips="${min_real_source_ips}" '
function value(name, fallback) {
  if (!(name in col) || col[name] == "") return fallback
  return $(col[name])
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
  print "scenario\tstatus\treason\trun_id\tduration_min\tsource_ips\trun_script\tk6_summary_ref\tnginx_access_ref\tspring_metrics_ref\thikari_log_ref\tpostgres_wait_ref\tdeploy_event_ref\tcache_state_ref\ttimeline_ref\tedge_429_rate\tbackend_429_count\tunknown_429_count\tfive_xx_count\tnginx_499_count\thikari_validation_warnings\tdb_pool_pending_max\tp999_ms"
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
  if (scenario == "mixed-workload-30m" && duration_min < mixed_min_duration_min) add_reason("mixed-duration<" mixed_min_duration_min)
  if (scenario == "mixed-workload-30m") {
    require_ref("workload_mix_ref", "workload-mix-missing")
    require_ref("workload_component_ref", "workload-component-missing")
    require_ref("outbox_lag_ref", "outbox-lag-missing")
    if (value("outbox_lag_max", "1") + 0 > 0) add_reason("outbox-lag>0")
  }
  if (scenario == "p999-long-correlation" && duration_min < p999_min_duration_min) add_reason("p999-duration<" p999_min_duration_min)
  if (scenario == "p999-long-correlation") {
    require_ref("postgres_checkpoint_ref", "postgres-checkpoint-missing")
    require_ref("postgres_temp_file_ref", "postgres-temp-file-missing")
    require_ref("nginx_upstream_latency_ref", "nginx-upstream-latency-missing")
  }
  if (scenario == "hikari-lifetime" && duration_min < hikari_min_duration_min) add_reason("hikari-duration<" hikari_min_duration_min)
  if (scenario == "deploy-drain" && duration_min < deploy_min_duration_min) add_reason("deploy-duration<" deploy_min_duration_min)
  if (scenario == "real-ip-multisource" && source_ips < min_real_source_ips) add_reason("source-ips<" min_real_source_ips)

  if (edge_429_rate > max_edge_429_rate) add_reason("edge429>" max_edge_429_rate)
  if (backend_429_count > 0) add_reason("backend429>0")
  if (unknown_429_count > 0) add_reason("unknown429>0")
  if (five_xx_count > 0) add_reason("5xx>0")
  if (nginx_499_count > 0) add_reason("499>0")
  if (hikari_validation_warnings > 0) add_reason("hikari-warning>0")
  if (db_pool_pending_max > max_pool_pending) add_reason("pool-pending>" max_pool_pending)
  if (p999_ms > max_p999_ms) add_reason("p999>" max_p999_ms)

  if (status == "fail") fail_count++
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    scenario, status, reason, run_id, duration_min, source_ips, run_script,
    value("k6_summary_ref", ""), value("nginx_access_ref", ""), value("spring_metrics_ref", ""),
    value("hikari_log_ref", ""), value("postgres_wait_ref", ""), value("deploy_event_ref", ""),
    value("cache_state_ref", ""), value("timeline_ref", ""), value("edge_429_rate", "0"),
    value("backend_429_count", "0"), value("unknown_429_count", "0"), value("five_xx_count", "0"), value("nginx_499_count", "0"),
    value("hikari_validation_warnings", "0"), value("db_pool_pending_max", "0"), value("p999_ms", "0")
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
    print "| Scenario | Status | Reason | Run id | Duration min | Source IPs | Script | Edge 429 | Unknown 429 | 499 | 5xx | Hikari warnings | p99.9 ms | Timeline |"
    print "| --- | --- | --- | --- | ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n",
      $1, $2, $3, $4, $5, $6, $7, $16, $18, $20, $19, $21, $23, $15
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read OCI Evidence Execution Gate

## Summary

- gate_status=${gate_status}
- missing_scenarios=${missing_scenarios:-none}
- required scenarios: ${required_scenarios}
- execution artifacts: k6, nginx access, Spring metrics, Hikari log, PostgreSQL wait, timeline
- real-IP minimum sources: ${min_real_source_ips}
- mixed workload min duration: ${mixed_min_duration_min}m
- p99.9 long correlation min duration: ${p999_min_duration_min}m
- Hikari lifetime min duration: ${hikari_min_duration_min}m
- deploy drain min duration: ${deploy_min_duration_min}m
- edge 429 max rate: ${max_edge_429_rate}
- p99.9 max: ${max_p999_ms}ms
- backend 429/unknown 429/499/5xx/Hikari warning/pool pending target: 0
- unknown 429 hard-zero
- p99.9 closure artifacts: PostgreSQL checkpoint, temp file, Nginx upstream latency
- mixed workload closure artifacts: workload mix, component split, outbox lag

## Result Table

${result_table}

## Evidence Contract

- OCI evidence는 run id, 실행 시각, 실행 script, k6 summary, Nginx access, Spring metrics, Hikari log, PostgreSQL wait, timeline을 같은 row에 남긴다.
- p99.9 long correlation은 PostgreSQL checkpoint, temp file, Nginx upstream latency artifact를 같은 run id로 묶는다.
- mixed workload는 workload mix, component split, outbox lag artifact를 같은 run id로 묶고 outbox lag max 0을 요구한다.
- cold/warm cache는 cache state artifact, deploy drain은 deploy event artifact를 추가로 요구한다.
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
