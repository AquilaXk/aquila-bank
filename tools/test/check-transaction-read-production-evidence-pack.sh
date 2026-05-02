#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-production-evidence-pack.sh"

echo "[transaction-read-production-evidence-pack] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/production-evidence.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
scenario	run_id	duration_min	source_ips	k6_summary_ref	nginx_aggregate_ref	nginx_499_aggregate_ref	spring_429_ref	hikari_log_ref	postgres_explain_ref	prometheus_timeline_ref	source_fairness_ref	cache_state_ref	deploy_event_ref	edge_429_rate	backend_429_count	unknown_429_count	five_xx_count	nginx_499_count	hikari_validation_warnings	db_pool_pending_max
hikari-soak	oci-001	30	1	oci/k6/hikari.json	oci/nginx/hikari.tsv	oci/nginx/hikari-499.tsv	oci/spring/hikari-429.tsv	oci/hikari/hikari.log	oci/pg/hikari-explain.txt	oci/prom/hikari.json	n/a	n/a	n/a	0.01	0	0	0	0	0	0
cold-warm	oci-002	10	1	oci/k6/cold-warm.json	oci/nginx/cold-warm.tsv	oci/nginx/cold-warm-499.tsv	oci/spring/cold-warm-429.tsv	oci/hikari/cold-warm.log	oci/pg/cold-warm-explain.txt	oci/prom/cold-warm.json	n/a	oci/cache/cold-warm.tsv	n/a	0.02	0	0	0	0	0	0
mixed-workload	oci-003	30	1	oci/k6/mixed.json	oci/nginx/mixed.tsv	oci/nginx/mixed-499.tsv	oci/spring/mixed-429.tsv	oci/hikari/mixed.log	oci/pg/mixed-explain.txt	oci/prom/mixed.json	n/a	n/a	n/a	0.08	0	0	0	0	0	0
real-ip-multisource	oci-004	10	2	oci/k6/real-ip.json	oci/nginx/real-ip.tsv	oci/nginx/real-ip-499.tsv	oci/spring/real-ip-429.tsv	oci/hikari/real-ip.log	oci/pg/real-ip-explain.txt	oci/prom/real-ip.json	oci/source/real-ip-fairness.tsv	n/a	n/a	0.07	0	0	0	0	0	0
deploy-drain	oci-005	5	1	oci/k6/deploy-drain.json	oci/nginx/deploy-drain.tsv	oci/nginx/deploy-drain-499.tsv	oci/spring/deploy-drain-429.tsv	oci/hikari/deploy-drain.log	oci/pg/deploy-drain-explain.txt	oci/prom/deploy-drain.json	n/a	n/a	oci/deploy/drain-events.tsv	0.04	0	0	0	0	0	0
p999-long	oci-006	30	1	oci/k6/p999.json	oci/nginx/p999.tsv	oci/nginx/p999-499.tsv	oci/spring/p999-429.tsv	oci/hikari/p999.log	oci/pg/p999-explain.txt	oci/prom/p999.json	n/a	n/a	n/a	0.06	0	0	0	0	0	0
TSV

echo "[transaction-read-production-evidence-pack] print plan"
plan="$(
  PROD_EVIDENCE_PACK_NAME=production-check \
  PROD_EVIDENCE_PACK_INPUT_TSV="${input_tsv}" \
  PROD_EVIDENCE_PACK_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=production-check" <<<"${plan}" >/dev/null
grep -F "required_scenarios=hikari-soak,cold-warm,mixed-workload,real-ip-multisource,deploy-drain,p999-long" <<<"${plan}" >/dev/null
grep -F "min_real_source_ips=2" <<<"${plan}" >/dev/null
grep -F "required_refs=k6_summary_ref,nginx_aggregate_ref,nginx_499_aggregate_ref,spring_429_ref,hikari_log_ref,postgres_explain_ref,prometheus_timeline_ref" <<<"${plan}" >/dev/null

echo "[transaction-read-production-evidence-pack] pass report"
output="$(
  PROD_EVIDENCE_PACK_NAME=production-check \
  PROD_EVIDENCE_PACK_INPUT_TSV="${input_tsv}" \
  PROD_EVIDENCE_PACK_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/production-check-production-evidence-pack.tsv"
test "${report_md}" = "${output_dir}/production-check-production-evidence-pack.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "Nginx 499 aggregate" "${report_md}" >/dev/null
grep -F "hard-zero: backend 429, unknown 429, 499, 5xx, Hikari warning, Hikari pending" "${report_md}" >/dev/null
grep -F $'real-ip-multisource\tpass\tok\toci-004\t10\t2' "${summary_tsv}" >/dev/null
grep -F "oci/source/real-ip-fairness.tsv" "${summary_tsv}" >/dev/null
grep -F "oci/deploy/drain-events.tsv" "${summary_tsv}" >/dev/null
grep -F "oci/cache/cold-warm.tsv" "${summary_tsv}" >/dev/null

