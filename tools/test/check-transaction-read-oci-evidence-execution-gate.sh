#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-oci-evidence-execution-gate.sh"

echo "[transaction-read-oci-evidence-execution] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/oci-execution.tsv"
output_dir="${temp_dir}/output"
artifact_dir="${temp_dir}/artifacts"
mkdir -p "${artifact_dir}"

touch \
  "${artifact_dir}/k6.json" \
  "${artifact_dir}/nginx.jsonl" \
  "${artifact_dir}/spring.json" \
  "${artifact_dir}/hikari.log" \
  "${artifact_dir}/postgres.tsv" \
  "${artifact_dir}/deploy.json" \
  "${artifact_dir}/cache.json" \
  "${artifact_dir}/timeline.json"

cat >"${input_tsv}" <<TSV
scenario	run_id	executed_at_utc	duration_min	source_ips	run_script	k6_summary_ref	nginx_access_ref	spring_metrics_ref	hikari_log_ref	postgres_wait_ref	deploy_event_ref	cache_state_ref	timeline_ref	edge_429_rate	backend_429_count	unknown_429_count	five_xx_count	nginx_499_count	hikari_validation_warnings	db_pool_pending_max	p999_ms
hikari-lifetime	run-hikari-001	2026-05-02T01:00:00Z	10	1	tools/test/run-transaction-read-weighted-10m-soak-gate.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	n/a	n/a	${artifact_dir}/timeline.json	0.01	0	0	0	0	0	0	420
real-ip-multisource	run-realip-001	2026-05-02T01:20:00Z	2	3	tools/test/run-transaction-read-edge-delay-contract-gate.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	n/a	n/a	${artifact_dir}/timeline.json	0.07	0	0	0	0	0	0	430
mixed-workload-30m	run-mixed-001	2026-05-02T02:00:00Z	30	1	tools/test/run-t3micro-mixed-workload-soak.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	n/a	n/a	${artifact_dir}/timeline.json	0.08	0	0	0	0	0	0	450
cold-warm-cache	run-cache-001	2026-05-02T02:40:00Z	3	1	tools/test/run-transaction-100m-cold-start-cache-warm-gate.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	n/a	${artifact_dir}/cache.json	${artifact_dir}/timeline.json	0.02	0	0	0	0	0	0	410
deploy-drain	run-drain-001	2026-05-02T03:00:00Z	5	1	tools/test/run-staging-deploy-transaction-replay-gate.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	${artifact_dir}/deploy.json	n/a	${artifact_dir}/timeline.json	0.04	0	0	0	0	0	0	470
p999-long-correlation	run-p999-001	2026-05-02T03:20:00Z	30	1	tools/test/run-transaction-read-p999-spike-attribution.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	n/a	n/a	${artifact_dir}/timeline.json	0.06	0	0	0	0	0	0	490
TSV

echo "[transaction-read-oci-evidence-execution] print plan"
plan="$(
  OCI_EVIDENCE_EXECUTION_NAME=oci-exec-check \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=oci-exec-check" <<<"${plan}" >/dev/null
grep -F "required_scenarios=hikari-lifetime,real-ip-multisource,mixed-workload-30m,cold-warm-cache,deploy-drain,p999-long-correlation" <<<"${plan}" >/dev/null
grep -F "mixed_min_duration_min=30" <<<"${plan}" >/dev/null
grep -F "p999_min_duration_min=30" <<<"${plan}" >/dev/null
grep -F "min_real_source_ips=2" <<<"${plan}" >/dev/null

echo "[transaction-read-oci-evidence-execution] pass report"
output="$(
  OCI_EVIDENCE_EXECUTION_NAME=oci-exec-check \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/oci-exec-check-oci-evidence-execution.tsv"
test "${report_md}" = "${output_dir}/oci-exec-check-oci-evidence-execution.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "execution artifacts: k6, nginx access, Spring metrics, Hikari log, PostgreSQL wait, timeline" "${report_md}" >/dev/null
grep -F "unknown 429 hard-zero" "${report_md}" >/dev/null
grep -F "real-IP minimum sources: 2" "${report_md}" >/dev/null
grep -F $'mixed-workload-30m\tpass\tok\trun-mixed-001\t30\t1\ttools/test/run-t3micro-mixed-workload-soak.sh' "${summary_tsv}" >/dev/null
grep -F $'real-ip-multisource\tpass\tok\trun-realip-001\t2\t3' "${summary_tsv}" >/dev/null

echo "[transaction-read-oci-evidence-execution] failure report"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "mixed-workload-30m" { $14 = "n/a"; $17 = 1; $18 = 1; $19 = 1; $20 = 2; $21 = 2; $22 = 560 } { print }' \
  "${input_tsv}" >"${input_tsv}.fail"
if OCI_EVIDENCE_EXECUTION_NAME=oci-exec-fail \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}.fail" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "OCI execution gate unexpectedly passed missing artifact and hard-zero failure" >&2
  exit 1
fi

OCI_EVIDENCE_EXECUTION_NAME=oci-exec-fail \
OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}.fail" \
OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F $'mixed-workload-30m\tfail\t' "${output_dir}/oci-exec-fail-oci-evidence-execution.tsv" >/dev/null
grep -F "timeline-missing" "${output_dir}/oci-exec-fail-oci-evidence-execution.tsv" >/dev/null
grep -F "unknown429>0" "${output_dir}/oci-exec-fail-oci-evidence-execution.tsv" >/dev/null
grep -F "5xx>0" "${output_dir}/oci-exec-fail-oci-evidence-execution.tsv" >/dev/null
grep -F "499>0" "${output_dir}/oci-exec-fail-oci-evidence-execution.tsv" >/dev/null
grep -F "hikari-warning>0" "${output_dir}/oci-exec-fail-oci-evidence-execution.tsv" >/dev/null
grep -F "pool-pending>0" "${output_dir}/oci-exec-fail-oci-evidence-execution.tsv" >/dev/null
grep -F "p999>500" "${output_dir}/oci-exec-fail-oci-evidence-execution.tsv" >/dev/null

echo "[transaction-read-oci-evidence-execution] missing scenario fails"
awk -F '\t' '$1 != "deploy-drain"' "${input_tsv}" >"${input_tsv}.missing"
if OCI_EVIDENCE_EXECUTION_NAME=oci-exec-missing \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}.missing" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "OCI execution gate unexpectedly passed missing deploy-drain scenario" >&2
  exit 1
fi

echo "[transaction-read-oci-evidence-execution] real IP source fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "real-ip-multisource" { $5 = 1 } { print }' \
  "${input_tsv}" >"${input_tsv}.source-fail"
if OCI_EVIDENCE_EXECUTION_NAME=oci-exec-source-fail \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}.source-fail" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "OCI execution gate unexpectedly passed single source real-IP evidence" >&2
  exit 1
fi
