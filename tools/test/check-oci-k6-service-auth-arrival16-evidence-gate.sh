#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-oci-k6-service-auth-arrival16-evidence-gate.sh"

echo "[oci-k6-service-auth-evidence] shell syntax"
test -f "${runner}"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_json="${temp_dir}/k6-summary.json"
nginx_aggregate_tsv="${temp_dir}/nginx-aggregate.tsv"
source_429_tsv="${temp_dir}/source-429.tsv"
pacing_summary="${temp_dir}/k6-pacing-summary.md"
auth_preflight_log="${temp_dir}/auth-preflight.log"
output_dir="${temp_dir}/output"

cat >"${summary_json}" <<'JSON'
{
  "metrics": {
    "http_reqs": {"values": {"count": 1000}},
    "aquila_transaction_accepted_200_count": {"values": {"count": 1000}},
    "aquila_transaction_unknown_429_count": {"values": {"count": 0}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": 120, "p(99)": 220}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": 130, "p(99)": 240}},
    "aquila_transaction_hot_deep_cursor_ms": {"values": {"p(95)": 140, "p(99)": 260}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": 420, "p(99)": 680}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": 430, "p(99)": 700}},
    "aquila_transaction_cold_deep_cursor_ms": {"values": {"p(95)": 450, "p(99)": 730}}
  }
}
JSON

cat >"${nginx_aggregate_tsv}" <<'TSV'
k6_run_id	status	limit_req_status	upstream_status	reject_source	reject_reason	upstream_reject_source	upstream_reject_reason	count	delayed_count	rejected_count	request_p95_ms	upstream_p95_ms
arrival16	200	PASSED	200	none	none	none	none	1000	0	0	120.000	110.000
TSV

cat >"${source_429_tsv}" <<'TSV'
source	status	rate	count	fail_threshold
total_429	pass	0	n/a	0.10
edge	pass	0	0	0.10
backend	pass	0	0	0.005
unknown	pass	0	0	0
502	pass	0	0	0
503	pass	0	0	0
accepted_200	observe	1	1000	n/a
TSV

cat >"${pacing_summary}" <<'MD'
# k6 Pacing Evidence

- k6_run_id=arrival16
- scenario_mode=constant-arrival-rate
- arrival-rate gate: 16/1s
MD

cat >"${auth_preflight_log}" <<'LOG'
[oci-k6-service-auth] auth item preflight hot account=910000001 items=1
[oci-k6-service-auth] auth item preflight cold account=910000002 items=1
LOG

echo "[oci-k6-service-auth-evidence] print plan"
plan="$(
  SERVICE_AUTH_EVIDENCE_NAME=arrival16-check \
  SERVICE_AUTH_EVIDENCE_K6_SUMMARY_JSON="${summary_json}" \
  SERVICE_AUTH_EVIDENCE_NGINX_AGGREGATE_TSV="${nginx_aggregate_tsv}" \
  SERVICE_AUTH_EVIDENCE_429_SOURCE_TSV="${source_429_tsv}" \
  SERVICE_AUTH_EVIDENCE_PACING_SUMMARY="${pacing_summary}" \
  SERVICE_AUTH_EVIDENCE_AUTH_PREFLIGHT_LOG="${auth_preflight_log}" \
  SERVICE_AUTH_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=arrival16-check" <<<"${plan}" >/dev/null
grep -F "expected_arrival_rate=16/1s" <<<"${plan}" >/dev/null
grep -F "k6_summary_json=${summary_json}" <<<"${plan}" >/dev/null

echo "[oci-k6-service-auth-evidence] complete artifacts pass"
output="$(
  SERVICE_AUTH_EVIDENCE_NAME=arrival16-check \
  SERVICE_AUTH_EVIDENCE_K6_SUMMARY_JSON="${summary_json}" \
  SERVICE_AUTH_EVIDENCE_NGINX_AGGREGATE_TSV="${nginx_aggregate_tsv}" \
  SERVICE_AUTH_EVIDENCE_429_SOURCE_TSV="${source_429_tsv}" \
  SERVICE_AUTH_EVIDENCE_PACING_SUMMARY="${pacing_summary}" \
  SERVICE_AUTH_EVIDENCE_AUTH_PREFLIGHT_LOG="${auth_preflight_log}" \
  SERVICE_AUTH_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/arrival16-check-service-auth-evidence.tsv"
test "${report_md}" = "${output_dir}/arrival16-check-service-auth-evidence.md"
grep -F $'check\tstatus\tvalue\tthreshold\treason' "${summary_tsv}" >/dev/null
grep -F $'accepted_200_count\tpass\t1000\t>0\tok' "${summary_tsv}" >/dev/null
grep -F $'nginx_transaction_read_rows\tpass\t1000\t>0\tok' "${summary_tsv}" >/dev/null
grep -F $'source_gate_failures\tpass\t0\t0\tok' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "| accepted_200_count | pass | 1000 | >0 | ok |" "${report_md}" >/dev/null
grep -F "| cold_deep_cursor_p95_ms | pass | 450 | present | ok |" "${report_md}" >/dev/null

echo "[oci-k6-service-auth-evidence] missing artifact fails"
if SERVICE_AUTH_EVIDENCE_NAME=arrival16-missing-check \
  SERVICE_AUTH_EVIDENCE_K6_SUMMARY_JSON="${temp_dir}/missing-summary.json" \
  SERVICE_AUTH_EVIDENCE_NGINX_AGGREGATE_TSV="${nginx_aggregate_tsv}" \
  SERVICE_AUTH_EVIDENCE_429_SOURCE_TSV="${source_429_tsv}" \
  SERVICE_AUTH_EVIDENCE_PACING_SUMMARY="${pacing_summary}" \
  SERVICE_AUTH_EVIDENCE_AUTH_PREFLIGHT_LOG="${auth_preflight_log}" \
  SERVICE_AUTH_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "service-auth evidence gate unexpectedly passed missing summary" >&2
  exit 1
fi

echo "[oci-k6-service-auth-evidence] 5xx summary fails"
summary_5xx_json="${temp_dir}/k6-summary-5xx.json"
jq '.metrics.aquila_transaction_503_count.values.count = 1' "${summary_json}" >"${summary_5xx_json}"
if SERVICE_AUTH_EVIDENCE_NAME=arrival16-5xx-check \
  SERVICE_AUTH_EVIDENCE_K6_SUMMARY_JSON="${summary_5xx_json}" \
  SERVICE_AUTH_EVIDENCE_NGINX_AGGREGATE_TSV="${nginx_aggregate_tsv}" \
  SERVICE_AUTH_EVIDENCE_429_SOURCE_TSV="${source_429_tsv}" \
  SERVICE_AUTH_EVIDENCE_PACING_SUMMARY="${pacing_summary}" \
  SERVICE_AUTH_EVIDENCE_AUTH_PREFLIGHT_LOG="${auth_preflight_log}" \
  SERVICE_AUTH_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "service-auth evidence gate unexpectedly passed 503 count" >&2
  exit 1
fi

echo "[oci-k6-service-auth-evidence] contract references"
grep -F "SERVICE_AUTH_EVIDENCE_K6_SUMMARY_JSON" "${runner}" >/dev/null
grep -F "aquila_transaction_accepted_200_count" "${runner}" >/dev/null
grep -F "source_gate_failures" "${runner}" >/dev/null
grep -F "nginx_transaction_read_rows" "${runner}" >/dev/null
