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

echo "[t3micro-defensive-aggregate] plan"
plan="$(
  T3MICRO_AGGREGATE_NAME=aggregate-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_CAPACITY_RESULT_MD="${capacity}" \
  T3MICRO_SSE_RESULT_MD="${sse}" \
  T3MICRO_ADMISSION_SUMMARY_TSV="${admission}" \
  T3MICRO_OUTBOX_SUMMARY_TSV="${outbox}" \
    "${script}" --print-plan
)"
grep -F "output=${temp_dir}/aggregate-check.md" <<<"${plan}" >/dev/null
grep -F "capacity=${capacity}" <<<"${plan}" >/dev/null
grep -F "admission=${admission}" <<<"${plan}" >/dev/null

echo "[t3micro-defensive-aggregate] report"
output="$(
  T3MICRO_AGGREGATE_NAME=aggregate-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_CAPACITY_RESULT_MD="${capacity}" \
  T3MICRO_SSE_RESULT_MD="${sse}" \
  T3MICRO_ADMISSION_SUMMARY_TSV="${admission}" \
  T3MICRO_OUTBOX_SUMMARY_TSV="${outbox}" \
    "${script}"
)"
output="$(tail -1 <<<"${output}")"
test "${output}" = "${temp_dir}/aggregate-check.md"
grep -F "| capacity | 0 | 82.50 | 712.00 | repeat=3 | ${capacity} |" "${output}" >/dev/null
grep -F "| sse reconnect | 0 | 61.00 | 512.00 | clients=8 rounds=3 | ${sse} |" "${output}" >/dev/null
grep -F "rejected=4 failed_rate=0.000000" "${output}" >/dev/null
grep -F "lag=1 failed=0 dlq=0" "${output}" >/dev/null
