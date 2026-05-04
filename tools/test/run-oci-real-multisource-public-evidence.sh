#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-oci-real-multisource-public-evidence.sh [--print-plan]

Environment:
  OCI_REAL_MULTISOURCE_NAME                       default oci-real-multisource-public-<timestamp>
  OCI_REAL_MULTISOURCE_RUN_ID                     default same as OCI_REAL_MULTISOURCE_NAME
  OCI_REAL_MULTISOURCE_CONTEXTS                   required comma-separated remote Docker contexts, at least 2
  OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON required single-source k6 summary JSON
  OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON  required multi-source k6 summary JSON
  OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV           required TSV: run,realip_remote_addr,limit_req_status,count
  OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV        required TSV: source_name,run_id,docker_context,realip_remote_addr,edge_429_rate,backend_429_rate,accepted_p95_ms,accepted_count,fairness_ratio,five_xx_count,artifact_uri
  OCI_REAL_MULTISOURCE_HOST_METRICS_TSV           required TSV: run_id,host_role,host_name,host_id,vm_id,network_id,docker_context,cpu_pct,rx_mbps,tx_mbps,artifact_uri
  OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV  required host metrics timeline TSV
  OCI_REAL_MULTISOURCE_ARTIFACT_URI               required evidence artifact reference
  OCI_REAL_MULTISOURCE_MIN_FAIRNESS_RATIO         default 0.80
  OCI_REAL_MULTISOURCE_MAX_FAIRNESS_RATIO         default 1.25
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
run_id="${OCI_REAL_MULTISOURCE_RUN_ID:-${name}}"
contexts_csv="${OCI_REAL_MULTISOURCE_CONTEXTS:-}"
single_summary="${OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON:-}"
multi_summary="${OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON:-}"
nginx_status_tsv="${OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV:-}"
source_evidence_tsv="${OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV:-}"
host_metrics_tsv="${OCI_REAL_MULTISOURCE_HOST_METRICS_TSV:-}"
host_metrics_timeline_tsv="${OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV:-}"
artifact_uri="${OCI_REAL_MULTISOURCE_ARTIFACT_URI:-}"
min_fairness_ratio="${OCI_REAL_MULTISOURCE_MIN_FAIRNESS_RATIO:-0.80}"
max_fairness_ratio="${OCI_REAL_MULTISOURCE_MAX_FAIRNESS_RATIO:-1.25}"
output_dir="${OCI_REAL_MULTISOURCE_OUTPUT_DIR:-build/reports/k6/${name}}"
multi_source_runner="tools/test/run-k6-transaction-100m-multisource.sh"
replay_gate="tools/test/run-oci-real-ip-multisource-capacity-replay.sh"
timeline_gate="tools/test/run-oci-offhost-host-metrics-timeline.sh"
contexts_tsv="${output_dir}/${name}-real-multisource-contexts.tsv"
source_summary_tsv="${output_dir}/${name}-real-multisource-source-summary.tsv"
host_metrics_summary_tsv="${output_dir}/${name}-real-multisource-host-metrics.tsv"
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

