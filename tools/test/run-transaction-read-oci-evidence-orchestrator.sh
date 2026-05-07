#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-oci-evidence-orchestrator.sh [--print-plan]

Environment:
  OCI_EVIDENCE_ORCH_NAME              default transaction-read-oci-evidence-orchestrator-<timestamp>
  OCI_EVIDENCE_ORCH_INPUT_TSV         required evidence artifact TSV
  OCI_EVIDENCE_ORCH_OUTPUT_DIR        default build/reports/k6/<name>
  OCI_EVIDENCE_ORCH_EXECUTED_AT_UTC   default current UTC timestamp
  OCI_EVIDENCE_ORCH_MIXED_DURATION_MIN default 30
  OCI_EVIDENCE_ORCH_P999_DURATION_MIN  default 30
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

name="${OCI_EVIDENCE_ORCH_NAME:-transaction-read-oci-evidence-orchestrator-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${OCI_EVIDENCE_ORCH_INPUT_TSV:-}"
output_dir="${OCI_EVIDENCE_ORCH_OUTPUT_DIR:-build/reports/k6/${name}}"
executed_at_utc="${OCI_EVIDENCE_ORCH_EXECUTED_AT_UTC:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
mixed_duration_min="${OCI_EVIDENCE_ORCH_MIXED_DURATION_MIN:-30}"
p999_duration_min="${OCI_EVIDENCE_ORCH_P999_DURATION_MIN:-30}"
execution_gate="tools/test/run-transaction-read-oci-evidence-execution-gate.sh"
execution_tsv="${output_dir}/${name}-oci-evidence-execution-input.tsv"
report_md="${output_dir}/${name}-oci-evidence-orchestrator.md"

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
  echo "[transaction-read-oci-evidence-orchestrator] name=${name}"
  echo "[transaction-read-oci-evidence-orchestrator] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-oci-evidence-orchestrator] output_dir=${output_dir}"
  echo "[transaction-read-oci-evidence-orchestrator] scenarios=$(scenarios)"
  echo "[transaction-read-oci-evidence-orchestrator] mixed_duration_min=${mixed_duration_min}"
  echo "[transaction-read-oci-evidence-orchestrator] p999_duration_min=${p999_duration_min}"
  echo "[transaction-read-oci-evidence-orchestrator] execution_gate=${execution_gate}"
  echo "[transaction-read-oci-evidence-orchestrator] mixed_script=tools/test/run-t3micro-mixed-workload-soak.sh"
  echo "[transaction-read-oci-evidence-orchestrator] cold_warm_script=tools/test/run-transaction-100m-cold-start-cache-warm-gate.sh"
  echo "[transaction-read-oci-evidence-orchestrator] deploy_drain_script=tools/test/run-staging-deploy-transaction-replay-gate.sh"
  echo "[transaction-read-oci-evidence-orchestrator] p999_script=tools/test/run-transaction-read-p999-spike-attribution.sh"
  echo "[transaction-read-oci-evidence-orchestrator] execution_tsv=${execution_tsv}"
  echo "[transaction-read-oci-evidence-orchestrator] report_md=${report_md}"
}

require_non_negative_integer "OCI_EVIDENCE_ORCH_MIXED_DURATION_MIN" "${mixed_duration_min}"
require_non_negative_integer "OCI_EVIDENCE_ORCH_P999_DURATION_MIN" "${p999_duration_min}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "OCI_EVIDENCE_ORCH_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "OCI_EVIDENCE_ORCH_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

