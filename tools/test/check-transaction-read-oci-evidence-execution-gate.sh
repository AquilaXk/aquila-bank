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
  "${artifact_dir}/timeline.json" \
  "${artifact_dir}/checkpoint.tsv" \
  "${artifact_dir}/temp-files.tsv" \
  "${artifact_dir}/nginx-upstream.tsv" \
  "${artifact_dir}/workload-mix.json" \
  "${artifact_dir}/workload-components.tsv" \
  "${artifact_dir}/outbox-lag.tsv" \
  "${artifact_dir}/read-429-source.tsv" \
  "${artifact_dir}/deploy-retry.json" \
  "${artifact_dir}/deploy-499.tsv"

cat >"${input_tsv}" <<TSV
scenario	run_id	executed_at_utc	duration_min	source_ips	run_script	k6_summary_ref	nginx_access_ref	spring_metrics_ref	hikari_log_ref	postgres_wait_ref	deploy_event_ref	cache_state_ref	timeline_ref	edge_429_rate	backend_429_count	unknown_429_count	five_xx_count	nginx_499_count	hikari_validation_warnings	db_pool_pending_max	p999_ms	postgres_checkpoint_ref	postgres_temp_file_ref	nginx_upstream_latency_ref	workload_mix_ref	workload_component_ref	outbox_lag_ref	outbox_lag_max	deploy_retry_contract_ref	deploy_reconnect_success_count	deploy_499_budget_ref	p95_ms	p99_ms	max_ms	postgres_checkpoint_count	postgres_temp_file_count	nginx_upstream_p95_ms	hikari_config_ref	hikari_max_lifetime_ms	hikari_keepalive_time_ms	postgres_idle_timeout_ms	oci_nat_idle_timeout_ms	hikari_zero_warning_soak_ref	workload_components	read_p999_ms	read_429_source_ref
hikari-lifetime	run-hikari-001	2026-05-02T01:00:00Z	30	1	tools/test/run-transaction-read-weighted-10m-soak-gate.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	n/a	n/a	${artifact_dir}/timeline.json	0.01	0	0	0	0	0	0	420	n/a	n/a	n/a	n/a	n/a	n/a	0	n/a	0	n/a	80	210	610	0	0	14.1	${artifact_dir}/hikari-config.tsv	45000	30000	300000	350000	${artifact_dir}/hikari-zero-warning.md	n/a	0	n/a
real-ip-multisource	run-realip-001	2026-05-02T01:20:00Z	2	3	tools/test/run-transaction-read-edge-delay-contract-gate.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	n/a	n/a	${artifact_dir}/timeline.json	0.07	0	0	0	0	0	0	430	n/a	n/a	n/a	n/a	n/a	n/a	0	n/a	0	n/a	82	215	620	0	0	15.2	n/a	0	0	0	0	n/a	n/a	0	n/a
mixed-workload-30m	run-mixed-001	2026-05-02T02:00:00Z	30	1	tools/test/run-t3micro-mixed-workload-soak.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	n/a	n/a	${artifact_dir}/timeline.json	0.08	0	0	0	0	0	0	450	n/a	n/a	n/a	${artifact_dir}/workload-mix.json	${artifact_dir}/workload-components.tsv	${artifact_dir}/outbox-lag.tsv	0	n/a	0	n/a	85	225	630	1	0	16.3	n/a	0	0	0	0	n/a	read,write,auth,notification,sse	440	${artifact_dir}/read-429-source.tsv
cold-warm-cache	run-cache-001	2026-05-02T02:40:00Z	3	1	tools/test/run-transaction-100m-cold-start-cache-warm-gate.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	n/a	${artifact_dir}/cache.json	${artifact_dir}/timeline.json	0.02	0	0	0	0	0	0	410	n/a	n/a	n/a	n/a	n/a	n/a	0	n/a	0	n/a	78	205	600	0	0	13.8	n/a	0	0	0	0	n/a	n/a	0	n/a
deploy-drain	run-drain-001	2026-05-02T03:00:00Z	5	1	tools/test/run-staging-deploy-transaction-replay-gate.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	${artifact_dir}/deploy.json	n/a	${artifact_dir}/timeline.json	0.04	0	0	0	0	0	0	470	n/a	n/a	n/a	n/a	n/a	n/a	0	${artifact_dir}/deploy-retry.json	2	${artifact_dir}/deploy-499.tsv	90	235	640	1	0	17.4	n/a	0	0	0	0	n/a	n/a	0	n/a
p999-long-correlation	run-p999-001	2026-05-02T03:20:00Z	30	1	tools/test/run-transaction-read-p999-spike-attribution.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	n/a	n/a	${artifact_dir}/timeline.json	0.06	0	0	0	0	0	0	490	${artifact_dir}/checkpoint.tsv	${artifact_dir}/temp-files.tsv	${artifact_dir}/nginx-upstream.tsv	n/a	n/a	n/a	0	n/a	0	n/a	95	220	650	3	0	18.5	n/a	0	0	0	0	n/a	n/a	0	n/a
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
grep -F "hikari_min_duration_min=30" <<<"${plan}" >/dev/null
grep -F "min_real_source_ips=2" <<<"${plan}" >/dev/null
grep -F "max_postgres_temp_file_delta=0" <<<"${plan}" >/dev/null

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
grep -F "p99.9 closure artifacts: PostgreSQL checkpoint, temp file, Nginx upstream latency" "${report_md}" >/dev/null
grep -F "latency percentiles: p95, p99, p99.9, max" "${report_md}" >/dev/null
grep -F "PostgreSQL temp file delta max: 0" "${report_md}" >/dev/null
grep -F "p999 long correlation metrics: PostgreSQL checkpoint delta, temp file delta, Nginx upstream p95" "${report_md}" >/dev/null
grep -F "Hikari lifetime alignment artifacts: config ref, zero-warning soak ref, timeout basis" "${report_md}" >/dev/null
grep -F "mixed workload closure artifacts: workload mix, component split, outbox lag" "${report_md}" >/dev/null
grep -F "mixed workload required components: read,write,auth,notification,sse" "${report_md}" >/dev/null
grep -F "mixed workload read p99.9 and 429 source artifact: required" "${report_md}" >/dev/null
grep -F "deploy drain closure artifacts: 499 budget, retry contract, reconnect success" "${report_md}" >/dev/null
grep -F "real-IP minimum sources: 2" "${report_md}" >/dev/null
grep -F $'\tp95_ms\tp99_ms\tmax_ms\tpostgres_checkpoint_count\tpostgres_temp_file_count\tnginx_upstream_p95_ms' "${summary_tsv}" >/dev/null
grep -F $'\thikari_config_ref\thikari_max_lifetime_ms\thikari_keepalive_time_ms\tpostgres_idle_timeout_ms\toci_nat_idle_timeout_ms\thikari_zero_warning_soak_ref' "${summary_tsv}" >/dev/null
grep -F $'\tworkload_components\tread_p999_ms\tread_429_source_ref' "${summary_tsv}" >/dev/null
grep -F $'mixed-workload-30m\tpass\tok\trun-mixed-001\t30\t1\ttools/test/run-t3micro-mixed-workload-soak.sh' "${summary_tsv}" >/dev/null
grep -F $'real-ip-multisource\tpass\tok\trun-realip-001\t2\t3' "${summary_tsv}" >/dev/null
awk -F '\t' '$1 == "p999-long-correlation" && $23 == 490 && $24 == 95 && $25 == 220 && $26 == 650 && $27 == 3 && $28 == 0 && $29 == 18.5 { found = 1 } END { exit !found }' "${summary_tsv}"
awk -F '\t' '$1 == "hikari-lifetime" && $5 == 30 && $30 ~ /hikari-config[.]tsv$/ && $31 == 45000 && $32 == 30000 && $33 == 300000 && $34 == 350000 && $35 ~ /hikari-zero-warning[.]md$/ { found = 1 } END { exit !found }' "${summary_tsv}"
awk -F '\t' '$1 == "mixed-workload-30m" && $36 == "read,write,auth,notification,sse" && $37 == 440 && $38 ~ /read-429-source[.]tsv$/ { found = 1 } END { exit !found }' "${summary_tsv}"

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

