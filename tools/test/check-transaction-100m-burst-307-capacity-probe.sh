#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-transaction-100m-burst-307-capacity-probe.sh"

echo "[transaction-100m-burst-307] shell syntax"
bash -n "${script}"

echo "[transaction-100m-burst-307] plan"
plan="$(
  BURST_307_PROBE_NAME=burst-307-check \
  BURST_307_PROBE_DURATION=20s \
  BURST_307_PROBE_VUS=307 \
  BURST_307_PROBE_OVERLOAD_MODE=true \
    "${script}" --print-plan
)"
grep -F "name=burst-307-check" <<<"${plan}" >/dev/null
grep -F "rate=307" <<<"${plan}" >/dev/null
grep -F "duration=20s" <<<"${plan}" >/dev/null
grep -F "vus=307" <<<"${plan}" >/dev/null
grep -F "overload_mode=true" <<<"${plan}" >/dev/null
grep -F "k6_report_name=burst-307-check-burst-307" <<<"${plan}" >/dev/null
grep -F "archive_output_dir=docs/performance-results/k6-profile" <<<"${plan}" >/dev/null

echo "[transaction-100m-burst-307] dry-run"
dry_run="$(
  BURST_307_PROBE_NAME=burst-307-check \
  BURST_307_PROBE_DURATION=20s \
  BURST_307_PROBE_VUS=307 \
  BURST_307_PROBE_OVERLOAD_MODE=true \
    "${script}" --dry-run
)"
grep -F "K6_SCENARIO_MODE=burst" <<<"${dry_run}" >/dev/null
grep -F "K6_BURST_RATE=307" <<<"${dry_run}" >/dev/null
grep -F "K6_PRE_ALLOCATED_VUS=307" <<<"${dry_run}" >/dev/null
grep -F "K6_MAX_VUS=307" <<<"${dry_run}" >/dev/null
grep -F "K6_OVERLOAD_MODE=true" <<<"${dry_run}" >/dev/null
grep -F "tools/test/run-k6-transaction-100m-loadtest.sh" <<<"${dry_run}" >/dev/null

echo "[transaction-100m-burst-307] invalid input fails"
if BURST_307_PROBE_RATE=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "zero burst rate unexpectedly passed" >&2
  exit 1
fi
if BURST_307_PROBE_DURATION=0s "${script}" --print-plan >/dev/null 2>&1; then
  echo "zero duration unexpectedly passed" >&2
  exit 1
fi

echo "[transaction-100m-burst-307] runner contract"
grep -F "BURST_307_PROBE_RATE" "${script}" >/dev/null
grep -F "K6_BURST_RATE=\"\${rate}\"" "${script}" >/dev/null
grep -F "K6_PRE_ALLOCATED_VUS=\"\${vus}\"" "${script}" >/dev/null
grep -F "K6_MAX_VUS=\"\${vus}\"" "${script}" >/dev/null