echo "[transaction-read-production-evidence-pack] hard-zero fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "mixed-workload" { $17 = 1; $18 = 1; $19 = 1; $20 = 1; $21 = 1 } { print }' \
  "${input_tsv}" >"${input_tsv}.hard-zero-fail"
if PROD_EVIDENCE_PACK_NAME=production-hard-zero-fail \
  PROD_EVIDENCE_PACK_INPUT_TSV="${input_tsv}.hard-zero-fail" \
  PROD_EVIDENCE_PACK_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "production evidence pack unexpectedly passed hard-zero violation" >&2
  exit 1
fi

PROD_EVIDENCE_PACK_NAME=production-hard-zero-fail \
PROD_EVIDENCE_PACK_INPUT_TSV="${input_tsv}.hard-zero-fail" \
PROD_EVIDENCE_PACK_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "unknown429>0" "${output_dir}/production-hard-zero-fail-production-evidence-pack.tsv" >/dev/null
grep -F "5xx>0" "${output_dir}/production-hard-zero-fail-production-evidence-pack.tsv" >/dev/null
grep -F "499>0" "${output_dir}/production-hard-zero-fail-production-evidence-pack.tsv" >/dev/null
grep -F "hikari-warning>0" "${output_dir}/production-hard-zero-fail-production-evidence-pack.tsv" >/dev/null
grep -F "pool-pending>0" "${output_dir}/production-hard-zero-fail-production-evidence-pack.tsv" >/dev/null

echo "[transaction-read-production-evidence-pack] missing ref fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "p999-long" { $11 = "n/a" } { print }' \
  "${input_tsv}" >"${input_tsv}.missing-ref"
if PROD_EVIDENCE_PACK_NAME=production-missing-ref \
  PROD_EVIDENCE_PACK_INPUT_TSV="${input_tsv}.missing-ref" \
  PROD_EVIDENCE_PACK_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "production evidence pack unexpectedly passed missing Prometheus timeline ref" >&2
  exit 1
fi
PROD_EVIDENCE_PACK_NAME=production-missing-ref \
PROD_EVIDENCE_PACK_INPUT_TSV="${input_tsv}.missing-ref" \
PROD_EVIDENCE_PACK_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "prometheus_timeline_ref-missing" "${output_dir}/production-missing-ref-production-evidence-pack.tsv" >/dev/null

echo "[transaction-read-production-evidence-pack] source fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "real-ip-multisource" { $4 = 1; $12 = "n/a" } { print }' \
  "${input_tsv}" >"${input_tsv}.source-fail"
if PROD_EVIDENCE_PACK_NAME=production-source-fail \
  PROD_EVIDENCE_PACK_INPUT_TSV="${input_tsv}.source-fail" \
  PROD_EVIDENCE_PACK_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "production evidence pack unexpectedly passed source evidence violation" >&2
  exit 1
fi
PROD_EVIDENCE_PACK_NAME=production-source-fail \
PROD_EVIDENCE_PACK_INPUT_TSV="${input_tsv}.source-fail" \
PROD_EVIDENCE_PACK_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "source-ips<2" "${output_dir}/production-source-fail-production-evidence-pack.tsv" >/dev/null
grep -F "source-fairness-missing" "${output_dir}/production-source-fail-production-evidence-pack.tsv" >/dev/null

echo "[transaction-read-production-evidence-pack] unsafe ref fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "hikari-soak" { $7 = "https://internal.example/nginx.tsv?token=secret" } { print }' \
  "${input_tsv}" >"${input_tsv}.unsafe-ref"
if PROD_EVIDENCE_PACK_NAME=production-unsafe-ref \
  PROD_EVIDENCE_PACK_INPUT_TSV="${input_tsv}.unsafe-ref" \
  PROD_EVIDENCE_PACK_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "production evidence pack unexpectedly passed unsafe URL/token ref" >&2
  exit 1
fi

echo "[transaction-read-production-evidence-pack] missing scenario fail"
awk -F '\t' '$1 != "deploy-drain"' "${input_tsv}" >"${input_tsv}.missing-scenario"
if PROD_EVIDENCE_PACK_NAME=production-missing-scenario \
  PROD_EVIDENCE_PACK_INPUT_TSV="${input_tsv}.missing-scenario" \
  PROD_EVIDENCE_PACK_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "production evidence pack unexpectedly passed missing scenario" >&2
  exit 1
fi
