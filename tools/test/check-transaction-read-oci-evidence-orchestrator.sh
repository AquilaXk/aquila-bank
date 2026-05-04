#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-oci-evidence-orchestrator.sh"

echo "[transaction-read-oci-evidence-orchestrator] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/oci-evidence-artifacts.tsv"
output_dir="${temp_dir}/output"

cat >"${input_tsv}" <<'TSV'
scenario	run_id	duration_min	source_ips	k6_summary_ref	nginx_access_ref	spring_metrics_ref	hikari_log_ref	postgres_wait_ref	deploy_event_ref	cache_state_ref	timeline_ref	edge_429_rate	backend_429_count	unknown_429_count	five_xx_count	nginx_499_count	hikari_validation_warnings	db_pool_pending_max	p999_ms	postgres_checkpoint_ref	postgres_temp_file_ref	nginx_upstream_latency_ref	workload_mix_ref	workload_component_ref	outbox_lag_ref	outbox_lag_max	deploy_retry_contract_ref	deploy_reconnect_success_count	deploy_499_budget_ref	p95_ms	p99_ms	max_ms	postgres_checkpoint_count	postgres_temp_file_count	nginx_upstream_p95_ms
hikari-lifetime	run-hikari-001	10	1	oci://run-hikari/k6.json	oci://run-hikari/nginx.jsonl	oci://run-hikari/spring.json	oci://run-hikari/hikari.log	oci://run-hikari/postgres.tsv	n/a	n/a	oci://run-hikari/timeline.json	0.01	0	0	0	0	0	0	420	n/a	n/a	n/a	n/a	n/a	n/a	0	n/a	0	n/a	80	210	610	0	0	14.1
real-ip-multisource	run-realip-001	2	3	oci://run-realip/k6.json	oci://run-realip/nginx.jsonl	oci://run-realip/spring.json	oci://run-realip/hikari.log	oci://run-realip/postgres.tsv	n/a	n/a	oci://run-realip/timeline.json	0.07	0	0	0	0	0	0	430	n/a	n/a	n/a	n/a	n/a	n/a	0	n/a	0	n/a	82	215	620	0	0	15.2
mixed-workload-30m	run-mixed-001	30	1	oci://run-mixed/k6.json	oci://run-mixed/nginx.jsonl	oci://run-mixed/spring.json	oci://run-mixed/hikari.log	oci://run-mixed/postgres.tsv	n/a	n/a	oci://run-mixed/timeline.json	0.08	0	0	0	0	0	0	450	n/a	n/a	n/a	oci://run-mixed/workload-mix.json	oci://run-mixed/workload-components.tsv	oci://run-mixed/outbox-lag.tsv	0	n/a	0	n/a	85	225	630	1	0	16.3
cold-warm-cache	run-cache-001	3	1	oci://run-cache/k6.json	oci://run-cache/nginx.jsonl	oci://run-cache/spring.json	oci://run-cache/hikari.log	oci://run-cache/postgres.tsv	n/a	oci://run-cache/cache.json	oci://run-cache/timeline.json	0.02	0	0	0	0	0	0	410	n/a	n/a	n/a	n/a	n/a	n/a	0	n/a	0	n/a	78	205	600	0	0	13.8
deploy-drain	run-drain-001	5	1	oci://run-drain/k6.json	oci://run-drain/nginx.jsonl	oci://run-drain/spring.json	oci://run-drain/hikari.log	oci://run-drain/postgres.tsv	oci://run-drain/deploy.json	n/a	oci://run-drain/timeline.json	0.04	0	0	0	0	0	0	470	n/a	n/a	n/a	n/a	n/a	n/a	0	oci://run-drain/deploy-retry.json	2	oci://run-drain/deploy-499.tsv	90	235	640	1	0	17.4
p999-long-correlation	run-p999-001	30	1	oci://run-p999/k6.json	oci://run-p999/nginx.jsonl	oci://run-p999/spring.json	oci://run-p999/hikari.log	oci://run-p999/postgres.tsv	n/a	n/a	oci://run-p999/timeline.json	0.06	0	0	0	0	0	0	490	oci://run-p999/checkpoint.tsv	oci://run-p999/temp-files.tsv	oci://run-p999/nginx-upstream.tsv	n/a	n/a	n/a	0	n/a	0	n/a	95	220	650	3	0	18.5
TSV

