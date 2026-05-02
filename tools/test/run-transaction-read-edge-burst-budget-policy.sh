#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-edge-burst-budget-policy.sh [--print-plan]

Environment:
  EDGE_BURST_POLICY_NAME                     default transaction-read-edge-burst-policy-<timestamp>
  EDGE_BURST_POLICY_INPUT_TSV                required TSV with burst-256/512 evidence
  EDGE_BURST_POLICY_OUTPUT_DIR               default build/reports/k6/<name>
  EDGE_BURST_POLICY_REQUIRED_BURSTS          default 256,512
  EDGE_BURST_POLICY_BURST256_MIN_429_RATE    default 0.20
  EDGE_BURST_POLICY_BURST256_MAX_429_RATE    default 0.75
  EDGE_BURST_POLICY_BURST512_MIN_429_RATE    default 0.50
  EDGE_BURST_POLICY_BURST512_MAX_429_RATE    default 0.90
  EDGE_BURST_POLICY_MAX_ACCEPTED_P95_MS      default 250
  EDGE_BURST_POLICY_MAX_RETRY_AFTER_P95_MS   default 300
  EDGE_BURST_POLICY_MAX_DELAYED_RATE         default 0.25
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

name="${EDGE_BURST_POLICY_NAME:-transaction-read-edge-burst-policy-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${EDGE_BURST_POLICY_INPUT_TSV:-}"
output_dir="${EDGE_BURST_POLICY_OUTPUT_DIR:-build/reports/k6/${name}}"
required_bursts="${EDGE_BURST_POLICY_REQUIRED_BURSTS:-256,512}"
burst256_min="${EDGE_BURST_POLICY_BURST256_MIN_429_RATE:-0.20}"
burst256_max="${EDGE_BURST_POLICY_BURST256_MAX_429_RATE:-0.75}"
burst512_min="${EDGE_BURST_POLICY_BURST512_MIN_429_RATE:-0.50}"
burst512_max="${EDGE_BURST_POLICY_BURST512_MAX_429_RATE:-0.90}"
max_accepted_p95_ms="${EDGE_BURST_POLICY_MAX_ACCEPTED_P95_MS:-250}"
max_retry_after_p95_ms="${EDGE_BURST_POLICY_MAX_RETRY_AFTER_P95_MS:-300}"
max_delayed_rate="${EDGE_BURST_POLICY_MAX_DELAYED_RATE:-0.25}"
summary_tsv="${output_dir}/${name}-edge-burst-budget-policy.tsv"
report_md="${output_dir}/${name}-edge-burst-budget-policy.md"
meta_file="${output_dir}/${name}-edge-burst-budget-policy.meta"

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

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

candidate_bursts() {
  if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
    echo "missing"
    return
  fi
  awk -F '\t' '
    NR == 1 {
      for (i = 1; i <= NF; i++) if ($i == "burst_rate") burst_col = i
      next
    }
    burst_col {
      if (result != "") result = result ","
      result = result $burst_col
    }
    END { if (result == "") print "missing"; else print result }
  ' "${input_tsv}"
}

missing_required_bursts() {
  local bursts="${1:-$(candidate_bursts)}"
  local item
  local missing=""
  IFS=',' read -r -a required_items <<<"${required_bursts}"
  for item in "${required_items[@]}"; do
    if [[ ",${bursts}," != *",${item},"* ]]; then
      if [[ -n "${missing}" ]]; then
        missing+=","
      fi
      missing+="${item}"
    fi
  done
  if [[ -z "${missing}" ]]; then
    echo "none"
  else
    echo "${missing}"
  fi
}

require_rate "EDGE_BURST_POLICY_BURST256_MIN_429_RATE" "${burst256_min}"
require_rate "EDGE_BURST_POLICY_BURST256_MAX_429_RATE" "${burst256_max}"
require_rate "EDGE_BURST_POLICY_BURST512_MIN_429_RATE" "${burst512_min}"
require_rate "EDGE_BURST_POLICY_BURST512_MAX_429_RATE" "${burst512_max}"
require_rate "EDGE_BURST_POLICY_MAX_DELAYED_RATE" "${max_delayed_rate}"
require_non_negative_number "EDGE_BURST_POLICY_MAX_ACCEPTED_P95_MS" "${max_accepted_p95_ms}"
require_non_negative_number "EDGE_BURST_POLICY_MAX_RETRY_AFTER_P95_MS" "${max_retry_after_p95_ms}"

