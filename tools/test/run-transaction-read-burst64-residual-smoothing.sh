#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-burst64-residual-smoothing.sh [--print-plan]

Environment:
  BURST64_SMOOTHING_NAME                  default transaction-read-burst64-residual-smoothing-<timestamp>
  BURST64_SMOOTHING_INPUT_TSV             required TSV with burst residual candidate evidence
  BURST64_SMOOTHING_OUTPUT_DIR            default build/reports/k6/<gate>
  BURST64_SMOOTHING_MAX_BURST48_429_RATE  default 0.10
  BURST64_SMOOTHING_MAX_BURST64_429_RATE  default 0.10
  BURST64_SMOOTHING_MIN_BURST80_429_RATE  default 0.10
  BURST64_SMOOTHING_MIN_BURST96_429_RATE  default 0.10
  BURST64_SMOOTHING_MAX_ACCEPTED_P95_MS   default 150
  BURST64_SMOOTHING_MAX_RETRY_P95_MS      default 250
  BURST64_SMOOTHING_MAX_REJECT_STREAK     default 4
  BURST64_SMOOTHING_MAX_DELAYED_RATE      default 0.05
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

name="${BURST64_SMOOTHING_NAME:-transaction-read-burst64-residual-smoothing-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${BURST64_SMOOTHING_INPUT_TSV:-}"
output_dir="${BURST64_SMOOTHING_OUTPUT_DIR:-build/reports/k6/${name}}"
max_burst48_429_rate="${BURST64_SMOOTHING_MAX_BURST48_429_RATE:-0.10}"
max_burst64_429_rate="${BURST64_SMOOTHING_MAX_BURST64_429_RATE:-0.10}"
min_burst80_429_rate="${BURST64_SMOOTHING_MIN_BURST80_429_RATE:-0.10}"
min_burst96_429_rate="${BURST64_SMOOTHING_MIN_BURST96_429_RATE:-0.10}"
max_accepted_p95_ms="${BURST64_SMOOTHING_MAX_ACCEPTED_P95_MS:-150}"
max_retry_p95_ms="${BURST64_SMOOTHING_MAX_RETRY_P95_MS:-250}"
max_reject_streak="${BURST64_SMOOTHING_MAX_REJECT_STREAK:-4}"
max_delayed_rate="${BURST64_SMOOTHING_MAX_DELAYED_RATE:-0.05}"
summary_tsv="${output_dir}/${name}-burst64-residual-smoothing.tsv"
report_md="${output_dir}/${name}-burst64-residual-smoothing.md"

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

policies() {
  if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
    echo "missing"
    return
  fi
  awk -F '\t' '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "policy") policy_col = i
      }
      next
    }
    policy_col {
      if (result != "") result = result ","
      result = result $policy_col
    }
    END {
      if (result == "") print "missing"; else print result
    }
  ' "${input_tsv}"
}

print_plan() {
  echo "[transaction-read-burst64-residual-smoothing] name=${name}"
  echo "[transaction-read-burst64-residual-smoothing] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-burst64-residual-smoothing] output_dir=${output_dir}"
  echo "[transaction-read-burst64-residual-smoothing] policies=$(policies)"
  echo "[transaction-read-burst64-residual-smoothing] max_burst48_429_rate=${max_burst48_429_rate}"
  echo "[transaction-read-burst64-residual-smoothing] max_burst64_429_rate=${max_burst64_429_rate}"
  echo "[transaction-read-burst64-residual-smoothing] min_burst80_429_rate=${min_burst80_429_rate}"
  echo "[transaction-read-burst64-residual-smoothing] min_burst96_429_rate=${min_burst96_429_rate}"
  echo "[transaction-read-burst64-residual-smoothing] max_accepted_p95_ms=${max_accepted_p95_ms}"
  echo "[transaction-read-burst64-residual-smoothing] max_retry_p95_ms=${max_retry_p95_ms}"
  echo "[transaction-read-burst64-residual-smoothing] max_reject_streak=${max_reject_streak}"
  echo "[transaction-read-burst64-residual-smoothing] max_delayed_rate=${max_delayed_rate}"
  echo "[transaction-read-burst64-residual-smoothing] summary_tsv=${summary_tsv}"
  echo "[transaction-read-burst64-residual-smoothing] report_md=${report_md}"
}

require_rate "BURST64_SMOOTHING_MAX_BURST48_429_RATE" "${max_burst48_429_rate}"
require_rate "BURST64_SMOOTHING_MAX_BURST64_429_RATE" "${max_burst64_429_rate}"
require_rate "BURST64_SMOOTHING_MIN_BURST80_429_RATE" "${min_burst80_429_rate}"
require_rate "BURST64_SMOOTHING_MIN_BURST96_429_RATE" "${min_burst96_429_rate}"
require_rate "BURST64_SMOOTHING_MAX_DELAYED_RATE" "${max_delayed_rate}"
require_non_negative_number "BURST64_SMOOTHING_MAX_ACCEPTED_P95_MS" "${max_accepted_p95_ms}"
require_non_negative_number "BURST64_SMOOTHING_MAX_RETRY_P95_MS" "${max_retry_p95_ms}"
require_non_negative_number "BURST64_SMOOTHING_MAX_REJECT_STREAK" "${max_reject_streak}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "BURST64_SMOOTHING_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "BURST64_SMOOTHING_INPUT_TSV" "${input_tsv}"
mkdir -p "${output_dir}"

