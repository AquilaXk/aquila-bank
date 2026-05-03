#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-runtime-policy-budget.sh [--print-plan]

Environment:
  RUNTIME_POLICY_NAME                 default transaction-read-runtime-policy-<timestamp>
  RUNTIME_POLICY_INPUT_TSV            required burst/VU runtime evidence TSV
  RUNTIME_POLICY_OUTPUT_DIR           default build/reports/k6/<name>
  RUNTIME_POLICY_REQUIRED_PROFILES    default burst48,burst64,burst96,vu16,paced-weighted-vu16
  RUNTIME_POLICY_BURST64_429_RATE     default 0.10
  RUNTIME_POLICY_PACED_VU16_429_RATE  default 0.001
  RUNTIME_POLICY_REJECT_STREAK_MAX    default 3
  RUNTIME_POLICY_ACCEPTED_P95_MS      default 350
  RUNTIME_POLICY_DELAYED_RATE         default 0.25
  RUNTIME_POLICY_MIN_PACED_DELAYED_RATE default 0.50
  RUNTIME_POLICY_RETRY_AFTER_P95_MS   default 300
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

name="${RUNTIME_POLICY_NAME:-transaction-read-runtime-policy-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${RUNTIME_POLICY_INPUT_TSV:-}"
output_dir="${RUNTIME_POLICY_OUTPUT_DIR:-build/reports/k6/${name}}"
required_profiles="${RUNTIME_POLICY_REQUIRED_PROFILES:-burst48,burst64,burst96,vu16,paced-weighted-vu16}"
burst64_429_rate="${RUNTIME_POLICY_BURST64_429_RATE:-0.10}"
paced_vu16_429_rate="${RUNTIME_POLICY_PACED_VU16_429_RATE:-0.001}"
reject_streak_max="${RUNTIME_POLICY_REJECT_STREAK_MAX:-3}"
accepted_p95_ms="${RUNTIME_POLICY_ACCEPTED_P95_MS:-350}"
delayed_rate="${RUNTIME_POLICY_DELAYED_RATE:-0.25}"
min_paced_delayed_rate="${RUNTIME_POLICY_MIN_PACED_DELAYED_RATE:-0.50}"
retry_after_p95_ms="${RUNTIME_POLICY_RETRY_AFTER_P95_MS:-300}"
summary_tsv="${output_dir}/${name}-runtime-policy.tsv"
report_md="${output_dir}/${name}-runtime-policy.md"
meta_file="${output_dir}/${name}-runtime-policy.meta"

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

require_non_negative_number() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${key} must be zero or greater: ${value}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[transaction-read-runtime-policy] name=${name}"
  echo "[transaction-read-runtime-policy] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-runtime-policy] output_dir=${output_dir}"
  echo "[transaction-read-runtime-policy] required_profiles=${required_profiles}"
  echo "[transaction-read-runtime-policy] burst64_429_rate=${burst64_429_rate}"
  echo "[transaction-read-runtime-policy] paced_vu16_429_rate=${paced_vu16_429_rate}"
  echo "[transaction-read-runtime-policy] reject_streak_max=${reject_streak_max}"
  echo "[transaction-read-runtime-policy] accepted_p95_ms=${accepted_p95_ms}"
  echo "[transaction-read-runtime-policy] delayed_rate=${delayed_rate}"
  echo "[transaction-read-runtime-policy] min_paced_delayed_rate=${min_paced_delayed_rate}"
  echo "[transaction-read-runtime-policy] retry_after_p95_ms=${retry_after_p95_ms}"
  echo "[transaction-read-runtime-policy] summary_tsv=${summary_tsv}"
  echo "[transaction-read-runtime-policy] report_md=${report_md}"
}

require_rate "RUNTIME_POLICY_BURST64_429_RATE" "${burst64_429_rate}"
require_rate "RUNTIME_POLICY_PACED_VU16_429_RATE" "${paced_vu16_429_rate}"
require_non_negative_number "RUNTIME_POLICY_REJECT_STREAK_MAX" "${reject_streak_max}"
require_non_negative_number "RUNTIME_POLICY_ACCEPTED_P95_MS" "${accepted_p95_ms}"
require_rate "RUNTIME_POLICY_DELAYED_RATE" "${delayed_rate}"
require_rate "RUNTIME_POLICY_MIN_PACED_DELAYED_RATE" "${min_paced_delayed_rate}"
require_non_negative_number "RUNTIME_POLICY_RETRY_AFTER_P95_MS" "${retry_after_p95_ms}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
  echo "RUNTIME_POLICY_INPUT_TSV is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"

