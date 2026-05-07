#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-source-fairness-baseline.sh"

echo "[transaction-read-source-fairness-baseline] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/source-fairness.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
scenario	run_id	hot_account_count	cold_account_count	source_ips	account_429_skew	account_429_skew_budget	account_p95_skew_ms	account_p95_skew_budget_ms	source_edge_429_skew	source_edge_429_skew_budget	k6_summary_ref	account_distribution_ref	source_distribution_ref	edge_backend_split_ref	limiter_key_ref	five_xx_count	unknown_429_count
multi-account-fixture	oci-accounts-001	8	8	1	0.020	0.050	22	75	0.000	0.050	oci/k6/accounts.json	oci/accounts/distribution.tsv	oci/source/single.tsv	oci/429/accounts-split.tsv	oci/nginx/limiter-key.txt	0	0
fairness-budget	oci-fairness-001	8	8	1	0.030	0.050	40	75	0.000	0.050	oci/k6/fairness.json	oci/accounts/fairness-distribution.tsv	oci/source/fairness-sources.tsv	oci/429/fairness-split.tsv	oci/nginx/limiter-key.txt	0	0
TSV

echo "[transaction-read-source-fairness-baseline] print plan"
plan="$(
  SOURCE_FAIRNESS_NAME=fairness-check \
  SOURCE_FAIRNESS_INPUT_TSV="${input_tsv}" \
  SOURCE_FAIRNESS_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=fairness-check" <<<"${plan}" >/dev/null
grep -F "required_scenarios=multi-account-fixture,fairness-budget" <<<"${plan}" >/dev/null
grep -F "min_hot_accounts=4" <<<"${plan}" >/dev/null
grep -F "min_cold_accounts=4" <<<"${plan}" >/dev/null
grep -F "min_source_ips=1" <<<"${plan}" >/dev/null
grep -F "budget_columns=account_429_skew_budget,account_p95_skew_budget_ms,source_edge_429_skew_budget" <<<"${plan}" >/dev/null

echo "[transaction-read-source-fairness-baseline] pass report"
output="$(
  SOURCE_FAIRNESS_NAME=fairness-check \
  SOURCE_FAIRNESS_INPUT_TSV="${input_tsv}" \
  SOURCE_FAIRNESS_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/fairness-check-source-fairness-baseline.tsv"
test "${report_md}" = "${output_dir}/fairness-check-source-fairness-baseline.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "min source IPs: 1" "${report_md}" >/dev/null
grep -F "fairness budget" "${report_md}" >/dev/null
grep -F $'fairness-budget\tpass\tok\toci-fairness-001\t8\t8\t1\t0.030\t0.050\t40\t75\t0.000\t0.050' "${summary_tsv}" >/dev/null
grep -F "oci/429/fairness-split.tsv" "${summary_tsv}" >/dev/null
grep -F "oci/nginx/limiter-key.txt" "${summary_tsv}" >/dev/null

echo "[transaction-read-source-fairness-baseline] account fixture fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "multi-account-fixture" { $3 = 1; $4 = 1 } { print }' \
  "${input_tsv}" >"${input_tsv}.account-fail"
if SOURCE_FAIRNESS_NAME=fairness-account-fail \
  SOURCE_FAIRNESS_INPUT_TSV="${input_tsv}.account-fail" \
  SOURCE_FAIRNESS_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "source fairness unexpectedly passed single-account fixture" >&2
  exit 1
fi
SOURCE_FAIRNESS_NAME=fairness-account-fail \
SOURCE_FAIRNESS_INPUT_TSV="${input_tsv}.account-fail" \
SOURCE_FAIRNESS_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "hot-accounts<4" "${output_dir}/fairness-account-fail-source-fairness-baseline.tsv" >/dev/null
grep -F "cold-accounts<4" "${output_dir}/fairness-account-fail-source-fairness-baseline.tsv" >/dev/null

echo "[transaction-read-source-fairness-baseline] budget fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "fairness-budget" { $6 = 0.090; $8 = 120; $10 = 0.080 } { print }' \
  "${input_tsv}" >"${input_tsv}.budget-fail"
if SOURCE_FAIRNESS_NAME=fairness-budget-fail \
  SOURCE_FAIRNESS_INPUT_TSV="${input_tsv}.budget-fail" \
  SOURCE_FAIRNESS_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "source fairness unexpectedly passed budget skew violation" >&2
  exit 1
fi
SOURCE_FAIRNESS_NAME=fairness-budget-fail \
SOURCE_FAIRNESS_INPUT_TSV="${input_tsv}.budget-fail" \
SOURCE_FAIRNESS_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "account429-skew-budget" "${output_dir}/fairness-budget-fail-source-fairness-baseline.tsv" >/dev/null
grep -F "account-p95-skew-budget" "${output_dir}/fairness-budget-fail-source-fairness-baseline.tsv" >/dev/null
grep -F "source-edge429-skew-budget" "${output_dir}/fairness-budget-fail-source-fairness-baseline.tsv" >/dev/null

echo "[transaction-read-source-fairness-baseline] refs and hard-zero fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "fairness-budget" { $14 = "n/a"; $16 = "https://internal.example/limiter?token=secret"; $17 = 1; $18 = 1 } { print }' \
  "${input_tsv}" >"${input_tsv}.ref-fail"
if SOURCE_FAIRNESS_NAME=fairness-ref-fail \
  SOURCE_FAIRNESS_INPUT_TSV="${input_tsv}.ref-fail" \
  SOURCE_FAIRNESS_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "source fairness unexpectedly passed ref/hard-zero violation" >&2
  exit 1
fi
SOURCE_FAIRNESS_NAME=fairness-ref-fail \
SOURCE_FAIRNESS_INPUT_TSV="${input_tsv}.ref-fail" \
SOURCE_FAIRNESS_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "source_distribution_ref-missing" "${output_dir}/fairness-ref-fail-source-fairness-baseline.tsv" >/dev/null
grep -F "limiter_key_ref-unsafe" "${output_dir}/fairness-ref-fail-source-fairness-baseline.tsv" >/dev/null
grep -F "5xx>0" "${output_dir}/fairness-ref-fail-source-fairness-baseline.tsv" >/dev/null
grep -F "unknown429>0" "${output_dir}/fairness-ref-fail-source-fairness-baseline.tsv" >/dev/null

echo "[transaction-read-source-fairness-baseline] missing scenario fail"
awk -F '\t' '$1 != "fairness-budget"' "${input_tsv}" >"${input_tsv}.missing"
if SOURCE_FAIRNESS_NAME=fairness-missing \
  SOURCE_FAIRNESS_INPUT_TSV="${input_tsv}.missing" \
  SOURCE_FAIRNESS_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "source fairness unexpectedly passed missing fairness-budget scenario" >&2
  exit 1
fi
