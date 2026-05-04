#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-oci-offhost-host-metrics-snapshot.sh"

echo "[oci-offhost-host-metrics-snapshot] shell syntax"
bash -n "${runner}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

echo "[oci-offhost-host-metrics-snapshot] run snapshot"
output="$(
  OCI_OFFHOST_HOST_METRICS_SNAPSHOT_NAME=offhost-snapshot-check \
  OCI_OFFHOST_HOST_METRICS_RUN_ID=offhost-snapshot-run \
  OCI_OFFHOST_GENERATOR_DOCKER_CONTEXT=oci-k6-generator-a \
  OCI_OFFHOST_TARGET_DOCKER_CONTEXT=target \
  OCI_OFFHOST_HOST_METRICS_OUTPUT_DIR="${temp_dir}/snapshot" \
    "${runner}"
)"

generator_tsv="$(grep -F "generator_host_metrics_tsv=" <<<"${output}" | cut -d= -f2-)"
target_tsv="$(grep -F "target_host_metrics_tsv=" <<<"${output}" | cut -d= -f2-)"
summary_json="$(grep -F "summary_json=" <<<"${output}" | cut -d= -f2-)"

test -s "${generator_tsv}"
test -s "${target_tsv}"
test -s "${summary_json}"

grep -F $'run_id\thost_role\thost_name\thost_id\tvm_id\tnetwork_id\tdocker_context\tcpu_pct\trx_mbps\ttx_mbps\tartifact_uri\tsample_count\tcpu_pct_max\trx_mbps_max\ttx_mbps_max' "${generator_tsv}" >/dev/null
grep -F $'run_id\thost_role\thost_name\thost_id\tvm_id\tnetwork_id\tdocker_context\tcpu_pct\trx_mbps\ttx_mbps\tartifact_uri\tsample_count\tcpu_pct_max\trx_mbps_max\ttx_mbps_max' "${target_tsv}" >/dev/null
awk -F '\t' 'NR == 2 && $1 == "offhost-snapshot-run" && $2 == "generator" && $4 != "" && $5 != "" && $6 != "" && $7 == "oci-k6-generator-a" { found = 1 } END { exit !found }' "${generator_tsv}"
awk -F '\t' 'NR == 2 && $1 == "offhost-snapshot-run" && $2 == "target" && $4 != "" && $5 != "" && $6 != "" && $7 == "target" { found = 1 } END { exit !found }' "${target_tsv}"
jq -e '.name == "offhost-snapshot-check" and .run_id == "offhost-snapshot-run" and .generator.tsv != "" and .generator.host_id != "" and .target.tsv != "" and .target.host_id != ""' "${summary_json}" >/dev/null
