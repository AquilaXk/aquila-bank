#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-deploy-drain-under-load-gate.sh"

echo "[transaction-read-deploy-drain] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/deploy-drain-evidence.tsv"
output_dir="${temp_dir}/output"
artifact_dir="${temp_dir}/artifacts"
mkdir -p "${artifact_dir}"
touch "${artifact_dir}/k6.json" "${artifact_dir}/nginx.jsonl" "${artifact_dir}/spring.json" \
  "${artifact_dir}/hikari.log" "${artifact_dir}/postgres.tsv" "${artifact_dir}/timeline.json" \
  "${artifact_dir}/deploy.json"

cat >"${input_tsv}" <<TSV
scenario	run_id	executed_at_utc	duration_min	source_ips	run_script	k6_summary_ref	nginx_access_ref	spring_metrics_ref	hikari_log_ref	postgres_wait_ref	deploy_event_ref	cache_state_ref	timeline_ref	edge_429_rate	backend_429_count	unknown_429_count	five_xx_count	nginx_499_count	hikari_validation_warnings	db_pool_pending_max	p999_ms
deploy-drain	run-drain-001	2026-05-02T03:00:00Z	5	1	tools/test/run-staging-deploy-transaction-replay-gate.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	${artifact_dir}/deploy.json	n/a	${artifact_dir}/timeline.json	0.04	0	0	0	0	0	0	470
TSV

echo "[transaction-read-deploy-drain] print plan"
plan="$(
  DEPLOY_DRAIN_GATE_NAME=deploy-drain-check \
  DEPLOY_DRAIN_GATE_INPUT_TSV="${input_tsv}" \
  DEPLOY_DRAIN_GATE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=deploy-drain-check" <<<"${plan}" >/dev/null
grep -F "paced_load_required=true" <<<"${plan}" >/dev/null
grep -F "actions=backend-restart,blue-green-drain" <<<"${plan}" >/dev/null
grep -F "execution_gate=tools/test/run-transaction-read-oci-evidence-execution-gate.sh" <<<"${plan}" >/dev/null

echo "[transaction-read-deploy-drain] pass report"
output="$(
  DEPLOY_DRAIN_GATE_NAME=deploy-drain-check \
  DEPLOY_DRAIN_GATE_INPUT_TSV="${input_tsv}" \
  DEPLOY_DRAIN_GATE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
test "${report_md}" = "${output_dir}/deploy-drain-check-deploy-drain-under-load.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "paced load 중 backend restart/blue-green drain" "${report_md}" >/dev/null
grep -F "5xx/499/unknown 429 hard-zero" "${report_md}" >/dev/null
grep -F "deploy event artifact: required" "${report_md}" >/dev/null

echo "[transaction-read-deploy-drain] missing deploy event fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $12 = "n/a"; print }' "${input_tsv}" >"${input_tsv}.missing-deploy"
if DEPLOY_DRAIN_GATE_NAME=deploy-drain-missing \
  DEPLOY_DRAIN_GATE_INPUT_TSV="${input_tsv}.missing-deploy" \
  DEPLOY_DRAIN_GATE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "deploy drain gate unexpectedly passed missing deploy event" >&2
  exit 1
fi
