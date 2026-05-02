#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-100m-multi-account-fixture.sh"
k6_script="ops/k6/transaction-read-100m.js"
k6_runner="tools/test/run-k6-transaction-100m-loadtest.sh"
compose_file="compose.loadtest.yml"

echo "[transaction-100m-multi-account] shell syntax"
bash -n "${runner}"

echo "[transaction-100m-multi-account] k6 contract"
grep -F "K6_HOT_ACCOUNT_IDS" "${k6_script}" >/dev/null
grep -F "K6_COLD_ACCOUNT_IDS" "${k6_script}" >/dev/null
grep -F "aquila_transaction_fairness_429_count" "${k6_script}" >/dev/null
grep -F "account_group" "${k6_script}" >/dev/null
grep -F "account_id: String(accountId)" "${k6_script}" >/dev/null
grep -F "pickAccount(hotAccountIds)" "${k6_script}" >/dev/null
grep -F "pickAccount(coldAccountIds)" "${k6_script}" >/dev/null
grep -F "K6_HOT_ACCOUNT_IDS" "${k6_runner}" >/dev/null
grep -F "K6_COLD_ACCOUNT_IDS" "${k6_runner}" >/dev/null
grep -F "K6_HOT_ACCOUNT_IDS: \${K6_HOT_ACCOUNT_IDS:-}" "${compose_file}" >/dev/null
grep -F "K6_COLD_ACCOUNT_IDS: \${K6_COLD_ACCOUNT_IDS:-}" "${compose_file}" >/dev/null

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

output_dir="${temp_dir}/output"

echo "[transaction-100m-multi-account] print plan"
plan="$(
  MULTI_ACCOUNT_FIXTURE_NAME=multi-account-check \
  MULTI_ACCOUNT_HOT_ACCOUNT_IDS=910000001,910000003,910000005 \
  MULTI_ACCOUNT_COLD_ACCOUNT_IDS=910000002,910000004 \
  MULTI_ACCOUNT_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "hot_account_count=3" <<<"${plan}" >/dev/null
grep -F "cold_account_count=2" <<<"${plan}" >/dev/null
grep -F "k6_env=K6_HOT_ACCOUNT_IDS=910000001,910000003,910000005 K6_COLD_ACCOUNT_IDS=910000002,910000004" <<<"${plan}" >/dev/null

echo "[transaction-100m-multi-account] manifest report"
output="$(
  MULTI_ACCOUNT_FIXTURE_NAME=multi-account-check \
  MULTI_ACCOUNT_HOT_ACCOUNT_IDS=910000001,910000003,910000005 \
  MULTI_ACCOUNT_COLD_ACCOUNT_IDS=910000002,910000004 \
  MULTI_ACCOUNT_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
manifest_json="${output_dir}/multi-account-check-multi-account-fixture.json"
test "${report_md}" = "${output_dir}/multi-account-check-multi-account-fixture.md"
grep -F '"hotAccountIds":["910000001","910000003","910000005"]' "${manifest_json}" >/dev/null
grep -F '"coldAccountIds":["910000002","910000004"]' "${manifest_json}" >/dev/null
grep -F "per-account fairness metric: aquila_transaction_fairness_429_count{account_id,account_group}" "${report_md}" >/dev/null
grep -F "global throughput and per-account fairness are reported separately" "${report_md}" >/dev/null

echo "[transaction-100m-multi-account] single hot account fails"
if MULTI_ACCOUNT_FIXTURE_NAME=multi-account-fail \
  MULTI_ACCOUNT_HOT_ACCOUNT_IDS=910000001 \
  MULTI_ACCOUNT_COLD_ACCOUNT_IDS=910000002,910000004 \
  MULTI_ACCOUNT_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "multi-account fixture unexpectedly passed single hot account" >&2
  exit 1
fi
