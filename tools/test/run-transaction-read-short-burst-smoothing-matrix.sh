#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-short-burst-smoothing-matrix.sh [--print-plan]

Environment:
  SHORT_BURST_SMOOTHING_NAME                  default transaction-read-short-burst-smoothing-<timestamp>
  SHORT_BURST_SMOOTHING_INPUT_TSV             required TSV with nodelay/delay candidates
  SHORT_BURST_SMOOTHING_OUTPUT_DIR            default build/reports/k6/<gate>
  SHORT_BURST_SMOOTHING_REQUIRED_POLICIES     default nodelay,delay1,burst64
  SHORT_BURST_SMOOTHING_MAX_BURST48_429_RATE  default 0.10
  SHORT_BURST_SMOOTHING_MAX_BURST64_429_RATE  default 0.10
  SHORT_BURST_SMOOTHING_MAX_ACCEPTED_P95_MS   default 200
  SHORT_BURST_SMOOTHING_MAX_ACCEPTED_P99_MS   default 300
  SHORT_BURST_SMOOTHING_MAX_RETRY_AFTER_P95_MS default 250
  SHORT_BURST_SMOOTHING_MAX_DELAYED_RATE      default 0.25
  SHORT_BURST_SMOOTHING_MAX_BACKEND_429_RATE  default 0.05
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

name="${SHORT_BURST_SMOOTHING_NAME:-transaction-read-short-burst-smoothing-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${SHORT_BURST_SMOOTHING_INPUT_TSV:-}"
output_dir="${SHORT_BURST_SMOOTHING_OUTPUT_DIR:-build/reports/k6/${name}}"
required_policies="${SHORT_BURST_SMOOTHING_REQUIRED_POLICIES:-nodelay,delay1,burst64}"
max_burst48_429_rate="${SHORT_BURST_SMOOTHING_MAX_BURST48_429_RATE:-0.10}"
max_burst64_429_rate="${SHORT_BURST_SMOOTHING_MAX_BURST64_429_RATE:-0.10}"
max_accepted_p95_ms="${SHORT_BURST_SMOOTHING_MAX_ACCEPTED_P95_MS:-200}"
max_accepted_p99_ms="${SHORT_BURST_SMOOTHING_MAX_ACCEPTED_P99_MS:-300}"
max_retry_after_p95_ms="${SHORT_BURST_SMOOTHING_MAX_RETRY_AFTER_P95_MS:-250}"
max_delayed_rate="${SHORT_BURST_SMOOTHING_MAX_DELAYED_RATE:-0.25}"
max_backend_429_rate="${SHORT_BURST_SMOOTHING_MAX_BACKEND_429_RATE:-0.05}"
summary_tsv="${output_dir}/${name}-short-burst-smoothing.tsv"
report_md="${output_dir}/${name}-short-burst-smoothing.md"

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

