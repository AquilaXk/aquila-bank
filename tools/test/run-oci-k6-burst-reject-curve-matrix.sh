#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-oci-k6-burst-reject-curve-matrix.sh [--print-plan]

Environment:
  OCI_K6_BURST_MATRIX_NAME            default oci-k6-burst-reject-curve-matrix-<timestamp>
  OCI_K6_BURST_MATRIX_INPUT_TSV       required TSV: burst_rate/k6_run_id/summary_json/nginx_aggregate_json/nginx_aggregate_tsv/nginx_aggregate_md
  OCI_K6_BURST_MATRIX_REQUIRED_RATES  default 32,48,64,80,96
  OCI_K6_BURST_MATRIX_OUTPUT_DIR      default build/reports/k6/<name>
  OCI_K6_BURST_MATRIX_PROMOTION_TARGET_RATE default 80
  OCI_K6_BURST_MATRIX_TARGET_429_THRESHOLD default 0.10
  OCI_K6_BURST_MATRIX_BACKEND_429_THRESHOLD default 0
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

name="${OCI_K6_BURST_MATRIX_NAME:-oci-k6-burst-reject-curve-matrix-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${OCI_K6_BURST_MATRIX_INPUT_TSV:-}"
required_burst_rates="${OCI_K6_BURST_MATRIX_REQUIRED_RATES:-32,48,64,80,96}"
output_dir="${OCI_K6_BURST_MATRIX_OUTPUT_DIR:-build/reports/k6/${name}}"
summary_tsv="${output_dir}/${name}-burst-reject-curve.tsv"
summary_json="${output_dir}/${name}-burst-reject-curve.json"
report_md="${output_dir}/${name}-burst-reject-curve.md"
target_429_threshold="${OCI_K6_BURST_MATRIX_TARGET_429_THRESHOLD:-${OCI_K6_BURST_MATRIX_BURST64_429_THRESHOLD:-0.10}}"
backend_429_threshold="${OCI_K6_BURST_MATRIX_BACKEND_429_THRESHOLD:-0}"
accepted_p95_threshold_ms="${OCI_K6_BURST_MATRIX_ACCEPTED_P95_THRESHOLD_MS:-100}"
promotion_target_rate="${OCI_K6_BURST_MATRIX_PROMOTION_TARGET_RATE:-80}"

IFS=',' read -r -a required_rate_items <<<"${required_burst_rates}"

is_zero() {
  awk -v value="$1" 'BEGIN { exit !(value == 0) }'
}

backend_gate_label() {
  if is_zero "${backend_429_threshold}"; then
    echo "backend 429 = 0"
    return
  fi
  echo "backend 429 <= ${backend_429_threshold}"
}

if ! [[ "${promotion_target_rate}" =~ ^[1-9][0-9]*$ ]]; then
  echo "OCI_K6_BURST_MATRIX_PROMOTION_TARGET_RATE must be a positive integer: ${promotion_target_rate}" >&2
  exit 1
fi