require_non_empty() {
  local key="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "${key} is required" >&2
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

validate_source_evidence() {
  require_file "OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV" "${source_evidence_tsv}"
  awk -F '\t' -v min_sources="${#contexts[@]}" -v expected_run_id="${run_id}" \
    -v min_fairness_ratio="${min_fairness_ratio}" -v max_fairness_ratio="${max_fairness_ratio}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) col[$i] = i
      split("source_name run_id docker_context realip_remote_addr edge_429_rate backend_429_rate accepted_p95_ms accepted_count fairness_ratio five_xx_count artifact_uri", required, " ")
      for (i in required) {
        if (!(required[i] in col)) {
          printf "missing required column: %s\n", required[i] > "/dev/stderr"
          exit 2
        }
      }
      print
      next
    }
    {
      source_name = $(col["source_name"])
      source_run_id = $(col["run_id"])
      context = $(col["docker_context"])
      real_ip = $(col["realip_remote_addr"])
      source_artifact_uri = $(col["artifact_uri"])
      edge_429 = $(col["edge_429_rate"]) + 0
      backend_429 = $(col["backend_429_rate"]) + 0
      p95 = $(col["accepted_p95_ms"]) + 0
      accepted_count = $(col["accepted_count"]) + 0
      fairness_ratio = $(col["fairness_ratio"]) + 0
      five_xx = $(col["five_xx_count"]) + 0
      if (source_name == "" || source_run_id == "" || context == "" || real_ip == "" || source_artifact_uri == "") {
        print "source evidence contains blank source/run/context/real IP/artifact" > "/dev/stderr"
        exit 3
      }
      if (source_run_id != expected_run_id) {
        printf "source evidence run id mismatch: source=%s run_id=%s expected=%s\n", source_name, source_run_id, expected_run_id > "/dev/stderr"
        exit 7
      }
      if (edge_429 < 0 || edge_429 > 1 || backend_429 < 0 || backend_429 > 1 || p95 <= 0 || accepted_count <= 0 || fairness_ratio < min_fairness_ratio || fairness_ratio > max_fairness_ratio || five_xx != 0) {
        printf "source evidence metric out of contract: source=%s edge429=%s backend429=%s p95=%s accepted=%s fairness=%s five_xx=%s\n", source_name, edge_429, backend_429, p95, accepted_count, fairness_ratio, five_xx > "/dev/stderr"
        exit 4
      }
      sources[source_name] = 1
      real_ips[real_ip] = 1
      print
    }
    END {
      if (NR <= 1) {
        print "source evidence is empty" > "/dev/stderr"
        exit 5
      }
      for (source in sources) source_count++
      for (ip in real_ips) real_ip_count++
      if (source_count < min_sources || real_ip_count < min_sources) {
        printf "source evidence requires at least %s distinct sources and real IPs: sources=%s real_ips=%s\n", min_sources, source_count, real_ip_count > "/dev/stderr"
        exit 6
      }
    }
  ' "${source_evidence_tsv}" >"${source_summary_tsv}"
}

