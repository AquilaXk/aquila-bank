#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-30m-soak-live-evidence-artifacts.sh"
gate="tools/test/run-transaction-read-30m-soak-live-evidence-gate.sh"

echo "[transaction-read-30m-soak-live-evidence-artifacts] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

artifact_dir="${temp_dir}/artifacts"
output_dir="${temp_dir}/output"
mkdir -p "${artifact_dir}"

summary_json="${artifact_dir}/k6-summary.json"
cat >"${summary_json}" <<'JSON'
{
  "metrics": {
    "aquila_transaction_edge_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_backend_429_count": {"values": {"count": 0}},
    "aquila_transaction_unknown_429_count": {"values": {"count": 0}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": 82, "p(99)": 190, "p(99.9)": 420, "max": 610}},
    "aquila_transaction_hot_cursor_ms": {"values": {"p(95)": 80, "p(99)": 185, "p(99.9)": 410, "max": 590}},
    "aquila_transaction_hot_deep_cursor_ms": {"values": {"p(95)": 83, "p(99)": 195, "p(99.9)": 430, "max": 600}},
    "aquila_transaction_cold_first_ms": {"values": {"p(95)": 95, "p(99)": 220, "p(99.9)": 490, "max": 650}},
    "aquila_transaction_cold_cursor_ms": {"values": {"p(95)": 90, "p(99)": 210, "p(99.9)": 470, "max": 640}},
    "aquila_transaction_cold_deep_cursor_ms": {"values": {"p(95)": 91, "p(99)": 215, "p(99.9)": 480, "max": 645}}
  }
}
JSON

access_log="${artifact_dir}/nginx-access.jsonl"
cat >"${access_log}" <<'JSONL'
{"request":"GET /api/v1/transactions?accountId=1 HTTP/1.1","status":200,"request_time":0.091,"upstream_status":"200","upstream_response_time":"0.014","limit_req_status":"PASSED","reject_source":"","reject_reason":"","upstream_reject_source":"","upstream_reject_reason":"","k6_run_id":"run-soak-artifacts-001"}
{"request":"GET /api/v1/transactions?accountId=2 HTTP/1.1","status":200,"request_time":0.088,"upstream_status":"200","upstream_response_time":"0.019","limit_req_status":"PASSED","reject_source":"","reject_reason":"","upstream_reject_source":"","upstream_reject_reason":"","k6_run_id":"run-soak-artifacts-001"}
JSONL

spring_metrics="${artifact_dir}/spring-metrics.prom"
cat >"${spring_metrics}" <<'PROM'
# TYPE hikaricp_connections_pending gauge
hikaricp_connections_pending{pool="aquila-bank-pool"} 0
# TYPE jvm_gc_pause_seconds summary
jvm_gc_pause_seconds_count 0
PROM

hikari_log="${artifact_dir}/hikari.log"
printf '2026-05-05T00:00:00Z INFO HikariPool-1 keepalive ok\n' >"${hikari_log}"

postgres_wait="${artifact_dir}/postgres-wait.tsv"
cat >"${postgres_wait}" <<'TSV'
sample_time_utc	wait_event_type	wait_event	count
2026-05-05T00:00:00Z	none	none	0
TSV

timeline="${artifact_dir}/timeline.tsv"
cat >"${timeline}" <<'TSV'
sample_time_utc	hikari_pending	pg_wait_count	gc_pause_count	note
2026-05-05T00:00:00Z	0	0	0	start
2026-05-05T00:00:05Z	0	0	0	load
TSV

checkpoint="${artifact_dir}/postgres-checkpoint.tsv"
cat >"${checkpoint}" <<'TSV'
metric	value
postgres_checkpoint_count	3
TSV

temp_file="${artifact_dir}/postgres-temp-file.tsv"
cat >"${temp_file}" <<'TSV'
metric	value
postgres_temp_file_count	0
TSV

hikari_config="${artifact_dir}/hikari-config.tsv"
cat >"${hikari_config}" <<'TSV'
key	value
hikari_max_lifetime_ms	45000
hikari_keepalive_time_ms	30000
postgres_idle_timeout_ms	300000
oci_nat_idle_timeout_ms	350000
TSV

echo "[transaction-read-30m-soak-live-evidence-artifacts] print plan"
plan="$(
  SOAK_30M_ARTIFACTS_NAME=soak-artifacts-check \
  SOAK_30M_ARTIFACTS_RUN_ID=run-soak-artifacts-001 \
  SOAK_30M_ARTIFACTS_OUTPUT_DIR="${output_dir}" \
  SOAK_30M_ARTIFACTS_K6_SUMMARY_JSON="${summary_json}" \
  SOAK_30M_ARTIFACTS_NGINX_ACCESS_LOG="${access_log}" \
  SOAK_30M_ARTIFACTS_SPRING_METRICS_REF="${spring_metrics}" \
  SOAK_30M_ARTIFACTS_HIKARI_LOG="${hikari_log}" \
  SOAK_30M_ARTIFACTS_POSTGRES_WAIT_REF="${postgres_wait}" \
  SOAK_30M_ARTIFACTS_TIMELINE_REF="${timeline}" \
  SOAK_30M_ARTIFACTS_POSTGRES_CHECKPOINT_REF="${checkpoint}" \
  SOAK_30M_ARTIFACTS_POSTGRES_TEMP_FILE_REF="${temp_file}" \
  SOAK_30M_ARTIFACTS_HIKARI_CONFIG_REF="${hikari_config}" \
    "${runner}" --print-plan
)"
grep -F "name=soak-artifacts-check" <<<"${plan}" >/dev/null
grep -F "run_id=run-soak-artifacts-001" <<<"${plan}" >/dev/null
grep -F "duration_min=30" <<<"${plan}" >/dev/null
grep -F "artifact_pack_refs=k6_summary,nginx_aggregate,spring_metrics,hikari_log,postgres_wait,timeline,postgres_checkpoint,postgres_temp_file,nginx_upstream_latency,hikari_config,hikari_zero_warning_soak" <<<"${plan}" >/dev/null
grep -F "failure_reason_ref=${output_dir}/failure-reason.env" <<<"${plan}" >/dev/null

