#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-mixed-workload-live-evidence-autogen.sh"
gate="tools/test/run-transaction-read-mixed-workload-30m-timeline.sh"

echo "[transaction-read-mixed-workload-live-evidence-autogen] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

output_dir="${temp_dir}/output"
name="mixed-live-autogen-check"
run_id="mixed-live-autogen-20260506"
artifact_uri="github-actions://AquilaXk/aquila-bank/actions/runs/456"

echo "[transaction-read-mixed-workload-live-evidence-autogen] print plan"
plan="$(
  MIXED_WORKLOAD_AUTOGEN_MODE=fixture \
  MIXED_WORKLOAD_LIVE_NAME="${name}" \
  MIXED_WORKLOAD_LIVE_RUN_ID="${run_id}" \
  MIXED_WORKLOAD_LIVE_OUTPUT_DIR="${output_dir}" \
  MIXED_WORKLOAD_ARTIFACT_URI="${artifact_uri}" \
    bash "${runner}" --print-plan
)"
grep -F "mode=fixture" <<<"${plan}" >/dev/null
grep -F "name=${name}" <<<"${plan}" >/dev/null
grep -F "run_id=${run_id}" <<<"${plan}" >/dev/null
grep -F "duration_min=30" <<<"${plan}" >/dev/null
grep -F "generated_dir=${output_dir}/generated" <<<"${plan}" >/dev/null
grep -F "generated_env=${output_dir}/${name}-generated-evidence.env" <<<"${plan}" >/dev/null
grep -F "manifest_tsv=${output_dir}/generated/${name}-mixed-workload-evidence-manifest.tsv" <<<"${plan}" >/dev/null
grep -F "artifact_uri=${artifact_uri}" <<<"${plan}" >/dev/null
grep -F "runner=tools/test/run-transaction-read-mixed-workload-oci-k6.sh" <<<"${plan}" >/dev/null
grep -F "manifest_runner_ref=tools/test/run-transaction-read-mixed-workload-oci-k6.sh" <<<"${plan}" >/dev/null

echo "[transaction-read-mixed-workload-live-evidence-autogen] fixture generation"
generated_env="$(
  MIXED_WORKLOAD_AUTOGEN_MODE=fixture \
  MIXED_WORKLOAD_LIVE_NAME="${name}" \
  MIXED_WORKLOAD_LIVE_RUN_ID="${run_id}" \
  MIXED_WORKLOAD_LIVE_OUTPUT_DIR="${output_dir}" \
  MIXED_WORKLOAD_ARTIFACT_URI="${artifact_uri}" \
    bash "${runner}" | tail -1
)"
test "${generated_env}" = "${output_dir}/${name}-generated-evidence.env"
test -f "${generated_env}"

# shellcheck disable=SC1090
source "${generated_env}"
test "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" = "${output_dir}/generated/${name}-mixed-workload-evidence-manifest.tsv"
test "${MIXED_WORKLOAD_LIVE_OUTPUT_DIR}" = "${output_dir}"
test -f "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}"
grep -F $'scenario\trun_id\texecuted_at_utc\tduration_min\tsource_ips\trun_script\tk6_summary_ref\tnginx_access_ref\tspring_metrics_ref\thikari_log_ref\tpostgres_wait_ref' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'mixed-workload-30m\tmixed-live-autogen-20260506\t' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\t30\t1\ttools/test/run-transaction-read-mixed-workload-oci-k6.sh\t' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\tread,write,auth,notification,sse\t440\t' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "workload-mix.json" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "workload-components.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "outbox-lag.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "read-429-source.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "read-buckets.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "write-status.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "idempotency.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\thot,cold,archive\t' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\t1\t0\t' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\tidempotency_replay_count\tidempotency_conflict_count\tidempotency_evidence_ref\twrite_accepted_ratio\tmin_write_accepted_ratio' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\twrite_accepted_ratio\tmin_write_accepted_ratio' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\t1\t0.80' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null

echo "[transaction-read-mixed-workload-live-evidence-autogen] generated manifest passes gate"
gate_output="$(
  MIXED_30M_TIMELINE_NAME="${name}" \
  MIXED_30M_TIMELINE_INPUT_TSV="${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${output_dir}/gate" \
    "${gate}"
)"
report_md="$(tail -1 <<<"${gate_output}")"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "workload mix/component and outbox lag artifact: required" "${report_md}" >/dev/null
grep -F "idempotency replay/conflict artifact: required" "${report_md}" >/dev/null

