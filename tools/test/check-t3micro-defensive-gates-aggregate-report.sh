#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-t3micro-defensive-gates-aggregate-report.sh"

echo "[t3micro-defensive-aggregate] shell syntax"
bash -n "${script}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

capacity="${temp_dir}/capacity.md"
capacity_summary="${temp_dir}/capacity-summary.tsv"
capacity_context="${temp_dir}/capacity-run-context.env"
sse="${temp_dir}/sse.md"
admission="${temp_dir}/admission.tsv"
outbox="${temp_dir}/outbox.tsv"
k6="${temp_dir}/k6-summary.md"
memory="${temp_dir}/memory.tsv"
capacity_prereq="${temp_dir}/capacity-prerequisite-failure.env"

cat >"${capacity}" <<'MD'
# capacity
- capacity smoke status: 0
- repeat: 3
- peakCpuPercent: 82.50
- peakMemoryMiB: 712.00
MD
cat >"${capacity_context}" <<'ENV'
CAPACITY_RUN_PURPOSE=capacity
CAPACITY_NAME=aggregate-check-capacity
CAPACITY_GENERATOR_MODE=docker-context
CAPACITY_K6_DOCKER_CONTEXT=capacity-k6-remote
CAPACITY_K6_REMOTE_BASE_URL=http://192.0.2.20:8080
CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL=http://192.0.2.20:9090/api/v1/write
ENV
printf "phase\tprofile\tstatus\tadmission\tvus\tbackend_cpus\tbackend_memory\tpostgres_cpus\tpostgres_memory\tdb_pool\toverload_mode\tduration\thttp_failed_rate\thttp_reqs\ttransaction_429_rate\thot_first_p95_ms\thot_cursor_p95_ms\tcold_first_p95_ms\tcold_cursor_p95_ms\tbackend_cpu_percent\tpostgres_cpu_percent\thikari_active\thikari_pending\thikari_max\tlog_path\tsummary_json\nsingle-host\tsingle-host-default\t0\t3\t8\t0.40\t512m\t0.60\t384m\t4\ttrue\t1m\t0\t1200\t0.010000\t210\t220\t610\t640\t82.50\t41.00\t3\t0\t4\tcapacity.log\tcapacity-summary.json\nlong-soak\tlong-soak-high-traffic\t0\t8\t8\t0.80\t640m\t0.60\t384m\t6\tfalse\t30m\t0\t12000\t0.000000\t230\t240\t650\t680\t88.25\t45.00\t5\t0\t6\tsoak.log\tsoak-summary.json\n" >"${capacity_summary}"

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
printf "component\tpeak_memory_mib\nbackend\t402.20\npostgres\t72.20\nprometheus\t54.10\npostgres-exporter\t3.20\n" >"${memory}"
cat >"${k6}" <<'MD'
# k6
- transaction 429 rate: 0.125
- hot first p95 ms: 220
- hot cursor p95 ms: 240
- cold first p95 ms: 640
- cold cursor p95 ms: 680
MD
cat >"${capacity_prereq}" <<'ENV'
CAPACITY_PREREQUISITE_STATUS=failed
CAPACITY_PREREQUISITE_FAILURE_REASON=missing-required-env
CAPACITY_PREREQUISITE_MISSING_VARS=CAPACITY_K6_DOCKER_CONTEXT,CAPACITY_K6_REMOTE_BASE_URL,CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL
CAPACITY_PREREQUISITE_GRADE=capacity
ENV

