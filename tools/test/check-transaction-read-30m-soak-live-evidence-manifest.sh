#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-30m-soak-live-evidence-manifest.sh"
gate="tools/test/run-transaction-read-30m-soak-live-evidence-gate.sh"

echo "[transaction-read-30m-soak-live-evidence-manifest] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

artifact_dir="${temp_dir}/artifacts"
output_dir="${temp_dir}/output"
mkdir -p "${artifact_dir}"

for file in \
  k6-summary.json \
  nginx-aggregate.tsv \
  spring-metrics.json \
  hikari.log \
  postgres-wait.tsv \
  timeline.tsv \
  postgres-checkpoint.tsv \
  postgres-temp-file.tsv \
  nginx-upstream.tsv \
  hikari-config.tsv \
  hikari-zero-warning.md; do
  printf "artifact=%s\n" "${file}" >"${artifact_dir}/${file}"
done

echo "[transaction-read-30m-soak-live-evidence-manifest] print plan"
plan="$(
  SOAK_30M_MANIFEST_NAME=soak-manifest-check \
  SOAK_30M_MANIFEST_RUN_ID=run-soak-manifest-001 \
  SOAK_30M_MANIFEST_OUTPUT_DIR="${output_dir}" \
    "${runner}" --print-plan
)"
grep -F "name=soak-manifest-check" <<<"${plan}" >/dev/null
grep -F "run_id=run-soak-manifest-001" <<<"${plan}" >/dev/null
grep -F "required_scenarios=hikari-lifetime,p999-long-correlation" <<<"${plan}" >/dev/null
grep -F "manifest_tsv=${output_dir}/soak-manifest-check-30m-soak-live-evidence-manifest.tsv" <<<"${plan}" >/dev/null

echo "[transaction-read-30m-soak-live-evidence-manifest] pass report"
output="$(
  SOAK_30M_MANIFEST_NAME=soak-manifest-check \
  SOAK_30M_MANIFEST_RUN_ID=run-soak-manifest-001 \
  SOAK_30M_MANIFEST_EXECUTED_AT_UTC=2026-05-04T08:00:00Z \
  SOAK_30M_MANIFEST_DURATION_MIN=30 \
  SOAK_30M_MANIFEST_K6_SUMMARY_REF="${artifact_dir}/k6-summary.json" \
  SOAK_30M_MANIFEST_NGINX_AGGREGATE_REF="${artifact_dir}/nginx-aggregate.tsv" \
  SOAK_30M_MANIFEST_SPRING_METRICS_REF="${artifact_dir}/spring-metrics.json" \
  SOAK_30M_MANIFEST_HIKARI_LOG_REF="${artifact_dir}/hikari.log" \
  SOAK_30M_MANIFEST_POSTGRES_WAIT_REF="${artifact_dir}/postgres-wait.tsv" \
  SOAK_30M_MANIFEST_TIMELINE_REF="${artifact_dir}/timeline.tsv" \
  SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_REF="${artifact_dir}/postgres-checkpoint.tsv" \
  SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_REF="${artifact_dir}/postgres-temp-file.tsv" \
  SOAK_30M_MANIFEST_NGINX_UPSTREAM_LATENCY_REF="${artifact_dir}/nginx-upstream.tsv" \
  SOAK_30M_MANIFEST_HIKARI_CONFIG_REF="${artifact_dir}/hikari-config.tsv" \
  SOAK_30M_MANIFEST_HIKARI_ZERO_WARNING_SOAK_REF="${artifact_dir}/hikari-zero-warning.md" \
  SOAK_30M_MANIFEST_P95_MS=95 \
  SOAK_30M_MANIFEST_P99_MS=220 \
  SOAK_30M_MANIFEST_P999_MS=490 \
  SOAK_30M_MANIFEST_MAX_MS=650 \
  SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_COUNT=3 \
  SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_COUNT=0 \
  SOAK_30M_MANIFEST_NGINX_UPSTREAM_P95_MS=18.5 \
  SOAK_30M_MANIFEST_HIKARI_MAX_LIFETIME_MS=120000 \
  SOAK_30M_MANIFEST_HIKARI_KEEPALIVE_TIME_MS=30000 \
  SOAK_30M_MANIFEST_POSTGRES_IDLE_TIMEOUT_MS=300000 \
  SOAK_30M_MANIFEST_OCI_NAT_IDLE_TIMEOUT_MS=350000 \
  SOAK_30M_MANIFEST_OUTPUT_DIR="${output_dir}" \
    "${runner}"
)"
manifest_tsv="$(tail -1 <<<"${output}")"
report_md="${output_dir}/soak-manifest-check-30m-soak-live-evidence-manifest.md"
test "${manifest_tsv}" = "${output_dir}/soak-manifest-check-30m-soak-live-evidence-manifest.tsv"
test -s "${report_md}"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "manifest rows: 2" "${report_md}" >/dev/null
grep -F $'hikari-lifetime\trun-soak-manifest-001\t2026-05-04T08:00:00Z\t30' "${manifest_tsv}" >/dev/null
grep -F $'p999-long-correlation\trun-soak-manifest-001\t2026-05-04T08:00:00Z\t30' "${manifest_tsv}" >/dev/null