validate_host_metrics() {
  require_file "OCI_REAL_MULTISOURCE_HOST_METRICS_TSV" "${host_metrics_tsv}"
  awk -F '\t' -v expected_run_id="${run_id}" -v contexts_csv="${contexts_csv}" '
    function is_number(value) {
      return value ~ /^[0-9]+([.][0-9]+)?$/
    }
    BEGIN {
      split(contexts_csv, context_items, ",")
      for (i in context_items) {
        if (context_items[i] != "") {
          expected_contexts[context_items[i]] = 1
          expected_context_count++
        }
      }
    }
    NR == 1 {
      for (i = 1; i <= NF; i++) col[$i] = i
      split("run_id host_role host_name host_id vm_id network_id docker_context cpu_pct rx_mbps tx_mbps artifact_uri", required, " ")
      for (i in required) {
        if (!(required[i] in col)) {
          printf "missing required host metric column: %s\n", required[i] > "/dev/stderr"
          exit 2
        }
      }
      print
      next
    }
    {
      host_run_id = $(col["run_id"])
      role = $(col["host_role"])
      host_name = $(col["host_name"])
      host_id = $(col["host_id"])
      vm_id = $(col["vm_id"])
      network_id = $(col["network_id"])
      context = $(col["docker_context"])
      cpu = $(col["cpu_pct"])
      rx = $(col["rx_mbps"])
      tx = $(col["tx_mbps"])
      host_artifact_uri = $(col["artifact_uri"])
      if (host_run_id != expected_run_id) {
        printf "host metric run id mismatch: host=%s run_id=%s expected=%s\n", host_name, host_run_id, expected_run_id > "/dev/stderr"
        exit 3
      }
      if (role != "generator" && role != "target") {
        printf "host metric role must be generator or target: host=%s role=%s\n", host_name, role > "/dev/stderr"
        exit 4
      }
      if (host_name == "" || host_id == "" || vm_id == "" || network_id == "" || context == "" || host_artifact_uri == "") {
        print "host metric contains blank host/identity/context/artifact" > "/dev/stderr"
        exit 5
      }
      if (!is_number(cpu) || !is_number(rx) || !is_number(tx)) {
        printf "host metric cpu/network values must be non-negative numbers: host=%s cpu=%s rx=%s tx=%s\n", host_name, cpu, rx, tx > "/dev/stderr"
        exit 6
      }
      if (role == "generator") {
        if (!(context in expected_contexts)) {
          printf "generator host metric context is not in multi-source contexts: context=%s\n", context > "/dev/stderr"
          exit 7
        }
        generator_contexts[context] = 1
        generator_host_names[host_name] = 1
        generator_host_ids[host_id] = 1
        generator_vm_ids[vm_id] = 1
        generator_network_ids[network_id] = 1
      }
      if (role == "target") {
        target_count++
        target_host_names[host_name] = 1
        target_host_ids[host_id] = 1
        target_vm_ids[vm_id] = 1
        target_network_ids[network_id] = 1
      }
      print
    }
    END {
      if (NR <= 1) {
        print "host metrics evidence is empty" > "/dev/stderr"
        exit 8
      }
      for (context in expected_contexts) {
        if (!(context in generator_contexts)) {
          printf "missing generator host metrics for context: %s\n", context > "/dev/stderr"
          exit 9
        }
      }
      if (target_count < 1) {
        print "missing target host metrics" > "/dev/stderr"
        exit 10
      }
      for (item in generator_host_names) {
        if (item in target_host_names) {
          printf "generator and target host/VM/network identity must differ: host_name=%s\n", item > "/dev/stderr"
          exit 11
        }
      }
      for (item in generator_host_ids) {
        if (item in target_host_ids) {
          printf "generator and target host/VM/network identity must differ: host_id=%s\n", item > "/dev/stderr"
          exit 12
        }
      }
      for (item in generator_vm_ids) {
        if (item in target_vm_ids) {
          printf "generator and target host/VM/network identity must differ: vm_id=%s\n", item > "/dev/stderr"
          exit 13
        }
      }
      for (item in generator_network_ids) {
        if (item in target_network_ids) {
          printf "generator and target host/VM/network identity must differ: network_id=%s\n", item > "/dev/stderr"
          exit 14
        }
      }
    }
  ' "${host_metrics_tsv}" >"${host_metrics_summary_tsv}"
}

