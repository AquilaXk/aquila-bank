#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-single-source-edge-budget-gate.sh [--print-plan]

Environment:
  SINGLE_SOURCE_EDGE_BUDGET_NAME                           default transaction-read-single-source-edge-budget-<timestamp>
  SINGLE_SOURCE_EDGE_BUDGET_INPUT_TSV                      required TSV with run/source_mode/429/failure columns
  SINGLE_SOURCE_EDGE_BUDGET_OUTPUT_DIR                     default build/reports/k6/<gate>
  SINGLE_SOURCE_EDGE_BUDGET_MAX_EDGE_429_RATE              default 0.10
  SINGLE_SOURCE_EDGE_BUDGET_MAX_BACKEND_429_RATE           default 0
  SINGLE_SOURCE_EDGE_BUDGET_OPERATING_MAX_EDGE_429_RATE    default 0.10
  SINGLE_SOURCE_EDGE_BUDGET_OPERATING_MAX_BACKEND_429_RATE default 0.0005
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

name="${SINGLE_SOURCE_EDGE_BUDGET_NAME:-transaction-read-single-source-edge-budget-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${SINGLE_SOURCE_EDGE_BUDGET_INPUT_TSV:-}"
output_dir="${SINGLE_SOURCE_EDGE_BUDGET_OUTPUT_DIR:-build/reports/k6/${name}}"
single_source_edge_429_rate="${SINGLE_SOURCE_EDGE_BUDGET_MAX_EDGE_429_RATE:-0.10}"
single_source_backend_429_rate="${SINGLE_SOURCE_EDGE_BUDGET_MAX_BACKEND_429_RATE:-0}"
operating_edge_429_rate="${SINGLE_SOURCE_EDGE_BUDGET_OPERATING_MAX_EDGE_429_RATE:-0.10}"
operating_backend_429_rate="${SINGLE_SOURCE_EDGE_BUDGET_OPERATING_MAX_BACKEND_429_RATE:-0.0005}"
summary_tsv="${output_dir}/${name}-single-source-edge-budget.tsv"
report_md="${output_dir}/${name}-single-source-edge-budget.md"

require_rate() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  fi
  awk -v value="${value}" 'BEGIN { exit !(value >= 0 && value <= 1) }' || {
    echo "${name} must be a rate between 0 and 1: ${value}" >&2
    exit 1
  }
}

require_file() {
  local name="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${name} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

source_modes() {
  if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
    echo "missing"
    return
  fi
  awk -F '\t' '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "source_mode") source_col = i
      }
      next
    }
    source_col {
      if (!seen[$source_col]++) {
        if (result != "") result = result ","
        result = result $source_col
      }
    }
    END {
      if (result == "") print "missing"; else print result
    }
  ' "${input_tsv}"
}

print_plan() {
  echo "[transaction-read-single-source-budget] name=${name}"
  echo "[transaction-read-single-source-budget] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-single-source-budget] output_dir=${output_dir}"
  echo "[transaction-read-single-source-budget] source_modes=$(source_modes)"
  echo "[transaction-read-single-source-budget] single_source_edge_429_rate=${single_source_edge_429_rate}"
  echo "[transaction-read-single-source-budget] single_source_backend_429_rate=${single_source_backend_429_rate}"
  echo "[transaction-read-single-source-budget] operating_edge_429_rate=${operating_edge_429_rate}"
  echo "[transaction-read-single-source-budget] operating_backend_429_rate=${operating_backend_429_rate}"
  echo "[transaction-read-single-source-budget] decision=single-source-operating-budget"
  echo "[transaction-read-single-source-budget] summary_tsv=${summary_tsv}"
  echo "[transaction-read-single-source-budget] report_md=${report_md}"
}

require_rate "SINGLE_SOURCE_EDGE_BUDGET_MAX_EDGE_429_RATE" "${single_source_edge_429_rate}"
require_rate "SINGLE_SOURCE_EDGE_BUDGET_MAX_BACKEND_429_RATE" "${single_source_backend_429_rate}"
require_rate "SINGLE_SOURCE_EDGE_BUDGET_OPERATING_MAX_EDGE_429_RATE" "${operating_edge_429_rate}"
require_rate "SINGLE_SOURCE_EDGE_BUDGET_OPERATING_MAX_BACKEND_429_RATE" "${operating_backend_429_rate}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "SINGLE_SOURCE_EDGE_BUDGET_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "SINGLE_SOURCE_EDGE_BUDGET_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

