#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-production-evidence-pack.sh [--print-plan]

Environment:
  PROD_EVIDENCE_PACK_NAME                 default transaction-read-production-evidence-pack-<timestamp>
  PROD_EVIDENCE_PACK_INPUT_TSV            required TSV with OCI production evidence refs
  PROD_EVIDENCE_PACK_OUTPUT_DIR           default build/reports/k6/<name>
  PROD_EVIDENCE_PACK_REQUIRED_SCENARIOS   default hikari-soak,cold-warm,mixed-workload,real-ip-multisource,deploy-drain,p999-long
  PROD_EVIDENCE_PACK_MIN_REAL_SOURCE_IPS  default 2
  PROD_EVIDENCE_PACK_MAX_EDGE_429_RATE    default 0.10
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

name="${PROD_EVIDENCE_PACK_NAME:-transaction-read-production-evidence-pack-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${PROD_EVIDENCE_PACK_INPUT_TSV:-}"
output_dir="${PROD_EVIDENCE_PACK_OUTPUT_DIR:-build/reports/k6/${name}}"
required_scenarios="${PROD_EVIDENCE_PACK_REQUIRED_SCENARIOS:-hikari-soak,cold-warm,mixed-workload,real-ip-multisource,deploy-drain,p999-long}"
min_real_source_ips="${PROD_EVIDENCE_PACK_MIN_REAL_SOURCE_IPS:-2}"
max_edge_429_rate="${PROD_EVIDENCE_PACK_MAX_EDGE_429_RATE:-0.10}"
summary_tsv="${output_dir}/${name}-production-evidence-pack.tsv"
report_md="${output_dir}/${name}-production-evidence-pack.md"
meta_file="${output_dir}/${name}-production-evidence-pack.meta"

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
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${key} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  fi
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

scenario_list() {
  if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
    echo "missing"
    return
  fi
  awk -F '\t' '
    NR == 1 {
      for (i = 1; i <= NF; i++) if ($i == "scenario") scenario_col = i
      next
    }
    scenario_col {
      if (result != "") result = result ","
      result = result $scenario_col
    }
    END { if (result == "") print "missing"; else print result }
  ' "${input_tsv}"
}

require_non_negative_integer "PROD_EVIDENCE_PACK_MIN_REAL_SOURCE_IPS" "${min_real_source_ips}"
require_rate "PROD_EVIDENCE_PACK_MAX_EDGE_429_RATE" "${max_edge_429_rate}"

