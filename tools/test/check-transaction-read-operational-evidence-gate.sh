#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-operational-evidence-gate.sh"

echo "[transaction-read-operational-evidence] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

evidence_tsv="${temp_dir}/operational-evidence.tsv"
output_dir="${temp_dir}/output"

cat >"${evidence_tsv}" <<'TSV'
scenario	duration_min	source_ips	cold_p95_ms	warm_p95_ms	p999_ms	edge_429_rate	backend_429_count	five_xx_count	nginx_499_count	hikari_validation_warnings	db_pool_pending_max	sse_reject_count	deploy_drain_5xx_count	pg_wait_p95_ms	timeline_artifact
hikari-soak	10	1	420	210	390	0.01	0	0	0	0	0	0	0	3	oci/hikari-soak.json
cold-warm	3	1	760	240	410	0.02	0	0	0	0	0	0	0	4	oci/cold-warm.json
mixed-workload	30	1	650	260	450	0.08	0	0	0	0	0	0	0	6	oci/mixed-workload.json
real-ip-multisource	2	3	560	230	430	0.07	0	0	0	0	0	0	0	5	oci/real-ip.json
deploy-drain	5	1	590	250	470	0.04	0	0	0	0	0	0	0	5	oci/deploy-drain.json
p999-long	30	1	700	280	490	0.06	0	0	0	0	0	0	0	8	oci/p999-long.json
TSV

echo "[transaction-read-operational-evidence] print plan"
plan="$(
  OP_EVIDENCE_NAME=operational-check \
  OP_EVIDENCE_INPUT_TSV="${evidence_tsv}" \
  OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=operational-check" <<<"${plan}" >/dev/null
grep -F "input_tsv=${evidence_tsv}" <<<"${plan}" >/dev/null
grep -F "required_scenarios=hikari-soak,cold-warm,mixed-workload,real-ip-multisource,deploy-drain,p999-long" <<<"${plan}" >/dev/null
grep -F "max_edge_429_rate=0.10" <<<"${plan}" >/dev/null
grep -F "max_p999_ms=500" <<<"${plan}" >/dev/null
grep -F "mixed_min_duration_min=30" <<<"${plan}" >/dev/null
grep -F "min_real_source_ips=2" <<<"${plan}" >/dev/null

echo "[transaction-read-operational-evidence] pass report"
output="$(
  OP_EVIDENCE_NAME=operational-check \
  OP_EVIDENCE_INPUT_TSV="${evidence_tsv}" \
  OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/operational-check-operational-evidence.tsv"
test "${report_md}" = "${output_dir}/operational-check-operational-evidence.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "Hikari validation warning: 0" "${report_md}" >/dev/null
grep -F "499/5xx/backend429 hard target: 0" "${report_md}" >/dev/null
grep -F "mixed workload min duration: 30m" "${report_md}" >/dev/null
grep -F "p99.9 long observation max: 500ms" "${report_md}" >/dev/null
grep -F $'mixed-workload\tpass\tok\t30\t1\t650\t260\t450\t0.08\t0\t0\t0\t0\t0\t0\t0\t6\toci/mixed-workload.json' "${summary_tsv}" >/dev/null
grep -F $'real-ip-multisource\tpass\tok\t2\t3' "${summary_tsv}" >/dev/null

echo "[transaction-read-operational-evidence] fail report"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "mixed-workload" { $6 = 560; $7 = 0.12; $9 = 1; $10 = 1; $11 = 1 } { print }' \
  "${evidence_tsv}" >"${evidence_tsv}.fail"
if OP_EVIDENCE_NAME=operational-fail \
  OP_EVIDENCE_INPUT_TSV="${evidence_tsv}.fail" \
  OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "operational evidence gate unexpectedly passed Hikari/499/p999/429 failure" >&2
  exit 1
fi

OP_EVIDENCE_NAME=operational-fail \
OP_EVIDENCE_INPUT_TSV="${evidence_tsv}.fail" \
OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F $'mixed-workload\tfail\t' "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "edge429>0.10" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "5xx>0" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "499>0" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null
grep -F "hikari-warning>0" "${output_dir}/operational-fail-operational-evidence.tsv" >/dev/null

echo "[transaction-read-operational-evidence] missing scenario fails"
awk -F '\t' '$1 != "deploy-drain"' "${evidence_tsv}" >"${evidence_tsv}.missing"
if OP_EVIDENCE_NAME=operational-missing \
  OP_EVIDENCE_INPUT_TSV="${evidence_tsv}.missing" \
  OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "operational evidence gate unexpectedly passed missing deploy-drain scenario" >&2
  exit 1
fi

echo "[transaction-read-operational-evidence] real IP source fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "real-ip-multisource" { $3 = 1 } { print }' \
  "${evidence_tsv}" >"${evidence_tsv}.source-fail"
if OP_EVIDENCE_NAME=operational-source-fail \
  OP_EVIDENCE_INPUT_TSV="${evidence_tsv}.source-fail" \
  OP_EVIDENCE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "operational evidence gate unexpectedly passed single source real-IP evidence" >&2
  exit 1
fi