echo "[t3micro-defensive-aggregate] plan"
plan="$(
  T3MICRO_AGGREGATE_NAME=aggregate-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_CAPACITY_RESULT_MD="${capacity}" \
  T3MICRO_CAPACITY_SUMMARY_TSV="${capacity_summary}" \
  T3MICRO_CAPACITY_RUN_CONTEXT_ENV="${capacity_context}" \
  T3MICRO_CAPACITY_PREREQUISITE_ENV="${capacity_prereq}" \
  T3MICRO_SSE_RESULT_MD="${sse}" \
  T3MICRO_ADMISSION_SUMMARY_TSV="${admission}" \
  T3MICRO_OUTBOX_SUMMARY_TSV="${outbox}" \
  T3MICRO_K6_SUMMARY_MD="${k6}" \
  T3MICRO_MEMORY_SUMMARY_TSV="${memory}" \
  T3MICRO_AGGREGATE_REQUIRED_GATES=capacity,sse,admission,outbox,k6,memory \
    "${script}" --print-plan
)"
grep -F "output=${temp_dir}/aggregate-check.md" <<<"${plan}" >/dev/null
grep -F "capacity=${capacity}" <<<"${plan}" >/dev/null
grep -F "capacity_summary=${capacity_summary}" <<<"${plan}" >/dev/null
grep -F "capacity_run_context=${capacity_context}" <<<"${plan}" >/dev/null
grep -F "capacity_prerequisite=${capacity_prereq}" <<<"${plan}" >/dev/null
grep -F "admission=${admission}" <<<"${plan}" >/dev/null
grep -F "k6=${k6}" <<<"${plan}" >/dev/null
grep -F "k6_profile_selector=representative" <<<"${plan}" >/dev/null
grep -F "memory=${memory}" <<<"${plan}" >/dev/null
grep -F "auto_inputs=true" <<<"${plan}" >/dev/null
grep -F "auto_input_run_id=missing" <<<"${plan}" >/dev/null
grep -F "auto_input_prefix=aggregate-check" <<<"${plan}" >/dev/null
grep -F "required_gates=capacity,sse,admission,outbox,k6,memory" <<<"${plan}" >/dev/null
grep -F "total_memory_budget_mib=900" <<<"${plan}" >/dev/null

auto_root="${temp_dir}/auto"
mkdir -p "${auto_root}/docs/performance-results" "${auto_root}/build/reports/admission/run" "${auto_root}/build/reports/outbox/run" "${auto_root}/build/reports/k6" "${auto_root}/build/reports/t3micro"
auto_capacity="${auto_root}/docs/performance-results/docker-t3micro-capacity-aggregate-auto-check.md"
auto_capacity_dir="${auto_root}/build/reports/k6/aggregate-auto-check-capacity"
auto_capacity_summary="${auto_capacity_dir}/capacity-summary.tsv"
auto_capacity_context="${auto_capacity_dir}/capacity-run-context.env"
auto_sse="${auto_root}/docs/performance-results/aggregate-auto-check-sse-reconnect.md"
auto_admission="${auto_root}/build/reports/admission/aggregate-auto-check-admission/http-admission-summary.tsv"
auto_outbox="${auto_root}/build/reports/outbox/aggregate-auto-check-outbox/outbox-provider-backlog-summary.tsv"
auto_k6="${auto_root}/build/reports/k6/transaction-100m-aggregate-auto-check-vu3-summary.md"
auto_k6_burst_failed="${auto_root}/build/reports/k6/transaction-100m-aggregate-auto-check-burst-vu128-summary.md"
auto_memory="${auto_root}/build/reports/t3micro/aggregate-auto-check-memory-summary.tsv"
cp "${capacity}" "${auto_capacity}"
mkdir -p "${auto_capacity_dir}" "$(dirname "${auto_admission}")" "$(dirname "${auto_outbox}")"
cp "${capacity_summary}" "${auto_capacity_summary}"
cp "${capacity_context}" "${auto_capacity_context}"
cp "${sse}" "${auto_sse}"
cp "${admission}" "${auto_admission}"
cp "${outbox}" "${auto_outbox}"
cat >"${auto_k6}" <<'MD'
# k6 pass
- resultPurpose: smoke
- resultStatus: 0
- run id: transaction-100m-aggregate-auto-check-vu3
- scenario mode: constant-vus
- checks rate: 1
- transaction 429 rate: 0
- transaction 503 rate: 0
- hot first p95 ms: 220
- hot cursor p95 ms: 240
- cold first p95 ms: 640
- cold cursor p95 ms: 680
MD
cat >"${auto_k6_burst_failed}" <<'MD'
# k6 failed burst
- resultPurpose: smoke
- resultStatus: 1
- run id: transaction-100m-aggregate-auto-check-burst-vu128
- scenario mode: burst
- burst rate: 256/1s
- pre allocated VUs: 128
- max VUs: 128
- checks rate: 1
- transaction 429 rate: 0.017
- transaction 503 rate: 0
- hot first p95 ms: 260
- hot cursor p95 ms: 270
- cold first p95 ms: 690
- cold cursor p95 ms: 710
MD
cp "${memory}" "${auto_memory}"
touch "${auto_k6}" "${auto_k6_burst_failed}"
stale_capacity="${auto_root}/docs/performance-results/stale-20260427-docker-t3micro-capacity.md"
stale_capacity_dir="${auto_root}/build/reports/k6/stale-20260427-capacity"
stale_capacity_summary="${stale_capacity_dir}/capacity-summary.tsv"
stale_capacity_context="${stale_capacity_dir}/capacity-run-context.env"
stale_sse="${auto_root}/docs/performance-results/stale-20260427-sse-reconnect.md"
stale_k6="${auto_root}/build/reports/k6/stale-20260427-k6-summary.md"
mkdir -p "${stale_capacity_dir}"
cp "${capacity}" "${stale_capacity}"
cp "${capacity_summary}" "${stale_capacity_summary}"
cp "${capacity_context}" "${stale_capacity_context}"
cp "${sse}" "${stale_sse}"
cp "${k6}" "${stale_k6}"

