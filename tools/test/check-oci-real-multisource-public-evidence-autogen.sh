#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-oci-real-multisource-public-evidence-autogen.sh"
gate="tools/test/run-oci-real-multisource-public-evidence.sh"

echo "[oci-real-multisource-public-evidence-autogen] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

output_dir="${temp_dir}/output"
name="real-multisource-autogen-check"
run_id="real-multisource-autogen-20260506"
artifact_uri="github-actions://AquilaXk/aquila-bank/actions/runs/123"

echo "[oci-real-multisource-public-evidence-autogen] print plan"
plan="$(
  OCI_REAL_MULTISOURCE_AUTOGEN_MODE=fixture \
  OCI_REAL_MULTISOURCE_NAME="${name}" \
  OCI_REAL_MULTISOURCE_RUN_ID="${run_id}" \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI="${artifact_uri}" \
    bash "${runner}" --print-plan
)"
grep -F "mode=fixture" <<<"${plan}" >/dev/null
grep -F "name=${name}" <<<"${plan}" >/dev/null
grep -F "run_id=${run_id}" <<<"${plan}" >/dev/null
grep -F "contexts=oci-k6-a,oci-k6-b" <<<"${plan}" >/dev/null
grep -F "context_count=2" <<<"${plan}" >/dev/null
grep -F "generated_dir=${output_dir}/generated" <<<"${plan}" >/dev/null
grep -F "generated_env=${output_dir}/${name}-generated-evidence.env" <<<"${plan}" >/dev/null
grep -F "generated_single_source_summary_json=${output_dir}/generated/${name}-single-source-summary.json" <<<"${plan}" >/dev/null
grep -F "generated_multi_source_summary_json=${output_dir}/generated/${name}-multi-source-summary.json" <<<"${plan}" >/dev/null
grep -F "generated_nginx_status_tsv=${output_dir}/generated/${name}-nginx-status.tsv" <<<"${plan}" >/dev/null
grep -F "generated_source_evidence_tsv=${output_dir}/generated/${name}-source-evidence.tsv" <<<"${plan}" >/dev/null
grep -F "generated_host_metrics_tsv=${output_dir}/generated/${name}-host-metrics.tsv" <<<"${plan}" >/dev/null
grep -F "generated_host_metrics_timeline_tsv=${output_dir}/generated/${name}-host-metrics-timeline.tsv" <<<"${plan}" >/dev/null
grep -F "artifact_uri=${artifact_uri}" <<<"${plan}" >/dev/null

echo "[oci-real-multisource-public-evidence-autogen] fixture generation"
generated_env="$(
  OCI_REAL_MULTISOURCE_AUTOGEN_MODE=fixture \
  OCI_REAL_MULTISOURCE_NAME="${name}" \
  OCI_REAL_MULTISOURCE_RUN_ID="${run_id}" \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI="${artifact_uri}" \
    bash "${runner}" | tail -1
)"
test "${generated_env}" = "${output_dir}/${name}-generated-evidence.env"
test -f "${generated_env}"

# shellcheck disable=SC1090
source "${generated_env}"
test "${OCI_REAL_MULTISOURCE_RUN_ID}" = "${run_id}"
test "${OCI_REAL_MULTISOURCE_CONTEXTS}" = "oci-k6-a,oci-k6-b"
test "${OCI_REAL_MULTISOURCE_ARTIFACT_URI}" = "${artifact_uri}"
test "${OCI_REAL_MULTISOURCE_OUTPUT_DIR}" = "${output_dir}"
test -f "${OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON}"
test -f "${OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON}"
test -f "${OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV}"
test -f "${OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV}"
test -f "${OCI_REAL_MULTISOURCE_HOST_METRICS_TSV}"
test -f "${OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV}"

grep -F $'source_name\trun_id\tdocker_context\trealip_remote_addr\tedge_429_rate\tbackend_429_rate\taccepted_p95_ms\taccepted_count\tfairness_ratio\tfive_xx_count\tartifact_uri' \
  "${OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV}" >/dev/null
