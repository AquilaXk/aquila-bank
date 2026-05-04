#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-429-source-gate.sh [--print-plan]

Environment:
  SOURCE_429_GATE_NAME     default transaction-read-429-source-<timestamp>
  SOURCE_429_SUMMARY_JSON  required k6 summary JSON
  SOURCE_429_OUTPUT_DIR    default build/reports/k6/<gate>
  SOURCE_429_RUN_ID        default SOURCE_429_GATE_NAME
  SOURCE_429_FAIL_RATE     default 0.10
  SOURCE_429_TOTAL_FAIL_RATE   default SOURCE_429_FAIL_RATE
  SOURCE_429_EDGE_FAIL_RATE    default SOURCE_429_FAIL_RATE
  SOURCE_429_BACKEND_FAIL_RATE default SOURCE_429_FAIL_RATE
  SOURCE_429_BACKEND_FAIL_COUNT optional backend 429 count ceiling
  SOURCE_429_BACKEND_ADMISSION_FAIL_RATE default SOURCE_429_BACKEND_FAIL_RATE
  SOURCE_429_BACKEND_ADMISSION_FAIL_COUNT optional backend-admission count ceiling
  SOURCE_429_FAIRNESS_FAIL_RATE default SOURCE_429_BACKEND_FAIL_RATE
  SOURCE_429_FAIRNESS_FAIL_COUNT optional fairness-limiter count ceiling
  SOURCE_429_NGINX_AGGREGATE_TSV optional Nginx aggregate TSV for backend source/499/5xx split
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

gate_name="${SOURCE_429_GATE_NAME:-transaction-read-429-source-$(date +%Y-%m-%d-%H%M%S)}"
summary_json="${SOURCE_429_SUMMARY_JSON:-}"
output_dir="${SOURCE_429_OUTPUT_DIR:-build/reports/k6/${gate_name}}"
run_id="${SOURCE_429_RUN_ID:-${gate_name}}"
fail_rate="${SOURCE_429_FAIL_RATE:-0.10}"
gate_mode="${SOURCE_429_GATE_MODE:-strict}"
total_fail_rate="${SOURCE_429_TOTAL_FAIL_RATE:-${fail_rate}}"
edge_fail_rate="${SOURCE_429_EDGE_FAIL_RATE:-${fail_rate}}"
backend_fail_rate="${SOURCE_429_BACKEND_FAIL_RATE:-${fail_rate}}"
backend_fail_count="${SOURCE_429_BACKEND_FAIL_COUNT:-}"
backend_admission_fail_rate="${SOURCE_429_BACKEND_ADMISSION_FAIL_RATE:-${backend_fail_rate}}"
backend_admission_fail_count="${SOURCE_429_BACKEND_ADMISSION_FAIL_COUNT:-${backend_fail_count}}"
fairness_fail_rate="${SOURCE_429_FAIRNESS_FAIL_RATE:-${backend_fail_rate}}"
fairness_fail_count="${SOURCE_429_FAIRNESS_FAIL_COUNT:-${backend_fail_count}}"
nginx_aggregate_tsv="${SOURCE_429_NGINX_AGGREGATE_TSV:-}"
summary_tsv="${output_dir}/${gate_name}-429-source.tsv"
report_md="${output_dir}/${gate_name}-429-source.md"

require_rate_value() {
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

number_greater_than() {
  awk -v value="$1" -v threshold="$2" 'BEGIN { exit !(value > threshold) }'
}

status_for_rate() {
  local value="$1"
  local threshold="$2"
  if number_greater_than "${value}" "${threshold}"; then
    echo "fail"
  else
    echo "pass"
  fi
}

status_for_rate_and_count() {
  local value="$1"
  local threshold="$2"
  local count="$3"
  local count_threshold="$4"
  if number_greater_than "${value}" "${threshold}"; then
    echo "fail"
    return
  fi
  if [[ -n "${count_threshold}" ]] && number_greater_than "${count}" "${count_threshold}"; then
    echo "fail"
    return
  fi
  echo "pass"
}

status_for_zero() {
  local value="$1"
  if number_greater_than "${value}" "0"; then
    echo "fail"
  else
    echo "pass"
  fi
}

status_for_unknown_zero() {
  local rate="$1"
  local count="$2"
  if number_greater_than "${rate}" "0" || number_greater_than "${count}" "0"; then
    echo "fail"
  else
    echo "pass"
  fi
}

threshold_label() {
  local rate_threshold="$1"
  local count_threshold="$2"
  if [[ -n "${count_threshold}" ]]; then
    echo "${rate_threshold},count<=${count_threshold}"
    return
  fi
  echo "${rate_threshold}"
}

metric_value() {
  local metric="$1"
  local field="$2"
  jq -r --arg metric "${metric}" --arg field "${field}" \
    '.metrics[$metric].values[$field] // "0"' "${summary_json}"
}

require_optional_count_value() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    return
  fi
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${name} must be a non-negative integer: ${value}" >&2
    exit 1
  fi
}