awk -F '\t' \
  -v max_burst48="${max_burst48_429_rate}" \
  -v max_burst64="${max_burst64_429_rate}" \
  -v min_burst80="${min_burst80_429_rate}" \
  -v min_burst96="${min_burst96_429_rate}" \
  -v max_p95="${max_accepted_p95_ms}" \
  -v max_retry_p95="${max_retry_p95_ms}" \
  -v max_streak="${max_reject_streak}" \
  -v max_delayed="${max_delayed_rate}" \
  '
  function value(name, fallback) {
    if (!(name in col) || col[name] == "") return fallback
    return $(col[name])
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      col[$i] = i
    }
    print "policy\tstatus\tburst48_429_rate\tburst64_429_rate\tburst80_429_rate\tburst96_429_rate\taccepted_p95_ms\tretry_after_p95_ms\treject_streak_max\tdelayed_rate\tfive_xx_count\tbackend_429_count"
    next
  }
  {
    policy = value("policy", "unknown")
    burst48 = value("burst48_429_rate", "1") + 0
    burst64 = value("burst64_429_rate", "1") + 0
    burst80 = value("burst80_429_rate", "0") + 0
    burst96 = value("burst96_429_rate", "0") + 0
    accepted_p95 = value("accepted_p95_ms", "999999") + 0
    retry_p95 = value("retry_after_p95_ms", "999999") + 0
    streak = value("reject_streak_max", "999999") + 0
    delayed = value("delayed_rate", "1") + 0
    five_xx = value("five_xx_count", "1") + 0
    backend_429 = value("backend_429_count", "1") + 0
    status = "pass"
    if (burst48 > max_burst48 || burst64 > max_burst64 || burst80 < min_burst80 || burst96 < min_burst96 || accepted_p95 > max_p95 || retry_p95 > max_retry_p95 || streak > max_streak || delayed > max_delayed || five_xx > 0 || backend_429 > 0) {
      status = "fail"
      fail_count++
    } else {
      pass_count++
    }
    printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
      policy, status, value("burst48_429_rate", "0"), value("burst64_429_rate", "0"),
      value("burst80_429_rate", "0"), value("burst96_429_rate", "0"), value("accepted_p95_ms", "0"),
      value("retry_after_p95_ms", "0"), value("reject_streak_max", "0"), value("delayed_rate", "0"),
      value("five_xx_count", "0"), value("backend_429_count", "0")
  }
  END {
    if (fail_count == "") fail_count = 0
    if (pass_count == "") pass_count = 0
    print "# fail_count=" fail_count > "/dev/stderr"
    print "# pass_count=" pass_count > "/dev/stderr"
  }
  ' "${input_tsv}" >"${summary_tsv}" 2>"${output_dir}/${name}-burst64-residual-smoothing.meta"

fail_count="$(awk -F '=' '/^# fail_count=/ { print $2 }' "${output_dir}/${name}-burst64-residual-smoothing.meta")"
pass_count="$(awk -F '=' '/^# pass_count=/ { print $2 }' "${output_dir}/${name}-burst64-residual-smoothing.meta")"
gate_status="pass"
if [[ "${fail_count}" != "0" || "${pass_count}" == "0" ]]; then
  gate_status="fail"
fi

matrix_table="$(awk -F '\t' '
  BEGIN {
    print "| Policy | Status | burst48 429 | burst64 429 | burst80 429 | burst96 429 | p95 ms | Retry p95 ms | Streak max | Delayed | 5xx | Backend 429 |"
    print "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Burst64 Residual Smoothing Gate

## Summary

- gate_status=${gate_status}
- burst 48 total 429 budget: <= ${max_burst48_429_rate}
- burst 64 total 429 budget: <= ${max_burst64_429_rate}
- burst 80/96 policy: fail-fast overload lower bound >= ${min_burst80_429_rate}/${min_burst96_429_rate}
- accepted p95 budget: <= ${max_accepted_p95_ms}ms
- retry-after p95 budget: <= ${max_retry_p95_ms}ms
- reject streak max: <= ${max_reject_streak}
- delayed ratio budget: <= ${max_delayed_rate}
- backend 429/5xx target: 0

## Matrix

${matrix_table}

## Contract Notes

- burst64는 residual 총 429 10% 이하를 pass 경계로 둔다.
- burst80 이상은 낮은 latency의 fail-fast overload로 남기며 accepted delay를 늘려 pass 처리하지 않는다.
- backend 429와 5xx는 edge smoothing 후보에서 즉시 탈락한다.

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read burst64 residual smoothing failed: ${summary_tsv}" >&2
  exit 1
fi
