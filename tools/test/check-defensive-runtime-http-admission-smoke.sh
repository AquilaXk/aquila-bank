#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-defensive-runtime-http-admission-smoke.sh"

echo "[defensive-http-admission] shell syntax"
bash -n "${script}"

echo "[defensive-http-admission] print plan"
plan="$(
  ADMISSION_NAME=admission-check \
  ADMISSION_REQUESTS=12 \
  ADMISSION_CONCURRENCY=4 \
  ADMISSION_EXPECT_429=true \
    "${script}" --print-plan
)"
grep -F "name=admission-check" <<<"${plan}" >/dev/null
grep -F "base_url=http://localhost:8080" <<<"${plan}" >/dev/null
grep -F "requests=12" <<<"${plan}" >/dev/null
grep -F "concurrency=4" <<<"${plan}" >/dev/null
grep -F "expect_429=true" <<<"${plan}" >/dev/null
grep -F "summary=build/reports/admission/admission-check/http-admission-summary.tsv" <<<"${plan}" >/dev/null

echo "[defensive-http-admission] dry-run command"
dry_run="$("${script}" --dry-run)"
grep -F "curl" <<<"${dry_run}" >/dev/null
grep -F "X-Account-Id:" <<<"${dry_run}" >/dev/null
grep -F "Authorization: Bearer ***" <<<"${dry_run}" >/dev/null
grep -F "/api/v1/transactions" <<<"${dry_run}" >/dev/null

echo "[defensive-http-admission] runner contract"
grep -F "ADMISSION_EXPECT_429" "${script}" >/dev/null
grep -F "ADMISSION_MAX_FAILED_RATE" "${script}" >/dev/null
grep -F "http-admission-summary.tsv" "${script}" >/dev/null
grep -F "Retry-After" "${script}" >/dev/null
grep -F "rejected_count" "${script}" >/dev/null
grep -F "failed_rate" "${script}" >/dev/null
grep -F "Authorization: Bearer ***" "${script}" >/dev/null

echo "[defensive-http-admission] invalid input fails"
if ADMISSION_REQUESTS=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "ADMISSION_REQUESTS=0 unexpectedly succeeded" >&2
  exit 1
fi
if ADMISSION_CONCURRENCY=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "ADMISSION_CONCURRENCY=0 unexpectedly succeeded" >&2
  exit 1
fi
if ADMISSION_EXPECT_429=maybe "${script}" --print-plan >/dev/null 2>&1; then
  echo "ADMISSION_EXPECT_429=maybe unexpectedly succeeded" >&2
  exit 1
fi
if ADMISSION_MAX_FAILED_RATE=1.5 "${script}" --print-plan >/dev/null 2>&1; then
  echo "ADMISSION_MAX_FAILED_RATE=1.5 unexpectedly succeeded" >&2
  exit 1
fi