print_plan() {
  echo "[transaction-read-production-evidence-pack] name=${name}"
  echo "[transaction-read-production-evidence-pack] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-production-evidence-pack] output_dir=${output_dir}"
  echo "[transaction-read-production-evidence-pack] scenarios=$(scenario_list)"
  echo "[transaction-read-production-evidence-pack] required_scenarios=${required_scenarios}"
  echo "[transaction-read-production-evidence-pack] min_real_source_ips=${min_real_source_ips}"
  echo "[transaction-read-production-evidence-pack] max_edge_429_rate=${max_edge_429_rate}"
  echo "[transaction-read-production-evidence-pack] required_refs=k6_summary_ref,nginx_aggregate_ref,nginx_499_aggregate_ref,spring_429_ref,hikari_log_ref,postgres_explain_ref,postgres_activity_ref,postgres_wait_ref,prometheus_timeline_ref,artifact_manifest_ref,hikari_closure_ref(hikari-soak)"
  echo "[transaction-read-production-evidence-pack] hard_zero=backend_429_count,unknown_429_count,five_xx_count,nginx_499_count,hikari_validation_warnings,db_pool_pending_max"
  echo "[transaction-read-production-evidence-pack] summary_tsv=${summary_tsv}"
  echo "[transaction-read-production-evidence-pack] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "PROD_EVIDENCE_PACK_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "PROD_EVIDENCE_PACK_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

awk -F '\t' \
  -v required_scenarios="${required_scenarios}" \
  -v min_real_source_ips="${min_real_source_ips}" \
  -v max_edge_429_rate="${max_edge_429_rate}" '
function value(name, fallback) {
  if (!(name in col) || col[name] == "") return fallback
  return $(col[name])
}
function add_reason(value) {
  if (reason == "ok") reason = value
  else reason = reason "," value
  status = "fail"
}
function unsafe_ref(value) {
  return value ~ /:\/\// || value ~ /(^|[?&])(token|password|secret|access_key|signature)=/ || value ~ /(Bearer|Authorization|PRIVATE KEY)/
}
function require_ref(name) {
  ref = value(name, "")
  if (ref == "" || ref == "n/a") add_reason(name "-missing")
  else if (unsafe_ref(ref)) add_reason(name "-unsafe")
}
BEGIN {
  split(required_scenarios, required_items, ",")
  for (i in required_items) required[required_items[i]] = 1
  split("scenario run_id duration_min source_ips k6_summary_ref nginx_aggregate_ref nginx_499_aggregate_ref spring_429_ref hikari_log_ref hikari_closure_ref postgres_explain_ref postgres_activity_ref postgres_wait_ref prometheus_timeline_ref artifact_manifest_ref mixed_workload_ref p999_latency_ref source_fairness_ref cache_state_ref deploy_event_ref edge_429_rate backend_429_count unknown_429_count five_xx_count nginx_499_count hikari_validation_warnings db_pool_pending_max", header_items, " ")
  print "scenario\tstatus\treason\trun_id\tduration_min\tsource_ips\tedge_429_rate\tbackend_429_count\tunknown_429_count\tfive_xx_count\tnginx_499_count\thikari_validation_warnings\tdb_pool_pending_max\tk6_summary_ref\tnginx_aggregate_ref\tnginx_499_aggregate_ref\tspring_429_ref\thikari_log_ref\thikari_closure_ref\tpostgres_explain_ref\tpostgres_activity_ref\tpostgres_wait_ref\tprometheus_timeline_ref\tartifact_manifest_ref\tmixed_workload_ref\tp999_latency_ref\tsource_fairness_ref\tcache_state_ref\tdeploy_event_ref"
}
NR == 1 {
  for (i = 1; i <= NF; i++) col[$i] = i
  for (i in header_items) {
    if (!(header_items[i] in col)) {
      printf "missing required column: %s\n", header_items[i] > "/dev/stderr"
      exit 2
    }
  }
  next
}
{
  scenario = value("scenario", "unknown")
  status = "pass"
  reason = "ok"
  seen[scenario] = 1

  require_ref("k6_summary_ref")
  require_ref("nginx_aggregate_ref")
  require_ref("nginx_499_aggregate_ref")
  require_ref("spring_429_ref")
  require_ref("hikari_log_ref")
  require_ref("postgres_explain_ref")
  require_ref("postgres_activity_ref")
  require_ref("postgres_wait_ref")
  require_ref("prometheus_timeline_ref")
  require_ref("artifact_manifest_ref")

  if (value("edge_429_rate", "1") + 0 > max_edge_429_rate) add_reason("edge429>" max_edge_429_rate)
  if (value("backend_429_count", "1") + 0 > 0) add_reason("backend429>0")
  if (value("unknown_429_count", "1") + 0 > 0) add_reason("unknown429>0")
  if (value("five_xx_count", "1") + 0 > 0) add_reason("5xx>0")
  if (value("nginx_499_count", "1") + 0 > 0) add_reason("499>0")
  if (value("hikari_validation_warnings", "1") + 0 > 0) add_reason("hikari-warning>0")
  if (value("db_pool_pending_max", "1") + 0 > 0) add_reason("pool-pending>0")

  source_fairness_ref = value("source_fairness_ref", "n/a")
  cache_state_ref = value("cache_state_ref", "n/a")
  deploy_event_ref = value("deploy_event_ref", "n/a")
  artifact_manifest_ref = value("artifact_manifest_ref", "n/a")
  hikari_closure_ref = value("hikari_closure_ref", "n/a")
  mixed_workload_ref = value("mixed_workload_ref", "n/a")
  p999_latency_ref = value("p999_latency_ref", "n/a")

  if (scenario == "hikari-soak") require_ref("hikari_closure_ref")
  if (scenario == "mixed-workload") require_ref("mixed_workload_ref")
  if (scenario == "p999-long") require_ref("p999_latency_ref")
  if (scenario == "real-ip-multisource") {
    if (value("source_ips", "0") + 0 < min_real_source_ips) add_reason("source-ips<" min_real_source_ips)
    if (source_fairness_ref == "" || source_fairness_ref == "n/a") add_reason("source-fairness-missing")
    else if (unsafe_ref(source_fairness_ref)) add_reason("source-fairness-unsafe")
  }
  if (scenario == "cold-warm") {
    if (cache_state_ref == "" || cache_state_ref == "n/a") add_reason("cache-state-missing")
    else if (unsafe_ref(cache_state_ref)) add_reason("cache-state-unsafe")
  }
  if (scenario == "deploy-drain") {
    if (deploy_event_ref == "" || deploy_event_ref == "n/a") add_reason("deploy-event-missing")
    else if (unsafe_ref(deploy_event_ref)) add_reason("deploy-event-unsafe")
  }

  if (status == "fail") fail_count++
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    scenario, status, reason, value("run_id", ""),
    value("duration_min", "0"), value("source_ips", "0"), value("edge_429_rate", "0"),
    value("backend_429_count", "0"), value("unknown_429_count", "0"),
    value("five_xx_count", "0"), value("nginx_499_count", "0"),
    value("hikari_validation_warnings", "0"), value("db_pool_pending_max", "0"),
    value("k6_summary_ref", ""), value("nginx_aggregate_ref", ""),
    value("nginx_499_aggregate_ref", ""), value("spring_429_ref", ""),
    value("hikari_log_ref", ""), hikari_closure_ref, value("postgres_explain_ref", ""),
    value("postgres_activity_ref", ""), value("postgres_wait_ref", ""),
    value("prometheus_timeline_ref", ""), artifact_manifest_ref, mixed_workload_ref,
    p999_latency_ref, source_fairness_ref, cache_state_ref,
    deploy_event_ref
}
END {
  missing = ""
  for (scenario in required) {
    if (!(scenario in seen)) {
      if (missing != "") missing = missing ","
      missing = missing scenario
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

scenario_table="$(awk -F '\t' '
  BEGIN {
    print "| Scenario | Status | Reason | Source IPs | Edge 429 | 499 | 5xx | Hikari warnings | Pool pending | Timeline |"
    print "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $6, $7, $11, $10, $12, $13, $23
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Production Evidence Pack

## Summary

- gate_status=${gate_status}
- required scenarios: ${required_scenarios}
- missing scenarios: ${missing_scenarios:-none}
- min real source IPs: ${min_real_source_ips}
- max edge 429 rate: ${max_edge_429_rate}
- hard-zero: backend 429, unknown 429, 499, 5xx, Hikari warning, Hikari pending

## Required Evidence

- k6 summary
- Nginx aggregate
- Nginx 499 aggregate
- Spring 429 attribution
- Hikari log
- Hikari zero-budget closure report
- PostgreSQL EXPLAIN
- PostgreSQL activity sampler
- PostgreSQL wait timeline
- Prometheus timeline
- OCI artifact manifest
- Hikari zero-budget closure artifact for hikari-soak runs
- mixed workload interference artifact for mixed-workload runs
- p99.9 latency artifact for p999-long runs
- source fairness artifact for real multi-source runs
- cache state artifact for cold/warm runs
- deploy event artifact for drain runs

## Scenario Table

${scenario_table}

## Contract Notes

- 운영 secret, token, URL은 artifact ref에 넣지 않고 상대 경로만 기록한다.
- 이 runner는 부하를 실행하지 않고 OCI에서 만든 증거 manifest를 review 가능한 pack으로 정규화한다.
- 429 source, 499, Hikari, PostgreSQL activity/wait, p99.9, cache state, deploy drain 증거는 같은 run id로 묶어야 한다.

## Artifacts

- input TSV: ${input_tsv}
- summary TSV: ${summary_tsv}
- report: ${report_md}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read production evidence pack failed: ${summary_tsv}" >&2
  exit 1
fi
