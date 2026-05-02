#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-operational-evidence-gate.sh [--print-plan]

Environment:
  OP_EVIDENCE_NAME                default transaction-read-operational-evidence-<timestamp>
  OP_EVIDENCE_INPUT_TSV           required TSV with OCI scenario evidence
  OP_EVIDENCE_OUTPUT_DIR          default build/reports/k6/<gate>
  OP_EVIDENCE_REQUIRED_SCENARIOS  default hikari-soak,cold-warm,mixed-workload,real-ip-multisource,deploy-drain,p999-long
  OP_EVIDENCE_MAX_EDGE_429_RATE   default 0.10
  OP_EVIDENCE_MAX_COLD_P95_MS     default 1000
  OP_EVIDENCE_MAX_WARM_P95_MS     default 350
  OP_EVIDENCE_MAX_P999_MS         default 500
  OP_EVIDENCE_MAX_POOL_PENDING    default 0
  OP_EVIDENCE_MAX_PG_WAIT_P95_MS  default 20
  OP_EVIDENCE_MAX_PG_WAIT_P999_MS default 100
  OP_EVIDENCE_MAX_CPU_PCT         default 95
  OP_EVIDENCE_MAX_MEMORY_PCT      default 90
  OP_EVIDENCE_MAX_DISK_IO_WAIT_PCT default 25
  OP_EVIDENCE_MIN_CACHE_BUFFER_HIT_RATIO default 0.95
  OP_EVIDENCE_MAX_NGINX_UPSTREAM_P999_MS default 500
  OP_EVIDENCE_MIXED_MIN_DURATION_MIN  default 30
  OP_EVIDENCE_P999_MIN_DURATION_MIN   default 30
  OP_EVIDENCE_HIKARI_MIN_DURATION_MIN default 10
  OP_EVIDENCE_DEPLOY_MIN_DURATION_MIN default 5
  OP_EVIDENCE_MIN_REAL_SOURCE_IPS     default 2
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

name="${OP_EVIDENCE_NAME:-transaction-read-operational-evidence-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${OP_EVIDENCE_INPUT_TSV:-}"
output_dir="${OP_EVIDENCE_OUTPUT_DIR:-build/reports/k6/${name}}"
required_scenarios="${OP_EVIDENCE_REQUIRED_SCENARIOS:-hikari-soak,cold-warm,mixed-workload,real-ip-multisource,deploy-drain,p999-long}"
max_edge_429_rate="${OP_EVIDENCE_MAX_EDGE_429_RATE:-0.10}"
max_cold_p95_ms="${OP_EVIDENCE_MAX_COLD_P95_MS:-1000}"
max_warm_p95_ms="${OP_EVIDENCE_MAX_WARM_P95_MS:-350}"
max_p999_ms="${OP_EVIDENCE_MAX_P999_MS:-500}"
max_pool_pending="${OP_EVIDENCE_MAX_POOL_PENDING:-0}"
max_pg_wait_p95_ms="${OP_EVIDENCE_MAX_PG_WAIT_P95_MS:-20}"
max_pg_wait_p999_ms="${OP_EVIDENCE_MAX_PG_WAIT_P999_MS:-100}"
max_cpu_pct="${OP_EVIDENCE_MAX_CPU_PCT:-95}"
max_memory_pct="${OP_EVIDENCE_MAX_MEMORY_PCT:-90}"
max_disk_io_wait_pct="${OP_EVIDENCE_MAX_DISK_IO_WAIT_PCT:-25}"
min_cache_buffer_hit_ratio="${OP_EVIDENCE_MIN_CACHE_BUFFER_HIT_RATIO:-0.95}"
max_nginx_upstream_p999_ms="${OP_EVIDENCE_MAX_NGINX_UPSTREAM_P999_MS:-500}"
mixed_min_duration_min="${OP_EVIDENCE_MIXED_MIN_DURATION_MIN:-30}"
p999_min_duration_min="${OP_EVIDENCE_P999_MIN_DURATION_MIN:-30}"
hikari_min_duration_min="${OP_EVIDENCE_HIKARI_MIN_DURATION_MIN:-10}"
deploy_min_duration_min="${OP_EVIDENCE_DEPLOY_MIN_DURATION_MIN:-5}"
min_real_source_ips="${OP_EVIDENCE_MIN_REAL_SOURCE_IPS:-2}"
summary_tsv="${output_dir}/${name}-operational-evidence.tsv"
report_md="${output_dir}/${name}-operational-evidence.md"
meta_file="${output_dir}/${name}-operational-evidence.meta"

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

