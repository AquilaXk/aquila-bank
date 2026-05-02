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
account_result_tsv="${temp_dir}/account-result.tsv"

cat >"${account_result_tsv}" <<'TSV'
account_group	account_id	planned_rows	accepted_p95_ms	rejected_429_rate
hot	910000001	33000000	91.0	0.003
hot	910000003	33000000	94.0	0.004
hot	910000005	33000000	89.0	0.002
cold	910000002	500000	112.0	0.005
cold	910000004	500000	118.0	0.006
TSV

echo "[transaction-100m-multi-account] print plan"
plan="$(
  MULTI_ACCOUNT_FIXTURE_NAME=multi-account-check \
  MULTI_ACCOUNT_HOT_ACCOUNT_IDS=910000001,910000003,910000005 \
  MULTI_ACCOUNT_COLD_ACCOUNT_IDS=910000002,910000004 \
  MULTI_ACCOUNT_ACCOUNT_RESULT_TSV="${account_result_tsv}" \
  MULTI_ACCOUNT_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "hot_account_count=3" <<<"${plan}" >/dev/null
grep -F "cold_account_count=2" <<<"${plan}" >/dev/null
grep -F "target_total_rows=100000000" <<<"${plan}" >/dev/null
grep -F "total_rows=100000000" <<<"${plan}" >/dev/null
grep -F "hot_rows_per_account=33000000 hot_rows_remainder=0" <<<"${plan}" >/dev/null
grep -F "cold_rows_per_account=500000" <<<"${plan}" >/dev/null
grep -F "account_result_tsv=${account_result_tsv}" <<<"${plan}" >/dev/null
grep -F "k6_env=K6_HOT_ACCOUNT_IDS=910000001,910000003,910000005 K6_COLD_ACCOUNT_IDS=910000002,910000004" <<<"${plan}" >/dev/null

echo "[transaction-100m-multi-account] manifest report"
output="$(
  MULTI_ACCOUNT_FIXTURE_NAME=multi-account-check \
  MULTI_ACCOUNT_HOT_ACCOUNT_IDS=910000001,910000003,910000005 \
  MULTI_ACCOUNT_COLD_ACCOUNT_IDS=910000002,910000004 \
  MULTI_ACCOUNT_ACCOUNT_RESULT_TSV="${account_result_tsv}" \
  MULTI_ACCOUNT_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
manifest_json="${output_dir}/multi-account-check-multi-account-fixture.json"
distribution_tsv="${output_dir}/multi-account-check-multi-account-distribution.tsv"
account_summary_tsv="${output_dir}/multi-account-check-multi-account-account-summary.tsv"
test "${report_md}" = "${output_dir}/multi-account-check-multi-account-fixture.md"
grep -F '"hotAccountIds":["910000001","910000003","910000005"]' "${manifest_json}" >/dev/null
grep -F '"coldAccountIds":["910000002","910000004"]' "${manifest_json}" >/dev/null
grep -F '"targetTotalRows":100000000' "${manifest_json}" >/dev/null
grep -F '"totalRows":100000000' "${manifest_json}" >/dev/null
grep -F '"hotRowsPerAccount":33000000' "${manifest_json}" >/dev/null
grep -F '"coldRowsPerAccount":500000' "${manifest_json}" >/dev/null
grep -F $'account_group\taccount_id\tplanned_rows' "${distribution_tsv}" >/dev/null
grep -F $'hot\t910000001\t33000000' "${distribution_tsv}" >/dev/null
grep -F $'cold\t910000004\t500000' "${distribution_tsv}" >/dev/null
grep -F $'all\t5\t100000000\t29\t0.004' "${account_summary_tsv}" >/dev/null
grep -F "per-account fairness metric: aquila_transaction_fairness_429_count{account_id,account_group}" "${report_md}" >/dev/null
grep -F "global throughput and per-account fairness are reported separately" "${report_md}" >/dev/null
grep -F "account result summary: ${account_summary_tsv}" "${report_md}" >/dev/null

echo "[transaction-100m-multi-account] missing account result fails when required"
if MULTI_ACCOUNT_FIXTURE_NAME=multi-account-missing-result \
  MULTI_ACCOUNT_HOT_ACCOUNT_IDS=910000001,910000003,910000005 \
  MULTI_ACCOUNT_COLD_ACCOUNT_IDS=910000002,910000004 \
  MULTI_ACCOUNT_REQUIRE_ACCOUNT_RESULT_TSV=true \
  MULTI_ACCOUNT_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "multi-account fixture unexpectedly passed missing account result TSV" >&2
  exit 1
fi

echo "[transaction-100m-multi-account] single hot account fails"
if MULTI_ACCOUNT_FIXTURE_NAME=multi-account-fail \
  MULTI_ACCOUNT_HOT_ACCOUNT_IDS=910000001 \
  MULTI_ACCOUNT_COLD_ACCOUNT_IDS=910000002,910000004 \
  MULTI_ACCOUNT_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "multi-account fixture unexpectedly passed single hot account" >&2
  exit 1
fi
