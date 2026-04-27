#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-t3micro-defensive-full-aggregate.sh"

echo "[t3micro-defensive-full-aggregate] shell syntax"
bash -n "${script}"

echo "[t3micro-defensive-full-aggregate] print plan"
plan="$(
  T3MICRO_FULL_AGGREGATE_NAME=full-aggregate-check \
  T3MICRO_FULL_BASE_URL=http://localhost:18080 \
  T3MICRO_FULL_K6_REPORT_NAME=full-aggregate-check-k6 \
  T3MICRO_FULL_MEMORY_SUMMARY_TSV=build/reports/t3micro/full-aggregate-check-memory-summary.tsv \
    "${script}" --print-plan
)"
grep -F "name=full-aggregate-check" <<<"${plan}" >/dev/null
grep -F "capacity_result=docs/performance-results/full-aggregate-check-capacity.md" <<<"${plan}" >/dev/null
grep -F "sse_result=docs/performance-results/full-aggregate-check-sse.md" <<<"${plan}" >/dev/null
grep -F "admission_summary=build/reports/admission/full-aggregate-check-admission/http-admission-summary.tsv" <<<"${plan}" >/dev/null
grep -F "outbox_summary=build/reports/outbox/full-aggregate-check-outbox/outbox-provider-backlog-summary.tsv" <<<"${plan}" >/dev/null
grep -F "k6_summary=build/reports/k6/full-aggregate-check-k6-summary.md" <<<"${plan}" >/dev/null
grep -F "memory_summary=build/reports/t3micro/full-aggregate-check-memory-summary.tsv" <<<"${plan}" >/dev/null
grep -F "required_gates=capacity,sse,admission,outbox,k6,memory" <<<"${plan}" >/dev/null

echo "[t3micro-defensive-full-aggregate] dry-run wiring"
dry_run="$(
  T3MICRO_FULL_AGGREGATE_NAME=full-aggregate-check \
  T3MICRO_FULL_BASE_URL=http://localhost:18080 \
  T3MICRO_FULL_MEMORY_SUMMARY_TSV=build/reports/t3micro/full-aggregate-check-memory-summary.tsv \
    "${script}" --dry-run
)"
grep -F "DOCKER_T3MICRO_RESULT_NAME=full-aggregate-check-capacity tools/test/run-docker-t3micro-capacity-smoke.sh" <<<"${dry_run}" >/dev/null
grep -F "SSE_T3MICRO_RESULT_NAME=full-aggregate-check-sse tools/test/run-sse-reconnect-storm-t3micro-gate.sh" <<<"${dry_run}" >/dev/null
grep -F "ADMISSION_NAME=full-aggregate-check-admission ADMISSION_BASE_URL=http://localhost:18080 tools/test/run-defensive-runtime-http-admission-compose.sh" <<<"${dry_run}" >/dev/null
grep -F "OUTBOX_BACKLOG_NAME=full-aggregate-check-outbox OUTBOX_BACKLOG_BASE_URL=http://localhost:18080 tools/test/run-outbox-provider-backlog-local-gate.sh" <<<"${dry_run}" >/dev/null
grep -F "K6_REPORT_NAME=full-aggregate-check-k6 tools/test/run-k6-transaction-100m-loadtest.sh" <<<"${dry_run}" >/dev/null
grep -F "T3MICRO_AGGREGATE_REQUIRED_GATES=capacity,sse,admission,outbox,k6,memory" <<<"${dry_run}" >/dev/null

echo "[t3micro-defensive-full-aggregate] contract"
grep -F "run-docker-t3micro-capacity-smoke.sh" "${script}" >/dev/null
grep -F "run-sse-reconnect-storm-t3micro-gate.sh" "${script}" >/dev/null
grep -F "run-defensive-runtime-http-admission-compose.sh" "${script}" >/dev/null
grep -F "run-outbox-provider-backlog-local-gate.sh" "${script}" >/dev/null
grep -F "run-k6-transaction-100m-loadtest.sh" "${script}" >/dev/null
grep -F "run-t3micro-defensive-gates-aggregate-report.sh" "${script}" >/dev/null

echo "[t3micro-defensive-full-aggregate] invalid input fails"
if T3MICRO_FULL_RUN_CAPACITY=maybe "${script}" --print-plan >/dev/null 2>&1; then
  echo "T3MICRO_FULL_RUN_CAPACITY=maybe unexpectedly succeeded" >&2
  exit 1
fi
if T3MICRO_FULL_REQUIRED_GATES=capacity,unknown "${script}" --print-plan >/dev/null 2>&1; then
  echo "unknown required gate unexpectedly succeeded" >&2
  exit 1
fi
