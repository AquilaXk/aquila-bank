#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-oci-real-multisource-public-evidence.sh [--print-plan]

Environment:
  OCI_REAL_MULTISOURCE_NAME                       default oci-real-multisource-public-<timestamp>
  OCI_REAL_MULTISOURCE_CONTEXTS                   required comma-separated remote Docker contexts, at least 2
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON required single-source k6 summary JSON
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON  required multi-source k6 summary JSON
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV           required TSV: run,realip_remote_addr,limit_req_status,count
  OCI_REAL_MULTISOURCE_OUTPUT_DIR                 default build/reports/k6/<name>
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

name="${OCI_REAL_MULTISOURCE_NAME:-oci-real-multisource-public-$(date +%Y-%m-%d-%H%M%S)}"
contexts_csv="${OCI_REAL_MULTISOURCE_CONTEXTS:-}"
single_summary="${OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON:-}"
multi_summary="${OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON:-}"
nginx_status_tsv="${OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV:-}"
output_dir="${OCI_REAL_MULTISOURCE_OUTPUT_DIR:-build/reports/k6/${name}}"
multi_source_runner="tools/test/run-k6-transaction-100m-multisource.sh"
replay_gate="tools/test/run-oci-real-ip-multisource-capacity-replay.sh"
contexts_tsv="${output_dir}/${name}-real-multisource-contexts.tsv"
report_md="${output_dir}/${name}-real-multisource-public-evidence.md"

contexts=()

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
    exit 1
  fi
}

parse_contexts() {
  local item
  if [[ -z "${contexts_csv}" ]]; then
    echo "OCI_REAL_MULTISOURCE_CONTEXTS is required" >&2
    exit 1
  fi
  IFS=',' read -r -a contexts <<<"${contexts_csv}"
  for item in "${contexts[@]}"; do
    if [[ -z "${item}" ]]; then
      echo "OCI_REAL_MULTISOURCE_CONTEXTS must not contain empty entries" >&2
      exit 1
    fi
  done
  if [[ "${#contexts[@]}" -lt 2 ]]; then
    echo "OCI_REAL_MULTISOURCE_CONTEXTS requires at least 2 remote Docker contexts" >&2
    exit 1
  fi
}

print_plan() {
  parse_contexts
  echo "[oci-real-multisource-public-evidence] name=${name}"
  echo "[oci-real-multisource-public-evidence] docker_contexts=${contexts_csv}"
  echo "[oci-real-multisource-public-evidence] docker_context_count=${#contexts[@]}"
  echo "[oci-real-multisource-public-evidence] single_source_summary=${single_summary:-missing}"
  echo "[oci-real-multisource-public-evidence] multi_source_summary=${multi_summary:-missing}"
  echo "[oci-real-multisource-public-evidence] nginx_status_tsv=${nginx_status_tsv:-missing}"
  echo "[oci-real-multisource-public-evidence] multi_source_runner=${multi_source_runner}"
  echo "[oci-real-multisource-public-evidence] replay_gate=${replay_gate}"
  echo "[oci-real-multisource-public-evidence] contexts_tsv=${contexts_tsv}"
  echo "[oci-real-multisource-public-evidence] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON" "${single_summary}"
  require_file "OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON" "${multi_summary}"
  require_file "OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV" "${nginx_status_tsv}"
  exit 0
fi

require_file "OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON" "${single_summary}"
require_file "OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON" "${multi_summary}"
require_file "OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV" "${nginx_status_tsv}"
mkdir -p "${output_dir}"

{
  printf "index\tdocker_context\n"
  for i in "${!contexts[@]}"; do
    printf "%s\t%s\n" "$((i + 1))" "${contexts[${i}]}"
  done
} >"${contexts_tsv}"

replay_output="$(
  OCI_REAL_IP_REPLAY_NAME="${name}" \
  OCI_REAL_IP_REPLAY_SINGLE_SOURCE_SUMMARY_JSON="${single_summary}" \
  OCI_REAL_IP_REPLAY_MULTI_SOURCE_SUMMARY_JSON="${multi_summary}" \
  OCI_REAL_IP_REPLAY_NGINX_STATUS_TSV="${nginx_status_tsv}" \
  OCI_REAL_IP_REPLAY_OUTPUT_DIR="${output_dir}" \
    "${replay_gate}"
)"
replay_report="$(tail -1 <<<"${replay_output}")"
replay_summary="${output_dir}/${name}-real-ip-replay.tsv"

cat >"${report_md}" <<REPORT
# OCI Real Multi-Source Public Evidence

## Summary

- gate_status=pass
- docker context count: ${#contexts[@]}
- single-source vs multi-source comparison: fixed
- real IP bucket split: delegated to replay gate
- multi-source runner: ${multi_source_runner}
- replay gate report: ${replay_report}

## Contract Notes

- 실제 public traffic evidence는 최소 2개 remote Docker context에서 생성한 multi-source run만 운영 후보로 인정한다.
- single-source run은 NAT/shared-client diagnostic baseline으로만 비교한다.
- Nginx real IP bucket 분리는 replay gate의 realip_remote_addr 집계로 검증한다.

## Artifacts

- contexts TSV: ${contexts_tsv}
- replay summary TSV: ${replay_summary}
- single-source summary JSON: ${single_summary}
- multi-source summary JSON: ${multi_summary}
- Nginx status TSV: ${nginx_status_tsv}
REPORT

echo "${report_md}"