echo "[t3micro-defensive-aggregate] auto inputs"
auto_plan="$(
  T3MICRO_AGGREGATE_NAME=aggregate-auto-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_AGGREGATE_SEARCH_ROOTS="${auto_root}/docs/performance-results ${auto_root}/build/reports" \
    "${script}" --print-plan
)"
grep -F "capacity=${auto_capacity}" <<<"${auto_plan}" >/dev/null
grep -F "capacity_summary=${auto_capacity_summary}" <<<"${auto_plan}" >/dev/null
grep -F "capacity_run_context=${auto_capacity_context}" <<<"${auto_plan}" >/dev/null
grep -F "sse=${auto_sse}" <<<"${auto_plan}" >/dev/null
grep -F "admission=${auto_admission}" <<<"${auto_plan}" >/dev/null
grep -F "outbox=${auto_outbox}" <<<"${auto_plan}" >/dev/null
grep -F "k6=${auto_k6}" <<<"${auto_plan}" >/dev/null
grep -F "memory=${auto_memory}" <<<"${auto_plan}" >/dev/null
if grep -F "burst-vu128" <<<"${auto_plan}" >/dev/null; then
  echo "auto input unexpectedly selected failed burst k6 summary as representative" >&2
  exit 1
fi
if grep -F "stale-20260427" <<<"${auto_plan}" >/dev/null; then
  echo "auto input unexpectedly selected stale artifact outside aggregate prefix" >&2
  exit 1
fi

alias_auto_root="${temp_dir}/alias-auto"
mkdir -p "${alias_auto_root}/build/reports/t3micro" "${alias_auto_root}/build/reports/k6"
alias_memory="${alias_auto_root}/build/reports/t3micro/post-observability-20260428-memory-summary.tsv"
alias_k6="${alias_auto_root}/build/reports/k6/transaction-100m-post-observability-20260428-vu3-summary.md"
cp "${memory}" "${alias_memory}"
cp "${auto_k6}" "${alias_k6}"
alias_plan="$(
  T3MICRO_AGGREGATE_NAME=t3micro-defensive-post-observability-20260428 \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_AGGREGATE_SEARCH_ROOTS="${alias_auto_root}/build/reports" \
    "${script}" --print-plan
)"
grep -F "k6=${alias_k6}" <<<"${alias_plan}" >/dev/null
grep -F "memory=${alias_memory}" <<<"${alias_plan}" >/dev/null

echo "[t3micro-defensive-aggregate] required inputs"
if T3MICRO_AGGREGATE_NAME=aggregate-required-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_AGGREGATE_AUTO_INPUTS=false \
  T3MICRO_AGGREGATE_REQUIRED_GATES=capacity \
    "${script}" --print-plan >/dev/null 2>&1; then
  echo "required capacity input unexpectedly passed when missing" >&2
  exit 1
fi
if T3MICRO_AGGREGATE_NAME=aggregate-required-smoke-only-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_AGGREGATE_AUTO_INPUTS=false \
  T3MICRO_CAPACITY_RESULT_MD="${capacity}" \
  T3MICRO_AGGREGATE_REQUIRED_GATES=capacity \
    "${script}" --print-plan >/dev/null 2>&1; then
  echo "required capacity input unexpectedly accepted local smoke markdown" >&2
  exit 1
