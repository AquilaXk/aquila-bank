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
grep -F "runner=tools/test/run-t3micro-mixed-workload-soak.sh" <<<"${plan}" >/dev/null

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
grep -F $'\t30\t1\ttools/test/run-t3micro-mixed-workload-soak.sh\t' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\tread,write,auth,notification,sse\t440\t' "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "workload-mix.json" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "workload-components.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "outbox-lag.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "read-429-source.tsv" "${MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV}" >/dev/null

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

echo "[transaction-read-mixed-workload-live-evidence-autogen] live stub generation"
stub_runner="${temp_dir}/mixed-runner-stub.sh"
cat >"${stub_runner}" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
echo "runner_name=${0}" >>"${MIXED_WORKLOAD_STUB_LOG}"
echo "soak_repeat=${SOAK_REPEAT:-missing}" >>"${MIXED_WORKLOAD_STUB_LOG}"
SH
chmod +x "${stub_runner}"
stub_output_dir="${temp_dir}/live-output"
stub_env="$(
  MIXED_WORKLOAD_AUTOGEN_MODE=live \
  MIXED_WORKLOAD_AUTOGEN_RUNNER="${stub_runner}" \
  MIXED_WORKLOAD_AUTOGEN_SOAK_REPEAT=2 \
  MIXED_WORKLOAD_STUB_LOG="${temp_dir}/stub.log" \
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