echo "[transaction-read-30m-soak-live-evidence-manifest] generated manifest passes live gate"
gate_output="$(
  SOAK_30M_LIVE_NAME=soak-manifest-live-check \
  SOAK_30M_LIVE_INPUT_TSV="${manifest_tsv}" \
  SOAK_30M_LIVE_OUTPUT_DIR="${temp_dir}/gate-output" \
    "${gate}"
)"
gate_report="$(tail -1 <<<"${gate_output}")"
grep -F "gate_status=pass" "${gate_report}" >/dev/null

echo "[transaction-read-30m-soak-live-evidence-manifest] missing artifact fails"
if SOAK_30M_MANIFEST_NAME=soak-manifest-missing \
  SOAK_30M_MANIFEST_RUN_ID=run-soak-manifest-001 \
  SOAK_30M_MANIFEST_K6_SUMMARY_REF="${artifact_dir}/missing-k6.json" \
  SOAK_30M_MANIFEST_NGINX_AGGREGATE_REF="${artifact_dir}/nginx-aggregate.tsv" \
  SOAK_30M_MANIFEST_SPRING_METRICS_REF="${artifact_dir}/spring-metrics.json" \
  SOAK_30M_MANIFEST_HIKARI_LOG_REF="${artifact_dir}/hikari.log" \
  SOAK_30M_MANIFEST_POSTGRES_WAIT_REF="${artifact_dir}/postgres-wait.tsv" \
  SOAK_30M_MANIFEST_TIMELINE_REF="${artifact_dir}/timeline.tsv" \
  SOAK_30M_MANIFEST_POSTGRES_CHECKPOINT_REF="${artifact_dir}/postgres-checkpoint.tsv" \
  SOAK_30M_MANIFEST_POSTGRES_TEMP_FILE_REF="${artifact_dir}/postgres-temp-file.tsv" \
  SOAK_30M_MANIFEST_NGINX_UPSTREAM_LATENCY_REF="${artifact_dir}/nginx-upstream.tsv" \
  SOAK_30M_MANIFEST_HIKARI_CONFIG_REF="${artifact_dir}/hikari-config.tsv" \
  SOAK_30M_MANIFEST_HIKARI_ZERO_WARNING_SOAK_REF="${artifact_dir}/hikari-zero-warning.md" \
  SOAK_30M_MANIFEST_OUTPUT_DIR="${temp_dir}/missing-output" \
    "${runner}" >"${temp_dir}/missing.log" 2>&1; then
  echo "30m soak manifest unexpectedly passed missing k6 summary artifact" >&2
  exit 1
fi
grep -F "SOAK_30M_MANIFEST_K6_SUMMARY_REF is required" "${temp_dir}/missing.log" >/dev/null
grep -F "SOAK_30M_MANIFEST_FAILURE_REASON=missing-artifact" "${temp_dir}/missing-output/soak-manifest-missing-30m-soak-live-evidence-manifest-failure.env" >/dev/null
