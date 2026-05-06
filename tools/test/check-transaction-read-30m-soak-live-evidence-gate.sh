#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-30m-soak-live-evidence-gate.sh"

echo "[transaction-read-30m-soak-live-evidence] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/30m-soak-live.tsv"
output_dir="${temp_dir}/output"
artifact_dir="${temp_dir}/artifacts"
mkdir -p "${artifact_dir}"

touch \
  "${artifact_dir}/k6.json" \
  "${artifact_dir}/nginx.jsonl" \
  "${artifact_dir}/spring.json" \
  "${artifact_dir}/hikari.log" \
  "${artifact_dir}/postgres-wait.tsv" \
  "${artifact_dir}/timeline.json" \
  "${artifact_dir}/checkpoint.tsv" \
  "${artifact_dir}/temp-files.tsv" \
  "${artifact_dir}/nginx-upstream.tsv" \
  "${artifact_dir}/hikari-config.tsv" \
  "${artifact_dir}/hikari-zero-warning.md"

cat >"${input_tsv}" <<TSV
scenario	run_id	executed_at_utc	duration_min	source_ips	run_script	k6_summary_ref	nginx_access_ref	spring_metrics_ref	hikari_log_ref	postgres_wait_ref	deploy_event_ref	cache_state_ref	timeline_ref	edge_429_rate	backend_429_count	unknown_429_count	five_xx_count	nginx_499_count	hikari_validation_warnings	db_pool_pending_max	p999_ms	postgres_checkpoint_ref	postgres_temp_file_ref	nginx_upstream_latency_ref	workload_mix_ref	workload_component_ref	outbox_lag_ref	outbox_lag_max	deploy_retry_contract_ref	deploy_reconnect_success_count	deploy_499_budget_ref	p95_ms	p99_ms	max_ms	postgres_checkpoint_count	postgres_temp_file_count	nginx_upstream_p95_ms	hikari_config_ref	hikari_max_lifetime_ms	hikari_keepalive_time_ms	postgres_idle_timeout_ms	oci_nat_idle_timeout_ms	hikari_zero_warning_soak_ref	workload_components	read_p999_ms	read_429_source_ref
hikari-lifetime	run-soak-live-001	2026-05-04T07:30:00Z	30	1	tools/test/run-transaction-read-weighted-10m-soak-gate.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres-wait.tsv	n/a	n/a	${artifact_dir}/timeline.json	0.01	0	0	0	0	0	0	420	n/a	n/a	n/a	n/a	n/a	n/a	0	n/a	0	n/a	82	210	610	0	0	14.1	${artifact_dir}/hikari-config.tsv	45000	30000	300000	350000	${artifact_dir}/hikari-zero-warning.md	n/a	0	n/a
p999-long-correlation	run-soak-live-001	2026-05-04T07:30:00Z	30	1	tools/test/run-transaction-read-p999-spike-attribution.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres-wait.tsv	n/a	n/a	${artifact_dir}/timeline.json	0.06	0	0	0	0	0	0	490	${artifact_dir}/checkpoint.tsv	${artifact_dir}/temp-files.tsv	${artifact_dir}/nginx-upstream.tsv	n/a	n/a	n/a	0	n/a	0	n/a	95	220	650	3	0	18.5	n/a	0	0	0	0	n/a	n/a	0	n/a
TSV

echo "[transaction-read-30m-soak-live-evidence] print plan"
plan="$(
  SOAK_30M_LIVE_NAME=soak-live-check \
  SOAK_30M_LIVE_INPUT_TSV="${input_tsv}" \
  SOAK_30M_LIVE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=soak-live-check" <<<"${plan}" >/dev/null
grep -F "required_scenarios=hikari-lifetime,p999-long-correlation" <<<"${plan}" >/dev/null
grep -F "min_duration_min=30" <<<"${plan}" >/dev/null
grep -F "require_shared_run_id=true" <<<"${plan}" >/dev/null
grep -F "latency_percentiles=p95,p99,p99.9,max" <<<"${plan}" >/dev/null
grep -F "required_artifacts=hikari_log,postgres_wait,postgres_checkpoint,postgres_temp_file,nginx_upstream_latency,hikari_config,hikari_zero_warning_soak" <<<"${plan}" >/dev/null
grep -F "postgres_temp_file_delta_budget=0" <<<"${plan}" >/dev/null

echo "[transaction-read-30m-soak-live-evidence] pass report"
output="$(
  SOAK_30M_LIVE_NAME=soak-live-check \
  SOAK_30M_LIVE_INPUT_TSV="${input_tsv}" \
  SOAK_30M_LIVE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/soak-live-check-30m-soak-live-evidence.tsv"