print_plan() {
  parse_contexts
  echo "[oci-real-multisource-public-evidence] name=${name}"
  echo "[oci-real-multisource-public-evidence] run_id=${run_id}"
  echo "[oci-real-multisource-public-evidence] docker_contexts=${contexts_csv}"
  echo "[oci-real-multisource-public-evidence] docker_context_count=${#contexts[@]}"
  echo "[oci-real-multisource-public-evidence] true_multi_source_required=true"
  echo "[oci-real-multisource-public-evidence] source_fairness_required=true"
  echo "[oci-real-multisource-public-evidence] fairness_ratio_range=${min_fairness_ratio}..${max_fairness_ratio}"
  echo "[oci-real-multisource-public-evidence] minimum_remote_docker_contexts=2"
  echo "[oci-real-multisource-public-evidence] single_source_summary=${single_summary:-missing}"
  echo "[oci-real-multisource-public-evidence] multi_source_summary=${multi_summary:-missing}"
  echo "[oci-real-multisource-public-evidence] nginx_status_tsv=${nginx_status_tsv:-missing}"
  echo "[oci-real-multisource-public-evidence] source_evidence_tsv=${source_evidence_tsv:-missing}"
  echo "[oci-real-multisource-public-evidence] host_metrics_tsv=${host_metrics_tsv:-missing}"
  echo "[oci-real-multisource-public-evidence] host_metrics_timeline_tsv=${host_metrics_timeline_tsv:-missing}"
  echo "[oci-real-multisource-public-evidence] artifact_uri=${artifact_uri:-missing}"
  echo "[oci-real-multisource-public-evidence] multi_source_runner=${multi_source_runner}"
  echo "[oci-real-multisource-public-evidence] replay_gate=${replay_gate}"
  echo "[oci-real-multisource-public-evidence] timeline_gate=${timeline_gate}"
  echo "[oci-real-multisource-public-evidence] contexts_tsv=${contexts_tsv}"
  echo "[oci-real-multisource-public-evidence] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  require_file "OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON" "${single_summary}"
  require_file "OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON" "${multi_summary}"
  require_file "OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV" "${nginx_status_tsv}"
  require_file "OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV" "${source_evidence_tsv}"
  require_file "OCI_REAL_MULTISOURCE_HOST_METRICS_TSV" "${host_metrics_tsv}"
  require_file "OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV" "${host_metrics_timeline_tsv}"
  exit 0
fi

require_file "OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON" "${single_summary}"
require_file "OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON" "${multi_summary}"
require_file "OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV" "${nginx_status_tsv}"
require_file "OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV" "${source_evidence_tsv}"
require_file "OCI_REAL_MULTISOURCE_HOST_METRICS_TSV" "${host_metrics_tsv}"
require_file "OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV" "${host_metrics_timeline_tsv}"
require_non_empty "OCI_REAL_MULTISOURCE_ARTIFACT_URI" "${artifact_uri}"
mkdir -p "${output_dir}"

{
  printf "index\tdocker_context\n"
  for i in "${!contexts[@]}"; do
    printf "%s\t%s\n" "$((i + 1))" "${contexts[${i}]}"
  done
} >"${contexts_tsv}"

validate_source_evidence
validate_host_metrics

timeline_output="$(
  OCI_OFFHOST_HOST_METRICS_TIMELINE_NAME="${name}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_RUN_ID="${run_id}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_INPUT_TSV="${host_metrics_timeline_tsv}" \
  OCI_OFFHOST_HOST_METRICS_TIMELINE_OUTPUT_DIR="${output_dir}" \
    "${timeline_gate}"
)"
timeline_report="$(tail -1 <<<"${timeline_output}")"

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
- actual execution run id: ${run_id}
- artifact reference: ${artifact_uri}
- docker context count: ${#contexts[@]}
- true multi-source public traffic evidence: fixed
- source fairness: verified
- single-source vs multi-source comparison: fixed
- real IP bucket split: verified
- source-level real IP/429/latency/fairness split: verified
- host-level CPU/network split: verified
- host metrics timeline: verified
- multi-source runner: ${multi_source_runner}
- replay gate report: ${replay_report}
- timeline gate report: ${timeline_report}

## Contract Notes

- 실제 public traffic evidence는 최소 2개 remote Docker context에서 생성한 multi-source run만 운영 후보로 인정한다.
- single-source run은 NAT/shared-client diagnostic baseline으로만 비교한다.
- Nginx real IP bucket 분리는 replay gate의 realip_remote_addr 집계로 검증한다.
- source fairness는 source별 accepted count와 fairness ratio를 함께 남겨 shared per-IP edge limit 착시를 배제한다.

## Artifacts

- contexts TSV: ${contexts_tsv}
- source summary TSV: ${source_summary_tsv}
- host metrics TSV: ${host_metrics_summary_tsv}
- host metrics timeline TSV: ${host_metrics_timeline_tsv}
- replay summary TSV: ${replay_summary}
- single-source summary JSON: ${single_summary}
- multi-source summary JSON: ${multi_summary}
- Nginx status TSV: ${nginx_status_tsv}
REPORT

echo "${report_md}"
