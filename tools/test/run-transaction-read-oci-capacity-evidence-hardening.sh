#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-oci-capacity-evidence-hardening.sh [--print-plan]

Environment:
  OCI_CAPACITY_EVIDENCE_NAME             default transaction-read-oci-capacity-evidence-<timestamp>
  OCI_CAPACITY_EVIDENCE_INPUT_TSV        required OCI evidence TSV
  OCI_CAPACITY_EVIDENCE_OUTPUT_DIR       default build/reports/k6/<name>
  OCI_CAPACITY_EVIDENCE_REQUIRED_SCENARIOS default hikari-10m,prometheus-30m,mixed-interference
  OCI_CAPACITY_EVIDENCE_MIN_SOURCE_IPS   default 1
  OCI_CAPACITY_EVIDENCE_MAX_CONN_ACQ_P95_MS default 50
  OCI_CAPACITY_EVIDENCE_ACCEPTED_P95_MS  default 350
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

name="${OCI_CAPACITY_EVIDENCE_NAME:-transaction-read-oci-capacity-evidence-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${OCI_CAPACITY_EVIDENCE_INPUT_TSV:-}"
output_dir="${OCI_CAPACITY_EVIDENCE_OUTPUT_DIR:-build/reports/k6/${name}}"
required_scenarios="${OCI_CAPACITY_EVIDENCE_REQUIRED_SCENARIOS:-hikari-10m,prometheus-30m,mixed-interference}"
min_source_ips="${OCI_CAPACITY_EVIDENCE_MIN_SOURCE_IPS:-1}"
max_conn_acq_p95_ms="${OCI_CAPACITY_EVIDENCE_MAX_CONN_ACQ_P95_MS:-50}"
accepted_p95_ms="${OCI_CAPACITY_EVIDENCE_ACCEPTED_P95_MS:-350}"
summary_tsv="${output_dir}/${name}-oci-capacity-evidence.tsv"
report_md="${output_dir}/${name}-oci-capacity-evidence.md"
meta_file="${output_dir}/${name}-oci-capacity-evidence.meta"

require_positive_integer() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${key} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[transaction-read-oci-capacity-evidence] name=${name}"
  echo "[transaction-read-oci-capacity-evidence] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-oci-capacity-evidence] output_dir=${output_dir}"
  echo "[transaction-read-oci-capacity-evidence] required_scenarios=${required_scenarios}"
  echo "[transaction-read-oci-capacity-evidence] min_source_ips=${min_source_ips}"
  echo "[transaction-read-oci-capacity-evidence] max_connection_acquisition_p95_ms=${max_conn_acq_p95_ms}"
  echo "[transaction-read-oci-capacity-evidence] accepted_p95_ms=${accepted_p95_ms}"
  echo "[transaction-read-oci-capacity-evidence] timeline_refs=k6,nginx,spring,hikari,pg_wait,cpu,memory,disk_io,network,prometheus,grafana"
  echo "[transaction-read-oci-capacity-evidence] summary_tsv=${summary_tsv}"
  echo "[transaction-read-oci-capacity-evidence] report_md=${report_md}"
}

require_positive_integer "OCI_CAPACITY_EVIDENCE_MIN_SOURCE_IPS" "${min_source_ips}"
require_positive_integer "OCI_CAPACITY_EVIDENCE_MAX_CONN_ACQ_P95_MS" "${max_conn_acq_p95_ms}"
require_positive_integer "OCI_CAPACITY_EVIDENCE_ACCEPTED_P95_MS" "${accepted_p95_ms}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
  echo "OCI_CAPACITY_EVIDENCE_INPUT_TSV is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"

awk -F '\t' \
  -v required_scenarios="${required_scenarios}" \
  -v min_source_ips="${min_source_ips}" \
  -v max_conn="${max_conn_acq_p95_ms}" \
  -v accepted_budget="${accepted_p95_ms}" '
