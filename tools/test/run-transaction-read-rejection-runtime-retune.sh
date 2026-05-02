#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-rejection-runtime-retune.sh [--print-plan]

Environment:
  REJECTION_RETUNE_NAME                 default transaction-read-rejection-runtime-retune-<timestamp>
  REJECTION_RETUNE_INPUT_TSV            required TSV with rejection runtime evidence
  REJECTION_RETUNE_OUTPUT_DIR           default build/reports/k6/<name>
  REJECTION_RETUNE_REQUIRED_SCENARIOS   default burst64,burst80,burst96,vu16-unpaced,retry-after
  REJECTION_RETUNE_MAX_BURST64_429_RATE default 0.10
  REJECTION_RETUNE_MAX_ACCEPTED_P95_MS  default 150
  REJECTION_RETUNE_MAX_RETRY_P95_MS     default 450
  REJECTION_RETUNE_MAX_REJECT_STREAK    default 3
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

name="${REJECTION_RETUNE_NAME:-transaction-read-rejection-runtime-retune-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${REJECTION_RETUNE_INPUT_TSV:-}"
output_dir="${REJECTION_RETUNE_OUTPUT_DIR:-build/reports/k6/${name}}"
required_scenarios="${REJECTION_RETUNE_REQUIRED_SCENARIOS:-burst64,burst80,burst96,vu16-unpaced,retry-after}"
max_burst64_429_rate="${REJECTION_RETUNE_MAX_BURST64_429_RATE:-0.10}"
max_accepted_p95_ms="${REJECTION_RETUNE_MAX_ACCEPTED_P95_MS:-150}"
max_retry_p95_ms="${REJECTION_RETUNE_MAX_RETRY_P95_MS:-450}"
max_reject_streak="${REJECTION_RETUNE_MAX_REJECT_STREAK:-3}"
summary_tsv="${output_dir}/${name}-rejection-runtime-retune.tsv"
report_md="${output_dir}/${name}-rejection-runtime-retune.md"
meta_file="${output_dir}/${name}-rejection-runtime-retune.meta"

require_non_negative_number() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${key} must be zero or greater: ${value}" >&2
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

require_rate "REJECTION_RETUNE_MAX_BURST64_429_RATE" "${max_burst64_429_rate}"
require_non_negative_number "REJECTION_RETUNE_MAX_ACCEPTED_P95_MS" "${max_accepted_p95_ms}"
require_non_negative_number "REJECTION_RETUNE_MAX_RETRY_P95_MS" "${max_retry_p95_ms}"
require_non_negative_number "REJECTION_RETUNE_MAX_REJECT_STREAK" "${max_reject_streak}"