grep -F $'source-a\treal-multisource-autogen-20260506\toci-k6-a\t198.51.100.10\t0.081\t0\t154.0\t1200\t0.98\t0\tgithub-actions://AquilaXk/aquila-bank/actions/runs/123/source-a' \
  "${OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV}" >/dev/null
grep -F $'source-b\treal-multisource-autogen-20260506\toci-k6-b\t198.51.100.11\t0.089\t0\t158.0\t1180\t1.02\t0\tgithub-actions://AquilaXk/aquila-bank/actions/runs/123/source-b' \
  "${OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV}" >/dev/null
grep -F $'real-multisource-autogen-20260506\tgenerator\tk6-a' "${OCI_REAL_MULTISOURCE_HOST_METRICS_TSV}" >/dev/null
grep -F $'real-multisource-autogen-20260506\tgenerator\tk6-b' "${OCI_REAL_MULTISOURCE_HOST_METRICS_TSV}" >/dev/null
grep -F $'real-multisource-autogen-20260506\ttarget\toci-a1-staging' "${OCI_REAL_MULTISOURCE_HOST_METRICS_TSV}" >/dev/null
grep -F $'\tload-coupled\t' "${OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV}" >/dev/null

echo "[oci-real-multisource-public-evidence-autogen] generated evidence passes gate"
gate_output="$(
  OCI_REAL_MULTISOURCE_NAME="${name}" \
  OCI_REAL_MULTISOURCE_RUN_ID="${OCI_REAL_MULTISOURCE_RUN_ID}" \
  OCI_REAL_MULTISOURCE_CONTEXTS="${OCI_REAL_MULTISOURCE_CONTEXTS}" \
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON="${OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON}" \
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON="${OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON}" \
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV="${OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV}" \
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV="${OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV="${OCI_REAL_MULTISOURCE_HOST_METRICS_TSV}" \
  OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV="${OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV}" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI="${OCI_REAL_MULTISOURCE_ARTIFACT_URI}" \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${output_dir}/gate" \
    "${gate}"
)"
report_md="$(tail -1 <<<"${gate_output}")"
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "true multi-source public traffic evidence: fixed" "${report_md}" >/dev/null

echo "[oci-real-multisource-public-evidence-autogen] one context fails"
if OCI_REAL_MULTISOURCE_AUTOGEN_MODE=fixture \
  OCI_REAL_MULTISOURCE_NAME="${name}-one-context" \
  OCI_REAL_MULTISOURCE_RUN_ID="${run_id}" \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${temp_dir}/one-context" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI="${artifact_uri}" \
    bash "${runner}" >"${temp_dir}/one-context.log" 2>&1; then
  echo "real multisource autogen unexpectedly passed one context" >&2
  exit 1
fi
grep -F "minimum remote Docker contexts required: 2" "${temp_dir}/one-context.log" >/dev/null
grep -F "failure_reason=insufficient-docker-contexts" "${temp_dir}/one-context/real-multisource-autogen-check-one-context-missing-evidence.env" >/dev/null

echo "[oci-real-multisource-public-evidence-autogen] live mode requires base url"
if OCI_REAL_MULTISOURCE_AUTOGEN_MODE=live \
  OCI_REAL_MULTISOURCE_NAME="${name}-live-missing-base-url" \
  OCI_REAL_MULTISOURCE_RUN_ID="${run_id}" \
  OCI_REAL_MULTISOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  OCI_REAL_MULTISOURCE_OUTPUT_DIR="${temp_dir}/live-missing-base-url" \
  OCI_REAL_MULTISOURCE_ARTIFACT_URI="${artifact_uri}" \
    bash "${runner}" >"${temp_dir}/live-missing-base-url.log" 2>&1; then
  echo "real multisource autogen unexpectedly passed live mode without base URL" >&2
  exit 1
fi
grep -F "OCI_REAL_MULTISOURCE_BASE_URL is required for live autogen" "${temp_dir}/live-missing-base-url.log" >/dev/null
grep -F "failure_reason=missing-base-url" "${temp_dir}/live-missing-base-url/${name}-live-missing-base-url-missing-evidence.env" >/dev/null
