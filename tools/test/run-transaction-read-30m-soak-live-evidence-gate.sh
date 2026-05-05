#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-30m-soak-live-evidence-gate.sh [--print-plan]

Environment:
  SOAK_30M_LIVE_NAME        default transaction-read-30m-soak-live-evidence-<timestamp>
  SOAK_30M_LIVE_INPUT_TSV   required OCI evidence manifest with hikari-lifetime and p999-long-correlation rows
  SOAK_30M_LIVE_OUTPUT_DIR  default build/reports/k6/<name>
  SOAK_30M_LIVE_MAX_POSTGRES_TEMP_FILE_DELTA default 0
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

name="${SOAK_30M_LIVE_NAME:-transaction-read-30m-soak-live-evidence-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${SOAK_30M_LIVE_INPUT_TSV:-}"
output_dir="${SOAK_30M_LIVE_OUTPUT_DIR:-build/reports/k6/${name}}"
max_postgres_temp_file_delta="${SOAK_30M_LIVE_MAX_POSTGRES_TEMP_FILE_DELTA:-0}"
execution_gate="tools/test/run-transaction-read-oci-evidence-execution-gate.sh"
execution_summary_tsv="${output_dir}/${name}-oci-evidence-execution.tsv"
summary_tsv="${output_dir}/${name}-30m-soak-live-evidence.tsv"
report_md="${output_dir}/${name}-30m-soak-live-evidence.md"

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
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

print_plan() {
  echo "[transaction-read-30m-soak-live-evidence] name=${name}"
  echo "[transaction-read-30m-soak-live-evidence] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-30m-soak-live-evidence] output_dir=${output_dir}"
  echo "[transaction-read-30m-soak-live-evidence] required_scenarios=hikari-lifetime,p999-long-correlation"
  echo "[transaction-read-30m-soak-live-evidence] min_duration_min=30"
  echo "[transaction-read-30m-soak-live-evidence] require_shared_run_id=true"
  echo "[transaction-read-30m-soak-live-evidence] latency_percentiles=p95,p99,p99.9,max"
  echo "[transaction-read-30m-soak-live-evidence] required_artifacts=hikari_log,postgres_wait,postgres_checkpoint,postgres_temp_file,nginx_upstream_latency,hikari_config,hikari_zero_warning_soak"
  echo "[transaction-read-30m-soak-live-evidence] postgres_temp_file_delta_budget=${max_postgres_temp_file_delta}"
  echo "[transaction-read-30m-soak-live-evidence] execution_gate=${execution_gate}"
  echo "[transaction-read-30m-soak-live-evidence] report_md=${report_md}"
}

require_non_negative_integer "SOAK_30M_LIVE_MAX_POSTGRES_TEMP_FILE_DELTA" "${max_postgres_temp_file_delta}"
print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "SOAK_30M_LIVE_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "SOAK_30M_LIVE_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

gate_output="$(
  OCI_EVIDENCE_EXECUTION_NAME="${name}" \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
  OCI_EVIDENCE_EXECUTION_REQUIRED_SCENARIOS=hikari-lifetime,p999-long-correlation \
  OCI_EVIDENCE_EXECUTION_HIKARI_MIN_DURATION_MIN=30 \
  OCI_EVIDENCE_EXECUTION_P999_MIN_DURATION_MIN=30 \
  OCI_EVIDENCE_EXECUTION_MAX_POOL_PENDING=0 \
  OCI_EVIDENCE_EXECUTION_MAX_POSTGRES_TEMP_FILE_DELTA="${max_postgres_temp_file_delta}" \
    "${execution_gate}"
)"
execution_report="$(tail -1 <<<"${gate_output}")"