awk -F '\t' \
  -v burst64_budget="${burst64_429_rate}" \
  -v paced_vu16_budget="${paced_vu16_429_rate}" \
  -v max_streak="${reject_streak_max}" \
  -v p95_budget="${accepted_p95_ms}" \
  -v delayed_budget="${delayed_rate}" \
  -v min_paced_delayed="${min_paced_delayed_rate}" \
  -v retry_budget="${retry_after_p95_ms}" \
  -v required_profiles="${required_profiles}" '
function value(name, fallback) {
  if (!(name in col) || $(col[name]) == "") return fallback
  return $(col[name])
}
BEGIN {
  split(required_profiles, required, ",")
  for (i in required) required_seen[required[i]] = 0
  print "profile\tstatus\tdecision\ttotal_429_rate\tedge_429_rate\tbackend_429_rate\tunknown_429_count\tfive_xx_count\taccepted_p95_ms\tdelayed_rate\tretry_after_p95_ms\treject_streak_max\tpolicy_candidate"
}
NR == 1 {
  for (i = 1; i <= NF; i++) col[$i] = i
  next
}
{
  profile = value("profile", "unknown")
  total_429 = value("total_429_rate", "1") + 0
  edge_429 = value("edge_429_rate", "0") + 0
  backend_429 = value("backend_429_rate", "0") + 0
  unknown_count = value("unknown_429_count", "1") + 0
  five_xx_count = value("five_xx_count", "1") + 0
  p95 = value("accepted_p95_ms", "999999") + 0
  delayed = value("delayed_rate", "1") + 0
  retry_p95 = value("retry_after_p95_ms", "999999") + 0
  streak = value("reject_streak_max", "999999") + 0
  candidate = value("policy_candidate", "n/a")
  decision = candidate
  required_seen[profile] = 1
  status = "pass"
  if (profile == "burst64") decision = "burst64-smoothing"
  if (profile == "vu16") decision = "client-pacing-required"
  if (profile == "paced-weighted-vu16") decision = "client-pacing-default"
  if (profile == "burst64" && total_429 > burst64_budget) status = "fail"
  if (profile == "paced-weighted-vu16" && total_429 > paced_vu16_budget) status = "fail"
  if (profile == "paced-weighted-vu16" && delayed < min_paced_delayed) status = "fail"
  if (profile != "paced-weighted-vu16" && delayed > delayed_budget) status = "fail"
  if (unknown_count > 0 || five_xx_count > 0 || p95 > p95_budget || retry_p95 > retry_budget || streak > max_streak) status = "fail"
  if (status == "fail") fail_count++
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    profile, status, decision, value("total_429_rate", "0"), value("edge_429_rate", "0"), value("backend_429_rate", "0"),
    unknown_count, five_xx_count, p95, value("delayed_rate", "0"), retry_p95, streak, candidate
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
  print "missing_profiles=" missing > "/dev/stderr"
}
' "${input_tsv}" >"${summary_tsv}" 2>"${meta_file}"

fail_count="$(awk -F '=' '/^fail_count=/ { print $2 }' "${meta_file}")"
missing_profiles="$(awk -F '=' '/^missing_profiles=/ { print $2 }' "${meta_file}")"
gate_status="pass"
if [[ "${fail_count}" != "0" ]]; then
  gate_status="fail"
fi

runtime_table="$(awk -F '\t' '
  BEGIN {
    print "| Profile | Status | Decision | Total 429 | Edge 429 | Backend 429 | Unknown 429 | 5xx | Accepted p95 ms | Delayed | Retry-After p95 ms | Reject streak | Candidate |"
    print "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Runtime Policy Budget

## Summary

- gate_status=${gate_status}
- required profiles: ${required_profiles}
- missing profiles: ${missing_profiles}
- burst64 <= 10% 429: ${burst64_429_rate}
- paced weighted VU16 <= 0.001 429: ${paced_vu16_429_rate}
- unpaced VU16 operating decision: client-pacing-required
- Retry-After reject streak <= 3: ${reject_streak_max}
- accepted p95 budget: <= ${accepted_p95_ms}ms
- delayed ratio budget: <= ${delayed_rate}
- paced client delayed ratio floor: >= ${min_paced_delayed_rate}
- Retry-After p95 budget: <= ${retry_after_p95_ms}ms
- hard-zero: unknown 429, 5xx

## Runtime Table

${runtime_table}

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read runtime policy budget failed: ${summary_tsv}" >&2
  exit 1
fi