fi
bad_context="${temp_dir}/bad-capacity-run-context.env"
sed 's/CAPACITY_GENERATOR_MODE=docker-context/CAPACITY_GENERATOR_MODE=local/' "${capacity_context}" >"${bad_context}"
if T3MICRO_AGGREGATE_NAME=aggregate-required-local-context-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_CAPACITY_SUMMARY_TSV="${capacity_summary}" \
  T3MICRO_CAPACITY_RUN_CONTEXT_ENV="${bad_context}" \
  T3MICRO_AGGREGATE_REQUIRED_GATES=capacity \
    "${script}" --print-plan >/dev/null 2>&1; then
  echo "required capacity input unexpectedly accepted local generator context" >&2
  exit 1
fi
required_prereq_output="${temp_dir}/aggregate-required-prereq-failed-check.md"
if T3MICRO_AGGREGATE_NAME=aggregate-required-prereq-failed-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_CAPACITY_PREREQUISITE_ENV="${capacity_prereq}" \
  T3MICRO_AGGREGATE_REQUIRED_GATES=capacity \
    "${script}" >/dev/null 2>&1; then
  echo "required capacity prerequisite failure unexpectedly exited zero" >&2
  exit 1
fi
test -f "${required_prereq_output}"
grep -F "| capacity prerequisite | failed | n/a | n/a | reason=missing-required-env missing=CAPACITY_K6_DOCKER_CONTEXT,CAPACITY_K6_REMOTE_BASE_URL,CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL | ${capacity_prereq} |" "${required_prereq_output}" >/dev/null

echo "[t3micro-defensive-aggregate] memory budget"
if T3MICRO_AGGREGATE_NAME=aggregate-memory-fail-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_MEMORY_SUMMARY_TSV="${memory}" \
  T3MICRO_TOTAL_MEMORY_BUDGET_MIB=400 \
    "${script}" --dry-run >/dev/null 2>&1; then
  echo "memory budget overflow unexpectedly passed" >&2
  exit 1
fi

echo "[t3micro-defensive-aggregate] report"
output="$(
  T3MICRO_AGGREGATE_NAME=aggregate-check \
  T3MICRO_AGGREGATE_OUTPUT_DIR="${temp_dir}" \
  T3MICRO_CAPACITY_RESULT_MD="${capacity}" \
  T3MICRO_CAPACITY_SUMMARY_TSV="${capacity_summary}" \
  T3MICRO_CAPACITY_RUN_CONTEXT_ENV="${capacity_context}" \
  T3MICRO_CAPACITY_PREREQUISITE_ENV="${capacity_prereq}" \
  T3MICRO_SSE_RESULT_MD="${sse}" \
  T3MICRO_ADMISSION_SUMMARY_TSV="${admission}" \
  T3MICRO_OUTBOX_SUMMARY_TSV="${outbox}" \
  T3MICRO_K6_SUMMARY_MD="${k6}" \
  T3MICRO_MEMORY_SUMMARY_TSV="${memory}" \
  T3MICRO_AGGREGATE_REQUIRED_GATES=capacity,sse,admission,outbox,k6,memory \
    "${script}"
)"
output="$(tail -1 <<<"${output}")"
test "${output}" = "${temp_dir}/aggregate-check.md"
grep -F "| capacity smoke | 0 | 82.50 | 712.00 | repeat=3 | ${capacity} |" "${output}" >/dev/null
grep -F "| capacity prerequisite | failed | n/a | n/a | reason=missing-required-env missing=CAPACITY_K6_DOCKER_CONTEXT,CAPACITY_K6_REMOTE_BASE_URL,CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL | ${capacity_prereq} |" "${output}" >/dev/null
grep -F "| capacity | pass | 82.50 | n/a | 429Rate=0.010000 hikariPending=0 profiles=1 | ${capacity_summary} |" "${output}" >/dev/null
grep -F "| capacity soak | pass | 88.25 | n/a | 429Rate=0.000000 hikariPending=0 profiles=1 | ${capacity_summary} |" "${output}" >/dev/null
grep -F "| sse reconnect | 0 | 61.00 | 512.00 | clients=8 rounds=3 | ${sse} |" "${output}" >/dev/null
grep -F "rejected=4 failed_rate=0.000000" "${output}" >/dev/null
grep -F "lag=1 failed=0 dlq=0" "${output}" >/dev/null
grep -F "hotFirstP95=220 hotCursorP95=240 coldFirstP95=640 coldCursorP95=680 429Rate=0.125" "${output}" >/dev/null
grep -F "| total memory | pass | n/a | 531.70 | budget=900 source=${memory} | ${memory} |" "${output}" >/dev/null