echo "[transaction-read-30m-soak-live-evidence-artifacts] pass report"
output="$(
  SOAK_30M_ARTIFACTS_NAME=soak-artifacts-check \
  SOAK_30M_ARTIFACTS_RUN_ID=run-soak-artifacts-001 \
  SOAK_30M_ARTIFACTS_EXECUTED_AT_UTC=2026-05-05T00:00:00Z \
  SOAK_30M_ARTIFACTS_OUTPUT_DIR="${output_dir}" \
  SOAK_30M_ARTIFACTS_K6_SUMMARY_JSON="${summary_json}" \
  SOAK_30M_ARTIFACTS_NGINX_ACCESS_LOG="${access_log}" \
  SOAK_30M_ARTIFACTS_SPRING_METRICS_REF="${spring_metrics}" \
  SOAK_30M_ARTIFACTS_HIKARI_LOG="${hikari_log}" \
  SOAK_30M_ARTIFACTS_POSTGRES_WAIT_REF="${postgres_wait}" \
  SOAK_30M_ARTIFACTS_TIMELINE_REF="${timeline}" \
  SOAK_30M_ARTIFACTS_POSTGRES_CHECKPOINT_REF="${checkpoint}" \
  SOAK_30M_ARTIFACTS_POSTGRES_TEMP_FILE_REF="${temp_file}" \
  SOAK_30M_ARTIFACTS_HIKARI_CONFIG_REF="${hikari_config}" \
    "${runner}"
)"
manifest_tsv="$(tail -1 <<<"${output}")"
test "${manifest_tsv}" = "${output_dir}/manifest/soak-artifacts-check-30m-soak-live-evidence-manifest.tsv"
test -s "${output_dir}/nginx-access-aggregate/soak-artifacts-check-nginx-access-aggregate.tsv"
test -s "${output_dir}/nginx-upstream-latency.tsv"
test -s "${output_dir}/hikari-zero-warning-soak.md"
grep -F $'hikari-lifetime\trun-soak-artifacts-001\t2026-05-05T00:00:00Z\t30' "${manifest_tsv}" >/dev/null
grep -F $'p999-long-correlation\trun-soak-artifacts-001\t2026-05-05T00:00:00Z\t30' "${manifest_tsv}" >/dev/null
grep -F "p95/p99/p99.9/max: 95/220/490/650" "${output_dir}/manifest/soak-artifacts-check-30m-soak-live-evidence-manifest.md" >/dev/null