echo "[transaction-read-oci-evidence-execution] temp file delta budget fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "p999-long-correlation" { $37 = 1 } { print }' \
  "${input_tsv}" >"${input_tsv}.temp-delta-fail"
if OCI_EVIDENCE_EXECUTION_NAME=oci-exec-temp-delta-fail \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}.temp-delta-fail" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "OCI execution gate unexpectedly passed temp file delta budget violation" >&2
  exit 1
fi
OCI_EVIDENCE_EXECUTION_NAME=oci-exec-temp-delta-fail \
OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}.temp-delta-fail" \
OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
  "${runner}" >/dev/null 2>&1 || true
grep -F "postgres-temp-file-delta>0" "${output_dir}/oci-exec-temp-delta-fail-oci-evidence-execution.tsv" >/dev/null

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

echo "[transaction-read-oci-evidence-execution] p999 closure artifact fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "p999-long-correlation" { $24 = "n/a" } { print }' \
  "${input_tsv}" >"${input_tsv}.p999-artifact-fail"
if OCI_EVIDENCE_EXECUTION_NAME=oci-exec-p999-artifact-fail \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}.p999-artifact-fail" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "OCI execution gate unexpectedly passed missing p999 temp file artifact" >&2
  exit 1
fi

echo "[transaction-read-oci-evidence-execution] p999 percentile metric fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "p999-long-correlation" { $33 = "n/a" } { print }' \
  "${input_tsv}" >"${input_tsv}.p999-metric-fail"
