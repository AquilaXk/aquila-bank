#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-retry-after-adaptive-backoff-v2.sh [--print-plan]

Environment:
  RETRY_AFTER_V2_NAME                    default transaction-read-retry-after-v2-<timestamp>
  RETRY_AFTER_V2_INPUT_TSV               required TSV with retry/backoff evidence
  RETRY_AFTER_V2_OUTPUT_DIR              default build/reports/k6/<gate>
  RETRY_AFTER_V2_MAX_RETRY_P95_MS        default 300
  RETRY_AFTER_V2_MAX_RETRY_MAX_MS        default 750
  RETRY_AFTER_V2_MAX_REJECT_STREAK_P95   default 3
  RETRY_AFTER_V2_MAX_REJECT_STREAK       default 4
  RETRY_AFTER_V2_REQUIRED_JITTER_MS      default 100
  RETRY_AFTER_V2_MAX_EDGE_429_RATE       default 0.10
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

name="${RETRY_AFTER_V2_NAME:-transaction-read-retry-after-v2-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${RETRY_AFTER_V2_INPUT_TSV:-}"
output_dir="${RETRY_AFTER_V2_OUTPUT_DIR:-build/reports/k6/${name}}"
max_retry_after_p95_ms="${RETRY_AFTER_V2_MAX_RETRY_P95_MS:-300}"
max_retry_after_max_ms="${RETRY_AFTER_V2_MAX_RETRY_MAX_MS:-750}"
max_reject_streak_p95="${RETRY_AFTER_V2_MAX_REJECT_STREAK_P95:-3}"
max_reject_streak="${RETRY_AFTER_V2_MAX_REJECT_STREAK:-4}"
required_jitter_ms="${RETRY_AFTER_V2_REQUIRED_JITTER_MS:-100}"
max_edge_429_rate="${RETRY_AFTER_V2_MAX_EDGE_429_RATE:-0.10}"
summary_tsv="${output_dir}/${name}-retry-after-v2.tsv"
report_md="${output_dir}/${name}-retry-after-v2.md"

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
  echo "[transaction-read-retry-after-v2] name=${name}"
  echo "[transaction-read-retry-after-v2] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-retry-after-v2] output_dir=${output_dir}"
  echo "[transaction-read-retry-after-v2] scenarios=$(scenarios)"
  echo "[transaction-read-retry-after-v2] max_retry_after_p95_ms=${max_retry_after_p95_ms}"
  echo "[transaction-read-retry-after-v2] max_retry_after_max_ms=${max_retry_after_max_ms}"
  echo "[transaction-read-retry-after-v2] max_reject_streak_p95=${max_reject_streak_p95}"
  echo "[transaction-read-retry-after-v2] max_reject_streak=${max_reject_streak}"
  echo "[transaction-read-retry-after-v2] required_jitter_ms=${required_jitter_ms}"
  echo "[transaction-read-retry-after-v2] max_edge_429_rate=${max_edge_429_rate}"
  echo "[transaction-read-retry-after-v2] summary_tsv=${summary_tsv}"
  echo "[transaction-read-retry-after-v2] report_md=${report_md}"
}

require_non_negative_number "RETRY_AFTER_V2_MAX_RETRY_P95_MS" "${max_retry_after_p95_ms}"
require_non_negative_number "RETRY_AFTER_V2_MAX_RETRY_MAX_MS" "${max_retry_after_max_ms}"
require_non_negative_number "RETRY_AFTER_V2_MAX_REJECT_STREAK_P95" "${max_reject_streak_p95}"
require_non_negative_number "RETRY_AFTER_V2_MAX_REJECT_STREAK" "${max_reject_streak}"
require_non_negative_number "RETRY_AFTER_V2_REQUIRED_JITTER_MS" "${required_jitter_ms}"
require_rate "RETRY_AFTER_V2_MAX_EDGE_429_RATE" "${max_edge_429_rate}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "RETRY_AFTER_V2_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "RETRY_AFTER_V2_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

