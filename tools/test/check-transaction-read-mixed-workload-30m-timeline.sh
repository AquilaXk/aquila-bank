#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-mixed-workload-30m-timeline.sh"

echo "[transaction-read-mixed-30m-timeline] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

input_tsv="${temp_dir}/mixed-evidence.tsv"
output_dir="${temp_dir}/output"
artifact_dir="${temp_dir}/artifacts"
mkdir -p "${artifact_dir}"
touch "${artifact_dir}/k6.json" "${artifact_dir}/nginx.jsonl" "${artifact_dir}/spring.json" \
  "${artifact_dir}/hikari.log" "${artifact_dir}/postgres.tsv" "${artifact_dir}/timeline.json" \
  "${artifact_dir}/workload-mix.json" "${artifact_dir}/workload-components.tsv" "${artifact_dir}/outbox-lag.tsv" \
  "${artifact_dir}/read-429-source.tsv" "${artifact_dir}/read-buckets.tsv" "${artifact_dir}/write-status.tsv" \
  "${artifact_dir}/idempotency.tsv"
printf 'metric\tvalue\nreplay_count\t2\nconflict_count\t0\n' >"${artifact_dir}/idempotency.tsv"

cat >"${input_tsv}" <<TSV
scenario	run_id	executed_at_utc	duration_min	source_ips	run_script	k6_summary_ref	nginx_access_ref	spring_metrics_ref	hikari_log_ref	postgres_wait_ref	deploy_event_ref	cache_state_ref	timeline_ref	edge_429_rate	backend_429_count	unknown_429_count	five_xx_count	nginx_499_count	hikari_validation_warnings	db_pool_pending_max	p999_ms	workload_mix_ref	workload_component_ref	outbox_lag_ref	outbox_lag_max	workload_components	read_p999_ms	read_429_source_ref	read_buckets	read_bucket_ref	write_2xx_count	write_unexpected_status_count	write_status_ref	idempotency_replay_count	idempotency_conflict_count	idempotency_evidence_ref	write_accepted_ratio	min_write_accepted_ratio
mixed-workload-30m	run-mixed-001	2026-05-02T02:00:00Z	30	1	tools/test/run-t3micro-mixed-workload-soak.sh	${artifact_dir}/k6.json	${artifact_dir}/nginx.jsonl	${artifact_dir}/spring.json	${artifact_dir}/hikari.log	${artifact_dir}/postgres.tsv	n/a	n/a	${artifact_dir}/timeline.json	0.08	0	0	0	0	0	0	450	${artifact_dir}/workload-mix.json	${artifact_dir}/workload-components.tsv	${artifact_dir}/outbox-lag.tsv	0	read,write,auth,notification,sse	440	${artifact_dir}/read-429-source.tsv	hot,cold,archive	${artifact_dir}/read-buckets.tsv	4	0	${artifact_dir}/write-status.tsv	2	0	${artifact_dir}/idempotency.tsv	0.806	0.80
TSV

echo "[transaction-read-mixed-30m-timeline] print plan"
plan="$(
  MIXED_30M_TIMELINE_NAME=mixed-30m-check \
  MIXED_30M_TIMELINE_INPUT_TSV="${input_tsv}" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=mixed-30m-check" <<<"${plan}" >/dev/null
grep -F "min_duration_min=30" <<<"${plan}" >/dev/null
grep -F "workload=read,write,auth,notification,sse" <<<"${plan}" >/dev/null
grep -F "write_evidence=idempotency replay/conflict + outbox lag" <<<"${plan}" >/dev/null
grep -F "execution_gate=tools/test/run-transaction-read-oci-evidence-execution-gate.sh" <<<"${plan}" >/dev/null

echo "[transaction-read-mixed-30m-timeline] pass report"
output="$(
  MIXED_30M_TIMELINE_NAME=mixed-30m-check \
  MIXED_30M_TIMELINE_INPUT_TSV="${input_tsv}" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
test "${report_md}" = "${output_dir}/mixed-30m-check-mixed-workload-30m-timeline.md"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "read + write interference + auth + SSE/notification" "${report_md}" >/dev/null
grep -F "Prometheus/Grafana long timeline artifact: required" "${report_md}" >/dev/null
grep -F "p95/p99.9, 429 source, 499/5xx, Hikari pending/warning hard gate" "${report_md}" >/dev/null
grep -F "read/write/auth/notification/SSE components: required" "${report_md}" >/dev/null
grep -F "read p99.9 and 429 source artifact: required" "${report_md}" >/dev/null
grep -F "hot/cold/archive read bucket artifact: required" "${report_md}" >/dev/null
grep -F "write 2xx and status classification artifact: required" "${report_md}" >/dev/null
grep -F "idempotency replay/conflict artifact: required" "${report_md}" >/dev/null
grep -F "write accepted ratio guardrail: required" "${report_md}" >/dev/null
grep -F "workload mix/component and outbox lag artifact: required" "${report_md}" >/dev/null