if OCI_EVIDENCE_EXECUTION_NAME=oci-exec-p999-metric-fail \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}.p999-metric-fail" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "OCI execution gate unexpectedly passed missing p999 percentile metric" >&2
  exit 1
fi

echo "[transaction-read-oci-evidence-execution] hikari lifetime alignment fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "hikari-lifetime" { $40 = 360000 } { print }' \
  "${input_tsv}" >"${input_tsv}.hikari-lifetime-fail"
if OCI_EVIDENCE_EXECUTION_NAME=oci-exec-hikari-lifetime-fail \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}.hikari-lifetime-fail" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "OCI execution gate unexpectedly passed invalid Hikari lifetime alignment" >&2
  exit 1
fi

echo "[transaction-read-oci-evidence-execution] mixed outbox closure fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "mixed-workload-30m" { $28 = "n/a"; $29 = 3 } { print }' \
  "${input_tsv}" >"${input_tsv}.mixed-outbox-fail"
if OCI_EVIDENCE_EXECUTION_NAME=oci-exec-mixed-outbox-fail \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}.mixed-outbox-fail" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "OCI execution gate unexpectedly passed missing mixed outbox artifact" >&2
  exit 1
fi

echo "[transaction-read-oci-evidence-execution] mixed workload component fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "mixed-workload-30m" { $45 = "read,write,notification,sse" } { print }' \
  "${input_tsv}" >"${input_tsv}.mixed-component-fail"
if OCI_EVIDENCE_EXECUTION_NAME=oci-exec-mixed-component-fail \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}.mixed-component-fail" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "OCI execution gate unexpectedly passed mixed workload without auth component" >&2
  exit 1
fi

echo "[transaction-read-oci-evidence-execution] deploy retry closure fail"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "deploy-drain" { $30 = "n/a"; $31 = 0; $32 = "n/a" } { print }' \
  "${input_tsv}" >"${input_tsv}.deploy-retry-fail"
if OCI_EVIDENCE_EXECUTION_NAME=oci-exec-deploy-retry-fail \
  OCI_EVIDENCE_EXECUTION_INPUT_TSV="${input_tsv}.deploy-retry-fail" \
  OCI_EVIDENCE_EXECUTION_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "OCI execution gate unexpectedly passed missing deploy retry and 499 evidence" >&2
  exit 1
fi
