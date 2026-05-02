#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-edge-rate-admission-matrix.sh [--print-plan]

Environment:
  EDGE_RATE_MATRIX_NAME                    default transaction-read-edge-rate-admission-<timestamp>
  EDGE_RATE_MATRIX_INPUT_TSV               required TSV with edge/admission/Hikari candidate metrics
  EDGE_RATE_MATRIX_OUTPUT_DIR              default build/reports/k6/<gate>
  EDGE_RATE_MATRIX_MAX_DELAYED_RATE        default 0.25
  EDGE_RATE_MATRIX_MAX_VU16_429_RATE       default 0.10
  EDGE_RATE_MATRIX_MAX_BURST48_429_RATE    default 0.10
  EDGE_RATE_MATRIX_MAX_BACKEND_429_RATE    default 0.0005
  EDGE_RATE_MATRIX_MAX_BACKEND_REJECTED    default 0
  EDGE_RATE_MATRIX_MAX_BACKEND_PENDING     default 0
  EDGE_RATE_MATRIX_MAX_BACKEND_CPU_PERCENT default 70
  EDGE_RATE_MATRIX_MAX_HIKARI_WARNINGS     default 0
  EDGE_RATE_MATRIX_REQUIRED_CANDIDATE_RATES optional comma-separated rates, e.g. 80,96
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
max_burst48_429_rate="${EDGE_RATE_MATRIX_MAX_BURST48_429_RATE:-0.10}"
max_backend_429_rate="${EDGE_RATE_MATRIX_MAX_BACKEND_429_RATE:-0.0005}"
max_backend_rejected="${EDGE_RATE_MATRIX_MAX_BACKEND_REJECTED:-0}"
max_backend_pending="${EDGE_RATE_MATRIX_MAX_BACKEND_PENDING:-0}"
max_backend_cpu_percent="${EDGE_RATE_MATRIX_MAX_BACKEND_CPU_PERCENT:-70}"
max_hikari_warnings="${EDGE_RATE_MATRIX_MAX_HIKARI_WARNINGS:-0}"
required_candidate_rates="${EDGE_RATE_MATRIX_REQUIRED_CANDIDATE_RATES:-}"
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

require_non_negative_number() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "${name} must be zero or greater: ${value}" >&2
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

missing_required_candidates() {
  if [[ -z "${required_candidate_rates}" ]]; then
    echo "none"
    return
  fi
  local rates
  rates=",${1:-$(candidate_rates)},"
  local missing=""
  local required
  IFS=',' read -r -a required_items <<<"${required_candidate_rates}"
  for required in "${required_items[@]}"; do
    if [[ -z "${required}" ]]; then
      continue
    fi
    if [[ "${rates}" != *",${required},"* ]]; then
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
  local rates
  rates="$(candidate_rates)"
  echo "[transaction-read-edge-rate-matrix] name=${name}"
  echo "[transaction-read-edge-rate-matrix] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-edge-rate-matrix] output_dir=${output_dir}"
  echo "[transaction-read-edge-rate-matrix] candidate_rates=${rates}"
  echo "[transaction-read-edge-rate-matrix] required_candidate_rates=${required_candidate_rates:-none}"
  echo "[transaction-read-edge-rate-matrix] missing_required_candidates=$(missing_required_candidates "${rates}")"
  echo "[transaction-read-edge-rate-matrix] max_delayed_rate=${max_delayed_rate}"
  echo "[transaction-read-edge-rate-matrix] max_vu16_429_rate=${max_vu16_429_rate}"
  echo "[transaction-read-edge-rate-matrix] max_burst48_429_rate=${max_burst48_429_rate}"
  echo "[transaction-read-edge-rate-matrix] max_backend_429_rate=${max_backend_429_rate}"
  echo "[transaction-read-edge-rate-matrix] max_backend_rejected=${max_backend_rejected}"
  echo "[transaction-read-edge-rate-matrix] max_backend_pending=${max_backend_pending}"
  echo "[transaction-read-edge-rate-matrix] max_backend_cpu_percent=${max_backend_cpu_percent}"
  echo "[transaction-read-edge-rate-matrix] recommended_policy=highest-pass-lowest-edge-reject"
  echo "[transaction-read-edge-rate-matrix] summary_tsv=${summary_tsv}"
  echo "[transaction-read-edge-rate-matrix] report_md=${report_md}"
}

