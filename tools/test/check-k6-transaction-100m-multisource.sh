#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-k6-transaction-100m-multisource.sh"

echo "[k6-transaction-100m-multisource] shell syntax"
bash -n "${runner}"

echo "[k6-transaction-100m-multisource] print plan"
plan="$(
  K6_MULTI_SOURCE_NAME=transaction-read-multisource-check \
  K6_MULTI_SOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  K6_MULTI_SOURCE_BASE_URLS=http://10.0.1.10:8080,http://10.0.2.10:8080 \
  K6_MULTI_SOURCE_PROMETHEUS_RW_SERVER_URLS=http://10.0.1.20:9090/api/v1/write,http://10.0.2.20:9090/api/v1/write \
  K6_MULTI_SOURCE_REMOTE_WORKDIRS=/srv/aquila-bank-a,/srv/aquila-bank-b \
  K6_MULTI_SOURCE_SOURCE_NAMES=source-a,source-b \
  K6_MULTI_SOURCE_GENERATOR_HOST_METRICS_TSVS=oci://evidence/host/k6-a.tsv,oci://evidence/host/k6-b.tsv \
  K6_MULTI_SOURCE_TARGET_HOST_METRICS_TSV=oci://evidence/host/target.tsv \
  K6_MULTI_SOURCE_EVIDENCE_ARTIFACT_URI=oci://evidence/transaction-read/multisource \
  K6_MULTI_SOURCE_RUN_ID_PREFIX=weighted-10m \
  K6_WORKLOAD_SHAPE=weighted-random \
  K6_DURATION=10m \
    "${runner}" --print-plan
)"
grep -F "name=transaction-read-multisource-check" <<<"${plan}" >/dev/null
grep -F "source boundary=multi-source-real-ip" <<<"${plan}" >/dev/null
grep -F "shard_count=2" <<<"${plan}" >/dev/null
grep -F "child runner=tools/test/run-k6-transaction-100m-loadtest.sh" <<<"${plan}" >/dev/null
grep -F "child generator mode=docker-context" <<<"${plan}" >/dev/null
grep -F "target_host_metrics_tsv=oci://evidence/host/target.tsv" <<<"${plan}" >/dev/null
grep -F "evidence_artifact_uri=oci://evidence/transaction-read/multisource" <<<"${plan}" >/dev/null
grep -F "shard=1 source=source-a context=oci-k6-a base_url=http://10.0.1.10:8080 prometheus_rw=http://10.0.1.20:9090/api/v1/write workdir=/srv/aquila-bank-a run_id=weighted-10m-shard-1 report_name=weighted-10m-shard-1 generator_host_metrics=oci://evidence/host/k6-a.tsv" <<<"${plan}" >/dev/null
grep -F "shard=2 source=source-b context=oci-k6-b base_url=http://10.0.2.10:8080 prometheus_rw=http://10.0.2.20:9090/api/v1/write workdir=/srv/aquila-bank-b run_id=weighted-10m-shard-2 report_name=weighted-10m-shard-2 generator_host_metrics=oci://evidence/host/k6-b.tsv" <<<"${plan}" >/dev/null
grep -F "shared_env K6_WORKLOAD_SHAPE=weighted-random K6_DURATION=10m" <<<"${plan}" >/dev/null
grep -F "execution_manifest_tsv=build/reports/k6/transaction-read-multisource-check/transaction-read-multisource-check-execution-manifest.tsv" <<<"${plan}" >/dev/null
grep -F "execution_report_md=build/reports/k6/transaction-read-multisource-check/transaction-read-multisource-check-execution-manifest.md" <<<"${plan}" >/dev/null

echo "[k6-transaction-100m-multisource] shared base URL"
shared_plan="$(
  K6_MULTI_SOURCE_NAME=transaction-read-multisource-shared-base-check \
  K6_MULTI_SOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  K6_MULTI_SOURCE_BASE_URLS=http://10.0.1.10:8080 \
  K6_MULTI_SOURCE_RUN_ID_PREFIX=shared-base \
    "${runner}" --print-plan
)"
grep -F "shard_count=2" <<<"${shared_plan}" >/dev/null
grep -F "shard=1 source=source-1 context=oci-k6-a base_url=http://10.0.1.10:8080 prometheus_rw=disabled" <<<"${shared_plan}" >/dev/null
grep -F "shard=2 source=source-2 context=oci-k6-b base_url=http://10.0.1.10:8080 prometheus_rw=disabled" <<<"${shared_plan}" >/dev/null

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
stub_runner="${temp_dir}/stub-k6.sh"
stub_log="${temp_dir}/stub-runs.tsv"
output_dir="${temp_dir}/output"
cat >"${stub_runner}" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
  "${K6_RUN_ID}" \
  "${K6_RUN_SOURCE_NAME}" \
  "${K6_DOCKER_CONTEXT}" \
  "${K6_REMOTE_BASE_URL}" \
  "${K6_GENERATOR_HOST_METRICS_TSV}" \
  "${K6_TARGET_HOST_METRICS_TSV}" \
  "${K6_EVIDENCE_ARTIFACT_URI}" \
  "$*" >>"${K6_STUB_LOG}"
