#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-residual-single-source-policy.sh [--print-plan]

Environment:
  RESIDUAL_SINGLE_SOURCE_NAME                         default transaction-read-residual-single-source-policy-<timestamp>
  RESIDUAL_SINGLE_SOURCE_INPUT_TSV                    required TSV with source/client contract evidence
  RESIDUAL_SINGLE_SOURCE_OUTPUT_DIR                   default build/reports/k6/<gate>
  RESIDUAL_SINGLE_SOURCE_MAX_OPERATING_EDGE_429_RATE  default 0.10
  RESIDUAL_SINGLE_SOURCE_MAX_DIAGNOSTIC_EDGE_429_RATE default 0.20
  RESIDUAL_SINGLE_SOURCE_MAX_OPERATING_RETRY_P95_MS   default 300
  RESIDUAL_SINGLE_SOURCE_MAX_OPERATING_REJECT_STREAK  default 4
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

name="${RESIDUAL_SINGLE_SOURCE_NAME:-transaction-read-residual-single-source-policy-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${RESIDUAL_SINGLE_SOURCE_INPUT_TSV:-}"
output_dir="${RESIDUAL_SINGLE_SOURCE_OUTPUT_DIR:-build/reports/k6/${name}}"
max_operating_edge_429_rate="${RESIDUAL_SINGLE_SOURCE_MAX_OPERATING_EDGE_429_RATE:-0.10}"
max_diagnostic_edge_429_rate="${RESIDUAL_SINGLE_SOURCE_MAX_DIAGNOSTIC_EDGE_429_RATE:-0.20}"
max_operating_retry_p95_ms="${RESIDUAL_SINGLE_SOURCE_MAX_OPERATING_RETRY_P95_MS:-300}"
max_operating_reject_streak="${RESIDUAL_SINGLE_SOURCE_MAX_OPERATING_REJECT_STREAK:-4}"
summary_tsv="${output_dir}/${name}-residual-single-source-policy.tsv"
report_md="${output_dir}/${name}-residual-single-source-policy.md"

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

require_non_negative_number() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be zero or greater: ${value}" >&2
    exit 1
  fi
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
    source_col && !seen[$source_col]++ {
      if (result != "") result = result ","
      result = result $source_col
    }
    END {
      if (result == "") print "missing"; else print result
    }
  ' "${input_tsv}"
}

print_plan() {
  echo "[transaction-read-residual-single-source-policy] name=${name}"
  echo "[transaction-read-residual-single-source-policy] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-residual-single-source-policy] output_dir=${output_dir}"
  echo "[transaction-read-residual-single-source-policy] source_modes=$(source_modes)"
  echo "[transaction-read-residual-single-source-policy] operating_contract=paced-or-multi-source"
  echo "[transaction-read-residual-single-source-policy] single_source_unpaced_class=diagnostic-only"
  echo "[transaction-read-residual-single-source-policy] max_operating_edge_429_rate=${max_operating_edge_429_rate}"
  echo "[transaction-read-residual-single-source-policy] max_diagnostic_edge_429_rate=${max_diagnostic_edge_429_rate}"
  echo "[transaction-read-residual-single-source-policy] max_operating_retry_p95_ms=${max_operating_retry_p95_ms}"
  echo "[transaction-read-residual-single-source-policy] max_operating_reject_streak=${max_operating_reject_streak}"
  echo "[transaction-read-residual-single-source-policy] summary_tsv=${summary_tsv}"
  echo "[transaction-read-residual-single-source-policy] report_md=${report_md}"
}

require_rate "RESIDUAL_SINGLE_SOURCE_MAX_OPERATING_EDGE_429_RATE" "${max_operating_edge_429_rate}"
require_rate "RESIDUAL_SINGLE_SOURCE_MAX_DIAGNOSTIC_EDGE_429_RATE" "${max_diagnostic_edge_429_rate}"
require_non_negative_number "RESIDUAL_SINGLE_SOURCE_MAX_OPERATING_RETRY_P95_MS" "${max_operating_retry_p95_ms}"
require_non_negative_number "RESIDUAL_SINGLE_SOURCE_MAX_OPERATING_REJECT_STREAK" "${max_operating_reject_streak}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "RESIDUAL_SINGLE_SOURCE_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "RESIDUAL_SINGLE_SOURCE_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