candidate_policies() {
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

missing_required_policies() {
  local policies="${1:-$(candidate_policies)}"
  local missing=""
  local required
  IFS=',' read -r -a required_items <<<"${required_policies}"
  for required in "${required_items[@]}"; do
    if [[ -z "${required}" ]]; then
      continue
    fi
    if [[ ",${policies}," != *",${required},"* ]]; then
      if [[ -n "${missing}" ]]; then
        missing+=","
      fi
      missing+="${required}"
    fi
  done
  if [[ -z "${missing}" ]]; then
    echo "none"
  else
    echo "${missing}"
  fi
}

print_plan() {
  local policies
  policies="$(candidate_policies)"
  echo "[transaction-read-short-burst-smoothing] name=${name}"
  echo "[transaction-read-short-burst-smoothing] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-short-burst-smoothing] output_dir=${output_dir}"
  echo "[transaction-read-short-burst-smoothing] candidate_policies=${policies}"
  echo "[transaction-read-short-burst-smoothing] required_policies=${required_policies}"
  echo "[transaction-read-short-burst-smoothing] missing_required_policies=$(missing_required_policies "${policies}")"
  echo "[transaction-read-short-burst-smoothing] max_burst48_429_rate=${max_burst48_429_rate}"
  echo "[transaction-read-short-burst-smoothing] max_burst64_429_rate=${max_burst64_429_rate}"
  echo "[transaction-read-short-burst-smoothing] max_accepted_p95_ms=${max_accepted_p95_ms}"
  echo "[transaction-read-short-burst-smoothing] max_accepted_p99_ms=${max_accepted_p99_ms}"
  echo "[transaction-read-short-burst-smoothing] max_retry_after_p95_ms=${max_retry_after_p95_ms}"
  echo "[transaction-read-short-burst-smoothing] max_delayed_rate=${max_delayed_rate}"
  echo "[transaction-read-short-burst-smoothing] max_backend_429_rate=${max_backend_429_rate}"
  echo "[transaction-read-short-burst-smoothing] retry_after_contract=fixed-150ms-plus-jitter-100ms"
  echo "[transaction-read-short-burst-smoothing] summary_tsv=${summary_tsv}"
  echo "[transaction-read-short-burst-smoothing] report_md=${report_md}"
}

require_rate "SHORT_BURST_SMOOTHING_MAX_BURST48_429_RATE" "${max_burst48_429_rate}"
require_rate "SHORT_BURST_SMOOTHING_MAX_BURST64_429_RATE" "${max_burst64_429_rate}"
require_rate "SHORT_BURST_SMOOTHING_MAX_DELAYED_RATE" "${max_delayed_rate}"
require_rate "SHORT_BURST_SMOOTHING_MAX_BACKEND_429_RATE" "${max_backend_429_rate}"
require_non_negative_number "SHORT_BURST_SMOOTHING_MAX_ACCEPTED_P95_MS" "${max_accepted_p95_ms}"
require_non_negative_number "SHORT_BURST_SMOOTHING_MAX_ACCEPTED_P99_MS" "${max_accepted_p99_ms}"
require_non_negative_number "SHORT_BURST_SMOOTHING_MAX_RETRY_AFTER_P95_MS" "${max_retry_after_p95_ms}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
    echo "SHORT_BURST_SMOOTHING_INPUT_TSV is required" >&2
    exit 1
  fi
  exit 0
fi

if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
  echo "SHORT_BURST_SMOOTHING_INPUT_TSV is required" >&2
  exit 1
fi

missing_policies="$(missing_required_policies)"
if [[ "${missing_policies}" != "none" ]]; then
  echo "SHORT_BURST_SMOOTHING_REQUIRED_POLICIES missing: ${missing_policies}" >&2
  exit 1
fi

mkdir -p "${output_dir}"
awk -F '\t' \
  -v max_burst48="${max_burst48_429_rate}" \
  -v max_burst64="${max_burst64_429_rate}" \
  -v max_p95="${max_accepted_p95_ms}" \
  -v max_p99="${max_accepted_p99_ms}" \
  -v max_retry_p95="${max_retry_after_p95_ms}" \
  -v max_delayed="${max_delayed_rate}" \
  -v max_backend_429="${max_backend_429_rate}" \
  '
  function value(name, fallback) {
    if (!(name in col) || col[name] == "") return fallback
    return $(col[name])
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      col[$i] = i
    }
    print "policy\tstatus\thot_limit_mode\tarchive_limit_mode\thot_burst\tarchive_burst\tburst48_429_rate\tburst64_429_rate\taccepted_p95_ms\taccepted_p99_ms\tretry_after_p95_ms\tedge_delayed_rate\tfive_xx_count\tbackend_429_rate\tbackend_pending\thikari_warning_count"
    next
  }
  {
    policy = value("policy", "unknown")
    burst48 = value("burst48_429_rate", "1") + 0
    burst64 = value("burst64_429_rate", "1") + 0
    accepted_p95 = value("accepted_p95_ms", "999999") + 0
    accepted_p99 = value("accepted_p99_ms", "999999") + 0
    retry_after_p95 = value("retry_after_p95_ms", "999999") + 0
    delayed = value("edge_delayed_rate", "1") + 0
    five_xx = value("five_xx_count", "1") + 0
    backend_429 = value("backend_429_rate", "1") + 0
    backend_pending = value("backend_pending", "1") + 0
    hikari_warnings = value("hikari_warning_count", "1") + 0
    status = "pass"
    if (burst48 > max_burst48 || burst64 > max_burst64 || accepted_p95 > max_p95 || accepted_p99 > max_p99 || retry_after_p95 > max_retry_p95 || delayed > max_delayed || five_xx > 0 || backend_429 > max_backend_429 || backend_pending > 0 || hikari_warnings > 0) {
      status = "fail"
    }
    if (status == "pass" && recommended == "") {
      recommended = policy
    }
    if (status == "pass") {
      pass_count++
    }
    printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
      policy, status, value("hot_limit_mode", "n/a"), value("archive_limit_mode", "n/a"),
      value("hot_burst", "0"), value("archive_burst", "0"), value("burst48_429_rate", "0"),
      value("burst64_429_rate", "0"), value("accepted_p95_ms", "0"), value("accepted_p99_ms", "0"), value("retry_after_p95_ms", "0"), value("edge_delayed_rate", "0"),
      value("five_xx_count", "0"), value("backend_429_rate", "0"), value("backend_pending", "0"),
      value("hikari_warning_count", "0")
  }
  END {
    if (pass_count == "") pass_count = 0
    if (recommended == "") recommended = "none"
    print "# recommended=" recommended > "/dev/stderr"
    print "# pass_count=" pass_count > "/dev/stderr"
  }
  ' "${input_tsv}" >"${summary_tsv}" 2>"${output_dir}/${name}-short-burst-smoothing.meta"

