#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-fairness-budget.sh [--print-plan]

Environment:
  FAIRNESS_BUDGET_NAME                    default transaction-read-fairness-budget-<timestamp>
  FAIRNESS_BUDGET_INPUT_TSV               required arrival/fairness evidence TSV
  FAIRNESS_BUDGET_OUTPUT_DIR              default build/reports/k6/<name>
  FAIRNESS_BUDGET_ARRIVAL_RATE            default 16
  FAIRNESS_BUDGET_REQUIRED_ENDPOINTS      default active,archive
  FAIRNESS_BUDGET_MAX_FAIRNESS_429_RATE   default 0.001
  FAIRNESS_BUDGET_MAX_BURST64_BACKEND_429_RATE default 0.005
  FAIRNESS_BUDGET_MAX_EDGE_429_RATE       default 0
  FAIRNESS_BUDGET_COLD_P95_MS             default 750
  FAIRNESS_BUDGET_ACCEPTED_P95_MS         default 350
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

name="${FAIRNESS_BUDGET_NAME:-transaction-read-fairness-budget-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${FAIRNESS_BUDGET_INPUT_TSV:-}"
output_dir="${FAIRNESS_BUDGET_OUTPUT_DIR:-build/reports/k6/${name}}"
arrival_rate="${FAIRNESS_BUDGET_ARRIVAL_RATE:-16}"
required_endpoints="${FAIRNESS_BUDGET_REQUIRED_ENDPOINTS:-active,archive}"
max_fairness_rate="${FAIRNESS_BUDGET_MAX_FAIRNESS_429_RATE:-0.001}"
max_burst64_backend_rate="${FAIRNESS_BUDGET_MAX_BURST64_BACKEND_429_RATE:-0.005}"
max_edge_rate="${FAIRNESS_BUDGET_MAX_EDGE_429_RATE:-0}"
cold_p95_budget_ms="${FAIRNESS_BUDGET_COLD_P95_MS:-750}"
accepted_p95_budget_ms="${FAIRNESS_BUDGET_ACCEPTED_P95_MS:-350}"
summary_tsv="${output_dir}/${name}-fairness-budget.tsv"
report_md="${output_dir}/${name}-fairness-budget.md"
meta_file="${output_dir}/${name}-fairness-budget.meta"

require_positive_integer() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${key} must be a positive integer: ${value}" >&2
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

print_plan() {
  echo "[transaction-read-fairness-budget] name=${name}"
  echo "[transaction-read-fairness-budget] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-fairness-budget] output_dir=${output_dir}"
  echo "[transaction-read-fairness-budget] arrival_rate=${arrival_rate}"
  echo "[transaction-read-fairness-budget] required_endpoints=${required_endpoints}"
  echo "[transaction-read-fairness-budget] max_fairness_429_rate=${max_fairness_rate}"
  echo "[transaction-read-fairness-budget] max_burst64_backend_429_rate=${max_burst64_backend_rate}"
  echo "[transaction-read-fairness-budget] max_edge_429_rate=${max_edge_rate}"
  echo "[transaction-read-fairness-budget] cold_account_p95_ms=${cold_p95_budget_ms}"
  echo "[transaction-read-fairness-budget] accepted_p95_ms=${accepted_p95_budget_ms}"
  echo "[transaction-read-fairness-budget] summary_tsv=${summary_tsv}"
  echo "[transaction-read-fairness-budget] report_md=${report_md}"
}

require_positive_integer "FAIRNESS_BUDGET_ARRIVAL_RATE" "${arrival_rate}"
require_rate "FAIRNESS_BUDGET_MAX_FAIRNESS_429_RATE" "${max_fairness_rate}"
require_rate "FAIRNESS_BUDGET_MAX_BURST64_BACKEND_429_RATE" "${max_burst64_backend_rate}"
require_rate "FAIRNESS_BUDGET_MAX_EDGE_429_RATE" "${max_edge_rate}"
require_positive_integer "FAIRNESS_BUDGET_COLD_P95_MS" "${cold_p95_budget_ms}"
require_positive_integer "FAIRNESS_BUDGET_ACCEPTED_P95_MS" "${accepted_p95_budget_ms}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
  echo "FAIRNESS_BUDGET_INPUT_TSV is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"

awk -F '\t' \
  -v target_rate="${arrival_rate}" \
  -v required_endpoints="${required_endpoints}" \
  -v max_fairness="${max_fairness_rate}" \
  -v max_burst64_backend="${max_burst64_backend_rate}" \
  -v max_edge="${max_edge_rate}" \
  -v cold_budget="${cold_p95_budget_ms}" \
  -v accepted_budget="${accepted_p95_budget_ms}" '
