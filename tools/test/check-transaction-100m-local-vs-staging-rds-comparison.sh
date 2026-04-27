#!/usr/bin/env bash
set -euo pipefail

script="tools/test/compare-transaction-100m-local-vs-staging-rds.sh"

echo "[transaction-100m-comparison] shell syntax"
bash -n "${script}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

local_md="${temp_dir}/local.md"
staging_md="${temp_dir}/staging.md"
cat >"${local_md}" <<'MD'
# local
- http_req_failed rate: 0.01
- checks rate: 0.99
- transaction 429 rate: 0.02
- hot first p95 ms: 300
- hot cursor p95 ms: 320
- cold first p95 ms: 600
- cold cursor p95 ms: 620
MD

cat >"${staging_md}" <<'MD'
# staging
- http_req_failed rate: 0
- checks rate: 1
- transaction 429 rate: 0.01
- hot first p95 ms: 280
- hot cursor p95 ms: 310
- cold first p95 ms: 580
- cold cursor p95 ms: 610
MD

echo "[transaction-100m-comparison] plan"
plan="$(
  LOCAL_K6_SUMMARY_MD="${local_md}" \
  STAGING_RDS_K6_SUMMARY_MD="${staging_md}" \
  COMPARISON_OUTPUT_DIR="${temp_dir}" \
  COMPARISON_RESULT_NAME=comparison-check \
    "${script}" --print-plan
)"
grep -F "local=${local_md}" <<<"${plan}" >/dev/null
grep -F "staging=${staging_md}" <<<"${plan}" >/dev/null
grep -F "output=${temp_dir}/comparison-check.md" <<<"${plan}" >/dev/null

echo "[transaction-100m-comparison] report"
output="$(
  LOCAL_K6_SUMMARY_MD="${local_md}" \
  STAGING_RDS_K6_SUMMARY_MD="${staging_md}" \
  COMPARISON_OUTPUT_DIR="${temp_dir}" \
  COMPARISON_RESULT_NAME=comparison-check \
    "${script}"
)"
output="$(tail -1 <<<"${output}")"
test "${output}" = "${temp_dir}/comparison-check.md"
grep -F "| http_req_failed rate | 0.01 | 0 |" "${output}" >/dev/null
grep -F "| hot first p95 ms | 300 | 280 |" "${output}" >/dev/null
grep -F "| cold cursor p95 ms | 620 | 610 |" "${output}" >/dev/null

echo "[transaction-100m-comparison] missing input fails"
if COMPARISON_OUTPUT_DIR="${temp_dir}" "${script}" >/dev/null 2>&1; then
  echo "missing comparison inputs unexpectedly succeeded" >&2
  exit 1
fi
