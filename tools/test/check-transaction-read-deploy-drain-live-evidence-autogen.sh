#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-deploy-drain-live-evidence-autogen.sh"
gate="tools/test/run-transaction-read-deploy-drain-under-load-gate.sh"

echo "[transaction-read-deploy-drain-live-evidence-autogen] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

output_dir="${temp_dir}/output"
name="deploy-drain-live-autogen-check"
run_id="deploy-drain-live-autogen-20260506"
artifact_uri="github-actions://AquilaXk/aquila-bank/actions/runs/789"

echo "[transaction-read-deploy-drain-live-evidence-autogen] print plan"
plan="$(
  DEPLOY_DRAIN_AUTOGEN_MODE=fixture \
  DEPLOY_DRAIN_LIVE_NAME="${name}" \
  DEPLOY_DRAIN_LIVE_RUN_ID="${run_id}" \
  DEPLOY_DRAIN_LIVE_OUTPUT_DIR="${output_dir}" \
  DEPLOY_DRAIN_ARTIFACT_URI="${artifact_uri}" \
    bash "${runner}" --print-plan
)"
grep -F "mode=fixture" <<<"${plan}" >/dev/null
grep -F "name=${name}" <<<"${plan}" >/dev/null
grep -F "run_id=${run_id}" <<<"${plan}" >/dev/null
grep -F "duration_min=5" <<<"${plan}" >/dev/null
grep -F "499_budget_count=0" <<<"${plan}" >/dev/null
grep -F "generated_dir=${output_dir}/generated" <<<"${plan}" >/dev/null
grep -F "generated_env=${output_dir}/${name}-generated-evidence.env" <<<"${plan}" >/dev/null
grep -F "manifest_tsv=${output_dir}/generated/${name}-deploy-drain-evidence-manifest.tsv" <<<"${plan}" >/dev/null
grep -F "artifact_uri=${artifact_uri}" <<<"${plan}" >/dev/null
grep -F "runner=tools/test/run-sse-multinode-drain-smoke.sh" <<<"${plan}" >/dev/null

echo "[transaction-read-deploy-drain-live-evidence-autogen] fixture generation"
generated_env="$(
  DEPLOY_DRAIN_AUTOGEN_MODE=fixture \
  DEPLOY_DRAIN_LIVE_NAME="${name}" \
  DEPLOY_DRAIN_LIVE_RUN_ID="${run_id}" \
  DEPLOY_DRAIN_LIVE_OUTPUT_DIR="${output_dir}" \
  DEPLOY_DRAIN_ARTIFACT_URI="${artifact_uri}" \
    bash "${runner}" | tail -1
)"
test "${generated_env}" = "${output_dir}/${name}-generated-evidence.env"
test -f "${generated_env}"

# shellcheck disable=SC1090
source "${generated_env}"
test "${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV}" = "${output_dir}/generated/${name}-deploy-drain-evidence-manifest.tsv"
test "${DEPLOY_DRAIN_LIVE_OUTPUT_DIR}" = "${output_dir}"
test "${DEPLOY_DRAIN_499_BUDGET_COUNT}" = "0"
test -f "${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV}"
grep -F $'scenario\trun_id\texecuted_at_utc\tduration_min\tsource_ips\trun_script\tk6_summary_ref\tnginx_access_ref\tspring_metrics_ref\thikari_log_ref\tpostgres_wait_ref\tdeploy_event_ref' "${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'deploy-drain\tdeploy-drain-live-autogen-20260506\t' "${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\t5\t1\ttools/test/run-sse-multinode-drain-smoke.sh\t' "${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F $'\tbackend-restart,blue-green-drain\t0\t2\t2' "${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "deploy-event.json" "${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "deploy-retry-contract.json" "${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV}" >/dev/null
grep -F "deploy-499-budget.tsv" "${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV}" >/dev/null

echo "[transaction-read-deploy-drain-live-evidence-autogen] generated manifest passes gate"
gate_output="$(
  DEPLOY_DRAIN_GATE_NAME="${name}" \
  DEPLOY_DRAIN_GATE_INPUT_TSV="${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV}" \
  DEPLOY_DRAIN_GATE_OUTPUT_DIR="${output_dir}/gate" \
  DEPLOY_DRAIN_GATE_499_BUDGET_COUNT="${DEPLOY_DRAIN_499_BUDGET_COUNT}" \
    "${gate}"
)"
report_md="$(tail -1 <<<"${gate_output}")"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "deploy retry/reconnect and 499 budget artifact: required" "${report_md}" >/dev/null