recommended="$(awk -F '=' '/^# recommended=/ { print $2 }' "${output_dir}/${name}-short-burst-smoothing.meta")"
pass_count="$(awk -F '=' '/^# pass_count=/ { print $2 }' "${output_dir}/${name}-short-burst-smoothing.meta")"
gate_status="pass"
if [[ "${pass_count}" == "0" || "${recommended}" == "none" ]]; then
  gate_status="fail"
fi

matrix_table="$(awk -F '\t' '
  BEGIN {
    print "| Policy | Status | Hot mode | Archive mode | burst48 429 | burst64 429 | p95 ms | p99 ms | Retry-After p95 ms | delayed rate | 5xx | backend 429 | backend pending | Hikari warnings |"
    print "| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Short-Burst Smoothing Matrix

## Summary

- gate_status=${gate_status}
- recommended policy: ${recommended}
- candidate policies: $(candidate_policies)
- required policies: ${required_policies}
- burst-48 429 budget: <= ${max_burst48_429_rate}
- burst-64 429 budget: <= ${max_burst64_429_rate}
- accepted p95 budget: <= ${max_accepted_p95_ms}ms
- accepted p99 budget: <= ${max_accepted_p99_ms}ms
- Retry-After contract: fixed 150ms + jitter 100ms
- Retry-After p95 budget: <= ${max_retry_after_p95_ms}ms
- delayed ratio budget: <= ${max_delayed_rate}
- backend 429 budget: <= ${max_backend_429_rate}
- hard fail budget: 5xx=0, backend pending=0, Hikari warnings=0

## Matrix

${matrix_table}

## Contract Notes

- \`burst64\` profile raises per-source rate/burst and uses \`nodelay\` so success cannot hide behind delayed 200s.
- \`delay=1\` keeps the previous balanced queue option for rollback comparison.
- \`delay=0\` renders \`nodelay\` and is the rollback path if staging p95 regresses.
- Nginx OSS does not expose per-request limiter queue depth, so Retry-After stays a fixed client backpressure contract.

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read short-burst smoothing matrix failed: ${summary_tsv}" >&2
  exit 1
fi
