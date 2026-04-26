#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-page-limit-sensitivity.sh"

echo "[transaction-read-page-limit-sensitivity] shell syntax"
bash -n "${runner}"

echo "[transaction-read-page-limit-sensitivity] print plan"
plan="$(
  PAGE_LIMIT_SENSITIVITY_NAME=transaction-limit-check \
  PAGE_LIMIT_SENSITIVITY_LIMITS=20,50,100,200 \
  PAGE_LIMIT_SENSITIVITY_DURATION=45s \
  PAGE_LIMIT_SENSITIVITY_VUS=4 \
    "${runner}" --print-plan
)"
grep -F "benchmark=transaction-limit-check" <<<"${plan}" >/dev/null
grep -F "limits=20,50,100,200" <<<"${plan}" >/dev/null
grep -F "duration=45s" <<<"${plan}" >/dev/null
grep -F "vus=4" <<<"${plan}" >/dev/null
grep -F "runner=tools/test/run-k6-transaction-100m-loadtest.sh" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/k6/transaction-limit-check/page-limit-sensitivity-summary.tsv" <<<"${plan}" >/dev/null

echo "[transaction-read-page-limit-sensitivity] runner contract"
grep -F "aquila_transaction_hot_first_ms" "${runner}" >/dev/null
grep -F "p(99)" "${runner}" >/dev/null
grep -F "max" "${runner}" >/dev/null
grep -F "aquila_transaction_429_rate" "${runner}" >/dev/null
grep -F "page-limit-sensitivity-summary.tsv" "${runner}" >/dev/null
grep -F "K6_LIMIT" "${runner}" >/dev/null
grep -F "K6_ARCHIVE_RESULTS=false" "${runner}" >/dev/null

echo "[transaction-read-page-limit-sensitivity] invalid input fails"
if PAGE_LIMIT_SENSITIVITY_LIMITS=20,bad "${runner}" --print-plan >/dev/null 2>&1; then
  echo "PAGE_LIMIT_SENSITIVITY_LIMITS=20,bad unexpectedly succeeded" >&2
  exit 1
fi
if PAGE_LIMIT_SENSITIVITY_DURATION=0m "${runner}" --print-plan >/dev/null 2>&1; then
  echo "PAGE_LIMIT_SENSITIVITY_DURATION=0m unexpectedly succeeded" >&2
  exit 1
fi