echo "[transaction-read-mixed-30m-timeline] unknown 429 fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $17 = 1; print }' "${input_tsv}" >"${input_tsv}.unknown"
if MIXED_30M_TIMELINE_NAME=mixed-30m-unknown \
  MIXED_30M_TIMELINE_INPUT_TSV="${input_tsv}.unknown" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "mixed 30m timeline unexpectedly passed unknown 429" >&2
  exit 1
fi

echo "[transaction-read-mixed-30m-timeline] outbox lag fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $25 = "n/a"; $26 = 2; print }' "${input_tsv}" >"${input_tsv}.outbox"
if MIXED_30M_TIMELINE_NAME=mixed-30m-outbox \
  MIXED_30M_TIMELINE_INPUT_TSV="${input_tsv}.outbox" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "mixed 30m timeline unexpectedly passed missing outbox evidence" >&2
  exit 1
fi

echo "[transaction-read-mixed-30m-timeline] auth component fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $27 = "read,write,notification,sse"; print }' "${input_tsv}" >"${input_tsv}.component"
if MIXED_30M_TIMELINE_NAME=mixed-30m-component \
  MIXED_30M_TIMELINE_INPUT_TSV="${input_tsv}.component" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "mixed 30m timeline unexpectedly passed missing auth component" >&2
  exit 1
fi

echo "[transaction-read-mixed-30m-timeline] read bucket fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $30 = "hot,cold"; print }' "${input_tsv}" >"${input_tsv}.bucket"
if MIXED_30M_TIMELINE_NAME=mixed-30m-bucket \
  MIXED_30M_TIMELINE_INPUT_TSV="${input_tsv}.bucket" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "mixed 30m timeline unexpectedly passed missing archive bucket" >&2
  exit 1
fi

echo "[transaction-read-mixed-30m-timeline] write 2xx fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $32 = 0; print }' "${input_tsv}" >"${input_tsv}.write"
if MIXED_30M_TIMELINE_NAME=mixed-30m-write \
  MIXED_30M_TIMELINE_INPUT_TSV="${input_tsv}.write" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "mixed 30m timeline unexpectedly passed write 2xx missing" >&2
  exit 1
fi

echo "[transaction-read-mixed-30m-timeline] idempotency evidence fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $37 = "n/a"; print }' "${input_tsv}" >"${input_tsv}.idempotency-evidence"
if MIXED_30M_TIMELINE_NAME=mixed-30m-idempotency-evidence \
  MIXED_30M_TIMELINE_INPUT_TSV="${input_tsv}.idempotency-evidence" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "mixed 30m timeline unexpectedly passed missing idempotency evidence" >&2
  exit 1
fi

echo "[transaction-read-mixed-30m-timeline] idempotency replay fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $35 = 0; print }' "${input_tsv}" >"${input_tsv}.idempotency-replay"
if MIXED_30M_TIMELINE_NAME=mixed-30m-idempotency-replay \
  MIXED_30M_TIMELINE_INPUT_TSV="${input_tsv}.idempotency-replay" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "mixed 30m timeline unexpectedly passed idempotency replay missing" >&2
  exit 1
fi

echo "[transaction-read-mixed-30m-timeline] idempotency conflict fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $36 = 1; print }' "${input_tsv}" >"${input_tsv}.idempotency-conflict"
if MIXED_30M_TIMELINE_NAME=mixed-30m-idempotency-conflict \
  MIXED_30M_TIMELINE_INPUT_TSV="${input_tsv}.idempotency-conflict" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "mixed 30m timeline unexpectedly passed idempotency conflict" >&2
  exit 1
fi

echo "[transaction-read-mixed-30m-timeline] write accepted ratio fails"
awk -F '\t' 'BEGIN { OFS = FS } NR == 1 { print; next } { $38 = 0.799; print }' "${input_tsv}" >"${input_tsv}.write-ratio"
if MIXED_30M_TIMELINE_NAME=mixed-30m-write-ratio \
  MIXED_30M_TIMELINE_INPUT_TSV="${input_tsv}.write-ratio" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "mixed 30m timeline unexpectedly passed low write accepted ratio" >&2
  exit 1
fi