SH
chmod +x "${stub_runner}"

echo "[k6-transaction-100m-multisource] execution manifest"
K6_STUB_LOG="${stub_log}" \
K6_MULTI_SOURCE_NAME=transaction-read-multisource-run-check \
K6_MULTI_SOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
K6_MULTI_SOURCE_BASE_URLS=http://10.0.1.10:8080 \
K6_MULTI_SOURCE_SOURCE_NAMES=source-a,source-b \
K6_MULTI_SOURCE_GENERATOR_HOST_METRICS_TSVS=oci://evidence/host/k6-a.tsv,oci://evidence/host/k6-b.tsv \
K6_MULTI_SOURCE_TARGET_HOST_METRICS_TSV=oci://evidence/host/target.tsv \
K6_MULTI_SOURCE_EVIDENCE_ARTIFACT_URI=oci://evidence/transaction-read/multisource \
K6_MULTI_SOURCE_RUN_ID_PREFIX=run-pack \
K6_MULTI_SOURCE_OUTPUT_DIR="${output_dir}" \
K6_MULTI_SOURCE_PARALLEL=false \
K6_MULTI_SOURCE_CHILD_RUNNER="${stub_runner}" \
  "${runner}" --no-up --no-deps >/dev/null
manifest_tsv="${output_dir}/transaction-read-multisource-run-check-execution-manifest.tsv"
report_md="${output_dir}/transaction-read-multisource-run-check-execution-manifest.md"
grep -F $'shard\tsource_name\tdocker_context\tbase_url\tprometheus_rw\tworkdir\trun_id\treport_name\tgenerator_host_metrics_tsv\ttarget_host_metrics_tsv\tevidence_artifact_uri' "${manifest_tsv}" >/dev/null
grep -F $'1\tsource-a\toci-k6-a\thttp://10.0.1.10:8080\tdisabled' "${manifest_tsv}" >/dev/null
grep -F $'2\tsource-b\toci-k6-b\thttp://10.0.1.10:8080\tdisabled' "${manifest_tsv}" >/dev/null
grep -F "target host metrics TSV: oci://evidence/host/target.tsv" "${report_md}" >/dev/null
grep -F "evidence artifact URI: oci://evidence/transaction-read/multisource" "${report_md}" >/dev/null
grep -F $'run-pack-shard-1\tsource-a\toci-k6-a\thttp://10.0.1.10:8080\toci://evidence/host/k6-a.tsv\toci://evidence/host/target.tsv\toci://evidence/transaction-read/multisource\t--no-up --no-deps' "${stub_log}" >/dev/null
grep -F $'run-pack-shard-2\tsource-b\toci-k6-b\thttp://10.0.1.10:8080\toci://evidence/host/k6-b.tsv\toci://evidence/host/target.tsv\toci://evidence/transaction-read/multisource\t--no-up --no-deps' "${stub_log}" >/dev/null

echo "[k6-transaction-100m-multisource] invalid context"
if K6_MULTI_SOURCE_NAME=transaction-read-multisource-invalid \
  K6_MULTI_SOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  K6_MULTI_SOURCE_BASE_URLS=http://10.0.1.10:8080,http://10.0.2.10:8080,http://10.0.3.10:8080 \
    "${runner}" --print-plan >/dev/null 2>&1; then
  echo "multi-source runner unexpectedly accepted mismatched base URLs" >&2
  exit 1
fi
if K6_MULTI_SOURCE_NAME=transaction-read-multisource-invalid-source \
  K6_MULTI_SOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  K6_MULTI_SOURCE_BASE_URLS=http://10.0.1.10:8080 \
  K6_MULTI_SOURCE_SOURCE_NAMES=source-a \
    "${runner}" --print-plan >/dev/null 2>&1; then
  echo "multi-source runner unexpectedly accepted mismatched source names" >&2
  exit 1
fi
if K6_MULTI_SOURCE_NAME=transaction-read-multisource-invalid-host-metrics \
  K6_MULTI_SOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  K6_MULTI_SOURCE_BASE_URLS=http://10.0.1.10:8080 \
  K6_MULTI_SOURCE_GENERATOR_HOST_METRICS_TSVS=a.tsv,b.tsv,c.tsv \
    "${runner}" --print-plan >/dev/null 2>&1; then
  echo "multi-source runner unexpectedly accepted mismatched generator host metrics" >&2
  exit 1
fi
