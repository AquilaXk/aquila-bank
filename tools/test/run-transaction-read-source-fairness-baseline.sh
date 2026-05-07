#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-source-fairness-baseline.sh [--print-plan]

Environment:
  SOURCE_FAIRNESS_NAME                default transaction-read-source-fairness-baseline-<timestamp>
  SOURCE_FAIRNESS_INPUT_TSV           required TSV with account/source fairness evidence
  SOURCE_FAIRNESS_OUTPUT_DIR          default build/reports/k6/<name>
  SOURCE_FAIRNESS_REQUIRED_SCENARIOS  default multi-account-fixture,fairness-budget
  SOURCE_FAIRNESS_MIN_HOT_ACCOUNTS    default 4
  SOURCE_FAIRNESS_MIN_COLD_ACCOUNTS   default 4
  SOURCE_FAIRNESS_MIN_SOURCE_IPS      default 1
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

name="${SOURCE_FAIRNESS_NAME:-transaction-read-source-fairness-baseline-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${SOURCE_FAIRNESS_INPUT_TSV:-}"
output_dir="${SOURCE_FAIRNESS_OUTPUT_DIR:-build/reports/k6/${name}}"
required_scenarios="${SOURCE_FAIRNESS_REQUIRED_SCENARIOS:-multi-account-fixture,fairness-budget}"
min_hot_accounts="${SOURCE_FAIRNESS_MIN_HOT_ACCOUNTS:-4}"
min_cold_accounts="${SOURCE_FAIRNESS_MIN_COLD_ACCOUNTS:-4}"
min_source_ips="${SOURCE_FAIRNESS_MIN_SOURCE_IPS:-1}"
summary_tsv="${output_dir}/${name}-source-fairness-baseline.tsv"
report_md="${output_dir}/${name}-source-fairness-baseline.md"
meta_file="${output_dir}/${name}-source-fairness-baseline.meta"

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

require_non_negative_integer "SOURCE_FAIRNESS_MIN_HOT_ACCOUNTS" "${min_hot_accounts}"
require_non_negative_integer "SOURCE_FAIRNESS_MIN_COLD_ACCOUNTS" "${min_cold_accounts}"
require_non_negative_integer "SOURCE_FAIRNESS_MIN_SOURCE_IPS" "${min_source_ips}"

