#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-promotion-pacing-contract.sh [--print-plan]

Environment:
  TRANSACTION_READ_PROMOTION_PACING_NAME          default transaction-read-promotion-pacing-<timestamp>
  TRANSACTION_READ_PROMOTION_PACING_INPUT_TSV     required profile evidence TSV
  TRANSACTION_READ_PROMOTION_PACING_OUTPUT_DIR    default build/reports/k6/<name>
  TRANSACTION_READ_PROMOTION_TARGET_RATE          default 64
  TRANSACTION_READ_PROMOTION_MAX_429_RATE         default 0.10
  TRANSACTION_READ_PROMOTION_MAX_BACKEND_429_RATE default 0.005
  TRANSACTION_READ_PROMOTION_MAX_PACED_VU16_429_RATE default 0.01
  TRANSACTION_READ_PROMOTION_MAX_ACCEPTED_P95_MS  default 100
  TRANSACTION_READ_PROMOTION_MAX_PACING_SLEEP_P95_MS default 300
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

name="${TRANSACTION_READ_PROMOTION_PACING_NAME:-transaction-read-promotion-pacing-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${TRANSACTION_READ_PROMOTION_PACING_INPUT_TSV:-}"
output_dir="${TRANSACTION_READ_PROMOTION_PACING_OUTPUT_DIR:-build/reports/k6/${name}}"
promotion_target_rate="${TRANSACTION_READ_PROMOTION_TARGET_RATE:-64}"
max_promotion_429_rate="${TRANSACTION_READ_PROMOTION_MAX_429_RATE:-0.10}"
max_backend_429_rate="${TRANSACTION_READ_PROMOTION_MAX_BACKEND_429_RATE:-0.005}"
max_paced_vu16_429_rate="${TRANSACTION_READ_PROMOTION_MAX_PACED_VU16_429_RATE:-0.01}"
max_accepted_p95_ms="${TRANSACTION_READ_PROMOTION_MAX_ACCEPTED_P95_MS:-100}"
max_pacing_sleep_p95_ms="${TRANSACTION_READ_PROMOTION_MAX_PACING_SLEEP_P95_MS:-300}"
summary_tsv="${output_dir}/${name}-promotion-pacing.tsv"
report_md="${output_dir}/${name}-promotion-pacing.md"
failure_log="${output_dir}/${name}-promotion-pacing.failures"

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

require_non_negative_number() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${key} must be a non-negative number: ${value}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[transaction-read-promotion-pacing] name=${name}"
  echo "[transaction-read-promotion-pacing] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-promotion-pacing] output_dir=${output_dir}"
  echo "[transaction-read-promotion-pacing] promotion_target_rate=${promotion_target_rate}"
  echo "[transaction-read-promotion-pacing] max_promotion_429_rate=${max_promotion_429_rate}"
  echo "[transaction-read-promotion-pacing] max_backend_429_rate=${max_backend_429_rate}"
  echo "[transaction-read-promotion-pacing] max_paced_vu16_429_rate=${max_paced_vu16_429_rate}"
  echo "[transaction-read-promotion-pacing] max_accepted_p95_ms=${max_accepted_p95_ms}"
  echo "[transaction-read-promotion-pacing] max_pacing_sleep_p95_ms=${max_pacing_sleep_p95_ms}"
  echo "[transaction-read-promotion-pacing] summary_tsv=${summary_tsv}"
  echo "[transaction-read-promotion-pacing] report_md=${report_md}"
}

require_positive_integer "TRANSACTION_READ_PROMOTION_TARGET_RATE" "${promotion_target_rate}"
require_rate "TRANSACTION_READ_PROMOTION_MAX_429_RATE" "${max_promotion_429_rate}"
require_rate "TRANSACTION_READ_PROMOTION_MAX_BACKEND_429_RATE" "${max_backend_429_rate}"
require_rate "TRANSACTION_READ_PROMOTION_MAX_PACED_VU16_429_RATE" "${max_paced_vu16_429_rate}"
require_non_negative_number "TRANSACTION_READ_PROMOTION_MAX_ACCEPTED_P95_MS" "${max_accepted_p95_ms}"
require_non_negative_number "TRANSACTION_READ_PROMOTION_MAX_PACING_SLEEP_P95_MS" "${max_pacing_sleep_p95_ms}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  if [[ -z "${input_tsv}" || ! -f "${input_tsv}" ]]; then
    echo "TRANSACTION_READ_PROMOTION_PACING_INPUT_TSV is required" >&2
    exit 1
  fi
  exit 0