function value(name, fallback) {
  if (!(name in col) || $(col[name]) == "") return fallback
  return $(col[name])
}
BEGIN {
  split(required_endpoints, endpoint_items, ",")
  for (i in endpoint_items) required_endpoint[endpoint_items[i]] = 1
  print "traffic_shape\tendpoint\tarrival_rate\tstatus\tfairness_429_rate\tbackend_429_rate\tedge_429_rate\tbackend_429_count\tunknown_429_count\tfive_xx_count\tcold_account_p95_ms\taccepted_p95_ms\trejection_reason"
}
NR == 1 {
  for (i = 1; i <= NF; i++) col[$i] = i
  next
}
{
  traffic_shape = value("traffic_shape", "arrival16")
  endpoint = value("endpoint", "combined")
  rate = value("arrival_rate", "0") + 0
  total = value("total_requests", "0") + 0
  fairness_count = value("backend_fairness_429_count", "0") + 0
  backend_count = value("backend_429_count", "0") + 0
  edge_count = value("edge_429_count", "0") + 0
  unknown_count = value("unknown_429_count", "1") + 0
  five_xx_count = value("five_xx_count", "1") + 0
  cold_p95 = value("cold_account_p95_ms", "999999") + 0
  accepted_p95 = value("accepted_p95_ms", "999999") + 0
  rejection_reason = value("rejection_reason", "fairness-limiter")
  fairness_rate = total > 0 ? fairness_count / total : 1
  backend_rate = total > 0 ? backend_count / total : 1
  edge_rate = total > 0 ? edge_count / total : 1
  status = "pass"
  if (traffic_shape == "arrival16" && rate == target_rate) {
    target_seen = 1
    if (endpoint in required_endpoint) endpoint_seen[endpoint] = 1
  }
  if (traffic_shape == "burst64") {
    burst64_seen = 1
    if (endpoint in required_endpoint) burst64_endpoint_seen[endpoint] = 1
  }
  target_failed = unknown_count > 0 || five_xx_count > 0 || cold_p95 > cold_budget || accepted_p95 > accepted_budget
  if (traffic_shape == "arrival16" && rate == target_rate) {
    target_failed = target_failed || fairness_rate >= max_fairness || edge_rate > max_edge
  }
  if (traffic_shape == "burst64") {
    target_failed = target_failed || backend_rate > max_burst64_backend
  }
  if ((endpoint == "active" || endpoint == "archive") && rejection_reason !~ endpoint) {
    target_failed = 1
  }
  if (((traffic_shape == "arrival16" && rate == target_rate) || traffic_shape == "burst64") && target_failed) {
    status = "fail"
  }
  if (status == "fail") fail_count++
  printf "%s\t%s\t%s\t%s\t%.6f\t%.6f\t%.6f\t%s\t%s\t%s\t%s\t%s\t%s\n",
    traffic_shape, endpoint, rate, status, fairness_rate, backend_rate, edge_rate, backend_count, unknown_count, five_xx_count, cold_p95, accepted_p95, rejection_reason
}
END {
  missing_endpoints = ""
  for (endpoint in required_endpoint) {
    if (endpoint_seen[endpoint] != 1) {
      if (missing_endpoints != "") missing_endpoints = missing_endpoints ","
      missing_endpoints = missing_endpoints endpoint
      fail_count++
    }
    if (burst64_endpoint_seen[endpoint] != 1) {
      if (missing_endpoints != "") missing_endpoints = missing_endpoints ","
      missing_endpoints = missing_endpoints "burst64:" endpoint
      fail_count++
    }
  }
  if (missing_endpoints == "") missing_endpoints = "none"
  if (!target_seen) fail_count++
  if (!burst64_seen) fail_count++
  print "fail_count=" (fail_count + 0) > "/dev/stderr"
  print "target_seen=" (target_seen + 0) > "/dev/stderr"
  print "missing_endpoints=" missing_endpoints > "/dev/stderr"
}
' "${input_tsv}" >"${summary_tsv}" 2>"${meta_file}"

fail_count="$(awk -F '=' '/^fail_count=/ { print $2 }' "${meta_file}")"
target_seen="$(awk -F '=' '/^target_seen=/ { print $2 }' "${meta_file}")"
missing_endpoints="$(awk -F '=' '/^missing_endpoints=/ { print $2 }' "${meta_file}")"
gate_status="pass"
if [[ "${fail_count}" != "0" || "${target_seen}" != "1" ]]; then
  gate_status="fail"
fi

fairness_table="$(awk -F '\t' '
  BEGIN {
    print "| Shape | Endpoint | Rate | Status | Fairness 429 | Backend 429 | Edge 429 | Backend count | Unknown 429 | 5xx | Cold p95 ms | Accepted p95 ms | Rejection reason |"
    print "| --- | --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Fairness Budget

## Summary

- gate_status=${gate_status}
- required endpoints: ${required_endpoints}
- missing endpoints: ${missing_endpoints}
- arrival16 backend fairness budget: < ${max_fairness_rate}
- burst64 backend 429 budget: <= ${max_burst64_backend_rate}
- edge 429 budget: <= ${max_edge_rate}
- cold account latency budget: <= ${cold_p95_budget_ms}ms
- accepted p95 budget: <= ${accepted_p95_budget_ms}ms
- rejection reason must include endpoint
- hard-zero: unknown 429, 5xx

## Fairness Table

${fairness_table}

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read fairness budget failed: ${summary_tsv}" >&2
  exit 1
fi