awk -F '\t' \
  -v max_retry_p95="${max_retry_after_p95_ms}" \
  -v max_retry_max="${max_retry_after_max_ms}" \
  -v max_streak_p95="${max_reject_streak_p95}" \
  -v max_streak="${max_reject_streak}" \
  -v required_jitter="${required_jitter_ms}" \
  -v max_edge="${max_edge_429_rate}" \
  '
  function value(name, fallback) {
    if (!(name in col) || col[name] == "") return fallback
    return $(col[name])
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      col[$i] = i
    }
    print "scenario\tstatus\tretry_after_p95_ms\tretry_after_max_ms\treject_streak_p95\treject_streak_max\tjitter_ms\tpreemptive_pacing_count\tedge_429_rate\tfive_xx_count"
    next
  }
  {
    scenario = value("scenario", "unknown")
    retry_p95 = value("retry_after_p95_ms", "999999") + 0
    retry_max = value("retry_after_max_ms", "999999") + 0
    streak_p95 = value("reject_streak_p95", "999999") + 0
    streak_max = value("reject_streak_max", "999999") + 0
    jitter = value("jitter_ms", "0") + 0
    pacing = value("preemptive_pacing_count", "0") + 0
    edge = value("edge_429_rate", "1") + 0
    five_xx = value("five_xx_count", "1") + 0
    status = "pass"
    if (retry_p95 > max_retry_p95 || retry_max > max_retry_max || streak_p95 > max_streak_p95 || streak_max > max_streak || jitter < required_jitter || pacing <= 0 || edge > max_edge || five_xx > 0) {
      status = "fail"
      fail_count++
    } else {
      pass_count++
    }
    printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
      scenario, status, value("retry_after_p95_ms", "0"), value("retry_after_max_ms", "0"),
      value("reject_streak_p95", "0"), value("reject_streak_max", "0"), value("jitter_ms", "0"),
      value("preemptive_pacing_count", "0"), value("edge_429_rate", "0"), value("five_xx_count", "0")
  }
  END {
    if (fail_count == "") fail_count = 0
    if (pass_count == "") pass_count = 0
    print "# fail_count=" fail_count > "/dev/stderr"
    print "# pass_count=" pass_count > "/dev/stderr"
  }
  ' "${input_tsv}" >"${summary_tsv}" 2>"${output_dir}/${name}-retry-after-v2.meta"

fail_count="$(awk -F '=' '/^# fail_count=/ { print $2 }' "${output_dir}/${name}-retry-after-v2.meta")"
pass_count="$(awk -F '=' '/^# pass_count=/ { print $2 }' "${output_dir}/${name}-retry-after-v2.meta")"
gate_status="pass"
if [[ "${fail_count}" != "0" || "${pass_count}" == "0" ]]; then
  gate_status="fail"
fi

retry_table="$(awk -F '\t' '
  BEGIN {
    print "| Scenario | Status | Retry p95 ms | Retry max ms | Streak p95 | Streak max | Jitter ms | Pacing count | Edge 429 | 5xx |"
    print "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9, $10
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Retry-After Adaptive Backoff V2 Gate

## Summary

- gate_status=${gate_status}
- overload amplification guard: retry p95 <= ${max_retry_after_p95_ms}ms, reject streak max <= ${max_reject_streak}
- retry max budget: <= ${max_retry_after_max_ms}ms
- reject streak p95 budget: <= ${max_reject_streak_p95}
- jitter floor: >= ${required_jitter_ms}ms
- operating edge 429 budget: <= ${max_edge_429_rate}
- 5xx target: 0

## Matrix

${retry_table}

## Contract Notes

- Retry-After는 fixed sleep이 아니라 jitter와 preemptive pacing 증거가 함께 있어야 한다.
- edge reject streak가 길어지면 정상 client 재시도 동기화가 커진 것으로 보고 gate에서 실패한다.
- 409-547ms p95와 streak 7 계열 증거는 v2 기준의 실패 사례로 유지한다.

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read retry-after adaptive backoff v2 failed: ${summary_tsv}" >&2
  exit 1
fi