awk -F '\t' \
  -v single_source_edge="${single_source_edge_429_rate}" \
  -v single_source_backend="${single_source_backend_429_rate}" \
  -v operating_edge="${operating_edge_429_rate}" \
  -v operating_backend="${operating_backend_429_rate}" \
  '
  function value(name, fallback) {
    if (!(name in col) || col[name] == "") return fallback
    return $(col[name])
  }
  function number_value(name) {
    return value(name, "0") + 0
  }
  function set_failure(new_reason) {
    status = "fail"
    if (reason == "") reason = new_reason
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      col[$i] = i
    }
    print "run\tstatus\tbudget_class\tsource_mode\tworkload_shape\tedge_429_rate\tedge_429_budget\tbackend_429_rate\tbackend_429_budget\tbackend_rejected_count\toperating_candidate\treason"
    next
  }
  {
    run = value("run", "unknown")
    source = value("source_mode", "unknown")
    shape = value("workload_shape", "unknown")
    edge = number_value("edge_429_rate")
    backend = number_value("backend_429_rate")
    backend_rejected = number_value("backend_rejected_count")
    upstream_502 = number_value("upstream_502_count")
    client_499 = number_value("client_499_count")
    hikari = number_value("hikari_warning_count")
    status = "pass"
    reason = ""

    if (source == "single-source") {
      budget_class = "operating-candidate"
      edge_budget = single_source_edge
      backend_budget = single_source_backend
      operating_candidate = "yes"
      reason = "within-single-source-operating-budget"
    } else if (source == "multi-source" || source == "preemptive-pacing") {
      budget_class = "operating-candidate"
      edge_budget = operating_edge
      backend_budget = operating_backend
      operating_candidate = "yes"
      reason = "within-operating-budget"
    } else {
      budget_class = "invalid"
      edge_budget = operating_edge
      backend_budget = operating_backend
      operating_candidate = "no"
      set_failure("invalid-source-mode")
    }

    if (edge > edge_budget) set_failure("edge-429-over-budget")
    if (backend > backend_budget) set_failure("backend-429-over-budget")
    if (operating_candidate == "yes" && backend_rejected > 0) set_failure("backend-rejected-over-budget")
    if (upstream_502 > 0 || client_499 > 0 || hikari > 0) set_failure("hard-zero-regression")

    if (status == "fail") fail_count++
    if (operating_candidate == "yes" && status == "pass") operating_pass_count++
    printf "%s\t%s\t%s\t%s\t%s\t%s\t%.2f\t%s\t%s\t%s\t%s\t%s\n",
      run, status, budget_class, source, shape, value("edge_429_rate", "0"), edge_budget,
      value("backend_429_rate", "0"), backend_budget, value("backend_rejected_count", "0"),
      operating_candidate, reason
  }
  END {
    if (fail_count == "") fail_count = 0
    if (operating_pass_count == "") operating_pass_count = 0
    print "# fail_count=" fail_count > "/dev/stderr"
    print "# operating_pass_count=" operating_pass_count > "/dev/stderr"
  }
  ' "${input_tsv}" >"${summary_tsv}" 2>"${output_dir}/${name}-single-source-edge-budget.meta"

fail_count="$(awk -F '=' '/^# fail_count=/ { print $2 }' "${output_dir}/${name}-single-source-edge-budget.meta")"
operating_pass_count="$(awk -F '=' '/^# operating_pass_count=/ { print $2 }' "${output_dir}/${name}-single-source-edge-budget.meta")"
gate_status="pass"
if [[ "${fail_count}" != "0" ]]; then
  gate_status="fail"
fi

budget_table="$(awk -F '\t' '
  BEGIN {
    print "| Run | Status | Budget class | Source | Edge 429 | Edge budget | Backend 429 | Backend budget | Backend rejected | Operating candidate | Reason |"
    print "| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $6, $7, $8, $9, $10, $11, $12
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Single-Source Edge Budget Gate

## Summary

- gate_status=${gate_status}
- single-source decision: operating-budget
- single-source edge 429 target: <= ${single_source_edge_429_rate}
- single-source backend 429 target: <= ${single_source_backend_429_rate}
- operating edge 429 target: < ${operating_edge_429_rate}
- operating backend 429 target: <= ${operating_backend_429_rate}
- 499/502/Hikari target: 0
- operating candidates: ${operating_pass_count}

## Budget

${budget_table}

## Contract Notes

- single-source VU16은 NAT/shared-client 방어 상한이라 운영 budget과 같은 10% 이하로 유지한다.
- 운영 후보는 multi-source 또는 preemptive pacing evidence에서 edge 429와 backend reject를 동시에 낮춰야 한다.
- 499/502/Hikari warning은 overload 방어 budget이 아니라 hard-zero gate로 유지한다.

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read single-source edge budget failed: ${summary_tsv}" >&2
  exit 1
fi
