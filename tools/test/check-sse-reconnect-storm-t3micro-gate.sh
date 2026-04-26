#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-sse-reconnect-storm-t3micro-gate.sh"

echo "[sse-reconnect-t3micro] shell syntax"
bash -n "${script}"

echo "[sse-reconnect-t3micro] print plan"
plan="$(
  SSE_T3MICRO_IMAGE=local-java21 \
  SSE_T3MICRO_CPUS=2 \
  SSE_T3MICRO_MEMORY=1024m \
  SSE_T3MICRO_MEMORY_SWAP=1024m \
  SSE_T3MICRO_PIDS_LIMIT=384 \
    "${script}" --print-plan
)"
grep -F "source=tools/test/run-sse-reconnect-storm-smoke.sh" <<<"${plan}" >/dev/null
grep -F "image=local-java21" <<<"${plan}" >/dev/null
grep -F "cpus=2 memory=1024m memory-swap=1024m pids-limit=384" <<<"${plan}" >/dev/null
grep -F "archive-result=true" <<<"${plan}" >/dev/null

echo "[sse-reconnect-t3micro] dry-run command"
dry_run="$(
  SSE_T3MICRO_IMAGE=local-java21 \
    "${script}" --dry-run
)"
grep -F -- "--cpus 2" <<<"${dry_run}" >/dev/null
grep -F -- "--memory 1024m" <<<"${dry_run}" >/dev/null
grep -F "local-java21 bash -lc tools/test/run-sse-reconnect-storm-smoke.sh" <<<"${dry_run}" >/dev/null

echo "[sse-reconnect-t3micro] contract"
grep -F "run-sse-reconnect-storm-smoke.sh" "${script}" >/dev/null
grep -F "SSE_T3MICRO_ARCHIVE_RESULT" "${script}" >/dev/null
grep -F "archive_sse_result" "${script}" >/dev/null
grep -F "docs/performance-results" "${script}" >/dev/null

echo "[sse-reconnect-t3micro] invalid input fails"
if SSE_T3MICRO_CPUS=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "SSE_T3MICRO_CPUS=0 unexpectedly succeeded" >&2
  exit 1
fi
if SSE_T3MICRO_MEMORY=0m "${script}" --print-plan >/dev/null 2>&1; then
  echo "SSE_T3MICRO_MEMORY=0m unexpectedly succeeded" >&2
  exit 1
fi
if SSE_T3MICRO_ARCHIVE_RESULT=maybe "${script}" --print-plan >/dev/null 2>&1; then
  echo "SSE_T3MICRO_ARCHIVE_RESULT=maybe unexpectedly succeeded" >&2
  exit 1
fi
