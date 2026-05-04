#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-oci-k6-burst-reject-curve-matrix.sh"

echo "[oci-k6-burst-reject-curve-matrix] shell syntax"
bash -n "${runner}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

write_summary() {
  local rate="$1"
  local total_429="$2"
  local edge_429="$3"
  local backend_429="$4"
  local backend_429_count="$5"
  local k6_503_count="$6"
  local accepted_count="$7"
  local accepted_p95="$8"
  local retry_after_p95="$9"
  local reject_streak_max="${10}"
  local summary_json="${temp_dir}/burst-${rate}-summary.json"

  cat >"${summary_json}" <<JSON
{
  "metrics": {
    "http_req_duration": {"values": {"p(95)": ${accepted_p95}}},
    "aquila_transaction_429_rate": {"values": {"rate": ${total_429}}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": ${edge_429}}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": ${backend_429}}},
    "aquila_transaction_backend_429_count": {"values": {"count": ${backend_429_count}}},
    "aquila_transaction_503_count": {"values": {"count": ${k6_503_count}}},
    "aquila_transaction_accepted_200_count": {"values": {"count": ${accepted_count}}},
    "aquila_transaction_retry_after_sleep_ms": {"values": {"p(95)": ${retry_after_p95}}},
    "aquila_transaction_retry_after_reject_streak": {"values": {"max": ${reject_streak_max}}}
  }
}
JSON
}

write_aggregate() {
  local rate="$1"
  local run_id="$2"
  local edge_429_count="$3"
  local nginx_499_count="$4"
  local nginx_5xx_count="$5"
  local aggregate_tsv="${temp_dir}/burst-${rate}-nginx.tsv"
  local aggregate_json="${temp_dir}/burst-${rate}-nginx.json"
  local aggregate_md="${temp_dir}/burst-${rate}-nginx.md"

  cat >"${aggregate_tsv}" <<TSV
k6_run_id	status	limit_req_status	upstream_status	reject_source	reject_reason	upstream_reject_source	upstream_reject_reason	count	delayed_count	rejected_count	request_p95_ms	upstream_p95_ms
${run_id}	200	PASSED	200	none	none	none	none	100	0	0	8.000	7.000
${run_id}	429	REJECTED	none	nginx-edge	edge-rate-limit	none	edge-rate-limit	${edge_429_count}	0	${edge_429_count}	1.000	0.000
${run_id}	499	DELAYED	none	none	none	none	none	${nginx_499_count}	${nginx_499_count}	0	30000.000	0.000
${run_id}	503	PASSED	503	backend	backend-unavailable	backend	backend-unavailable	${nginx_5xx_count}	0	0	20.000	19.000
TSV

  jq -Rn '
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
      ]
  ' <"${aggregate_tsv}" >"${aggregate_json}"

  cat >"${aggregate_md}" <<MD
# Burst ${rate} Nginx Aggregate

- k6_run_id=${run_id}
- edge_429_count=${edge_429_count}
- nginx_499_count=${nginx_499_count}
- nginx_5xx_count=${nginx_5xx_count}
MD
}

input_tsv="${temp_dir}/burst-reject-curve-input.tsv"
{
  printf "burst_rate\tk6_run_id\tsummary_json\tnginx_aggregate_json\tnginx_aggregate_tsv\tnginx_aggregate_md\n"
  for rate in 32 48 64 80 96; do
    case "${rate}" in
      32)
        write_summary "${rate}" 0.00 0.00 0.00 0 0 5760 5.1 0 0
        write_aggregate "${rate}" "matrix-burst32" 0 0 0
        ;;
      48)
        write_summary "${rate}" 0.03 0.030 0.000 0 0 5700 6.2 25 1
        write_aggregate "${rate}" "matrix-burst48" 168 0 0
        ;;
      64)
        write_summary "${rate}" 0.06 0.060 0.000 0 0 5650 7.5 40 2
        write_aggregate "${rate}" "matrix-burst64" 465 0 0
        ;;
      80)
        write_summary "${rate}" 0.08 0.080 0.000 0 0 5400 11.7 55 4
        write_aggregate "${rate}" "matrix-burst80" 610 0 0
        ;;
      96)
        write_summary "${rate}" 0.35 0.350 0.000 0 0 5100 18.3 80 6
        write_aggregate "${rate}" "matrix-burst96" 1840 0 0
        ;;
    esac
    printf "%s\tmatrix-burst%s\t%s\t%s\t%s\t%s\n" \
      "${rate}" \
      "${rate}" \
      "${temp_dir}/burst-${rate}-summary.json" \
      "${temp_dir}/burst-${rate}-nginx.json" \
      "${temp_dir}/burst-${rate}-nginx.tsv" \
      "${temp_dir}/burst-${rate}-nginx.md"
  done
} >"${input_tsv}"