fi

if [[ -z "${input_tsv}" || ! -f "${input_tsv}" ]]; then
  echo "TRANSACTION_READ_PROMOTION_PACING_INPUT_TSV is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"

awk -F '\t' \
  -v OFS='\t' \
  -v target="${promotion_target_rate}" \
  -v max429="${max_promotion_429_rate}" \
  -v maxBackend429="${max_backend_429_rate}" \
  -v maxPaced429="${max_paced_vu16_429_rate}" \
  -v maxP95="${max_accepted_p95_ms}" \
  -v maxPacingSleep="${max_pacing_sleep_p95_ms}" \
  -v summary="${summary_tsv}" \
  -v failures="${failure_log}" '
  function value(name, fallback) {
    return ($(col[name]) == "" ? fallback : $(col[name]))
  }
  function gt(value, limit) {
    return (value + 0) > (limit + 0)
  }
  function ge(value, limit) {
    return (value + 0) >= (limit + 0)
  }
  function add_reason(reason) {
    reasons = (reasons == "ok" ? reason : reasons "," reason)
  }
  function require_ref(field, reason) {
    ref = value(field, "")
    if (ref == "" || ref == "missing" || ref == "n/a") add_reason(reason)
  }
  function reset_row() {
    reasons = "ok"
    status = "pass"
    profile = value("profile", "")
    role = value("gate_role", "")
    target_rate = value("promotion_target_rate", target)
    burst_rate = value("burst_rate", "0")
    pacing = value("preemptive_pacing", "false")
    total429 = value("total_429_rate", "1")
    edge429 = value("edge_429_rate", "1")
    backend429 = value("backend_429_rate", "1")
    backend429_count = value("backend_429_count", "0")
    five_xx = value("five_xx_count", "1")
    nginx499 = value("nginx_499_count", "1")
    p95 = value("accepted_p95_ms", maxP95)
    pacing_sleep = value("pacing_sleep_p95_ms", "0")
  }
  function enforce_common_hard_zero(prefix) {
    if (gt(backend429, maxBackend429)) add_reason(prefix "backend429>" maxBackend429)
    if (gt(five_xx, 0)) add_reason(prefix "5xx>0")
    if (gt(nginx499, 0)) add_reason(prefix "499>0")
  }
  function write_row() {
    if (reasons != "ok" && status != "observe") {
      status = "fail"
      gate_status = "fail"
      print profile " reason=" reasons > failures
    }
    print profile, role, status, reasons, target_rate, burst_rate, pacing, total429, edge429, backend429, backend429_count, five_xx, nginx499, p95, pacing_sleep, value("summary_ref", ""), value("nginx_aggregate_ref", ""), value("pacing_summary_ref", "") >> summary
  }
  BEGIN {
    gate_status = "pass"
    promotion_count = 0
    paced_count = 0
    unpaced_count = 0
    print "profile", "gate_role", "status", "reason", "promotion_target_rate", "burst_rate", "preemptive_pacing", "total_429_rate", "edge_429_rate", "backend_429_rate", "backend_429_count", "five_xx_count", "nginx_499_count", "accepted_p95_ms", "pacing_sleep_p95_ms", "summary_ref", "nginx_aggregate_ref", "pacing_summary_ref" > summary
    print "" > failures
    close(failures)
    system("rm -f " failures)
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) col[$i] = i
    next
  }
  NR > 1 {
    reset_row()
    if (profile == "vu16-unpaced") {
      unpaced_count++
      status = "observe"
      if (role != "saturation-observation") {
        status = "fail"
        add_reason("vu16-unpaced-must-be-observation")
      }
      if (pacing != "false") {
        status = "fail"
        add_reason("vu16-unpaced-must-disable-pacing")
      }
      if (gt(five_xx, 0)) {
        status = "fail"
        add_reason("vu16-unpaced-5xx>0")
      }
      if (gt(nginx499, 0)) {
        status = "fail"
        add_reason("vu16-unpaced-499>0")
      }
    } else if (profile == "vu16-paced") {
      paced_count++
      if (role != "paced-saturation-contract") add_reason("paced-vu16-role-mismatch")
      if (pacing != "true") add_reason("paced-vu16-pacing-disabled")
      if (gt(total429, maxPaced429)) add_reason("paced-vu16-total429>" maxPaced429)
      if (gt(edge429, maxPaced429)) add_reason("paced-vu16-edge429>" maxPaced429)
      enforce_common_hard_zero("paced-vu16-")
      if (ge(p95, maxP95)) add_reason("paced-vu16-p95>=" maxP95)
      if (gt(pacing_sleep, maxPacingSleep)) add_reason("paced-vu16-pacing-sleep>" maxPacingSleep)
      require_ref("pacing_summary_ref", "paced-vu16-pacing-summary-missing")
    } else if (role == "promotion-target") {
      promotion_count++
      if (target_rate != target) add_reason("promotion-target-rate-mismatch")
      if (burst_rate != target) add_reason("promotion-burst-rate-mismatch")
      if (pacing != "false") add_reason("promotion-burst-must-not-use-vu-pacing")
      if (gt(total429, max429)) add_reason("promotion-total429>" max429)
      if (gt(edge429, max429)) add_reason("promotion-edge429>" max429)
      enforce_common_hard_zero("promotion-")
      if (ge(p95, maxP95)) add_reason("promotion-p95>=" maxP95)
    } else if (role == "overload-observation") {
      status = "observe"
      if ((burst_rate + 0) <= (target + 0)) {
        status = "fail"
        add_reason("observation-burst-not-above-target")
      }
      enforce_common_hard_zero("observation-")
    } else {
      add_reason("unknown-gate-role")
    }
    write_row()
  }
  END {
    if (promotion_count != 1) {
      print "promotion-target reason=promotion-target-count!=" promotion_count > failures
      gate_status = "fail"
    }
    if (paced_count != 1) {
      print "vu16-paced reason=paced-vu16-count!=" paced_count > failures
      gate_status = "fail"
    }
    if (unpaced_count != 1) {
      print "vu16-unpaced reason=vu16-unpaced-count!=" unpaced_count > failures
      gate_status = "fail"
    }
    print gate_status > (summary ".status")
  }