function value(name, fallback) {
  if (!(name in col) || $(col[name]) == "") return fallback
  return $(col[name])
}
function has_component(components, item) {
  return ("," components ",") ~ ("," item ",")
}
function require_ref(name, reason) {
  ref = value(name, "")
  if (ref == "" || ref == "n/a") {
    if (missing_ref != "none") missing_ref = missing_ref ","
    missing_ref = missing_ref reason
  }
}
BEGIN {
  split(required_scenarios, required, ",")
  for (i in required) required_seen[required[i]] = 0
  print "scenario\tstatus\tduration_min\tsource_ips\thikari_validation_warnings\tconnection_acquisition_p95_ms\tunknown_429_count\tfive_xx_count\taccepted_p95_ms\twrite_integrity_status\tmissing_ref"
}
NR == 1 {
  for (i = 1; i <= NF; i++) col[$i] = i
  next
}
{
  scenario = value("scenario", "unknown")
  duration = value("duration_min", "0") + 0
  source_ips = value("source_ips", "0") + 0
  hikari_warnings = value("hikari_validation_warnings", "1") + 0
  conn_p95 = value("connection_acquisition_p95_ms", "999999") + 0
  unknown = value("unknown_429_count", "1") + 0
  five_xx = value("five_xx_count", "1") + 0
  accepted_p95 = value("accepted_p95_ms", "999999") + 0
  write_integrity = value("write_integrity_status", "n/a")
  components = value("mixed_components", "")
  missing_ref = "none"
  require_ref("k6_ref", "k6")
  require_ref("nginx_ref", "nginx")
  require_ref("spring_ref", "spring")
  require_ref("hikari_ref", "hikari")
  require_ref("pg_wait_ref", "pg_wait")
  require_ref("cpu_ref", "cpu")
  require_ref("memory_ref", "memory")
  require_ref("disk_io_ref", "disk_io")
  require_ref("network_ref", "network")
  require_ref("prometheus_ref", "prometheus")
  require_ref("grafana_ref", "grafana")
  require_ref("source_ip_breakdown_ref", "source_ip_breakdown")
  required_seen[scenario] = 1
  status = "pass"
  if (missing_ref != "none") status = "fail"
  if (hikari_warnings > 0 || conn_p95 > max_conn || unknown > 0 || five_xx > 0 || accepted_p95 > accepted_budget) status = "fail"
  if (scenario == "hikari-10m" && duration < 10) status = "fail"
  if (scenario == "prometheus-30m" && (duration < 30 || source_ips < min_source_ips)) status = "fail"
  if (scenario == "mixed-interference") {
    if (duration < 30 || source_ips < min_source_ips || write_integrity != "pass") status = "fail"
    if (!has_component(components, "read") || !has_component(components, "write") || !has_component(components, "auth") || !has_component(components, "notification") || !has_component(components, "sse")) status = "fail"
  }
  if (status == "fail") fail_count++
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    scenario, status, duration, source_ips, hikari_warnings, conn_p95, unknown, five_xx, accepted_p95, write_integrity, missing_ref
}
END {
  missing = ""
  for (i in required) {
    if (required_seen[required[i]] != 1) {
      if (missing != "") missing = missing ","
      missing = missing required[i]
      fail_count++
    }
  }
  if (missing == "") missing = "none"
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

evidence_table="$(awk -F '\t' '
  BEGIN {
    print "| Scenario | Status | Duration min | Source IPs | Hikari warnings | Conn acquisition p95 ms | Unknown 429 | 5xx | Accepted p95 ms | Write integrity | Missing ref |"
    print "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read OCI Capacity Evidence Hardening

## Summary

- gate_status=${gate_status}
- required scenarios: ${required_scenarios}
- missing scenarios: ${missing_scenarios}
- 10m Hikari validation warning budget: 0
- connection acquisition p95 budget: <= ${max_conn_acq_p95_ms}ms
- accepted p95 budget: <= ${accepted_p95_ms}ms
- source attribution requires source_ips >= ${min_source_ips} and source_ip_breakdown_ref
- 30m Prometheus/Grafana timeline includes CPU, memory, disk IO, network, Hikari, pg wait
- mixed workload requires read, write, auth, notification, sse and write_integrity_status=pass
- hard-zero: unknown 429, 5xx

## Evidence Table

${evidence_table}

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read OCI capacity evidence failed: ${summary_tsv}" >&2
  exit 1
fi