require_rate "OP_EVIDENCE_MAX_EDGE_429_RATE" "${max_edge_429_rate}"
require_non_negative_number "OP_EVIDENCE_MAX_COLD_P95_MS" "${max_cold_p95_ms}"
require_non_negative_number "OP_EVIDENCE_MAX_WARM_P95_MS" "${max_warm_p95_ms}"
require_non_negative_number "OP_EVIDENCE_MAX_P999_MS" "${max_p999_ms}"
require_non_negative_integer "OP_EVIDENCE_MAX_POOL_PENDING" "${max_pool_pending}"
require_non_negative_number "OP_EVIDENCE_MAX_PG_WAIT_P95_MS" "${max_pg_wait_p95_ms}"
require_non_negative_number "OP_EVIDENCE_MAX_PG_WAIT_P999_MS" "${max_pg_wait_p999_ms}"
require_non_negative_number "OP_EVIDENCE_MAX_CPU_PCT" "${max_cpu_pct}"
require_non_negative_number "OP_EVIDENCE_MAX_MEMORY_PCT" "${max_memory_pct}"
require_non_negative_number "OP_EVIDENCE_MAX_DISK_IO_WAIT_PCT" "${max_disk_io_wait_pct}"
require_rate "OP_EVIDENCE_MIN_CACHE_BUFFER_HIT_RATIO" "${min_cache_buffer_hit_ratio}"
require_non_negative_number "OP_EVIDENCE_MAX_NGINX_UPSTREAM_P999_MS" "${max_nginx_upstream_p999_ms}"
require_non_negative_integer "OP_EVIDENCE_MIXED_MIN_DURATION_MIN" "${mixed_min_duration_min}"
require_non_negative_integer "OP_EVIDENCE_P999_MIN_DURATION_MIN" "${p999_min_duration_min}"
require_non_negative_integer "OP_EVIDENCE_HIKARI_MIN_DURATION_MIN" "${hikari_min_duration_min}"
require_non_negative_integer "OP_EVIDENCE_DEPLOY_MIN_DURATION_MIN" "${deploy_min_duration_min}"
require_non_negative_integer "OP_EVIDENCE_MIN_REAL_SOURCE_IPS" "${min_real_source_ips}"