echo "[transaction-read-mixed-workload-live-evidence-autogen] live stub generation"
stub_runner="${temp_dir}/mixed-runner-stub.sh"
cat >"${stub_runner}" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
echo "runner_name=${0}" >>"${MIXED_WORKLOAD_STUB_LOG}"
echo "soak_repeat=${SOAK_REPEAT:-missing}" >>"${MIXED_WORKLOAD_STUB_LOG}"
artifact_dir="$(dirname "${MIXED_WORKLOAD_STUB_ENV}")"
mkdir -p "${artifact_dir}"
for file in \
  runner-k6-summary.json \
  runner-nginx.jsonl \
  runner-spring.json \
  runner-hikari.log \
  runner-postgres-wait.tsv \
  runner-timeline.json \
  runner-workload-mix.json \
  runner-workload-components.tsv \
  runner-outbox-lag.tsv \
  runner-read-429-source.tsv \
  runner-read-buckets.tsv \
  runner-write-status.tsv \
  runner-idempotency.tsv; do
  printf "stub\n" >"${artifact_dir}/${file}"
done
{
  printf "MIXED_WORKLOAD_RUNNER_ENV_FORMAT=%q\n" "oci-mixed-v1"
  printf "MIXED_WORKLOAD_RUNNER_RUN_SCRIPT=%q\n" "tools/test/run-transaction-read-mixed-workload-oci-k6.sh"
  printf "MIXED_WORKLOAD_RUNNER_EXECUTED_AT_UTC=%q\n" "2026-05-06T12:00:00Z"
  printf "MIXED_WORKLOAD_RUNNER_SOURCE_IPS=%q\n" "1"
  printf "MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF=%q\n" "${artifact_dir}/runner-k6-summary.json"
  printf "MIXED_WORKLOAD_RUNNER_NGINX_ACCESS_REF=%q\n" "${artifact_dir}/runner-nginx.jsonl"
  printf "MIXED_WORKLOAD_RUNNER_SPRING_METRICS_REF=%q\n" "${artifact_dir}/runner-spring.json"
  printf "MIXED_WORKLOAD_RUNNER_HIKARI_LOG_REF=%q\n" "${artifact_dir}/runner-hikari.log"
  printf "MIXED_WORKLOAD_RUNNER_POSTGRES_WAIT_REF=%q\n" "${artifact_dir}/runner-postgres-wait.tsv"
  printf "MIXED_WORKLOAD_RUNNER_TIMELINE_REF=%q\n" "${artifact_dir}/runner-timeline.json"
  printf "MIXED_WORKLOAD_RUNNER_WORKLOAD_MIX_REF=%q\n" "${artifact_dir}/runner-workload-mix.json"
  printf "MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF=%q\n" "${artifact_dir}/runner-workload-components.tsv"
  printf "MIXED_WORKLOAD_RUNNER_OUTBOX_LAG_REF=%q\n" "${artifact_dir}/runner-outbox-lag.tsv"
  printf "MIXED_WORKLOAD_RUNNER_READ_429_SOURCE_REF=%q\n" "${artifact_dir}/runner-read-429-source.tsv"
  printf "MIXED_WORKLOAD_RUNNER_READ_BUCKET_REF=%q\n" "${artifact_dir}/runner-read-buckets.tsv"
  printf "MIXED_WORKLOAD_RUNNER_WRITE_STATUS_REF=%q\n" "${artifact_dir}/runner-write-status.tsv"
  printf "MIXED_WORKLOAD_RUNNER_IDEMPOTENCY_EVIDENCE_REF=%q\n" "${artifact_dir}/runner-idempotency.tsv"
  printf "MIXED_WORKLOAD_RUNNER_EDGE_429_RATE=%q\n" "0.03"
  printf "MIXED_WORKLOAD_RUNNER_BACKEND_429_COUNT=%q\n" "0"
  printf "MIXED_WORKLOAD_RUNNER_UNKNOWN_429_COUNT=%q\n" "0"
  printf "MIXED_WORKLOAD_RUNNER_FIVE_XX_COUNT=%q\n" "0"
  printf "MIXED_WORKLOAD_RUNNER_NGINX_499_COUNT=%q\n" "0"
  printf "MIXED_WORKLOAD_RUNNER_HIKARI_VALIDATION_WARNINGS=%q\n" "0"
  printf "MIXED_WORKLOAD_RUNNER_DB_POOL_PENDING_MAX=%q\n" "0"
  printf "MIXED_WORKLOAD_RUNNER_P95_MS=%q\n" "101"
  printf "MIXED_WORKLOAD_RUNNER_P99_MS=%q\n" "222"
  printf "MIXED_WORKLOAD_RUNNER_P999_MS=%q\n" "333"
  printf "MIXED_WORKLOAD_RUNNER_MAX_MS=%q\n" "444"
  printf "MIXED_WORKLOAD_RUNNER_POSTGRES_CHECKPOINT_COUNT=%q\n" "1"
  printf "MIXED_WORKLOAD_RUNNER_POSTGRES_TEMP_FILE_COUNT=%q\n" "0"
  printf "MIXED_WORKLOAD_RUNNER_NGINX_UPSTREAM_P95_MS=%q\n" "12.5"
  printf "MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENTS=%q\n" "read,write,auth,notification,sse"
  printf "MIXED_WORKLOAD_RUNNER_READ_P999_MS=%q\n" "333"
  printf "MIXED_WORKLOAD_RUNNER_READ_BUCKETS=%q\n" "hot,cold,archive"
  printf "MIXED_WORKLOAD_RUNNER_WRITE_2XX_COUNT=%q\n" "7"
  printf "MIXED_WORKLOAD_RUNNER_WRITE_UNEXPECTED_STATUS_COUNT=%q\n" "2"
  printf "MIXED_WORKLOAD_RUNNER_WRITE_ACCEPTED_RATIO=%q\n" "0.875"
  printf "MIXED_WORKLOAD_RUNNER_MIN_WRITE_ACCEPTED_RATIO=%q\n" "0.80"
  printf "MIXED_WORKLOAD_RUNNER_IDEMPOTENCY_REPLAY_COUNT=%q\n" "4"
  printf "MIXED_WORKLOAD_RUNNER_IDEMPOTENCY_CONFLICT_COUNT=%q\n" "0"
  printf "MIXED_WORKLOAD_RUNNER_OUTBOX_LAG_MAX=%q\n" "0"
} >"${MIXED_WORKLOAD_STUB_ENV}"
echo "${MIXED_WORKLOAD_STUB_ENV}"
SH
chmod +x "${stub_runner}"
stub_output_dir="${temp_dir}/live-output"
stub_runner_env="${temp_dir}/runner-env/oci-mixed-runner.env"
stub_env="$(
  MIXED_WORKLOAD_AUTOGEN_MODE=live \
  MIXED_WORKLOAD_AUTOGEN_RUNNER="${stub_runner}" \
  MIXED_WORKLOAD_AUTOGEN_SOAK_REPEAT=2 \
  MIXED_WORKLOAD_STUB_LOG="${temp_dir}/stub.log" \
  MIXED_WORKLOAD_STUB_ENV="${stub_runner_env}" \
  MIXED_WORKLOAD_LIVE_NAME="${name}-live" \
  MIXED_WORKLOAD_LIVE_RUN_ID="${run_id}-live" \
  MIXED_WORKLOAD_LIVE_OUTPUT_DIR="${stub_output_dir}" \
  MIXED_WORKLOAD_ARTIFACT_URI="${artifact_uri}/live" \
    bash "${runner}" | tail -1
)"
test "${stub_env}" = "${stub_output_dir}/${name}-live-generated-evidence.env"
grep -F "soak_repeat=2" "${temp_dir}/stub.log" >/dev/null

