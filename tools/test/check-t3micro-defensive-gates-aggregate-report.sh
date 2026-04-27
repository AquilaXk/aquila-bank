#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-t3micro-defensive-gates-aggregate-report.sh"

echo "[t3micro-defensive-aggregate] shell syntax"
bash -n "${script}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

capacity="${temp_dir}/capacity.md"
sse="${temp_dir}/sse.md"
admission="${temp_dir}/admission.tsv"
outbox="${temp_dir}/outbox.tsv"
k6="${temp_dir}/k6-summary.md"

cat >"${capacity}" <<'MD'
# capacity
- capacity smoke status: 0
- repeat: 3
- peakCpuPercent: 82.50
- peakMemoryMiB: 712.00
MD

cat >"${sse}" <<'MD'
# sse
- status: 0
- reconnectClients: 8
- reconnectRounds: 3
- peakCpuPercent: 61.00
- peakMemoryMiB: 512.00
MD

printf "requests\tsuccess_count\trejected_count\tfailed_count\tfailed_rate\tretry_after_count\traw_path\n16\t12\t4\t0\t0.000000\t4\traw.tsv\n" >"${admission}"
printf "lag_seconds\tfailed_count\tquarantined_count\tstale_sending_count\tnotification_lag_count\tdlq_count\tchannel_quarantined_count\toutbox_json\tnotification_json\tchannel_json\n1\t0\t0\t0\t0\t0\t0\toutbox.json\tnotification.json\tchannel.json\n" >"${outbox}"
cat >"${k6}" <<'MD'
# k6
- transaction 429 rate: 0.125
- hot first p95 ms: 220
- hot cursor p95 ms: 240
- cold first p95 ms: 640
- cold cursor p95 ms: 680
MD

echo "[t3micro-defensive-aggregate] plan"
plan="$(
  T3MICRO_AGGREGATE_NAME=aggregate-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_CAPACITY_RESULT_MD="${capacity}" \
  T3MICRO_SSE_RESULT_MD="${sse}" \
  T3MICRO_ADMISSION_SUMMARY_TSV="${admission}" \
  T3MICRO_OUTBOX_SUMMARY_TSV="${outbox}" \
  T3MICRO_K6_SUMMARY_MD="${k6}" \
    "${script}" --print-plan
)"
grep -F "output=${temp_dir}/aggregate-check.md" <<<"${plan}" >/dev/null
grep -F "capacity=${capacity}" <<<"${plan}" >/dev/null
grep -F "admission=${admission}" <<<"${plan}" >/dev/null
grep -F "k6=${k6}" <<<"${plan}" >/dev/null
grep -F "auto_inputs=true" <<<"${plan}" >/dev/null

auto_root="${temp_dir}/auto"
mkdir -p "${auto_root}/docs/performance-results" "${auto_root}/build/reports/admission/run" "${auto_root}/build/reports/outbox/run" "${auto_root}/build/reports/k6"
auto_capacity="${auto_root}/docs/performance-results/docker-t3micro-capacity-auto.md"
auto_sse="${auto_root}/docs/performance-results/sse-reconnect-auto.md"
auto_admission="${auto_root}/build/reports/admission/run/http-admission-summary.tsv"
auto_outbox="${auto_root}/build/reports/outbox/run/outbox-provider-backlog-summary.tsv"
auto_k6="${auto_root}/build/reports/k6/transaction-100m-auto-summary.md"
cp "${capacity}" "${auto_capacity}"
cp "${sse}" "${auto_sse}"
cp "${admission}" "${auto_admission}"
cp "${outbox}" "${auto_outbox}"
cp "${k6}" "${auto_k6}"

echo "[t3micro-defensive-aggregate] auto inputs"
auto_plan="$(
  T3MICRO_AGGREGATE_NAME=aggregate-auto-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_AGGREGATE_SEARCH_ROOTS="${auto_root}/docs/performance-results ${auto_root}/build/reports" \
    "${script}" --print-plan
)"
grep -F "capacity=${auto_capacity}" <<<"${auto_plan}" >/dev/null
grep -F "sse=${auto_sse}" <<<"${auto_plan}" >/dev/null
grep -F "admission=${auto_admission}" <<<"${auto_plan}" >/dev/null
grep -F "outbox=${auto_outbox}" <<<"${auto_plan}" >/dev/null
grep -F "k6=${auto_k6}" <<<"${auto_plan}" >/dev/null

echo "[t3micro-defensive-aggregate] report"
output="$(
  T3MICRO_AGGREGATE_NAME=aggregate-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_CAPACITY_RESULT_MD="${capacity}" \
  T3MICRO_SSE_RESULT_MD="${sse}" \
  T3MICRO_ADMISSION_SUMMARY_TSV="${admission}" \
  T3MICRO_OUTBOX_SUMMARY_TSV="${outbox}" \
  T3MICRO_K6_SUMMARY_MD="${k6}" \
    "${script}"
)"
output="$(tail -1 <<<"${output}")"
test "${output}" = "${temp_dir}/aggregate-check.md"
grep -F "| capacity | 0 | 82.50 | 712.00 | repeat=3 | ${capacity} |" "${output}" >/dev/null
grep -F "| sse reconnect | 0 | 61.00 | 512.00 | clients=8 rounds=3 | ${sse} |" "${output}" >/dev/null
grep -F "rejected=4 failed_rate=0.000000" "${output}" >/dev/null
grep -F "lag=1 failed=0 dlq=0" "${output}" >/dev/null
grep -F "hotFirstP95=220 hotCursorP95=240 coldFirstP95=640 coldCursorP95=680 429Rate=0.125" "${output}" >/dev/null