awk -F '\t' -v executed_at="${executed_at_utc}" '
function value(name, fallback) {
  if (!(name in col) || col[name] == "") return fallback
  return $(col[name])
}
function script_for(scenario) {
  if (scenario == "hikari-lifetime") return "tools/test/run-transaction-read-weighted-10m-soak-gate.sh"
  if (scenario == "mixed-workload-30m") return "tools/test/run-t3micro-mixed-workload-soak.sh"
  if (scenario == "cold-warm-cache") return "tools/test/run-transaction-100m-cold-start-cache-warm-gate.sh"
  if (scenario == "deploy-drain") return "tools/test/run-staging-deploy-transaction-replay-gate.sh"
  if (scenario == "p999-long-correlation") return "tools/test/run-transaction-read-p999-spike-attribution.sh"
  return ""
}
NR == 1 {
  for (i = 1; i <= NF; i++) {
    col[$i] = i
  }
  print "scenario\trun_id\texecuted_at_utc\tduration_min\tsource_ips\trun_script\tk6_summary_ref\tnginx_access_ref\tspring_metrics_ref\thikari_log_ref\tpostgres_wait_ref\tdeploy_event_ref\tcache_state_ref\ttimeline_ref\tedge_429_rate\tbackend_429_count\tunknown_429_count\tfive_xx_count\tnginx_499_count\thikari_validation_warnings\tdb_pool_pending_max\tp999_ms\tpostgres_checkpoint_ref\tpostgres_temp_file_ref\tnginx_upstream_latency_ref\tworkload_mix_ref\tworkload_component_ref\toutbox_lag_ref\toutbox_lag_max\tdeploy_retry_contract_ref\tdeploy_reconnect_success_count\tdeploy_499_budget_ref\tp95_ms\tp99_ms\tmax_ms\tpostgres_checkpoint_count\tpostgres_temp_file_count\tnginx_upstream_p95_ms\thikari_config_ref\thikari_max_lifetime_ms\thikari_keepalive_time_ms\tpostgres_idle_timeout_ms\toci_nat_idle_timeout_ms\thikari_zero_warning_soak_ref\tworkload_components\tread_p999_ms\tread_429_source_ref"
  next
}
{
  scenario = value("scenario", "unknown")
  run_script = script_for(scenario)
  if (run_script == "") {
    printf "unknown OCI evidence scenario: %s\n", scenario > "/dev/stderr"
    exit 1
  }
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    scenario,
    value("run_id", ""),
    executed_at,
    value("duration_min", "0"),
    value("source_ips", "0"),
    run_script,
    value("k6_summary_ref", ""),
    value("nginx_access_ref", ""),
    value("spring_metrics_ref", ""),
    value("hikari_log_ref", ""),
    value("postgres_wait_ref", ""),
    value("deploy_event_ref", "n/a"),
    value("cache_state_ref", "n/a"),
    value("timeline_ref", ""),
    value("edge_429_rate", "1"),
    value("backend_429_count", "1"),
    value("unknown_429_count", "1"),
    value("five_xx_count", "1"),
    value("nginx_499_count", "1"),
    value("hikari_validation_warnings", "1"),
    value("db_pool_pending_max", "999999"),
    value("p999_ms", "999999"),
    value("postgres_checkpoint_ref", "n/a"),
    value("postgres_temp_file_ref", "n/a"),
    value("nginx_upstream_latency_ref", "n/a"),
    value("workload_mix_ref", "n/a"),
    value("workload_component_ref", "n/a"),
    value("outbox_lag_ref", "n/a"),
    value("outbox_lag_max", "0"),
    value("deploy_retry_contract_ref", "n/a"),
    value("deploy_reconnect_success_count", "0"),
    value("deploy_499_budget_ref", "n/a"),
    value("p95_ms", ""),
    value("p99_ms", ""),
    value("max_ms", ""),
    value("postgres_checkpoint_count", ""),
    value("postgres_temp_file_count", ""),
    value("nginx_upstream_p95_ms", ""),
    value("hikari_config_ref", "n/a"),
    value("hikari_max_lifetime_ms", "0"),
    value("hikari_keepalive_time_ms", "0"),
    value("postgres_idle_timeout_ms", "0"),
    value("oci_nat_idle_timeout_ms", "0"),
    value("hikari_zero_warning_soak_ref", "n/a"),
    value("workload_components", "n/a"),
    value("read_p999_ms", "0"),
    value("read_429_source_ref", "n/a")
}
' "${input_tsv}" >"${execution_tsv}"

gate_output="$(
  OCI_EVIDENCE_EXECUTION_NAME="${name}" \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${execution_tsv}" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
  OCI_EVIDENCE_EXECUTION_MIXED_MIN_DURATION_MIN="${mixed_duration_min}" \
  OCI_EVIDENCE_EXECUTION_P999_MIN_DURATION_MIN="${p999_duration_min}" \
    "${execution_gate}"
)"
execution_report="$(tail -1 <<<"${gate_output}")"

cat >"${report_md}" <<REPORT
# Transaction Read OCI Evidence Orchestrator

## Summary

- gate_status=pass
- evidence input TSV: ${input_tsv}
- execution input TSV: ${execution_tsv}
- execution gate report: ${execution_report}
- mixed workload min duration: ${mixed_duration_min}m
- p99.9 timeline min duration: ${p999_duration_min}m

## Scenario Mapping

- hikari-lifetime: tools/test/run-transaction-read-weighted-10m-soak-gate.sh
- mixed-workload-30m: tools/test/run-t3micro-mixed-workload-soak.sh
- cold-warm-cache: tools/test/run-transaction-100m-cold-start-cache-warm-gate.sh
- deploy-drain: tools/test/run-staging-deploy-transaction-replay-gate.sh
- p999-long-correlation: tools/test/run-transaction-read-p999-spike-attribution.sh

## Contract Notes

- 이 runner는 OCI에서 생성된 실제 artifact ref를 기존 execution gate 입력으로 정규화한다.
- 실행 자체는 각 scenario runner가 담당하고, 이 단계는 같은 run id evidence가 review 가능한 manifest인지 닫는다.
- private endpoint, token, Grafana secret URL은 저장하지 않고 artifact ref만 남긴다.

## Artifacts

- orchestrator report: ${report_md}
- execution gate report: ${execution_report}
- execution TSV: ${execution_tsv}
REPORT

echo "${report_md}"