echo "[transaction-read-oci-evidence-orchestrator] print plan"
plan="$(
  OCI_EVIDENCE_ORCH_NAME=orch-check \
  OCI_EVIDENCE_ORCH_INPUT_TSV="${input_tsv}" \
  OCI_EVIDENCE_ORCH_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=orch-check" <<<"${plan}" >/dev/null
grep -F "mixed_duration_min=30" <<<"${plan}" >/dev/null
grep -F "p999_duration_min=30" <<<"${plan}" >/dev/null
grep -F "execution_gate=tools/test/run-transaction-read-oci-evidence-execution-gate.sh" <<<"${plan}" >/dev/null
grep -F "mixed_script=tools/test/run-t3micro-mixed-workload-soak.sh" <<<"${plan}" >/dev/null
grep -F "cold_warm_script=tools/test/run-transaction-100m-cold-start-cache-warm-gate.sh" <<<"${plan}" >/dev/null
grep -F "deploy_drain_script=tools/test/run-staging-deploy-transaction-replay-gate.sh" <<<"${plan}" >/dev/null
grep -F "p999_script=tools/test/run-transaction-read-p999-spike-attribution.sh" <<<"${plan}" >/dev/null

echo "[transaction-read-oci-evidence-orchestrator] pass report"
output="$(
  OCI_EVIDENCE_ORCH_NAME=orch-check \
  OCI_EVIDENCE_ORCH_INPUT_TSV="${input_tsv}" \
  OCI_EVIDENCE_ORCH_OUTPUT_DIR="${output_dir}" \
  OCI_EVIDENCE_ORCH_EXECUTED_AT_UTC=2026-05-02T09:00:00Z \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
execution_tsv="${output_dir}/orch-check-oci-evidence-execution-input.tsv"
test "${report_md}" = "${output_dir}/orch-check-oci-evidence-orchestrator.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "execution gate report:" "${report_md}" >/dev/null
grep -F $'mixed-workload-30m\trun-mixed-001\t2026-05-02T09:00:00Z\t30\t1\ttools/test/run-t3micro-mixed-workload-soak.sh' "${execution_tsv}" >/dev/null
grep -F $'cold-warm-cache\trun-cache-001\t2026-05-02T09:00:00Z\t3\t1\ttools/test/run-transaction-100m-cold-start-cache-warm-gate.sh' "${execution_tsv}" >/dev/null
grep -F $'p999-long-correlation\trun-p999-001\t2026-05-02T09:00:00Z\t30\t1\ttools/test/run-transaction-read-p999-spike-attribution.sh' "${execution_tsv}" >/dev/null
grep -F "oci://run-p999/checkpoint.tsv" "${execution_tsv}" >/dev/null
grep -F "oci://run-p999/temp-files.tsv" "${execution_tsv}" >/dev/null
grep -F "oci://run-p999/nginx-upstream.tsv" "${execution_tsv}" >/dev/null
grep -F $'\tp95_ms\tp99_ms\tmax_ms\tpostgres_checkpoint_count\tpostgres_temp_file_count\tnginx_upstream_p95_ms' "${execution_tsv}" >/dev/null
awk -F '\t' '$1 == "p999-long-correlation" && $33 == 95 && $34 == 220 && $35 == 650 && $36 == 3 && $37 == 0 && $38 == 18.5 { found = 1 } END { exit !found }' "${execution_tsv}"
grep -F "oci://run-mixed/workload-mix.json" "${execution_tsv}" >/dev/null
grep -F "oci://run-mixed/workload-components.tsv" "${execution_tsv}" >/dev/null
grep -F "oci://run-mixed/outbox-lag.tsv" "${execution_tsv}" >/dev/null
grep -F "oci://run-drain/deploy-retry.json" "${execution_tsv}" >/dev/null
grep -F "oci://run-drain/deploy-499.tsv" "${execution_tsv}" >/dev/null

echo "[transaction-read-oci-evidence-orchestrator] missing scenario fails"
awk -F '\t' '$1 != "deploy-drain"' "${input_tsv}" >"${input_tsv}.missing"
if OCI_EVIDENCE_ORCH_NAME=orch-missing \
  OCI_EVIDENCE_ORCH_INPUT_TSV="${input_tsv}.missing" \
  OCI_EVIDENCE_ORCH_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "orchestrator unexpectedly passed missing deploy-drain scenario" >&2
  exit 1
fi
