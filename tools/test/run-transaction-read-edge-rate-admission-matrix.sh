#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-edge-rate-admission-matrix.sh [--print-plan]

Environment:
  EDGE_RATE_MATRIX_NAME                  default transaction-read-edge-rate-admission-<timestamp>
  EDGE_RATE_MATRIX_INPUT_TSV             required TSV with edge/admission/Hikari candidate metrics
  EDGE_RATE_MATRIX_OUTPUT_DIR            default build/reports/k6/<gate>
  EDGE_RATE_MATRIX_MAX_DELAYED_RATE      default 0.25
  EDGE_RATE_MATRIX_MAX_VU16_429_RATE     default 0.10
  EDGE_RATE_MATRIX_MAX_BURST48_429_RATE  default 0.35
  EDGE_RATE_MATRIX_MAX_BACKEND_PENDING   default 0
  EDGE_RATE_MATRIX_MAX_HIKARI_WARNINGS   default 0
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

name="${EDGE_RATE_MATRIX_NAME:-transaction-read-edge-rate-admission-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${EDGE_RATE_MATRIX_INPUT_TSV:-}"
output_dir="${EDGE_RATE_MATRIX_OUTPUT_DIR:-build/reports/k6/${name}}"
max_delayed_rate="${EDGE_RATE_MATRIX_MAX_DELAYED_RATE:-0.25}"
max_vu16_429_rate="${EDGE_RATE_MATRIX_MAX_VU16_429_RATE:-0.10}"
max_burst48_429_rate="${EDGE_RATE_MATRIX_MAX_BURST48_429_RATE:-0.35}"
max_backend_pending="${EDGE_RATE_MATRIX_MAX_BACKEND_PENDING:-0}"
max_hikari_warnings="${EDGE_RATE_MATRIX_MAX_HIKARI_WARNINGS:-0}"
summary_tsv="${output_dir}/${name}-rate-admission-matrix.tsv"
report_md="${output_dir}/${name}-rate-admission-matrix.md"

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

require_non_negative_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${name} must be a non-negative integer: ${value}" >&2
    exit 1
  fi
}

candidate_rates() {
  if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
    echo "missing"
    return
  fi
  awk -F '\t' '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "edge_rate_rps") rate_col = i
      }
      next
    }
    rate_col {
      if (result != "") result = result ","
      result = result $rate_col
    }
    END {
      if (result == "") print "missing"; else print result
    }
  ' "${input_tsv}"
}

print_plan() {
  echo "[transaction-read-edge-rate-matrix] name=${name}"
  echo "[transaction-read-edge-rate-matrix] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-edge-rate-matrix] output_dir=${output_dir}"
  echo "[transaction-read-edge-rate-matrix] candidate_rates=$(candidate_rates)"
  echo "[transaction-read-edge-rate-matrix] max_delayed_rate=${max_delayed_rate}"
  echo "[transaction-read-edge-rate-matrix] max_vu16_429_rate=${max_vu16_429_rate}"
  echo "[transaction-read-edge-rate-matrix] max_burst48_429_rate=${max_burst48_429_rate}"
  echo "[transaction-read-edge-rate-matrix] recommended_policy=highest-pass-lowest-edge-reject"
  echo "[transaction-read-edge-rate-matrix] summary_tsv=${summary_tsv}"
  echo "[transaction-read-edge-rate-matrix] report_md=${report_md}"
}

require_rate "EDGE_RATE_MATRIX_MAX_DELAYED_RATE" "${max_delayed_rate}"
require_rate "EDGE_RATE_MATRIX_MAX_VU16_429_RATE" "${max_vu16_429_rate}"
require_rate "EDGE_RATE_MATRIX_MAX_BURST48_429_RATE" "${max_burst48_429_rate}"
require_non_negative_integer "EDGE_RATE_MATRIX_MAX_BACKEND_PENDING" "${max_backend_pending}"
require_non_negative_integer "EDGE_RATE_MATRIX_MAX_HIKARI_WARNINGS" "${max_hikari_warnings}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
    echo "EDGE_RATE_MATRIX_INPUT_TSV is required" >&2
    exit 1
  fi
  exit 0