print_plan() {
  local bursts
  bursts="$(candidate_bursts)"
  echo "[transaction-read-edge-burst-policy] name=${name}"
  echo "[transaction-read-edge-burst-policy] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-edge-burst-policy] output_dir=${output_dir}"
  echo "[transaction-read-edge-burst-policy] candidate_bursts=${bursts}"
  echo "[transaction-read-edge-burst-policy] required_bursts=${required_bursts}"
  echo "[transaction-read-edge-burst-policy] missing_required_bursts=$(missing_required_bursts "${bursts}")"
  echo "[transaction-read-edge-burst-policy] burst256_allowed_429_rate=${burst256_min}..${burst256_max}"
  echo "[transaction-read-edge-burst-policy] burst512_allowed_429_rate=${burst512_min}..${burst512_max}"
  echo "[transaction-read-edge-burst-policy] classification=defensive-reject|under-protected|excessive-loss"
  echo "[transaction-read-edge-burst-policy] max_accepted_p95_ms=${max_accepted_p95_ms}"
  echo "[transaction-read-edge-burst-policy] max_retry_after_p95_ms=${max_retry_after_p95_ms}"
  echo "[transaction-read-edge-burst-policy] max_delayed_rate=${max_delayed_rate}"
  echo "[transaction-read-edge-burst-policy] summary_tsv=${summary_tsv}"
  echo "[transaction-read-edge-burst-policy] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "EDGE_BURST_POLICY_INPUT_TSV" "${input_tsv}"
  exit 0
fi

require_file "EDGE_BURST_POLICY_INPUT_TSV" "${input_tsv}"
missing_bursts="$(missing_required_bursts)"
if [[ "${missing_bursts}" != "none" ]]; then
  echo "EDGE_BURST_POLICY_REQUIRED_BURSTS missing: ${missing_bursts}" >&2
  exit 1
fi

mkdir -p "${output_dir}"

awk -F '\t' \
  -v burst256_min="${burst256_min}" \
  -v burst256_max="${burst256_max}" \
  -v burst512_min="${burst512_min}" \
  -v burst512_max="${burst512_max}" \
  -v max_p95="${max_accepted_p95_ms}" \
  -v max_retry="${max_retry_after_p95_ms}" \
  -v max_delayed="${max_delayed_rate}" '
function value(name, fallback) {
  if (!(name in col) || col[name] == "") return fallback
  return $(col[name])
}
function allowed_min(burst) {
  return burst == 512 ? burst512_min : burst256_min
}
function allowed_max(burst) {
  return burst == 512 ? burst512_max : burst256_max
}
BEGIN {
  print "burst_rate\tstatus\tclassification\tedge_429_rate\tbackend_429_count\tunknown_429_count\tfive_xx_count\tnginx_499_count\taccepted_p95_ms\tretry_after_p95_ms\tdelayed_rate\tpolicy_candidate"
}
NR == 1 {
  for (i = 1; i <= NF; i++) col[$i] = i
  next
}
{
  burst = value("burst_rate", "0") + 0
  edge = value("edge_429_rate", "1") + 0
  backend = value("backend_429_count", "1") + 0
  unknown = value("unknown_429_count", "1") + 0
  five_xx = value("five_xx_count", "1") + 0
  status499 = value("nginx_499_count", "1") + 0
  p95 = value("accepted_p95_ms", "999999") + 0
  retry = value("retry_after_p95_ms", "999999") + 0
  delayed = value("delayed_rate", "1") + 0
  candidate = value("policy_candidate", "n/a")
  min_rate = allowed_min(burst)
  max_rate = allowed_max(burst)
  status = "pass"
  if (edge < min_rate) {
    classification = "under-protected"
    status = "fail"
  } else if (edge > max_rate) {
    classification = "excessive-loss"
    status = "fail"
  } else {
    classification = "defensive-reject"
  }
  if (backend > 0 || unknown > 0 || five_xx > 0 || status499 > 0 || p95 > max_p95 || retry > max_retry || delayed > max_delayed) {
    status = "fail"
  }
  if (status == "fail") fail_count++
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
    burst, status, classification, value("edge_429_rate", "0"), value("backend_429_count", "0"),
    value("unknown_429_count", "0"), value("five_xx_count", "0"), value("nginx_499_count", "0"),
    value("accepted_p95_ms", "0"), value("retry_after_p95_ms", "0"), value("delayed_rate", "0"), candidate
}
END {
  print "fail_count=" (fail_count + 0) > "/dev/stderr"
}
' "${input_tsv}" >"${summary_tsv}" 2>"${meta_file}"

fail_count="$(awk -F '=' '/^fail_count=/ { print $2 }' "${meta_file}")"
gate_status="pass"
if [[ "${fail_count}" != "0" ]]; then
  gate_status="fail"
fi

policy_table="$(awk -F '\t' '
  BEGIN {
    print "| Burst | Status | Classification | Edge 429 | Backend 429 | Unknown 429 | 5xx | 499 | p95 ms | Retry-After p95 ms | delayed | Candidate |"
    print "| ---: | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Edge Burst Budget Policy

## Summary

- gate_status=${gate_status}
- required bursts: ${required_bursts}
- burst-256 allowed 429 budget: ${burst256_min}..${burst256_max}
- burst-512 allowed 429 budget: ${burst512_min}..${burst512_max}
- accepted p95 budget: <= ${max_accepted_p95_ms}ms
- Retry-After p95 budget: <= ${max_retry_after_p95_ms}ms
- delayed ratio budget: <= ${max_delayed_rate}
- hard-zero: backend 429, unknown 429, 499, 5xx
- 거절이 정상 방어인지, 과도한 손실인지 수치화한다.

## Policy Table

${policy_table}

## Contract Notes

- defensive-reject: high burst에서 edge 429가 하한 이상, 상한 이하로 발생해 fail-fast 방어로 해석한다.
- under-protected: edge reject가 너무 낮아 backend/latency 보호가 부족한 후보로 본다.
- excessive-loss: edge reject가 상한을 넘어 정상 요청 손실이 과도한 후보로 본다.

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read edge burst budget policy failed: ${summary_tsv}" >&2
  exit 1
fi