require_rate_value "SOURCE_429_FAIL_RATE" "${fail_rate}"
require_rate_value "SOURCE_429_TOTAL_FAIL_RATE" "${total_fail_rate}"
require_rate_value "SOURCE_429_EDGE_FAIL_RATE" "${edge_fail_rate}"
require_rate_value "SOURCE_429_BACKEND_FAIL_RATE" "${backend_fail_rate}"
require_rate_value "SOURCE_429_BACKEND_ADMISSION_FAIL_RATE" "${backend_admission_fail_rate}"
require_rate_value "SOURCE_429_FAIRNESS_FAIL_RATE" "${fairness_fail_rate}"
require_optional_count_value "SOURCE_429_BACKEND_FAIL_COUNT" "${backend_fail_count}"
require_optional_count_value "SOURCE_429_BACKEND_ADMISSION_FAIL_COUNT" "${backend_admission_fail_count}"
require_optional_count_value "SOURCE_429_FAIRNESS_FAIL_COUNT" "${fairness_fail_count}"

print_plan() {
  echo "[transaction-read-429-source] gate=${gate_name}"
  echo "[transaction-read-429-source] summary_json=${summary_json:-missing}"
  echo "[transaction-read-429-source] run_id=${run_id}"
  echo "[transaction-read-429-source] gate_mode=${gate_mode}"
  echo "[transaction-read-429-source] output_dir=${output_dir}"
  echo "[transaction-read-429-source] fail_rate=${fail_rate}"
  echo "[transaction-read-429-source] total_fail_rate=${total_fail_rate}"
  echo "[transaction-read-429-source] edge_fail_rate=${edge_fail_rate}"
  echo "[transaction-read-429-source] backend_fail_rate=${backend_fail_rate}"
  echo "[transaction-read-429-source] backend_fail_count=${backend_fail_count:-n/a}"
  echo "[transaction-read-429-source] backend_admission_fail_rate=${backend_admission_fail_rate}"
  echo "[transaction-read-429-source] backend_admission_fail_count=${backend_admission_fail_count:-n/a}"
  echo "[transaction-read-429-source] fairness_fail_rate=${fairness_fail_rate}"
  echo "[transaction-read-429-source] fairness_fail_count=${fairness_fail_count:-n/a}"
  echo "[transaction-read-429-source] nginx_aggregate_tsv=${nginx_aggregate_tsv:-missing}"
  echo "[transaction-read-429-source] summary_tsv=${summary_tsv}"
  echo "[transaction-read-429-source] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

if [[ -z "${summary_json}" || ! -s "${summary_json}" ]]; then
  echo "SOURCE_429_SUMMARY_JSON is required: ${summary_json:-missing}" >&2
  exit 1
fi
if [[ -n "${nginx_aggregate_tsv}" && ! -s "${nginx_aggregate_tsv}" ]]; then
  echo "SOURCE_429_NGINX_AGGREGATE_TSV is missing or empty: ${nginx_aggregate_tsv}" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"

nginx_total_count=0
backend_admission_count=0
fairness_limiter_count=0
nginx_499_count=0
nginx_5xx_count=0
backend_admission_rate=0
fairness_limiter_rate=0
nginx_499_rate=0
nginx_5xx_rate=0

rate_from_count() {
  local count="$1"
  local total="$2"
  awk -v count="${count}" -v total="${total}" 'BEGIN {
    if (total <= 0) printf "0"; else printf "%.6f", count / total
  }'
}

aggregate_count_by_reason() {
  local reason="$1"
  awk -F '\t' -v reason="${reason}" '
    NR > 1 && $2 == "429" && ($6 == reason || $7 == reason || $8 == reason) {
      count += $9
    }
    END { print count + 0 }
  ' "${nginx_aggregate_tsv}"
}

aggregate_count_by_status() {
  local pattern="$1"
  awk -F '\t' -v pattern="${pattern}" '
    NR > 1 && $2 ~ pattern {
      count += $9
    }
    END { print count + 0 }
  ' "${nginx_aggregate_tsv}"
}