# shellcheck disable=SC1090
source "${stub_env}"
grep -F "runner-k6-summary.json" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "runner-workload-components.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "runner-read-buckets.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "runner-write-status.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "runner-idempotency.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\t0.03\t0\t0\t0\t0\t0\t0\t333\t' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\t101\t222\t444\t1\t0\t12.5\t' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\thot,cold,archive\t' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\t7\t2\t' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\t7\t2\t' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" | grep -F $'\t4\t0\t' >/dev/null
grep -F $'\t0.875\t0.80' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
live_gate_output="$(
  MIXED_30M_TIMELINE_NAME="${name}-live" \
  MIXED_30M_TIMELINE_INPUT_TSV="${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" \
  MIXED_30M_TIMELINE_OUTPUT_DIR="${stub_output_dir}/gate" \
    "${gate}"
)"
live_report_md="$(tail -1 <<<"${live_gate_output}")"
grep -F "gate_status=pass" "${live_report_md}" >/dev/null

echo "[transaction-read-mixed-workload-live-evidence-autogen] live runner failure writes artifact"
bad_runner="${temp_dir}/mixed-runner-bad.sh"
cat >"${bad_runner}" <<'SH'
#!/usr/bin/env bash
exit 9
SH
chmod +x "${bad_runner}"
if MIXED_WORKLOAD_AUTOGEN_MODE=live \
  MIXED_WORKLOAD_AUTOGEN_RUNNER="${bad_runner}" \
  MIXED_WORKLOAD_LIVE_NAME="${name}-bad" \
  MIXED_WORKLOAD_LIVE_RUN_ID="${run_id}-bad" \
  MIXED_WORKLOAD_LIVE_OUTPUT_DIR="${temp_dir}/bad-output" \
  MIXED_WORKLOAD_ARTIFACT_URI="${artifact_uri}/bad" \
    bash "${runner}" >"${temp_dir}/bad.log" 2>&1; then
  echo "mixed workload autogen unexpectedly passed failed live runner" >&2
  exit 1
fi
grep -F "mixed workload live runner failed" "${temp_dir}/bad.log" >/dev/null
grep -F "failure_reason=live-runner-failed" "${temp_dir}/bad-output/${name}-bad-missing-evidence.env" >/dev/null