output_dir="${temp_dir}/output"

echo "[oci-k6-burst-reject-curve-matrix] print plan"
plan="$(
  OCI_K6_BURST_MATRIX_NAME=burst-matrix-check \
  OCI_K6_BURST_MATRIX_INPUT_TSV="${input_tsv}" \
  OCI_K6_BURST_MATRIX_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=burst-matrix-check" <<<"${plan}" >/dev/null
grep -F "required_burst_rates=32,48,64,80,96" <<<"${plan}" >/dev/null
grep -F "promotion_target_rate=80" <<<"${plan}" >/dev/null
grep -F "gate=burst80 total/edge 429 <= 0.10, backend 429 = 0, k6 503 = 0, nginx 5xx = 0, nginx 499 = 0, accepted p95 < 100ms" <<<"${plan}" >/dev/null
grep -F "summary_tsv=${output_dir}/burst-matrix-check-burst-reject-curve.tsv" <<<"${plan}" >/dev/null

echo "[oci-k6-burst-reject-curve-matrix] report"
output="$(
  OCI_K6_BURST_MATRIX_NAME=burst-matrix-check \
  OCI_K6_BURST_MATRIX_INPUT_TSV="${input_tsv}" \
  OCI_K6_BURST_MATRIX_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/burst-matrix-check-burst-reject-curve.tsv"
summary_json="${output_dir}/burst-matrix-check-burst-reject-curve.json"
test "${report_md}" = "${output_dir}/burst-matrix-check-burst-reject-curve.md"
grep -F $'burst_rate\tstatus\tk6_run_id\ttotal_429_rate\tedge_429_rate\tbackend_429_rate\tbackend_429_count\tk6_503_count\tnginx_5xx_count\tnginx_499_count\taccepted_count\taccepted_p95_ms\tretry_after_p95_ms\treject_streak_max\tsummary_json\tnginx_aggregate_json\tnginx_aggregate_tsv\tnginx_aggregate_md' "${summary_tsv}" >/dev/null
grep -F $'64\tpass\tmatrix-burst64\t0.060000\t0.060000\t0.000000\t0\t0\t0\t0\t5650\t7.500\t40.000\t2' "${summary_tsv}" >/dev/null
grep -F $'80\tpass\tmatrix-burst80\t0.080000\t0.080000\t0.000000\t0\t0\t0\t0\t5400\t11.700\t55.000\t4' "${summary_tsv}" >/dev/null
grep -F $'96\tobserve\tmatrix-burst96\t0.350000\t0.350000\t0.000000\t0\t0\t0\t0\t5100\t18.300\t80.000\t6' "${summary_tsv}" >/dev/null
grep -F "burst80 gate: pass" "${report_md}" >/dev/null
grep -F "promotion target rate: 80" "${report_md}" >/dev/null
grep -F "rows above target: overload observation rows; 429 ceiling is not applied" "${report_md}" >/dev/null
grep -F "matrix input TSV: ${input_tsv}" "${report_md}" >/dev/null
jq -e '.items | length == 5' "${summary_json}" >/dev/null
jq -e '.promotion_target_rate == 80' "${summary_json}" >/dev/null
jq -e '.items[] | select(.burst_rate == 64 and .status == "pass" and .edge_429_rate == 0.06 and .backend_429_count == 0 and .nginx_499_count == 0)' "${summary_json}" >/dev/null
jq -e '.items[] | select(.burst_rate == 80 and .status == "pass" and .total_429_rate == 0.08)' "${summary_json}" >/dev/null
jq -e '.items[] | select(.burst_rate == 96 and .status == "observe" and .total_429_rate == 0.35)' "${summary_json}" >/dev/null

echo "[oci-k6-burst-reject-curve-matrix] missing required rate fails"
missing_input="${temp_dir}/missing-input.tsv"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 || $1 != "48" { print }' "${input_tsv}" >"${missing_input}"
if OCI_K6_BURST_MATRIX_NAME=burst-matrix-missing \
  OCI_K6_BURST_MATRIX_INPUT_TSV="${missing_input}" \
  OCI_K6_BURST_MATRIX_OUTPUT_DIR="${temp_dir}/missing-output" \
    "${runner}" >"${temp_dir}/missing.log" 2>&1; then
  echo "missing burst rate evidence unexpectedly passed" >&2
  exit 1