print_plan() {
  echo "[oci-k6-burst-reject-curve-matrix] name=${name}"
  echo "[oci-k6-burst-reject-curve-matrix] input_tsv=${input_tsv:-missing}"
  echo "[oci-k6-burst-reject-curve-matrix] required_burst_rates=${required_burst_rates}"
  echo "[oci-k6-burst-reject-curve-matrix] promotion_target_rate=${promotion_target_rate}"
  echo "[oci-k6-burst-reject-curve-matrix] output_dir=${output_dir}"
  echo "[oci-k6-burst-reject-curve-matrix] gate=burst${promotion_target_rate} total/edge 429 <= ${target_429_threshold}, $(backend_gate_label), k6 503 = 0, nginx 5xx = 0, nginx 499 = 0, accepted p95 < ${accepted_p95_threshold_ms}ms"
  echo "[oci-k6-burst-reject-curve-matrix] summary_tsv=${summary_tsv}"
  echo "[oci-k6-burst-reject-curve-matrix] summary_json=${summary_json}"
  echo "[oci-k6-burst-reject-curve-matrix] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
    echo "OCI_K6_BURST_MATRIX_INPUT_TSV is required" >&2
    exit 1
  fi
  exit 0
fi

if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
  echo "OCI_K6_BURST_MATRIX_INPUT_TSV is required" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

expected_header=$'burst_rate\tk6_run_id\tsummary_json\tnginx_aggregate_json\tnginx_aggregate_tsv\tnginx_aggregate_md'
actual_header="$(head -n 1 "${input_tsv}")"
if [[ "${actual_header}" != "${expected_header}" ]]; then
  echo "invalid input TSV header: ${actual_header}" >&2
  exit 1
fi

mkdir -p "${output_dir}"
raw_tsv="${output_dir}/${name}-burst-reject-curve.raw.tsv"
failures_file="${output_dir}/${name}-burst-reject-curve.failures"
: >"${failures_file}"

gt() {
  awk -v left="$1" -v right="$2" 'BEGIN { exit !(left > right) }'
}

gte() {
  awk -v left="$1" -v right="$2" 'BEGIN { exit !(left >= right) }'
}

metric_required() {
  local summary_file="$1"
  local metric_name="$2"
  local value_name="$3"
  jq -er --arg metric_name "${metric_name}" --arg value_name "${value_name}" '
    .metrics[$metric_name].values[$value_name]
    | if type == "number" then . else tonumber end
  ' "${summary_file}"
}

metric_optional() {
  local summary_file="$1"
  local metric_name="$2"
  local value_name="$3"
  local fallback="$4"
  jq -er --arg metric_name "${metric_name}" --arg value_name "${value_name}" --arg fallback "${fallback}" '
    (.metrics[$metric_name].values[$value_name] // ($fallback | tonumber))
    | if type == "number" then . else tonumber end
  ' "${summary_file}"
}

accepted_p95_metric() {
  local summary_file="$1"
  local expected_response_p95
  expected_response_p95="$(jq -er '.metrics["http_req_duration{expected_response:true}"].values["p(95)"] // empty' "${summary_file}" 2>/dev/null || true)"
  if [[ -n "${expected_response_p95}" ]]; then
    printf "%s\n" "${expected_response_p95}"
    return
  fi
  metric_required "${summary_file}" "http_req_duration" "p(95)"
}

nginx_status_count() {
  local aggregate_json="$1"
  local run_id="$2"
  local status_pattern="$3"
  jq -er --arg run_id "${run_id}" --arg status_pattern "${status_pattern}" '
    [
      .[]
      | select(((.k6_run_id // "unknown") | tostring) == $run_id)
      | select(((.status // "0") | tostring) | test($status_pattern))
      | (.count // 0 | tonumber)
    ]
    | add // 0
  ' "${aggregate_json}"
}

record_failure() {
  local message="$1"
  echo "${message}" | tee -a "${failures_file}" >&2
}

{
  printf "burst_rate\tstatus\tk6_run_id\ttotal_429_rate\tedge_429_rate\tbackend_429_rate\tbackend_429_count\tk6_503_count\tnginx_5xx_count\tnginx_499_count\taccepted_count\taccepted_p95_ms\tretry_after_p95_ms\treject_streak_max\tsummary_json\tnginx_aggregate_json\tnginx_aggregate_tsv\tnginx_aggregate_md\n"

  while IFS=$'\t' read -r burst_rate k6_run_id summary_file aggregate_json aggregate_tsv aggregate_md extra; do
    if [[ -z "${burst_rate}" && -z "${k6_run_id}" ]]; then
      continue
    fi
    if [[ -n "${extra:-}" ]]; then
      echo "invalid input TSV row for burst ${burst_rate}: too many columns" >&2
      exit 1
    fi
    if [[ ! "${burst_rate}" =~ ^[0-9]+$ ]]; then
      echo "invalid burst rate: ${burst_rate}" >&2
      exit 1
    fi
    for file in "${summary_file}" "${aggregate_json}" "${aggregate_tsv}" "${aggregate_md}"; do
      if [[ -z "${file}" || ! -s "${file}" ]]; then
        echo "missing burst ${burst_rate} evidence file: ${file:-empty}" >&2
        exit 1
      fi
    done
    if ! jq -e --arg run_id "${k6_run_id}" '
      type == "array"
      and any(.[]; ((.k6_run_id // "unknown") | tostring) == $run_id)
    ' "${aggregate_json}" >/dev/null; then
      echo "Nginx aggregate has no row for k6_run_id=${k6_run_id}" >&2
      exit 1
    fi

    total_429_rate="$(metric_required "${summary_file}" "aquila_transaction_429_rate" "rate")"
    edge_429_rate="$(metric_required "${summary_file}" "aquila_transaction_edge_429_rate" "rate")"
    backend_429_rate="$(metric_required "${summary_file}" "aquila_transaction_backend_429_rate" "rate")"
    backend_429_count="$(metric_optional "${summary_file}" "aquila_transaction_backend_429_count" "count" "0")"
    k6_503_count="$(metric_optional "${summary_file}" "aquila_transaction_503_count" "count" "0")"
    accepted_count="$(metric_optional "${summary_file}" "aquila_transaction_accepted_200_count" "count" "0")"
    accepted_p95_ms="$(accepted_p95_metric "${summary_file}")"
    retry_after_p95_ms="$(metric_optional "${summary_file}" "aquila_transaction_retry_after_sleep_ms" "p(95)" "0")"
    reject_streak_max="$(metric_optional "${summary_file}" "aquila_transaction_retry_after_reject_streak" "max" "0")"
    nginx_499_count="$(nginx_status_count "${aggregate_json}" "${k6_run_id}" "^499$")"
    nginx_5xx_count="$(nginx_status_count "${aggregate_json}" "${k6_run_id}" "^5")"

    status="pass"
    if gt "${burst_rate}" "${promotion_target_rate}"; then
      status="observe"
    fi
    if gt "${k6_503_count}" "0"; then
      status="fail"
      record_failure "burst${burst_rate} gate failed: k6_503_count=${k6_503_count} > 0"
    fi
    if gt "${nginx_5xx_count}" "0"; then
      status="fail"
      record_failure "burst${burst_rate} gate failed: nginx_5xx_count=${nginx_5xx_count} > 0"
    fi
    if gt "${nginx_499_count}" "0"; then
      status="fail"
      record_failure "burst${burst_rate} gate failed: nginx_499_count=${nginx_499_count} > 0"
    fi
    if is_zero "${backend_429_threshold}" && gt "${backend_429_count}" "0"; then
      status="fail"
      record_failure "$(printf 'burst%s gate failed: backend_429_count=%.0f > 0' "${burst_rate}" "${backend_429_count}")"
    fi
    if gt "${backend_429_rate}" "${backend_429_threshold}"; then
      status="fail"
      record_failure "$(printf 'burst%s gate failed: backend_429_rate=%.6f > %.6f' "${burst_rate}" "${backend_429_rate}" "${backend_429_threshold}")"
    fi
    if [[ "${burst_rate}" == "${promotion_target_rate}" ]]; then
      if gt "${total_429_rate}" "${target_429_threshold}"; then
        status="fail"
        record_failure "$(printf 'burst%s gate failed: total_429_rate=%.6f > %.6f' "${promotion_target_rate}" "${total_429_rate}" "${target_429_threshold}")"
      fi
      if gt "${edge_429_rate}" "${target_429_threshold}"; then
        status="fail"
        record_failure "$(printf 'burst%s gate failed: edge_429_rate=%.6f > %.6f' "${promotion_target_rate}" "${edge_429_rate}" "${target_429_threshold}")"
      fi
      if gte "${accepted_p95_ms}" "${accepted_p95_threshold_ms}"; then
        status="fail"
        record_failure "$(printf 'burst%s gate failed: accepted_p95_ms=%.3f >= %.3f' "${promotion_target_rate}" "${accepted_p95_ms}" "${accepted_p95_threshold_ms}")"
      fi
    fi

    printf "%s\t%s\t%s\t%.6f\t%.6f\t%.6f\t%.0f\t%.0f\t%.0f\t%.0f\t%.0f\t%.3f\t%.3f\t%.0f\t%s\t%s\t%s\t%s\n" \
      "${burst_rate}" \
      "${status}" \
      "${k6_run_id}" \
      "${total_429_rate}" \
      "${edge_429_rate}" \
      "${backend_429_rate}" \
      "${backend_429_count}" \
      "${k6_503_count}" \
      "${nginx_5xx_count}" \
      "${nginx_499_count}" \
      "${accepted_count}" \
      "${accepted_p95_ms}" \
      "${retry_after_p95_ms}" \
      "${reject_streak_max}" \
      "${summary_file}" \
      "${aggregate_json}" \
      "${aggregate_tsv}" \
      "${aggregate_md}"
  done < <(tail -n +2 "${input_tsv}")
} >"${raw_tsv}"

{
  head -n 1 "${raw_tsv}"
  for required_rate in "${required_rate_items[@]}"; do
    row_count="$(awk -F '\t' -v rate="${required_rate}" 'NR > 1 && $1 == rate { count += 1 } END { print count + 0 }' "${raw_tsv}")"
    if [[ "${row_count}" == "0" ]]; then
      echo "missing burst rate evidence: ${required_rate}" >&2
      exit 1
    fi
    if [[ "${row_count}" != "1" ]]; then
      echo "duplicate burst rate evidence: ${required_rate}" >&2
      exit 1
    fi
    awk -F '\t' -v rate="${required_rate}" 'NR > 1 && $1 == rate { print }' "${raw_tsv}"
  done
} >"${summary_tsv}"

promotion_target_row_count="$(awk -F '\t' -v rate="${promotion_target_rate}" 'NR > 1 && $1 == rate { count += 1 } END { print count + 0 }' "${summary_tsv}")"
if [[ "${promotion_target_row_count}" == "0" ]]; then
  echo "missing promotion target burst rate evidence: ${promotion_target_rate}" >&2
  exit 1
fi

jq -Rn --arg name "${name}" --arg input_tsv "${input_tsv}" --arg required_burst_rates "${required_burst_rates}" --arg promotion_target_rate "${promotion_target_rate}" '
  def number_or_string:
    if test("^-?[0-9]+([.][0-9]+)?$") then tonumber else . end;
  (input | split("\t")) as $headers
  | [
      inputs
      | split("\t") as $row
      | reduce range(0; $headers | length) as $i (
          {};
          .[$headers[$i]] = (($row[$i] // "") | number_or_string)
        )
    ] as $items
  | {
      name: $name,
      input_tsv: $input_tsv,
      required_burst_rates: ($required_burst_rates | split(",") | map(tonumber)),
      promotion_target_rate: ($promotion_target_rate | tonumber),
      items: $items
    }
' <"${summary_tsv}" >"${summary_json}"

promotion_target_status="$(awk -F '\t' -v rate="${promotion_target_rate}" 'NR > 1 && $1 == rate { print $2 }' "${summary_tsv}")"
if [[ -s "${failures_file}" ]]; then
  promotion_target_gate="fail"
else
  promotion_target_gate="${promotion_target_status:-missing}"
fi

matrix_table="$(awk -F '\t' '
  BEGIN {
    print "| Burst | Status | Total 429 | Edge 429 | Backend 429 | k6 503 | Nginx 5xx | Nginx 499 | Accepted p95 ms | Retry-after p95 ms | Reject streak max |"
    print "| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $4, $5, $6, $8, $9, $10, $12, $13, $14
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# OCI k6 Burst Reject Curve Matrix

## Summary

- matrix input TSV: ${input_tsv}
- required burst rates: ${required_burst_rates}
- promotion target rate: ${promotion_target_rate}
- burst${promotion_target_rate} gate: ${promotion_target_gate}
- rows above target: overload observation rows; 429 ceiling is not applied

## Matrix

${matrix_table}

## Gate

- burst${promotion_target_rate}: total/edge 429 <= ${target_429_threshold}, $(backend_gate_label), k6 503 = 0, nginx 5xx = 0, nginx 499 = 0, accepted p95 < ${accepted_p95_threshold_ms}ms
- all bursts: $(backend_gate_label), k6 503 = 0, nginx 5xx = 0, nginx 499 = 0
- rows above target: reject curve observation only for 429 budget; still fails on 5xx/499

## Artifacts

- summary TSV: ${summary_tsv}
- summary JSON: ${summary_json}
REPORT

echo "${report_md}"
if [[ -s "${failures_file}" ]]; then
  exit 1
fi