awk -F '\t' '
BEGIN {
  print "run_id\tstatus\treason\thikari_duration_min\tp999_duration_min\tp95_ms\tp99_ms\tp999_ms\tmax_ms\thikari_pending_max\thikari_validation_warnings\tpostgres_wait_ref\tpostgres_checkpoint_count\tpostgres_temp_file_count\tnginx_upstream_p95_ms\thikari_config_ref\thikari_max_lifetime_ms\thikari_keepalive_time_ms\tpostgres_idle_timeout_ms\toci_nat_idle_timeout_ms\thikari_zero_warning_soak_ref"
}
NR == 1 { next }
$1 == "hikari-lifetime" {
  hikari_seen = 1
  hikari_run_id = $4
  hikari_duration = $5
  hikari_pending = $22
  hikari_warnings = $21
  hikari_postgres_wait_ref = $12
  hikari_config_ref = $30
  hikari_max_lifetime = $31
  hikari_keepalive = $32
  postgres_idle = $33
  oci_nat = $34
  hikari_zero_warning = $35
}
$1 == "p999-long-correlation" {
  p999_seen = 1
  p999_run_id = $4
  p999_duration = $5
  p95 = $24
  p99 = $25
  p999 = $23
  max = $26
  p999_pending = $22
  p999_warnings = $21
  p999_postgres_wait_ref = $12
  checkpoint_count = $27
  temp_file_count = $28
  upstream_p95 = $29
}
END {
  status = "pass"
  reason = "ok"
  if (!hikari_seen) { status = "fail"; reason = "hikari-lifetime-missing" }
  if (!p999_seen) { status = "fail"; reason = (reason == "ok" ? "p999-long-correlation-missing" : reason ",p999-long-correlation-missing") }
  if (hikari_seen && p999_seen && hikari_run_id != p999_run_id) {
    status = "fail"
    reason = (reason == "ok" ? "run-id-mismatch" : reason ",run-id-mismatch")
  }
  pending = hikari_pending
  if (p999_pending > pending) pending = p999_pending
  warnings = hikari_warnings
  if (p999_warnings > warnings) warnings = p999_warnings
  postgres_wait_ref = (hikari_postgres_wait_ref != "" ? hikari_postgres_wait_ref : p999_postgres_wait_ref)
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    hikari_run_id, status, reason, hikari_duration, p999_duration, p95, p99, p999, max,
    pending, warnings, postgres_wait_ref, checkpoint_count, temp_file_count, upstream_p95,
    hikari_config_ref, hikari_max_lifetime, hikari_keepalive, postgres_idle, oci_nat, hikari_zero_warning
  if (status == "fail") exit 2
}
' "${execution_summary_tsv}" >"${summary_tsv}"

gate_status="$(awk -F '\t' 'NR == 2 { print $2 }' "${summary_tsv}")"
run_id="$(awk -F '\t' 'NR == 2 { print $1 }' "${summary_tsv}")"
hikari_pending="$(awk -F '\t' 'NR == 2 { print $10 }' "${summary_tsv}")"
hikari_warnings="$(awk -F '\t' 'NR == 2 { print $11 }' "${summary_tsv}")"
hikari_max_lifetime="$(awk -F '\t' 'NR == 2 { print $17 }' "${summary_tsv}")"
hikari_keepalive="$(awk -F '\t' 'NR == 2 { print $18 }' "${summary_tsv}")"
postgres_idle="$(awk -F '\t' 'NR == 2 { print $19 }' "${summary_tsv}")"
oci_nat="$(awk -F '\t' 'NR == 2 { print $20 }' "${summary_tsv}")"
postgres_temp_file_delta="$(awk -F '\t' 'NR == 2 { print $14 }' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read 30m Soak Live Evidence Gate

## Summary

- gate_status=${gate_status}
- shared run id: ${run_id}
- minimum duration: 30m
- latency percentiles: p95/p99/p99.9/max
- Hikari pending: ${hikari_pending}
- Hikari validation warnings: ${hikari_warnings}
- PostgreSQL wait/checkpoint/temp file artifacts: verified
- PostgreSQL temp file delta: ${postgres_temp_file_delta} (budget <= ${max_postgres_temp_file_delta})
- Nginx upstream latency artifact: verified
- Hikari lifetime alignment: verified
- Hikari maxLifetime/keepaliveTime: ${hikari_max_lifetime}ms/${hikari_keepalive}ms
- PostgreSQL/NAT idle basis: ${postgres_idle}ms/${oci_nat}ms
- execution gate report: ${execution_report}

## Contract Notes

- 30m soak live evidence는 Hikari lifetime row와 p99.9 long correlation row가 같은 run id일 때만 인정한다.
- p99.9 long correlation은 p95, p99, p99.9, max와 PostgreSQL checkpoint/temp file delta, Nginx upstream latency를 요구한다.
- Hikari lifetime은 config ref, zero-warning soak ref, PostgreSQL/NAT timeout basis를 요구한다.
- Hikari pending, Hikari validation warning, 5xx, 499, unknown 429는 execution gate에서 hard-zero로 검증한다.

## Artifacts

- input TSV: ${input_tsv}
- execution report: ${execution_report}
- execution summary TSV: ${execution_summary_tsv}
- live evidence summary TSV: ${summary_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read 30m soak live evidence gate failed: ${summary_tsv}" >&2
  exit 1
fi