echo "[transaction-read-deploy-drain-live-evidence-autogen] live stub generation"
stub_runner="${temp_dir}/deploy-drain-runner-stub.sh"
cat >"${stub_runner}" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
echo "runner_name=${0}" >>"${DEPLOY_DRAIN_STUB_LOG}"
echo "run_id=${DEPLOY_DRAIN_RUN_ID:-missing}" >>"${DEPLOY_DRAIN_STUB_LOG}"
SH
chmod +x "${stub_runner}"
stub_output_dir="${temp_dir}/live-output"
stub_env="$(
  DEPLOY_DRAIN_AUTOGEN_MODE=live \
  DEPLOY_DRAIN_AUTOGEN_RUNNER="${stub_runner}" \
  DEPLOY_DRAIN_STUB_LOG="${temp_dir}/stub.log" \
  DEPLOY_DRAIN_LIVE_NAME="${name}-live" \
  DEPLOY_DRAIN_LIVE_RUN_ID="${run_id}-live" \
  DEPLOY_DRAIN_LIVE_OUTPUT_DIR="${stub_output_dir}" \
  DEPLOY_DRAIN_ARTIFACT_URI="${artifact_uri}/live" \
    bash "${runner}" | tail -1
)"
test "${stub_env}" = "${stub_output_dir}/${name}-live-generated-evidence.env"
grep -F "run_id=${run_id}-live" "${temp_dir}/stub.log" >/dev/null

# shellcheck disable=SC1090
source "${stub_env}"
live_gate_output="$(
  DEPLOY_DRAIN_GATE_NAME="${name}-live" \
  DEPLOY_DRAIN_GATE_INPUT_TSV="${DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV}" \
  DEPLOY_DRAIN_GATE_OUTPUT_DIR="${stub_output_dir}/gate" \
  DEPLOY_DRAIN_GATE_499_BUDGET_COUNT="${DEPLOY_DRAIN_499_BUDGET_COUNT}" \
    "${gate}"
)"
live_report_md="$(tail -1 <<<"${live_gate_output}")"
grep -F "gate_status=pass" "${live_report_md}" >/dev/null

echo "[transaction-read-deploy-drain-live-evidence-autogen] live runner failure writes artifact"
bad_runner="${temp_dir}/deploy-drain-runner-bad.sh"
cat >"${bad_runner}" <<'SH'
#!/usr/bin/env bash
exit 9
SH
chmod +x "${bad_runner}"
if DEPLOY_DRAIN_AUTOGEN_MODE=live \
  DEPLOY_DRAIN_AUTOGEN_RUNNER="${bad_runner}" \
  DEPLOY_DRAIN_LIVE_NAME="${name}-bad" \
  DEPLOY_DRAIN_LIVE_RUN_ID="${run_id}-bad" \
  DEPLOY_DRAIN_LIVE_OUTPUT_DIR="${temp_dir}/bad-output" \
  DEPLOY_DRAIN_ARTIFACT_URI="${artifact_uri}/bad" \
    bash "${runner}" >"${temp_dir}/bad.log" 2>&1; then
  echo "deploy drain autogen unexpectedly passed failed live runner" >&2
  exit 1
fi
grep -F "deploy drain live runner failed" "${temp_dir}/bad.log" >/dev/null
grep -F "failure_reason=live-runner-failed" "${temp_dir}/bad-output/${name}-bad-missing-evidence.env" >/dev/null