fi
grep -F "missing burst rate evidence: 48" "${temp_dir}/missing.log" >/dev/null

echo "[oci-k6-burst-reject-curve-matrix] custom burst64 promotion target gate fails"
bad_summary="${temp_dir}/burst-64-summary-bad.json"
jq '.metrics.aquila_transaction_429_rate.values.rate = 0.11' \
  "${temp_dir}/burst-64-summary.json" >"${bad_summary}"
bad_input="${temp_dir}/bad-input.tsv"
awk -F '\t' -v bad_summary="${bad_summary}" '
  BEGIN { OFS = FS }
  NR == 1 { print; next }
  $1 == "64" { $3 = bad_summary }
  { print }
' "${input_tsv}" >"${bad_input}"
if OCI_K6_BURST_MATRIX_NAME=burst-matrix-bad \
  OCI_K6_BURST_MATRIX_INPUT_TSV="${bad_input}" \
  OCI_K6_BURST_MATRIX_OUTPUT_DIR="${temp_dir}/bad-output" \
  OCI_K6_BURST_MATRIX_PROMOTION_TARGET_RATE=64 \
    "${runner}" >"${temp_dir}/bad.log" 2>&1; then
  echo "burst64 gate unexpectedly passed" >&2
  exit 1
fi
grep -F "burst64 gate failed: total_429_rate=0.110000 > 0.100000" "${temp_dir}/bad.log" >/dev/null

echo "[oci-k6-burst-reject-curve-matrix] burst64 backend hard-zero fails"
backend_bad_summary="${temp_dir}/burst-64-summary-backend-bad.json"
jq '.metrics.aquila_transaction_backend_429_rate.values.rate = 0.0002 | .metrics.aquila_transaction_backend_429_count.values.count = 1' \
  "${temp_dir}/burst-64-summary.json" >"${backend_bad_summary}"
backend_bad_input="${temp_dir}/backend-bad-input.tsv"
awk -F '\t' -v bad_summary="${backend_bad_summary}" '
  BEGIN { OFS = FS }
  NR == 1 { print; next }
  $1 == "64" { $3 = bad_summary }
  { print }
' "${input_tsv}" >"${backend_bad_input}"
if OCI_K6_BURST_MATRIX_NAME=burst-matrix-backend-bad \
  OCI_K6_BURST_MATRIX_INPUT_TSV="${backend_bad_input}" \
  OCI_K6_BURST_MATRIX_OUTPUT_DIR="${temp_dir}/backend-bad-output" \
    "${runner}" >"${temp_dir}/backend-bad.log" 2>&1; then
  echo "burst matrix unexpectedly passed backend 429 hard-zero violation" >&2
  exit 1
fi
grep -F "burst64 gate failed: backend_429_count=1 > 0" "${temp_dir}/backend-bad.log" >/dev/null

echo "[oci-k6-burst-reject-curve-matrix] burst80 promotion target gate fails"
target80_bad_summary="${temp_dir}/burst-80-summary-bad.json"
jq '.metrics.aquila_transaction_429_rate.values.rate = 0.22 | .metrics.aquila_transaction_edge_429_rate.values.rate = 0.216' \
  "${temp_dir}/burst-80-summary.json" >"${target80_bad_summary}"
target80_bad_input="${temp_dir}/target80-bad-input.tsv"
awk -F '\t' -v bad_summary="${target80_bad_summary}" '
  BEGIN { OFS = FS }
  NR == 1 { print; next }
  $1 == "80" { $3 = bad_summary }
  { print }
' "${input_tsv}" >"${target80_bad_input}"
if OCI_K6_BURST_MATRIX_NAME=burst-matrix-target80 \
  OCI_K6_BURST_MATRIX_INPUT_TSV="${target80_bad_input}" \
  OCI_K6_BURST_MATRIX_OUTPUT_DIR="${temp_dir}/target80-output" \
    "${runner}" >"${temp_dir}/target80.log" 2>&1; then
  echo "burst80 promotion target unexpectedly passed" >&2
  exit 1
fi
grep -F "burst80 gate failed: total_429_rate=0.220000 > 0.100000" "${temp_dir}/target80.log" >/dev/null