require_rate "EDGE_RATE_MATRIX_MAX_DELAYED_RATE" "${max_delayed_rate}"
require_rate "EDGE_RATE_MATRIX_MAX_VU16_429_RATE" "${max_vu16_429_rate}"
require_rate "EDGE_RATE_MATRIX_MAX_BURST48_429_RATE" "${max_burst48_429_rate}"
require_rate "EDGE_RATE_MATRIX_MAX_BACKEND_429_RATE" "${max_backend_429_rate}"
require_non_negative_integer "EDGE_RATE_MATRIX_MAX_BACKEND_REJECTED" "${max_backend_rejected}"
require_non_negative_integer "EDGE_RATE_MATRIX_MAX_BACKEND_PENDING" "${max_backend_pending}"
require_non_negative_number "EDGE_RATE_MATRIX_MAX_BACKEND_CPU_PERCENT" "${max_backend_cpu_percent}"
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
missing_candidates="$(missing_required_candidates)"
if [[ "${missing_candidates}" != "none" ]]; then
  echo "EDGE_RATE_MATRIX_REQUIRED_CANDIDATE_RATES missing: ${missing_candidates}" >&2
  exit 1
fi

mkdir -p "${output_dir}"
awk -F '\t' \
  -v max_delayed="${max_delayed_rate}" \
  -v max_vu16="${max_vu16_429_rate}" \
  -v max_burst48="${max_burst48_429_rate}" \
  -v max_backend_429="${max_backend_429_rate}" \
  -v max_backend_rejected="${max_backend_rejected}" \
  -v max_pending="${max_backend_pending}" \
  -v max_cpu="${max_backend_cpu_percent}" \
  -v max_hikari="${max_hikari_warnings}" \
  '
  function value(name, fallback) {
    if (!(name in col) || col[name] == "") return fallback
    return $(col[name])
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      col[$i] = i
    }
    print "edge_rate_rps\tstatus\tsource_mode\thot_burst\tarchive_burst\tbackend_admission_max\thikari_max\tarrival16_delayed_rate\tvu16_429_rate\tburst48_429_rate\tbackend_429_rate\tbackend_rejected_count\tbackend_pending\tbackend_cpu_percent\thikari_warning_count"
    next
  }
  {
    rate = value("edge_rate_rps", "0")
    arrival_delayed = value("arrival16_delayed_rate", "0") + 0
    vu16_429 = value("vu16_429_rate", "0") + 0
    burst48_429 = value("burst48_429_rate", "0") + 0
    backend_429 = value("backend_429_rate", "0") + 0
    backend_rejected = value("backend_rejected_count", "0") + 0
    backend_pending = value("backend_pending", "0") + 0
    backend_cpu = value("backend_cpu_percent", "0") + 0
    hikari_warnings = value("hikari_warning_count", "0") + 0
    status = "pass"
    if (arrival_delayed > max_delayed || vu16_429 > max_vu16 || burst48_429 > max_burst48 || backend_429 > max_backend_429 || backend_rejected > max_backend_rejected || backend_pending > max_pending || backend_cpu > max_cpu || hikari_warnings > max_hikari) {
      status = "fail"
    }
    if (status == "pass") {
      pass_count++
      recommended = rate
    }
    printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
      rate, status, value("source_mode", "n/a"), value("hot_burst", "0"), value("archive_burst", "0"),
      value("backend_admission_max", "0"), value("hikari_max", "0"),
      value("arrival16_delayed_rate", "0"), value("vu16_429_rate", "0"), value("burst48_429_rate", "0"),
      value("backend_429_rate", "0"), value("backend_rejected_count", "0"),
      value("backend_pending", "0"), value("backend_cpu_percent", "0"), value("hikari_warning_count", "0")
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
    print "| Edge rate | Status | Source | Admission | Hikari | arrival16 delayed | VU16 429 | burst48 429 | Backend 429 | Backend rejected | Backend pending | CPU | Hikari warnings |"
    print "| ---: | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Edge Rate / Admission / Hikari Matrix

## Summary

- gate_status=${gate_status}
- recommended edge rate: ${recommended}r/s
- candidate rates: $(candidate_rates)
- required candidate rates: ${required_candidate_rates:-none}
- delayed budget: < ${max_delayed_rate}
- VU16 429 budget: < ${max_vu16_429_rate}
- burst-48 429 budget: < ${max_burst48_429_rate}
- backend 429 budget: < ${max_backend_429_rate}
- backend rejected budget: <= ${max_backend_rejected}
- backend CPU budget: <= ${max_backend_cpu_percent}%

## Matrix

${matrix_table}

## Contract Notes

- shared NAT note: single \`\$binary_remote_addr\` source is conservative for local k6 and NAT-heavy clients.
- recommendation policy: choose the highest passing edge rate only when backend pending, backend reject, CPU, and Hikari warnings stay inside budget.
- backend admission, backend reject, CPU, and Hikari are tracked together so edge rate-up does not hide the next bottleneck.

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read edge rate/admission matrix failed: ${summary_tsv}" >&2
  exit 1
fi