fi

if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
  echo "EDGE_RATE_MATRIX_INPUT_TSV is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
awk -F '\t' \
  -v max_delayed="${max_delayed_rate}" \
  -v max_vu16="${max_vu16_429_rate}" \
  -v max_burst48="${max_burst48_429_rate}" \
  -v max_pending="${max_backend_pending}" \
  -v max_hikari="${max_hikari_warnings}" \
  '
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      col[$i] = i
    }
    print "edge_rate_rps\tstatus\thot_burst\tarchive_burst\tbackend_admission_max\thikari_max\tarrival16_delayed_rate\tvu16_429_rate\tburst48_429_rate\tbackend_pending\tbackend_cpu_percent\thikari_warning_count"
    next
  }
  {
    rate = $col["edge_rate_rps"]
    arrival_delayed = $col["arrival16_delayed_rate"] + 0
    vu16_429 = $col["vu16_429_rate"] + 0
    burst48_429 = $col["burst48_429_rate"] + 0
    backend_pending = $col["backend_pending"] + 0
    hikari_warnings = $col["hikari_warning_count"] + 0
    status = "pass"
    if (arrival_delayed > max_delayed || vu16_429 > max_vu16 || burst48_429 > max_burst48 || backend_pending > max_pending || hikari_warnings > max_hikari) {
      status = "fail"
    }
    if (status == "pass") {
      pass_count++
      recommended = rate
    }
    printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
      rate, status, $col["hot_burst"], $col["archive_burst"],
      $col["backend_admission_max"], $col["hikari_max"],
      $col["arrival16_delayed_rate"], $col["vu16_429_rate"], $col["burst48_429_rate"],
      $col["backend_pending"], $col["backend_cpu_percent"], $col["hikari_warning_count"]
  }
  END {
    if (pass_count == "") pass_count = 0
    if (recommended == "") {
      print "# recommended=none" > "/dev/stderr"
    } else {
      print "# recommended=" recommended > "/dev/stderr"
    }
    print "# pass_count=" pass_count > "/dev/stderr"
  }
  ' "${input_tsv}" >"${summary_tsv}" 2>"${output_dir}/${name}-matrix.meta"

recommended="$(awk -F '=' '/^# recommended=/ { print $2 }' "${output_dir}/${name}-matrix.meta")"
pass_count="$(awk -F '=' '/^# pass_count=/ { print $2 }' "${output_dir}/${name}-matrix.meta")"
gate_status="pass"
if [[ "${pass_count}" == "0" || "${recommended}" == "none" ]]; then
  gate_status="fail"
fi

matrix_table="$(awk -F '\t' '
  BEGIN {
    print "| Edge rate | Status | Admission | Hikari | arrival16 delayed | VU16 429 | burst48 429 | Backend pending | Hikari warnings |"
    print "| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $5, $6, $7, $8, $9, $10, $12
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Edge Rate / Admission / Hikari Matrix

## Summary

- gate_status=${gate_status}
- recommended edge rate: ${recommended}r/s
- candidate rates: $(candidate_rates)
- delayed budget: < ${max_delayed_rate}
- VU16 429 budget: < ${max_vu16_429_rate}
- burst-48 429 budget: < ${max_burst48_429_rate}

## Matrix

${matrix_table}

## Contract Notes

- shared NAT note: single \`\$binary_remote_addr\` source is conservative for local k6 and NAT-heavy clients.
- recommendation policy: choose the highest passing edge rate only when backend pending and Hikari warnings stay at 0.
- backend admission and Hikari are tracked together so edge rate-up does not hide the next bottleneck.

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read edge rate/admission matrix failed: ${summary_tsv}" >&2
  exit 1
fi