test "${report_md}" = "${output_dir}/soak-live-check-30m-soak-live-evidence.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "shared run id: run-soak-live-001" "${report_md}" >/dev/null
grep -F "latency percentiles: p95/p99/p99.9/max" "${report_md}" >/dev/null
grep -F "Hikari pending: 0" "${report_md}" >/dev/null
grep -F "Hikari validation warnings: 0" "${report_md}" >/dev/null
grep -F "PostgreSQL wait/checkpoint/temp file artifacts: verified" "${report_md}" >/dev/null
grep -F "PostgreSQL temp file delta: 0 (budget <= 0)" "${report_md}" >/dev/null
grep -F "Nginx upstream latency artifact: verified" "${report_md}" >/dev/null
grep -F "429/5xx/499 hard-zero: backend=0 unknown=0 5xx=0 499=0" "${report_md}" >/dev/null
grep -F "Hikari lifetime alignment: verified" "${report_md}" >/dev/null
grep -F $'run-soak-live-001\tpass\tok\t30\t30\t95\t220\t490\t650\t0\t0' "${summary_tsv}" >/dev/null

echo "[transaction-read-30m-soak-live-evidence] shared run id mismatch fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "p999-long-correlation" { $2 = "run-soak-live-002" } { print }' \
  "${input_tsv}" >"${input_tsv}.run-id-fail"
if SOAK_30M_LIVE_NAME=soak-live-run-id-fail \
  SOAK_30M_LIVE_INPUT_TSV="${input_tsv}.run-id-fail" \
  SOAK_30M_LIVE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "30m soak live evidence unexpectedly passed split run ids" >&2
  exit 1
fi

echo "[transaction-read-30m-soak-live-evidence] temp file artifact fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "p999-long-correlation" { $24 = "n/a" } { print }' \
  "${input_tsv}" >"${input_tsv}.temp-file-fail"
if SOAK_30M_LIVE_NAME=soak-live-temp-fail \
  SOAK_30M_LIVE_INPUT_TSV="${input_tsv}.temp-file-fail" \
  SOAK_30M_LIVE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "30m soak live evidence unexpectedly passed missing temp file artifact" >&2
  exit 1
fi

echo "[transaction-read-30m-soak-live-evidence] temp file delta budget fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "p999-long-correlation" { $37 = 1 } { print }' \
  "${input_tsv}" >"${input_tsv}.temp-file-delta-fail"
if SOAK_30M_LIVE_NAME=soak-live-temp-delta-fail \
  SOAK_30M_LIVE_INPUT_TSV="${input_tsv}.temp-file-delta-fail" \
  SOAK_30M_LIVE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "30m soak live evidence unexpectedly passed temp file delta budget violation" >&2
  exit 1
fi

echo "[transaction-read-30m-soak-live-evidence] Hikari warning fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "hikari-lifetime" { $20 = 1 } { print }' \
  "${input_tsv}" >"${input_tsv}.hikari-warning-fail"
if SOAK_30M_LIVE_NAME=soak-live-hikari-warning-fail \
  SOAK_30M_LIVE_INPUT_TSV="${input_tsv}.hikari-warning-fail" \
  SOAK_30M_LIVE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "30m soak live evidence unexpectedly passed Hikari validation warning" >&2
  exit 1
fi

echo "[transaction-read-30m-soak-live-evidence] Hikari lifetime alignment fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "hikari-lifetime" { $40 = 360000 } { print }' \
  "${input_tsv}" >"${input_tsv}.hikari-lifetime-fail"
if SOAK_30M_LIVE_NAME=soak-live-hikari-lifetime-fail \
  SOAK_30M_LIVE_INPUT_TSV="${input_tsv}.hikari-lifetime-fail" \
  SOAK_30M_LIVE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "30m soak live evidence unexpectedly passed invalid Hikari lifetime alignment" >&2
  exit 1
fi

echo "[transaction-read-30m-soak-live-evidence] p99.9 budget fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } $1 == "p999-long-correlation" { $22 = 550 } { print }' \
  "${input_tsv}" >"${input_tsv}.p999-fail"
if SOAK_30M_LIVE_NAME=soak-live-p999-fail \
  SOAK_30M_LIVE_INPUT_TSV="${input_tsv}.p999-fail" \
  SOAK_30M_LIVE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "30m soak live evidence unexpectedly passed p99.9 budget violation" >&2
  exit 1
fi