gate_output="$(
  SOAK_30M_LIVE_NAME=soak-artifacts-live-check \
  SOAK_30M_LIVE_INPUT_TSV="${manifest_tsv}" \
  SOAK_30M_LIVE_OUTPUT_DIR="${temp_dir}/gate-output" \
    "${gate}"
)"
gate_report="$(tail -1 <<<"${gate_output}")"
grep -F "gate_status=pass" "${gate_report}" >/dev/null

echo "[transaction-read-30m-soak-live-evidence-artifacts] Hikari warning fails"
warning_log="${artifact_dir}/hikari-warning.log"
printf '2026-05-05T00:00:00Z WARN Failed to validate connection org.postgresql.jdbc.PgConnection@1\n' >"${warning_log}"
if SOAK_30M_ARTIFACTS_NAME=soak-artifacts-warning \
  SOAK_30M_ARTIFACTS_RUN_ID=run-soak-artifacts-001 \
  SOAK_30M_ARTIFACTS_OUTPUT_DIR="${temp_dir}/warning-output" \
  SOAK_30M_ARTIFACTS_K6_SUMMARY_JSON="${summary_json}" \
  SOAK_30M_ARTIFACTS_NGINX_ACCESS_LOG="${access_log}" \
  SOAK_30M_ARTIFACTS_SPRING_METRICS_REF="${spring_metrics}" \
  SOAK_30M_ARTIFACTS_HIKARI_LOG="${warning_log}" \
  SOAK_30M_ARTIFACTS_POSTGRES_WAIT_REF="${postgres_wait}" \
  SOAK_30M_ARTIFACTS_TIMELINE_REF="${timeline}" \
  SOAK_30M_ARTIFACTS_POSTGRES_CHECKPOINT_REF="${checkpoint}" \
  SOAK_30M_ARTIFACTS_POSTGRES_TEMP_FILE_REF="${temp_file}" \
  SOAK_30M_ARTIFACTS_HIKARI_CONFIG_REF="${hikari_config}" \
    "${runner}" >"${temp_dir}/warning.log" 2>&1; then
  echo "30m soak artifact pack unexpectedly passed Hikari warning" >&2
  exit 1
fi
grep -F "hikari validation warnings must be zero" "${temp_dir}/warning.log" >/dev/null
test -s "${temp_dir}/warning-output/manifest/soak-artifacts-warning-30m-soak-live-evidence-manifest.tsv"
test -s "${temp_dir}/warning-output/manifest/soak-artifacts-warning-30m-soak-live-evidence-manifest.md"
test -s "${temp_dir}/warning-output/failure-reason.env"
test -s "${temp_dir}/warning-output/soak-artifacts-warning-30m-soak-live-evidence-failure.md"
grep -F "failure_reason=hikari-validation-warning" "${temp_dir}/warning-output/failure-reason.env" >/dev/null
grep -F "gate_status=fail" "${temp_dir}/warning-output/manifest/soak-artifacts-warning-30m-soak-live-evidence-manifest.md" >/dev/null
grep -F "failure_reason=hikari-validation-warning" "${temp_dir}/warning-output/manifest/soak-artifacts-warning-30m-soak-live-evidence-manifest.md" >/dev/null