print_plan() {
  echo "[transaction-read-rejection-runtime-retune] name=${name}"
  echo "[transaction-read-rejection-runtime-retune] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-rejection-runtime-retune] output_dir=${output_dir}"
  echo "[transaction-read-rejection-runtime-retune] scenarios=$(scenarios)"
  echo "[transaction-read-rejection-runtime-retune] required_scenarios=${required_scenarios}"
  echo "[transaction-read-rejection-runtime-retune] max_burst64_429_rate=${max_burst64_429_rate}"
  echo "[transaction-read-rejection-runtime-retune] max_accepted_p95_ms=${max_accepted_p95_ms}"
  echo "[transaction-read-rejection-runtime-retune] max_retry_p95_ms=${max_retry_p95_ms}"
  echo "[transaction-read-rejection-runtime-retune] max_reject_streak=${max_reject_streak}"
  echo "[transaction-read-rejection-runtime-retune] required_policy=fail-fast|smoothing"
  echo "[transaction-read-rejection-runtime-retune] hard_zero=five_xx_count,backend_429_count,unknown_429_count"
  echo "[transaction-read-rejection-runtime-retune] summary_tsv=${summary_tsv}"
  echo "[transaction-read-rejection-runtime-retune] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "REJECTION_RETUNE_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "REJECTION_RETUNE_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

awk -F '\t' \
  -v required_scenarios="${required_scenarios}" \
  -v max_burst64_429_rate="${max_burst64_429_rate}" \
  -v max_accepted_p95_ms="${max_accepted_p95_ms}" \
  -v max_retry_p95_ms="${max_retry_p95_ms}" \
  -v max_reject_streak="${max_reject_streak}" '
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
function valid_policy(policy) {
  return policy == "fail-fast" || policy == "smoothing"
}
BEGIN {
  split(required_scenarios, required_items, ",")
  for (i in required_items) required[required_items[i]] = 1
  split("scenario policy total_429_rate edge_429_rate accepted_hot_p95_ms accepted_cold_p95_ms retry_after_p95_ms reject_streak_max five_xx_count backend_429_count unknown_429_count degradation_curve_ref gate_threshold_ref operation_policy_ref", header_items, " ")
  print "scenario\tstatus\treason\tpolicy\ttotal_429_rate\tedge_429_rate\taccepted_hot_p95_ms\taccepted_cold_p95_ms\tretry_after_p95_ms\treject_streak_max\tfive_xx_count\tbackend_429_count\tunknown_429_count\tdegradation_curve_ref\tgate_threshold_ref\toperation_policy_ref"
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
  policy = value("policy", "unknown")
  status = "pass"
  reason = "ok"
  seen[scenario] = 1

  if (!valid_policy(policy)) add_reason("policy-invalid")
  require_ref("gate_threshold_ref")
  require_ref("operation_policy_ref")

  if (value("five_xx_count", "1") + 0 > 0) add_reason("5xx>0")
  if (value("backend_429_count", "1") + 0 > 0) add_reason("backend429>0")
  if (value("unknown_429_count", "1") + 0 > 0) add_reason("unknown429>0")

  if (scenario == "burst64") {
    if (value("total_429_rate", "1") + 0 > max_burst64_429_rate) add_reason("burst64-total429>" max_burst64_429_rate)
    if (value("edge_429_rate", "1") + 0 > max_burst64_429_rate) add_reason("burst64-edge429>" max_burst64_429_rate)
    if (value("accepted_hot_p95_ms", "999999") + 0 > max_accepted_p95_ms) add_reason("hot-p95>" max_accepted_p95_ms)
    if (value("accepted_cold_p95_ms", "999999") + 0 > max_accepted_p95_ms) add_reason("cold-p95>" max_accepted_p95_ms)
  }
  if (scenario == "burst80" || scenario == "burst96") {
    require_ref("degradation_curve_ref")
  }
  if (scenario == "vu16-unpaced") {
    if (!valid_policy(policy)) add_reason("vu16-policy-missing")
  }
  if (scenario == "retry-after") {
    if (value("retry_after_p95_ms", "999999") + 0 > max_retry_p95_ms) add_reason("retry-p95>" max_retry_p95_ms)
    if (value("reject_streak_max", "999999") + 0 > max_reject_streak) add_reason("streak-max>" max_reject_streak)
  }

  if (status == "fail") fail_count++
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    scenario, status, reason, policy, value("total_429_rate", "0"), value("edge_429_rate", "0"),
    value("accepted_hot_p95_ms", "0"), value("accepted_cold_p95_ms", "0"),
    value("retry_after_p95_ms", "0"), value("reject_streak_max", "0"),
    value("five_xx_count", "0"), value("backend_429_count", "0"),
    value("unknown_429_count", "0"), value("degradation_curve_ref", "n/a"),
    value("gate_threshold_ref", ""), value("operation_policy_ref", "")
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

retune_table="$(awk -F '\t' '
  BEGIN {
    print "| Scenario | Status | Reason | Policy | Total 429 | Edge 429 | Hot p95 | Cold p95 | Retry p95 | Streak max |"
    print "| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9, $10
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Rejection Runtime Retune

## Summary

- gate_status=${gate_status}
- required scenarios: ${required_scenarios}
- missing scenarios: ${missing_scenarios:-none}
- burst64 total/edge 429 budget: <= ${max_burst64_429_rate}
- accepted hot/cold p95 budget: <= ${max_accepted_p95_ms}ms
- Retry-After p95 budget: <= ${max_retry_p95_ms}ms
- reject streak max: <= ${max_reject_streak}
- hard-zero: 5xx, backend 429, unknown 429

## Retune Matrix

${retune_table}

## Contract Notes

- burst64는 residual rejection과 accepted latency를 같이 통과해야 한다.
- burst80/96은 degradation curve ref를 필수로 남겨 운영자가 smoothing과 fail-fast 경계를 비교할 수 있어야 한다.
- unpaced VU16은 smoothing 또는 fail-fast 중 하나로 닫고, gate threshold와 운영 policy ref를 같은 row에 남긴다.

## Artifacts

- input TSV: ${input_tsv}
- summary TSV: ${summary_tsv}
- report: ${report_md}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read rejection runtime retune failed: ${summary_tsv}" >&2
  exit 1
fi