if [[ -n "${nginx_aggregate_tsv}" ]]; then
  nginx_total_count="$(awk -F '\t' 'NR > 1 { count += $9 } END { print count + 0 }' "${nginx_aggregate_tsv}")"
  backend_admission_count="$(aggregate_count_by_reason "backend-admission")"
  fairness_limiter_count="$(aggregate_count_by_reason "fairness-limiter")"
  nginx_499_count="$(aggregate_count_by_status "^499$")"
  nginx_5xx_count="$(aggregate_count_by_status "^5")"
  backend_admission_rate="$(rate_from_count "${backend_admission_count}" "${nginx_total_count}")"
  fairness_limiter_rate="$(rate_from_count "${fairness_limiter_count}" "${nginx_total_count}")"
  nginx_499_rate="$(rate_from_count "${nginx_499_count}" "${nginx_total_count}")"
  nginx_5xx_rate="$(rate_from_count "${nginx_5xx_count}" "${nginx_total_count}")"
fi

http_reqs="$(metric_value http_reqs count)"
total_429_rate="$(metric_value aquila_transaction_429_rate rate)"
edge_429_rate="$(metric_value aquila_transaction_edge_429_rate rate)"
edge_429_count="$(metric_value aquila_transaction_edge_429_count count)"
backend_429_rate="$(metric_value aquila_transaction_backend_429_rate rate)"
backend_429_count="$(metric_value aquila_transaction_backend_429_count count)"
unknown_429_rate="$(metric_value aquila_transaction_unknown_429_rate rate)"
unknown_429_count="$(metric_value aquila_transaction_unknown_429_count count)"
transaction_502_rate="$(metric_value aquila_transaction_502_rate rate)"
transaction_502_count="$(metric_value aquila_transaction_502_count count)"
transaction_503_rate="$(metric_value aquila_transaction_503_rate rate)"
transaction_503_count="$(metric_value aquila_transaction_503_count count)"
accepted_200_rate="$(metric_value aquila_transaction_accepted_200_rate rate)"
accepted_200_count="$(metric_value aquila_transaction_accepted_200_count count)"

total_429_status="$(status_for_rate "${total_429_rate}" "${total_fail_rate}")"
edge_429_status="$(status_for_rate "${edge_429_rate}" "${edge_fail_rate}")"
backend_429_status="$(status_for_rate_and_count "${backend_429_rate}" "${backend_fail_rate}" "${backend_429_count}" "${backend_fail_count}")"
unknown_429_status="$(status_for_unknown_zero "${unknown_429_rate}" "${unknown_429_count}")"
transaction_502_status="$(status_for_zero "${transaction_502_count}")"
transaction_503_status="$(status_for_zero "${transaction_503_count}")"
backend_admission_status="$(status_for_rate_and_count "${backend_admission_rate}" "${backend_admission_fail_rate}" "${backend_admission_count}" "${backend_admission_fail_count}")"
fairness_limiter_status="$(status_for_rate_and_count "${fairness_limiter_rate}" "${fairness_fail_rate}" "${fairness_limiter_count}" "${fairness_fail_count}")"
nginx_499_status="$(status_for_zero "${nginx_499_count}")"
nginx_5xx_status="$(status_for_zero "${nginx_5xx_count}")"

backend_threshold="$(threshold_label "${backend_fail_rate}" "${backend_fail_count}")"
backend_admission_threshold="$(threshold_label "${backend_admission_fail_rate}" "${backend_admission_fail_count}")"
fairness_threshold="$(threshold_label "${fairness_fail_rate}" "${fairness_fail_count}")"

gate_status="pass"
gate_items=("${total_429_status}" "${edge_429_status}" "${backend_429_status}" "${unknown_429_status}" "${transaction_502_status}" "${transaction_503_status}")
if [[ -n "${nginx_aggregate_tsv}" ]]; then
  gate_items+=("${backend_admission_status}" "${fairness_limiter_status}" "${nginx_499_status}" "${nginx_5xx_status}")
fi
for item in "${gate_items[@]}"; do
  if [[ "${item}" == "fail" ]]; then
    gate_status="fail"
  fi
done