awk -F '\t' \
  -v max_operating_edge="${max_operating_edge_429_rate}" \
  -v max_diagnostic_edge="${max_diagnostic_edge_429_rate}" \
  -v max_operating_retry_p95="${max_operating_retry_p95_ms}" \
  -v max_operating_streak="${max_operating_reject_streak}" \
  '
  function value(name, fallback) {
    if (!(name in col) || col[name] == "") return fallback
    return $(col[name])
  }
  function set_failure(new_reason) {
    status = "fail"
    if (reason == "") reason = new_reason
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      col[$i] = i
    }
    print "run\tstatus\tbudget_class\tsource_mode\tclient_contract\tedge_429_rate\tedge_429_budget\tbackend_429_count\tfive_xx_count\tnginx_499_count\tretry_after_p95_ms\treject_streak_max"
    next
  }
  {
    run = value("run", "unknown")
    source = value("source_mode", "unknown")
    contract = value("client_contract", "unknown")
    edge = value("edge_429_rate", "1") + 0
    backend_429 = value("backend_429_count", "1") + 0
    five_xx = value("five_xx_count", "1") + 0
    nginx_499 = value("nginx_499_count", "1") + 0
    retry_p95 = value("retry_after_p95_ms", "999999") + 0
    reject_streak = value("reject_streak_max", "999999") + 0
    status = "pass"
    reason = "within-policy"

    if (source == "single-source-unpaced") {
      budget_class = "diagnostic-only"
      edge_budget = max_diagnostic_edge
      if (contract != "diagnostic") set_failure("single-source-unpaced-must-be-diagnostic")
      if (edge > edge_budget) set_failure("diagnostic-edge-429-over-budget")
      diagnostic_count++
    } else if (source == "single-source-paced" || source == "multi-source") {
      budget_class = "operating"
      edge_budget = max_operating_edge
      if (contract != "operating") set_failure("paced-or-multisource-must-be-operating")
      if (edge > edge_budget) set_failure("operating-edge-429-over-budget")
      if (retry_p95 > max_operating_retry_p95) set_failure("operating-retry-after-p95-over-budget")
      if (reject_streak > max_operating_streak) set_failure("operating-reject-streak-over-budget")
      operating_count++
      if (status == "pass") operating_pass_count++
    } else {
      budget_class = "invalid"
      edge_budget = max_operating_edge
      set_failure("invalid-source-mode")
    }

    if (backend_429 > 0 || five_xx > 0 || nginx_499 > 0) set_failure("hard-zero-regression")
    if (status == "fail") fail_count++

    printf "%s\t%s\t%s\t%s\t%s\t%s\t%.2f\t%s\t%s\t%s\t%s\t%s\n",
      run, status, budget_class, source, contract, value("edge_429_rate", "0"), edge_budget,
      value("backend_429_count", "0"), value("five_xx_count", "0"), value("nginx_499_count", "0"),
      value("retry_after_p95_ms", "0"), value("reject_streak_max", "0")
  }
  END {
    if (fail_count == "") fail_count = 0
    if (operating_count == "") operating_count = 0
    if (operating_pass_count == "") operating_pass_count = 0
    if (diagnostic_count == "") diagnostic_count = 0
    print "# fail_count=" fail_count > "/dev/stderr"
    print "# operating_count=" operating_count > "/dev/stderr"
    print "# operating_pass_count=" operating_pass_count > "/dev/stderr"
    print "# diagnostic_count=" diagnostic_count > "/dev/stderr"
  }
  ' "${input_tsv}" >"${summary_tsv}" 2>"${output_dir}/${name}-residual-single-source-policy.meta"

fail_count="$(awk -F '=' '/^# fail_count=/ { print $2 }' "${output_dir}/${name}-residual-single-source-policy.meta")"
operating_count="$(awk -F '=' '/^# operating_count=/ { print $2 }' "${output_dir}/${name}-residual-single-source-policy.meta")"
operating_pass_count="$(awk -F '=' '/^# operating_pass_count=/ { print $2 }' "${output_dir}/${name}-residual-single-source-policy.meta")"
diagnostic_count="$(awk -F '=' '/^# diagnostic_count=/ { print $2 }' "${output_dir}/${name}-residual-single-source-policy.meta")"
gate_status="pass"
if [[ "${fail_count}" != "0" || "${operating_count}" == "0" || "${operating_pass_count}" == "0" ]]; then
  gate_status="fail"
fi

budget_table="$(awk -F '\t' '
  BEGIN {
    print "| Run | Status | Class | Source | Contract | Edge 429 | Budget | Backend 429 | 5xx | 499 | Retry p95 ms | Streak max |"
    print "| --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Residual Single-Source 429 Policy

## Summary

- gate_status=${gate_status}
- operating contract: paced-or-multi-source
- single-source unpaced decision: diagnostic-only
- operating edge 429 budget: <= ${max_operating_edge_429_rate}
- diagnostic edge 429 ceiling: <= ${max_diagnostic_edge_429_rate}
- operating retry p95 budget: <= ${max_operating_retry_p95_ms}ms
- operating reject streak max: <= ${max_operating_reject_streak}
- backend 429/5xx/499 target: 0
- diagnostic evidence rows: ${diagnostic_count}
- operating evidence rows: ${operating_pass_count}/${operating_count}

## Budget

${budget_table}

## Contract Notes

- VU16 unpaced single-source는 NAT/shared-client 병목 재현용 diagnostic으로만 인정한다.
- 운영 pass는 paced client contract 또는 real-IP multi-source evidence만 인정한다.
- backend 429, 5xx, 499는 rate 정책이 아니라 hard-zero 회귀로 처리한다.

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read residual single-source policy failed: ${summary_tsv}" >&2
  exit 1
fi