print_plan() {
  echo "[transaction-read-source-fairness-baseline] name=${name}"
  echo "[transaction-read-source-fairness-baseline] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-source-fairness-baseline] output_dir=${output_dir}"
  echo "[transaction-read-source-fairness-baseline] scenarios=$(scenarios)"
  echo "[transaction-read-source-fairness-baseline] required_scenarios=${required_scenarios}"
  echo "[transaction-read-source-fairness-baseline] min_hot_accounts=${min_hot_accounts}"
  echo "[transaction-read-source-fairness-baseline] min_cold_accounts=${min_cold_accounts}"
  echo "[transaction-read-source-fairness-baseline] min_source_ips=${min_source_ips}"
  echo "[transaction-read-source-fairness-baseline] budget_columns=account_429_skew_budget,account_p95_skew_budget_ms,source_edge_429_skew_budget"
  echo "[transaction-read-source-fairness-baseline] required_refs=k6_summary_ref,account_distribution_ref,source_distribution_ref,edge_backend_split_ref,limiter_key_ref"
  echo "[transaction-read-source-fairness-baseline] hard_zero=five_xx_count,unknown_429_count"
  echo "[transaction-read-source-fairness-baseline] summary_tsv=${summary_tsv}"
  echo "[transaction-read-source-fairness-baseline] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "SOURCE_FAIRNESS_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "SOURCE_FAIRNESS_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

awk -F '\t' \
  -v required_scenarios="${required_scenarios}" \
  -v min_hot_accounts="${min_hot_accounts}" \
  -v min_cold_accounts="${min_cold_accounts}" \
  -v min_source_ips="${min_source_ips}" '
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
  split("scenario run_id hot_account_count cold_account_count source_ips account_429_skew account_429_skew_budget account_p95_skew_ms account_p95_skew_budget_ms source_edge_429_skew source_edge_429_skew_budget k6_summary_ref account_distribution_ref source_distribution_ref edge_backend_split_ref limiter_key_ref five_xx_count unknown_429_count", header_items, " ")
  print "scenario\tstatus\treason\trun_id\thot_account_count\tcold_account_count\tsource_ips\taccount_429_skew\taccount_429_skew_budget\taccount_p95_skew_ms\taccount_p95_skew_budget_ms\tsource_edge_429_skew\tsource_edge_429_skew_budget\tk6_summary_ref\taccount_distribution_ref\tsource_distribution_ref\tedge_backend_split_ref\tlimiter_key_ref"
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
  require_ref("account_distribution_ref")
  require_ref("source_distribution_ref")
  require_ref("edge_backend_split_ref")
  require_ref("limiter_key_ref")

  if (value("five_xx_count", "1") + 0 > 0) add_reason("5xx>0")
  if (value("unknown_429_count", "1") + 0 > 0) add_reason("unknown429>0")

  if (scenario == "multi-account-fixture") {
    if (value("hot_account_count", "0") + 0 < min_hot_accounts) add_reason("hot-accounts<" min_hot_accounts)
    if (value("cold_account_count", "0") + 0 < min_cold_accounts) add_reason("cold-accounts<" min_cold_accounts)
  }
  if (scenario == "fairness-budget") {
    if (value("account_429_skew", "1") + 0 > value("account_429_skew_budget", "0") + 0) add_reason("account429-skew-budget")
    if (value("account_p95_skew_ms", "999999") + 0 > value("account_p95_skew_budget_ms", "0") + 0) add_reason("account-p95-skew-budget")
    if (value("source_edge_429_skew", "1") + 0 > value("source_edge_429_skew_budget", "0") + 0) add_reason("source-edge429-skew-budget")
  }

  if (status == "fail") fail_count++
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    scenario, status, reason, value("run_id", ""),
    value("hot_account_count", "0"), value("cold_account_count", "0"), value("source_ips", "0"),
    value("account_429_skew", "0"), value("account_429_skew_budget", "0"),
    value("account_p95_skew_ms", "0"), value("account_p95_skew_budget_ms", "0"),
    value("source_edge_429_skew", "0"), value("source_edge_429_skew_budget", "0"),
    value("k6_summary_ref", ""), value("account_distribution_ref", ""),
    value("source_distribution_ref", ""), value("edge_backend_split_ref", ""),
    value("limiter_key_ref", "")
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

fairness_table="$(awk -F '\t' '
  BEGIN {
    print "| Scenario | Status | Reason | Hot accounts | Cold accounts | Source IPs | Account 429 skew | Account p95 skew ms | Source edge 429 skew |"
    print "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s/%s | %s/%s | %s/%s |\n", $1, $2, $3, $5, $6, $7, $8, $9, $10, $11, $12, $13
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Source Fairness Baseline

## Summary

- gate_status=${gate_status}
- required scenarios: ${required_scenarios}
- missing scenarios: ${missing_scenarios:-none}
- min hot accounts: ${min_hot_accounts}
- min cold accounts: ${min_cold_accounts}
- min source IPs: ${min_source_ips}
- hard-zero: 5xx, unknown 429

## Fairness Matrix

${fairness_table}

## Contract Notes

- hot/cold 1계좌 fixture 착시를 막기 위해 account bucket 수를 먼저 검증한다.
- public source IP 값은 저장하지 않고 source count와 artifact ref만 남긴다.
- fairness budget은 manifest의 budget column과 측정값을 비교하므로 실제 OCI rerun 결과를 그대로 review할 수 있다.

## Artifacts

- input TSV: ${input_tsv}
- summary TSV: ${summary_tsv}
- report: ${report_md}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read source fairness baseline failed: ${summary_tsv}" >&2
  exit 1
fi