print_plan() {
  echo "[transaction-read-operational-evidence] name=${name}"
  echo "[transaction-read-operational-evidence] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-operational-evidence] output_dir=${output_dir}"
  echo "[transaction-read-operational-evidence] required_scenarios=${required_scenarios}"
  echo "[transaction-read-operational-evidence] max_edge_429_rate=${max_edge_429_rate}"
  echo "[transaction-read-operational-evidence] max_cold_p95_ms=${max_cold_p95_ms}"
  echo "[transaction-read-operational-evidence] max_warm_p95_ms=${max_warm_p95_ms}"
  echo "[transaction-read-operational-evidence] max_p999_ms=${max_p999_ms}"
  echo "[transaction-read-operational-evidence] max_pool_pending=${max_pool_pending}"
  echo "[transaction-read-operational-evidence] max_pg_wait_p95_ms=${max_pg_wait_p95_ms}"
  echo "[transaction-read-operational-evidence] max_pg_wait_p999_ms=${max_pg_wait_p999_ms}"
  echo "[transaction-read-operational-evidence] max_cpu_pct=${max_cpu_pct}"
  echo "[transaction-read-operational-evidence] max_memory_pct=${max_memory_pct}"
  echo "[transaction-read-operational-evidence] max_disk_io_wait_pct=${max_disk_io_wait_pct}"
  echo "[transaction-read-operational-evidence] min_cache_buffer_hit_ratio=${min_cache_buffer_hit_ratio}"
  echo "[transaction-read-operational-evidence] max_nginx_upstream_p999_ms=${max_nginx_upstream_p999_ms}"
  echo "[transaction-read-operational-evidence] mixed_min_duration_min=${mixed_min_duration_min}"
  echo "[transaction-read-operational-evidence] p999_min_duration_min=${p999_min_duration_min}"
  echo "[transaction-read-operational-evidence] hikari_min_duration_min=${hikari_min_duration_min}"
  echo "[transaction-read-operational-evidence] deploy_min_duration_min=${deploy_min_duration_min}"
  echo "[transaction-read-operational-evidence] min_real_source_ips=${min_real_source_ips}"
  echo "[transaction-read-operational-evidence] summary_tsv=${summary_tsv}"
  echo "[transaction-read-operational-evidence] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
    echo "OP_EVIDENCE_INPUT_TSV is required" >&2
    exit 1
  fi
  exit 0
fi

if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
  echo "OP_EVIDENCE_INPUT_TSV is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"

awk -F '\t' \
  -v required_scenarios="${required_scenarios}" \
  -v max_edge_429_rate="${max_edge_429_rate}" \
  -v max_cold_p95_ms="${max_cold_p95_ms}" \
  -v max_warm_p95_ms="${max_warm_p95_ms}" \
  -v max_p999_ms="${max_p999_ms}" \
  -v max_pool_pending="${max_pool_pending}" \
  -v max_pg_wait_p95_ms="${max_pg_wait_p95_ms}" \
  -v max_pg_wait_p999_ms="${max_pg_wait_p999_ms}" \
  -v max_cpu_pct="${max_cpu_pct}" \
  -v max_memory_pct="${max_memory_pct}" \
  -v max_disk_io_wait_pct="${max_disk_io_wait_pct}" \
  -v min_cache_buffer_hit_ratio="${min_cache_buffer_hit_ratio}" \
  -v max_nginx_upstream_p999_ms="${max_nginx_upstream_p999_ms}" \
  -v mixed_min_duration_min="${mixed_min_duration_min}" \
  -v p999_min_duration_min="${p999_min_duration_min}" \
  -v hikari_min_duration_min="${hikari_min_duration_min}" \
  -v deploy_min_duration_min="${deploy_min_duration_min}" \
  -v min_real_source_ips="${min_real_source_ips}" '
function add_reason(value) {
  if (reason == "ok") {
    reason = value
  } else {
    reason = reason "," value
  }
  status = "fail"
}
BEGIN {
  split(required_scenarios, required_items, ",")
  for (i in required_items) {
    required[required_items[i]] = 1
  }
  split("scenario duration_min source_ips cold_p95_ms warm_p95_ms p999_ms edge_429_rate backend_429_count five_xx_count nginx_499_count hikari_validation_warnings db_pool_pending_max sse_reject_count deploy_drain_5xx_count pg_wait_p95_ms pg_wait_p999_ms cpu_max_pct memory_max_pct disk_io_wait_pct network_rx_drop_count network_tx_drop_count timeline_artifact resource_timeline_artifact workload_mix_ref outbox_lag_max cache_buffer_hit_ratio cache_wait_event_ref nginx_upstream_p999_ms deploy_retry_contract_ref deploy_reconnect_success_count", header_items, " ")
  print "scenario\tstatus\treason\tduration_min\tsource_ips\tcold_p95_ms\twarm_p95_ms\tp999_ms\tedge_429_rate\tbackend_429_count\tfive_xx_count\tnginx_499_count\thikari_validation_warnings\tdb_pool_pending_max\tsse_reject_count\tdeploy_drain_5xx_count\tpg_wait_p95_ms\tpg_wait_p999_ms\tcpu_max_pct\tmemory_max_pct\tdisk_io_wait_pct\tnetwork_rx_drop_count\tnetwork_tx_drop_count\ttimeline_artifact\tresource_timeline_artifact\tworkload_mix_ref\toutbox_lag_max\tcache_buffer_hit_ratio\tcache_wait_event_ref\tnginx_upstream_p999_ms\tdeploy_retry_contract_ref\tdeploy_reconnect_success_count"
}
NR == 1 {
  for (i = 1; i <= NF; i++) {
    col[$i] = i
  }
  for (i in header_items) {
    if (!(header_items[i] in col)) {
      printf "missing required column: %s\n", header_items[i] > "/dev/stderr"
      exit 2
    }
  }
  next
}
{
  scenario = $col["scenario"]
  duration_min = $col["duration_min"] + 0
  source_ips = $col["source_ips"] + 0
  cold_p95_ms = $col["cold_p95_ms"] + 0
  warm_p95_ms = $col["warm_p95_ms"] + 0
  p999_ms = $col["p999_ms"] + 0
  edge_429_rate = $col["edge_429_rate"] + 0
  backend_429_count = $col["backend_429_count"] + 0
  five_xx_count = $col["five_xx_count"] + 0
  nginx_499_count = $col["nginx_499_count"] + 0
  hikari_validation_warnings = $col["hikari_validation_warnings"] + 0
  db_pool_pending_max = $col["db_pool_pending_max"] + 0
  sse_reject_count = $col["sse_reject_count"] + 0
  deploy_drain_5xx_count = $col["deploy_drain_5xx_count"] + 0
  pg_wait_p95_ms = $col["pg_wait_p95_ms"] + 0
  pg_wait_p999_ms = $col["pg_wait_p999_ms"] + 0
  cpu_max_pct = $col["cpu_max_pct"] + 0
  memory_max_pct = $col["memory_max_pct"] + 0
  disk_io_wait_pct = $col["disk_io_wait_pct"] + 0
  network_rx_drop_count = $col["network_rx_drop_count"] + 0
  network_tx_drop_count = $col["network_tx_drop_count"] + 0
  timeline_artifact = $col["timeline_artifact"]
  resource_timeline_artifact = $col["resource_timeline_artifact"]
  workload_mix_ref = $col["workload_mix_ref"]
  outbox_lag_max = $col["outbox_lag_max"] + 0
  cache_buffer_hit_ratio = $col["cache_buffer_hit_ratio"] + 0
  cache_wait_event_ref = $col["cache_wait_event_ref"]
  nginx_upstream_p999_ms = $col["nginx_upstream_p999_ms"] + 0
  deploy_retry_contract_ref = $col["deploy_retry_contract_ref"]
  deploy_reconnect_success_count = $col["deploy_reconnect_success_count"] + 0
  status = "pass"
  reason = "ok"
  seen[scenario] = 1

  if (cold_p95_ms > max_cold_p95_ms) add_reason("cold-p95>" max_cold_p95_ms)
  if (warm_p95_ms > max_warm_p95_ms) add_reason("warm-p95>" max_warm_p95_ms)
  if (p999_ms > max_p999_ms) add_reason("p999>" max_p999_ms)
  if (edge_429_rate > max_edge_429_rate) add_reason("edge429>" max_edge_429_rate)
  if (backend_429_count > 0) add_reason("backend429>0")
  if (five_xx_count > 0) add_reason("5xx>0")
  if (nginx_499_count > 0) add_reason("499>0")
  if (hikari_validation_warnings > 0) add_reason("hikari-warning>0")
  if (db_pool_pending_max > max_pool_pending) add_reason("pool-pending>" max_pool_pending)
  if (sse_reject_count > 0) add_reason("sse-reject>0")
  if (deploy_drain_5xx_count > 0) add_reason("deploy-drain-5xx>0")
  if (pg_wait_p95_ms > max_pg_wait_p95_ms) add_reason("pg-wait-p95>" max_pg_wait_p95_ms)
  if (pg_wait_p999_ms > max_pg_wait_p999_ms) add_reason("pg-wait-p999>" max_pg_wait_p999_ms)
  if (cpu_max_pct > max_cpu_pct) add_reason("cpu>" max_cpu_pct)
  if (memory_max_pct > max_memory_pct) add_reason("memory>" max_memory_pct)
  if (disk_io_wait_pct > max_disk_io_wait_pct) add_reason("disk-io-wait>" max_disk_io_wait_pct)
  if (network_rx_drop_count > 0 || network_tx_drop_count > 0) add_reason("network-drop>0")
  if (timeline_artifact == "" || timeline_artifact == "n/a") add_reason("timeline-missing")
  if (resource_timeline_artifact == "" || resource_timeline_artifact == "n/a") add_reason("resource-timeline-missing")
  if (nginx_upstream_p999_ms > max_nginx_upstream_p999_ms) add_reason("nginx-p999>" max_nginx_upstream_p999_ms)
  if (scenario == "mixed-workload") {
    if (workload_mix_ref == "" || workload_mix_ref == "n/a") add_reason("workload-mix-missing")
    if (outbox_lag_max > 0) add_reason("outbox-lag>0")
  }
  if (scenario == "cold-warm") {
    if (cache_buffer_hit_ratio < min_cache_buffer_hit_ratio) add_reason("cache-buffer-hit<" min_cache_buffer_hit_ratio)
    if (cache_wait_event_ref == "" || cache_wait_event_ref == "n/a") add_reason("cache-wait-event-missing")
  }
  if (scenario == "deploy-drain") {
    if (deploy_retry_contract_ref == "" || deploy_retry_contract_ref == "n/a") add_reason("deploy-retry-contract-missing")
    if (deploy_reconnect_success_count <= 0) add_reason("deploy-reconnect-missing")
  }
  if (scenario == "mixed-workload" && duration_min < mixed_min_duration_min) {
    add_reason("mixed-duration<" mixed_min_duration_min)
  }
  if (scenario == "p999-long" && duration_min < p999_min_duration_min) {
    add_reason("p999-duration<" p999_min_duration_min)
  }
  if (scenario == "hikari-soak" && duration_min < hikari_min_duration_min) {
    add_reason("hikari-duration<" hikari_min_duration_min)
  }
  if (scenario == "deploy-drain" && duration_min < deploy_min_duration_min) {
    add_reason("deploy-duration<" deploy_min_duration_min)
  }
  if (scenario == "real-ip-multisource" && source_ips < min_real_source_ips) {
    add_reason("source-ips<" min_real_source_ips)
  }

  if (status == "fail") fail_count++
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    scenario, status, reason, duration_min, source_ips, cold_p95_ms, warm_p95_ms, p999_ms,
    edge_429_rate, backend_429_count, five_xx_count, nginx_499_count,
    hikari_validation_warnings, db_pool_pending_max, sse_reject_count,
    deploy_drain_5xx_count, pg_wait_p95_ms, pg_wait_p999_ms, cpu_max_pct,
    memory_max_pct, disk_io_wait_pct, network_rx_drop_count, network_tx_drop_count,
    timeline_artifact, resource_timeline_artifact, workload_mix_ref, outbox_lag_max,
    cache_buffer_hit_ratio, cache_wait_event_ref, nginx_upstream_p999_ms,
    deploy_retry_contract_ref, deploy_reconnect_success_count
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

status_table="$(awk -F '\t' '
  BEGIN {
    print "| Scenario | Status | Reason | Duration min | Source IPs | Edge 429 | 499 | 5xx | Hikari warnings | p99.9 ms | PG wait p99.9 | Resource timeline |"
    print "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n",
      $1, $2, $3, $4, $5, $9, $12, $11, $13, $8, $18, $25
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Operational Evidence Gate

## Summary

- gate_status=${gate_status}
- missing_scenarios=${missing_scenarios:-none}
- required scenarios: ${required_scenarios}
- Hikari validation warning: 0
- 499/5xx/backend429 hard target: 0
- edge 429 max rate: ${max_edge_429_rate}
- cold first-read p95 max: ${max_cold_p95_ms}ms
- warm steady-read p95 max: ${max_warm_p95_ms}ms
- p99.9 long observation max: ${max_p999_ms}ms
- PG wait p95/p99.9 max: ${max_pg_wait_p95_ms}ms / ${max_pg_wait_p999_ms}ms
- resource max: CPU ${max_cpu_pct}%, memory ${max_memory_pct}%, disk io wait ${max_disk_io_wait_pct}%
- network drop hard target: 0
- resource timeline artifact: required
- mixed workload outbox lag hard target: 0
- cache buffer hit min: ${min_cache_buffer_hit_ratio}
- Nginx upstream p99.9 max: ${max_nginx_upstream_p999_ms}ms
- deploy retry/reconnect contract: required
- mixed workload min duration: ${mixed_min_duration_min}m
- real-IP multi-source minimum sources: ${min_real_source_ips}

## Result Table

${status_table}

## Evidence Contract

- Hikari idle validation warning, 499, 5xx, backend 429은 hard-zero로 묶는다.
- cold/warm cache, mixed workload, real-IP multi-source, deploy drain, p99.9 long observation은 같은 TSV timeline artifact로 추적한다.
- 이 gate는 OCI 실측 산출물을 닫는 검증기이며, 실제 부하 실행은 각 run-* script와 OCI runner에서 수행한다.

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read operational evidence gate failed: ${summary_tsv}" >&2
  exit 1
fi