{
  printf "source\tstatus\trate\tcount\tfail_threshold\n"
  printf "total_429\t%s\t%s\t%s\t%s\n" "${total_429_status}" "${total_429_rate}" "n/a" "${total_fail_rate}"
  printf "edge\t%s\t%s\t%s\t%s\n" "${edge_429_status}" "${edge_429_rate}" "${edge_429_count}" "${edge_fail_rate}"
  printf "backend\t%s\t%s\t%s\t%s\n" "${backend_429_status}" "${backend_429_rate}" "${backend_429_count}" "${backend_threshold}"
  printf "unknown\t%s\t%s\t%s\t0\n" "${unknown_429_status}" "${unknown_429_rate}" "${unknown_429_count}"
  printf "502\t%s\t%s\t%s\t0\n" "${transaction_502_status}" "${transaction_502_rate}" "${transaction_502_count}"
  printf "503\t%s\t%s\t%s\t0\n" "${transaction_503_status}" "${transaction_503_rate}" "${transaction_503_count}"
  printf "accepted_200\tobserve\t%s\t%s\tn/a\n" "${accepted_200_rate}" "${accepted_200_count}"
  printf "http_reqs\tobserve\tn/a\t%s\tn/a\n" "${http_reqs}"
  if [[ -n "${nginx_aggregate_tsv}" ]]; then
    printf "backend-admission\t%s\t%s\t%s\t%s\n" "${backend_admission_status}" "${backend_admission_rate}" "${backend_admission_count}" "${backend_admission_threshold}"
    printf "fairness-limiter\t%s\t%s\t%s\t%s\n" "${fairness_limiter_status}" "${fairness_limiter_rate}" "${fairness_limiter_count}" "${fairness_threshold}"
    printf "nginx_499\t%s\t%s\t%s\t0\n" "${nginx_499_status}" "${nginx_499_rate}" "${nginx_499_count}"
    printf "nginx_5xx\t%s\t%s\t%s\t0\n" "${nginx_5xx_status}" "${nginx_5xx_rate}" "${nginx_5xx_count}"
    printf "nginx_rows\tobserve\tn/a\t%s\tn/a\n" "${nginx_total_count}"
  fi
} >"${summary_tsv}"

nginx_aggregate_report=""
if [[ -n "${nginx_aggregate_tsv}" ]]; then
  nginx_aggregate_report="$(cat <<REPORT

## Nginx Aggregate Source Split

- nginx aggregate rows=${nginx_total_count}
- nginx aggregate TSV: ${nginx_aggregate_tsv}

| Source | Status | Rate | Count | Threshold |
| --- | --- | ---: | ---: | --- |
| backend-admission | ${backend_admission_status} | ${backend_admission_rate} | ${backend_admission_count} | ${backend_admission_threshold} |
| fairness-limiter | ${fairness_limiter_status} | ${fairness_limiter_rate} | ${fairness_limiter_count} | ${fairness_threshold} |
| nginx 499 | ${nginx_499_status} | ${nginx_499_rate} | ${nginx_499_count} | 0 |
| nginx 5xx | ${nginx_5xx_status} | ${nginx_5xx_rate} | ${nginx_5xx_count} | 0 |
REPORT
)"
fi

cat >"${report_md}" <<REPORT
# Transaction Read 429 Source Gate

## Summary

- gate: ${gate_name}
- gate_status=${gate_status}
- run_id=${run_id}
- gate_mode=${gate_mode}
- fail_rate=${fail_rate}
- total_fail_rate=${total_fail_rate}
- edge_fail_rate=${edge_fail_rate}
- backend_fail_rate=${backend_fail_rate}
- http_reqs=${http_reqs}
- unknown 429 hard-zero: rate=0 and count=0 required

## Source Split

| Source | Status | Rate | Count | Threshold |
| --- | --- | ---: | ---: | --- |
| total 429 | ${total_429_status} | ${total_429_rate} | n/a | ${total_fail_rate} |
| edge 429 | ${edge_429_status} | ${edge_429_rate} | ${edge_429_count} | ${edge_fail_rate} |
| backend 429 | ${backend_429_status} | ${backend_429_rate} | ${backend_429_count} | ${backend_threshold} |
| unknown 429 | ${unknown_429_status} | ${unknown_429_rate} | ${unknown_429_count} | 0 |
| 502 | ${transaction_502_status} | ${transaction_502_rate} | ${transaction_502_count} | 0 |
| 503 | ${transaction_503_status} | ${transaction_503_rate} | ${transaction_503_count} | 0 |
| accepted 200 | observe | ${accepted_200_rate} | ${accepted_200_count} | n/a |
${nginx_aggregate_report}

## Artifacts

- summary TSV: ${summary_tsv}
- summary JSON: ${summary_json}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read 429 source gate failed: ${summary_tsv}" >&2
  exit 1
fi