' "${input_tsv}"

gate_status="$(cat "${summary_tsv}.status")"
rm -f "${summary_tsv}.status"

{
  echo "# Transaction Read Promotion Pacing Contract"
  echo
  echo "## Summary"
  echo
  echo "- gate_status=${gate_status}"
  echo "- promotion target: burst${promotion_target_rate}"
  echo "- max promotion total/edge 429 rate: ${max_promotion_429_rate}"
  echo "- max backend 429 rate: ${max_backend_429_rate}"
  echo "- max paced VU16 total/edge 429 rate: ${max_paced_vu16_429_rate}"
  echo "- VU16 unpaced: observation-only saturation probe"
  echo "- VU16 paced: safe saturation contract"
  echo
  echo "## Matrix"
  echo
  echo "| Profile | Role | Status | Reason | Total 429 | Edge 429 | Backend 429 | 5xx | 499 | Accepted p95 ms | Pacing p95 ms |"
  echo "| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
  awk -F '\t' 'NR > 1 { printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $8, $9, $10, $12, $13, $14, $15 }' "${summary_tsv}"
  echo
  echo "## Contract Notes"
  echo
  echo "- burst64는 현재 promotion target이며 total/edge 429 10%, backend 429 0.5%, 5xx/499 0, accepted p95 100ms 미만을 요구한다."
  echo "- burst80 이상은 명시 promotion target이 아니면 overload observation이며 429 초과를 success 신호로 해석하지 않는다."
  echo "- VU16 unpaced는 단일 source saturation 진단으로만 남기고 promotion-safe evidence로 사용하지 않는다."
  echo "- VU16 paced는 request-before token pacing이 켜진 safe saturation contract로 429/5xx/499 budget을 통과해야 한다."
  echo
  echo "## Artifacts"
  echo
  echo "- summary TSV: ${summary_tsv}"
  echo "- input TSV: ${input_tsv}"
} >"${report_md}"

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  if [[ -s "${failure_log}" ]]; then
    cat "${failure_log}" >&2
  fi
  echo "transaction read promotion pacing contract failed: ${summary_tsv}" >&2
  exit 1
fi
